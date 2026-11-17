/*
Copyright 2026 The Spice.ai OSS Authors

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
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

/**
 * Tests for refreshDataset against a local HTTP server.
 */
public class RefreshHttpTest extends TestCase {

    private HttpServer httpServer;
    private TestFlightSqlServer flightServer;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastRawPath = new AtomicReference<>();
    private final AtomicReference<String> lastRawQuery = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastUserAgentHeader = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private volatile int responseCode = 201;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/", this::handle);
        httpServer.start();
    }

    @Override
    protected void tearDown() throws Exception {
        httpServer.stop(0);
        if (this.flightServer != null) {
            this.flightServer.close();
            this.flightServer = null;
        }
        super.tearDown();
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath.set(exchange.getRequestURI().getPath());
        lastRawPath.set(exchange.getRequestURI().getRawPath());
        lastRawQuery.set(exchange.getRequestURI().getRawQuery());
        lastUserAgentHeader.set(exchange.getRequestHeaders().getFirst("X-Spice-User-Agent"));
        lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
        try (InputStream body = exchange.getRequestBody()) {
            lastBody.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        exchange.sendResponseHeaders(responseCode, -1);
        exchange.close();
    }

    private SpiceClient newClient() throws Exception {
        return newClient(null);
    }

    private SpiceClient newClient(String apiKey) throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withHttpAddress(new URI("http://localhost:" + httpServer.getAddress().getPort()));
        if (apiKey != null) {
            // withApiKey() makes the constructor perform a real Flight handshake, so an
            // authenticated client needs a real (test) Flight endpoint to handshake against.
            String appId = apiKey.split("\\|")[0];
            this.flightServer = new TestFlightSqlServer(appId, apiKey);
            builder = builder.withFlightAddress(this.flightServer.flightUri()).withApiKey(apiKey);
        }
        return builder.build();
    }

    public void testRefreshDataset() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("taxi_trips");
            assertEquals("/v1/datasets/taxi_trips/acceleration/refresh", lastPath.get());
            assertEquals("{}", lastBody.get());
            assertNotNull(lastUserAgentHeader.get());
            assertTrue(lastUserAgentHeader.get().startsWith("spice-java/"));
            assertNull(lastApiKeyHeader.get());
        }
    }

    public void testRefreshDatasetSendsApiKeyWhenConfigured() throws Exception {
        try (SpiceClient client = newClient("test-app|test-key")) {
            client.refreshDataset("taxi_trips");
            assertEquals("test-app|test-key", lastApiKeyHeader.get());
        }
    }

    public void testRefreshDatasetWithOptions() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("taxi_trips", new RefreshOptions()
                    .withRefreshMode("full")
                    .withRefreshSql("SELECT * FROM taxi_trips"));
            assertTrue(lastBody.get().contains("\"refresh_mode\":\"full\""));
            assertTrue(lastBody.get().contains("\"refresh_sql\":\"SELECT * FROM taxi_trips\""));
        }
    }

    public void testRefreshDatasetNon201Throws() throws Exception {
        responseCode = 500;
        try (SpiceClient client = newClient()) {
            client.refreshDataset("taxi_trips");
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("500"));
        }
    }

    public void testRefreshDatasetKeepsQualifiedName() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("spice.public.taxi_trips");
            assertEquals("/v1/datasets/spice.public.taxi_trips/acceleration/refresh", lastRawPath.get());
        }
    }

    public void testRefreshDatasetEncodesNameAsOnePathSegment() throws Exception {
        // A name carrying '?' or '#' must stay inside the dataset segment
        // instead of ending the path early.
        try (SpiceClient client = newClient()) {
            client.refreshDataset("status?x=1#");
            assertEquals("/v1/datasets/status%3Fx%3D1%23/acceleration/refresh", lastRawPath.get());
            assertNull("Name must not start a query string", lastRawQuery.get());
        }
    }

    public void testRefreshDatasetRejectsPathSeparators() throws Exception {
        // Encoding a separator is not enough: a server or proxy that decodes the
        // path before routing (this JDK test server picks its handler from the
        // decoded path) turns "orders%2Fcdc" back into "orders/cdc" and sends
        // the authenticated POST to another route, here the CDC write endpoint.
        // '%' is rejected too, so a second decode cannot produce a separator.
        AtomicReference<String> cdcRawPath = new AtomicReference<>();
        httpServer.createContext("/v1/datasets/orders/cdc", exchange -> {
            cdcRawPath.set(exchange.getRequestURI().getRawPath());
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        List<String> accepted = new ArrayList<>();
        try (SpiceClient client = newClient()) {
            for (String name : new String[] { "orders/cdc", "orders\\cdc", "orders%2Fcdc" }) {
                try {
                    client.refreshDataset(name);
                    accepted.add(name);
                } catch (IllegalArgumentException expected) {
                    // expected
                }
            }
        }
        assertNull("A dataset name reached another route: " + cdcRawPath.get(), cdcRawPath.get());
        assertEquals("Names with separators or '%' must be rejected", Collections.emptyList(), accepted);
        assertNull("No request should be sent", lastPath.get());
    }

    public void testRefreshDatasetEncodesSpaceAndPlus() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("my data+set");
            assertEquals("/v1/datasets/my%20data%2Bset/acceleration/refresh", lastRawPath.get());
            assertEquals("/v1/datasets/my data+set/acceleration/refresh", lastPath.get());
        }
    }

    public void testRefreshDatasetRejectsUnpairedSurrogate() throws Exception {
        // UTF-8 cannot encode an unpaired surrogate; URLEncoder would silently
        // substitute '?', refreshing "orders_?" instead of rejecting the name.
        try (SpiceClient client = newClient()) {
            for (String name : new String[] { "orders_\uD800", "orders_\uDC00x" }) {
                try {
                    client.refreshDataset(name);
                    fail("Expected IllegalArgumentException, but sent " + lastRawPath.get());
                } catch (IllegalArgumentException expected) {
                    // expected
                }
            }
            assertNull("No request should be sent", lastPath.get());
        }
    }

    public void testRefreshDatasetRejectsDotSegments() throws Exception {
        try (SpiceClient client = newClient()) {
            for (String name : new String[] { ".", ".." }) {
                try {
                    client.refreshDataset(name);
                    fail("Expected IllegalArgumentException for " + name);
                } catch (IllegalArgumentException expected) {
                    // expected
                }
            }
            assertNull("No request should be sent", lastPath.get());
        }
    }

    public void testRefreshDatasetEmptyNameThrows() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
