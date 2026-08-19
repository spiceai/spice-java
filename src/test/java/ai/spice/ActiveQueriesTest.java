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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SpiceClient#listActiveQueries()} and
 * {@link SpiceClient#cancelActiveQuery(String)} against a local JDK
 * {@link HttpServer}, so they need no live Spice runtime.
 */
public class ActiveQueriesTest extends TestCase {

    private static final String VALID_QUERY_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    private HttpServer server;
    private SpiceClient client;
    private TestFlightSqlServer flightServer;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAcceptHeader = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();

    private void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    /** Stubs a canned response for one path, recording the request first. */
    private void stub(String path, int statusCode, String body) {
        this.server.createContext(path, exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().getPath());
            lastAcceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private SpiceClient clientForServer(String apiKey) throws Exception {
        this.server.start();
        URI httpAddress = new URI("http://127.0.0.1:" + this.server.getAddress().getPort());
        SpiceClientBuilder builder = SpiceClient.builder().withHttpAddress(httpAddress);
        if (apiKey != null) {
            // withApiKey() makes the constructor perform a real Flight handshake, so an
            // authenticated client needs a real (test) Flight endpoint to handshake against —
            // these tests only exercise the HTTP path, but SpiceClient always builds its Flight
            // channels eagerly. Without this, the handshake targets the builder's default flight
            // address, where nothing is listening; that failed unpredictably by platform
            // (reliably on Windows CI, only sometimes on Linux/macOS).
            String appId = apiKey.split("\\|")[0];
            this.flightServer = new TestFlightSqlServer(appId, apiKey);
            builder = builder.withFlightAddress(this.flightServer.flightUri()).withApiKey(apiKey);
        }
        return builder.build();
    }

    /** Like {@link #clientForServer(String)}, but with a trailing slash on the base address. */
    private SpiceClient clientForServerWithTrailingSlash() throws Exception {
        this.server.start();
        URI httpAddress = new URI("http://127.0.0.1:" + this.server.getAddress().getPort() + "/");
        return SpiceClient.builder().withHttpAddress(httpAddress).build();
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
        if (this.flightServer != null) {
            this.flightServer.close();
            this.flightServer = null;
        }
        super.tearDown();
    }

    // ==================== listActiveQueries ====================

    public void testListActiveQueriesParsesMultipleQueries() throws Exception {
        startServer();
        stub("/v1/sql/active", 200, "{"
                + "\"queries\":["
                + "{\"query_id\":\"" + VALID_QUERY_ID + "\",\"protocol\":\"flight\","
                + "\"sql_preview\":\"SELECT * FROM taxi_trips\",\"started_at_ms\":1700000000000},"
                + "{\"query_id\":\"9d2c1b3a-1111-2222-3333-444455556666\",\"protocol\":\"http\","
                + "\"sql_preview\":\"SELECT 1\",\"started_at_ms\":1700000001000}"
                + "],\"total_count\":2}");
        this.client = clientForServer(null);

        List<ActiveQuery> queries = this.client.listActiveQueries();

        assertEquals("GET", lastMethod.get());
        assertEquals("/v1/sql/active", lastPath.get());
        assertNull("no API key configured, so none should be sent", lastApiKeyHeader.get());

        assertEquals(2, queries.size());
        assertEquals(VALID_QUERY_ID, queries.get(0).getQueryId());
        assertEquals("flight", queries.get(0).getProtocol());
        assertEquals("SELECT * FROM taxi_trips", queries.get(0).getSqlPreview());
        assertEquals(1700000000000L, queries.get(0).getStartedAtMs());
        assertEquals(Instant.ofEpochMilli(1700000000000L), queries.get(0).getStartedAt());

        assertEquals("http", queries.get(1).getProtocol());
    }

    public void testListActiveQueriesReturnsEmptyListWhenNoneRunning() throws Exception {
        startServer();
        stub("/v1/sql/active", 200, "{\"queries\":[],\"total_count\":0}");
        this.client = clientForServer(null);

        List<ActiveQuery> queries = this.client.listActiveQueries();

        assertTrue(queries.isEmpty());
    }

    public void testListActiveQueriesSendsApiKeyWhenConfigured() throws Exception {
        startServer();
        stub("/v1/sql/active", 200, "{\"queries\":[]}");
        this.client = clientForServer("test-app|test-key");

        this.client.listActiveQueries();

        assertEquals("test-app|test-key", lastApiKeyHeader.get());
    }

    public void testListActiveQueriesForbiddenThrowsSpecificMessage() throws Exception {
        startServer();
        stub("/v1/sql/active", 403, "Forbidden");
        this.client = clientForServer(null);

        try {
            this.client.listActiveQueries();
            fail("a 403 should raise");
        } catch (ExecutionException e) {
            assertTrue("message should explain the fix: " + e.getMessage(),
                    e.getMessage().contains("does not allow listing queries"));
        }
    }

    public void testListActiveQueriesOtherErrorThrowsGenericMessage() throws Exception {
        startServer();
        stub("/v1/sql/active", 500, "internal error");
        this.client = clientForServer(null);

        try {
            this.client.listActiveQueries();
            fail("a 500 should raise");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("500"));
        }
    }

    public void testListActiveQueriesResolvesTrailingSlashBase() throws Exception {
        startServer();
        stub("/v1/sql/active", 200, "{\"queries\":[]}");
        this.client = clientForServerWithTrailingSlash();

        this.client.listActiveQueries();

        assertEquals("a trailing slash on the base address must not double up",
                "/v1/sql/active", lastPath.get());
    }

    public void testListActiveQueriesRejectsMalformedQueriesField() throws Exception {
        startServer();
        // "queries" present but not an array: must not be silently treated as zero
        // active queries.
        stub("/v1/sql/active", 200, "{\"queries\":\"not-an-array\"}");
        this.client = clientForServer(null);

        try {
            this.client.listActiveQueries();
            fail("a malformed queries field should raise");
        } catch (ExecutionException e) {
            assertTrue("expected a malformed-response message: " + e.getMessage(),
                    e.getMessage().contains("unexpected active-queries response"));
        }
    }

    public void testListActiveQueriesRejectsNonObjectBody() throws Exception {
        startServer();
        stub("/v1/sql/active", 200, "[]");
        this.client = clientForServer(null);

        try {
            this.client.listActiveQueries();
            fail("a non-object body should raise");
        } catch (ExecutionException e) {
            assertTrue("expected a malformed-response message: " + e.getMessage(),
                    e.getMessage().contains("unexpected active-queries response"));
        }
    }

    // ==================== cancelActiveQuery ====================

    public void testCancelActiveQuerySucceedsOn200() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 200,
                "{\"query_id\":\"" + VALID_QUERY_ID + "\",\"status\":\"CANCELLED\"}");
        this.client = clientForServer(null);

        this.client.cancelActiveQuery(VALID_QUERY_ID);

        assertEquals("POST", lastMethod.get());
        assertEquals("/v1/sql/" + VALID_QUERY_ID + "/cancel", lastPath.get());
    }

    public void testCancelActiveQueryResolvesTrailingSlashBase() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 200,
                "{\"query_id\":\"" + VALID_QUERY_ID + "\",\"status\":\"CANCELLED\"}");
        this.client = clientForServerWithTrailingSlash();

        this.client.cancelActiveQuery(VALID_QUERY_ID);

        assertEquals("a trailing slash on the base address must not double up",
                "/v1/sql/" + VALID_QUERY_ID + "/cancel", lastPath.get());
    }

    public void testCancelActiveQuerySendsApiKeyWhenConfigured() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 200, "{}");
        this.client = clientForServer("test-app|test-key");

        this.client.cancelActiveQuery(VALID_QUERY_ID);

        assertEquals("test-app|test-key", lastApiKeyHeader.get());
    }

    public void testCancelActiveQueryBadRequestThrowsSpecificMessage() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 400, "bad request");
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery(VALID_QUERY_ID);
            fail("a 400 should raise");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("not a valid UUID"));
        }
    }

    public void testCancelActiveQueryForbiddenThrowsSpecificMessage() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 403, "forbidden");
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery(VALID_QUERY_ID);
            fail("a 403 should raise");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("does not allow cancelling queries"));
        }
    }

    public void testCancelActiveQueryNotFoundThrowsSpecificMessage() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 404, "not found");
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery(VALID_QUERY_ID);
            fail("a 404 should raise");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("no active query"));
        }
    }

    public void testCancelActiveQueryOtherErrorThrowsGenericMessage() throws Exception {
        startServer();
        stub("/v1/sql/" + VALID_QUERY_ID + "/cancel", 500, "internal error");
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery(VALID_QUERY_ID);
            fail("a 500 should raise");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("500"));
        }
    }

    public void testCancelActiveQueryRejectsNullQueryIdWithoutHttpCall() throws Exception {
        startServer();
        // No context is stubbed: any request reaching the server 404s, which the
        // assertions below distinguish from client-side rejection.
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery(null);
            fail("a null queryId should raise IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertNull("no request should have been sent", lastPath.get());
    }

    public void testCancelActiveQueryRejectsEmptyQueryIdWithoutHttpCall() throws Exception {
        startServer();
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery("");
            fail("an empty queryId should raise IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertNull("no request should have been sent", lastPath.get());
    }

    public void testCancelActiveQueryRejectsNonUuidWithoutHttpCall() throws Exception {
        startServer();
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery("not-a-uuid");
            fail("a non-UUID queryId should raise IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not a valid UUID"));
        }
        assertNull("no request should have been sent", lastPath.get());
    }

    public void testCancelActiveQueryRejectsPathTraversalAttemptWithoutHttpCall() throws Exception {
        startServer();
        this.client = clientForServer(null);

        try {
            this.client.cancelActiveQuery("../../etc/passwd");
            fail("a path-traversal-shaped queryId should raise IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertNull("no request should have been sent", lastPath.get());
    }
}
