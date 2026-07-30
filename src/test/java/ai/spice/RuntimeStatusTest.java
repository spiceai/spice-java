/*
Copyright 2024-2026 The Spice.ai OSS Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ai.spice;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

/**
 * Unit tests for the runtime health, readiness, and status endpoints.
 *
 * <p>
 * These run against a JDK {@link HttpServer} bound to an ephemeral port, so they
 * need no live Spice runtime.
 */
public class RuntimeStatusTest extends TestCase {

    private HttpServer server;
    private SpiceClient client;

    /**
     * A canned response for one path.
     */
    private void stub(String path, int statusCode, String body) {
        this.server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private SpiceClient clientForServer(String apiKey) throws Exception {
        this.server.start();
        URI httpAddress = new URI("http://127.0.0.1:" + this.server.getAddress().getPort());
        SpiceClientBuilder builder = SpiceClient.builder().withHttpAddress(httpAddress);
        if (apiKey != null) {
            builder = builder.withApiKey(apiKey);
        }
        return builder.build();
    }

    @Override
    protected void tearDown() throws Exception {
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
        super.tearDown();
    }

    // ==================== isHealthy ====================

    public void testIsHealthyReturnsTrueOnOk() throws Exception {
        startServer();
        stub("/health", 200, "ok");
        this.client = clientForServer(null);

        assertTrue("healthy runtime should report healthy", this.client.isHealthy());
    }

    public void testIsHealthyReturnsFalseOnNonOkStatus() throws Exception {
        startServer();
        stub("/health", 503, "unavailable");
        this.client = clientForServer(null);

        assertFalse("503 should not report healthy", this.client.isHealthy());
    }

    public void testIsHealthyReturnsFalseWhenRuntimeIsUnreachable() throws Exception {
        // Bind a server to claim a port, then stop it so nothing is listening.
        startServer();
        this.client = clientForServer(null);
        this.server.stop(0);
        this.server = null;

        assertFalse("unreachable runtime should report unhealthy, not throw", this.client.isHealthy());
    }

    // ==================== isReady ====================

    public void testIsReadyReturnsTrueWhenReady() throws Exception {
        startServer();
        stub("/v1/ready", 200, "ready");
        this.client = clientForServer(null);

        assertTrue("ready runtime should report ready", this.client.isReady());
    }

    public void testIsReadyReturnsFalseWhileStillLoading() throws Exception {
        startServer();
        stub("/v1/ready", 503, "not ready");
        this.client = clientForServer(null);

        assertFalse("a loading runtime should not report ready", this.client.isReady());
    }

    public void testIsReadyRejectsNotReadyBodyOn200() throws Exception {
        // "not ready" contains "ready", so a substring check would report this
        // loading runtime as ready.
        startServer();
        stub("/v1/ready", 200, "not ready");
        this.client = clientForServer(null);

        assertFalse("a body of \"not ready\" is not ready", this.client.isReady());
    }

    public void testIsReadyToleratesSurroundingWhitespace() throws Exception {
        startServer();
        stub("/v1/ready", 200, "ready\n");
        this.client = clientForServer(null);

        assertTrue("a trailing newline should not matter", this.client.isReady());
    }

    public void testProbeRequestResolvesTrailingSlashBase() throws Exception {
        // A base address with a trailing slash must not yield a doubled slash.
        HttpRequest request = SpiceClient.buildProbeRequest(
                new URI("http://127.0.0.1:8090/"), "/v1/ready", null);

        assertEquals("http://127.0.0.1:8090/v1/ready", request.uri().toString());
    }

    public void testProbeRequestHandlesBaseWithoutTrailingSlash() throws Exception {
        HttpRequest request = SpiceClient.buildProbeRequest(
                new URI("http://127.0.0.1:8090"), "/v1/status", null);

        assertEquals("http://127.0.0.1:8090/v1/status", request.uri().toString());
    }

    // Building a client with an API key eagerly handshakes against Flight, so the
    // authenticated paths are covered at the request-building level instead.

    public void testProbeRequestSendsApiKeyWhenConfigured() throws Exception {
        HttpRequest request = SpiceClient.buildProbeRequest(
                new URI("http://127.0.0.1:8090"), "/v1/ready", "test-app|test-key");

        assertEquals("http://127.0.0.1:8090/v1/ready", request.uri().toString());
        assertEquals("the API key should be sent, as Spice.ai Cloud requires",
                "test-app|test-key", request.headers().firstValue("X-API-Key").orElse(null));
    }

    public void testProbeRequestOmitsApiKeyWhenAbsent() throws Exception {
        HttpRequest request = SpiceClient.buildProbeRequest(
                new URI("http://127.0.0.1:8090"), "/health", null);

        assertFalse("/health is unauthenticated",
                request.headers().firstValue("X-API-Key").isPresent());
        assertTrue("the user agent identifies the SDK",
                request.headers().firstValue("X-Spice-User-Agent").isPresent());
        assertEquals("GET", request.method());
    }

    // ==================== runtimeStatus ====================

    public void testRuntimeStatusParsesAllConnections() throws Exception {
        startServer();
        stub("/v1/status", 200, "["
                + "{\"name\":\"http\",\"endpoint\":\"127.0.0.1:8090\",\"status\":\"Ready\"},"
                + "{\"name\":\"flight\",\"endpoint\":\"127.0.0.1:50051\",\"status\":\"Initializing\"},"
                + "{\"name\":\"metrics\",\"endpoint\":\"N/A\",\"status\":\"Disabled\"},"
                + "{\"name\":\"opentelemetry\",\"endpoint\":\"127.0.0.1:4317\",\"status\":\"Error\"}"
                + "]");
        this.client = clientForServer(null);

        List<ConnectionDetails> status = this.client.runtimeStatus();

        assertEquals(4, status.size());

        assertEquals("http", status.get(0).getName());
        assertEquals("127.0.0.1:8090", status.get(0).getEndpoint());
        assertEquals(ComponentStatus.READY, status.get(0).getStatus());
        assertTrue(status.get(0).isReady());

        assertEquals(ComponentStatus.INITIALIZING, status.get(1).getStatus());
        assertFalse("an initializing component is not ready", status.get(1).isReady());

        assertEquals(ComponentStatus.DISABLED, status.get(2).getStatus());
        assertEquals("N/A", status.get(2).getEndpoint());

        assertEquals(ComponentStatus.ERROR, status.get(3).getStatus());
        assertTrue(status.get(3).getStatus().isError());
    }

    public void testRuntimeStatusThrowsOnErrorStatusCode() throws Exception {
        startServer();
        stub("/v1/status", 500, "Error converting to CSV");
        this.client = clientForServer(null);

        try {
            this.client.runtimeStatus();
            fail("a 500 should raise");
        } catch (ExecutionException e) {
            assertTrue("the message should carry the status code",
                    e.getMessage().contains("500"));
        }
    }

    public void testRuntimeStatusReportsUnreachableRuntimeClearly() throws Exception {
        startServer();
        this.client = clientForServer(null);
        int port = this.server.getAddress().getPort();
        this.server.stop(0);
        this.server = null;

        try {
            this.client.runtimeStatus();
            fail("an unreachable runtime should raise");
        } catch (ExecutionException e) {
            // The message should tell the user what to fix, not leak a stack internal.
            assertTrue("the message should name the address: " + e.getMessage(),
                    e.getMessage().contains(String.valueOf(port)));
        }
    }

    // ==================== parseRuntimeStatus ====================

    public void testParseRuntimeStatusOnEmptyArray() throws Exception {
        assertTrue(SpiceClient.parseRuntimeStatus("[]").isEmpty());
    }

    public void testParseRuntimeStatusToleratesMissingMembers() throws Exception {
        List<ConnectionDetails> status = SpiceClient.parseRuntimeStatus("[{\"name\":\"http\"}]");

        assertEquals(1, status.size());
        assertEquals("http", status.get(0).getName());
        assertNull(status.get(0).getEndpoint());
        assertEquals("an absent status is unknown, not a crash",
                ComponentStatus.UNKNOWN, status.get(0).getStatus());
    }

    public void testParseRuntimeStatusRejectsNonArray() {
        try {
            SpiceClient.parseRuntimeStatus("{\"name\":\"http\"}");
            fail("a JSON object is not a valid status response");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("unexpected status response"));
        }
    }

    public void testParseRuntimeStatusRejectsMalformedJson() {
        try {
            SpiceClient.parseRuntimeStatus("not json at all {{{");
            fail("malformed JSON should raise");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("malformed status response"));
        }
    }

    // ==================== ComponentStatus ====================

    public void testComponentStatusFromWireValue() {
        assertEquals(ComponentStatus.INITIALIZING, ComponentStatus.fromWireValue("Initializing"));
        assertEquals(ComponentStatus.READY, ComponentStatus.fromWireValue("Ready"));
        assertEquals(ComponentStatus.DISABLED, ComponentStatus.fromWireValue("Disabled"));
        assertEquals(ComponentStatus.ERROR, ComponentStatus.fromWireValue("Error"));
        assertEquals(ComponentStatus.REFRESHING, ComponentStatus.fromWireValue("Refreshing"));
        assertEquals(ComponentStatus.SHUTTING_DOWN, ComponentStatus.fromWireValue("ShuttingDown"));
        assertEquals(ComponentStatus.NOT_LOADED, ComponentStatus.fromWireValue("NotLoaded"));
    }

    public void testComponentStatusFallsBackToUnknown() {
        // A newer runtime adding a status must not break an older client.
        assertEquals(ComponentStatus.UNKNOWN, ComponentStatus.fromWireValue("SomethingNew"));
        assertEquals(ComponentStatus.UNKNOWN, ComponentStatus.fromWireValue(null));
    }

    public void testComponentStatusPredicates() {
        assertTrue(ComponentStatus.READY.isReady());
        assertFalse(ComponentStatus.INITIALIZING.isReady());
        assertTrue(ComponentStatus.ERROR.isError());
        assertFalse(ComponentStatus.READY.isError());
    }

    public void testConnectionDetailsKeepsTheRawStatus() {
        ConnectionDetails details = new ConnectionDetails("flight", "127.0.0.1:50051", "SomethingNew");

        assertEquals(ComponentStatus.UNKNOWN, details.getStatus());
        assertEquals("the verbatim value stays available", "SomethingNew", details.getRawStatus());
        assertTrue(details.toString().contains("flight"));
    }
}
