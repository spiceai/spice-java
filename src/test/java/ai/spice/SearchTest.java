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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

/**
 * Tests for {@link SpiceClient#search(SearchRequest)} against a local HTTP
 * server.
 */
public class SearchTest extends TestCase {

    private HttpServer httpServer;
    private TestFlightSqlServer flightServer;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentTypeHeader = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseCode = 200;
    private volatile String responseBody = "{\"results\":[],\"duration_ms\":1}";

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
        requestCount.incrementAndGet();
        lastPath.set(exchange.getRequestURI().getPath());
        lastMethod.set(exchange.getRequestMethod());
        lastContentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
        try (InputStream body = exchange.getRequestBody()) {
            lastBody.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
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
            // Without this, the handshake targets the builder's default flight address, where
            // nothing is listening; that failed unpredictably by platform (reliably on Windows
            // CI, only sometimes on Linux/macOS).
            String appId = apiKey.split("\\|")[0];
            this.flightServer = new TestFlightSqlServer(appId, apiKey);
            builder = builder.withFlightAddress(this.flightServer.flightUri()).withApiKey(apiKey);
        }
        return builder.build();
    }

    public void testSearchSendsPostToSearchPath() throws Exception {
        try (SpiceClient client = newClient()) {
            client.search(new SearchRequest("find me similar rows"));
            assertEquals("/v1/search", lastPath.get());
            assertEquals("POST", lastMethod.get());
            assertEquals("application/json", lastContentTypeHeader.get());
        }
    }

    public void testSearchOmitsOptionalFieldsWhenUnset() throws Exception {
        try (SpiceClient client = newClient()) {
            client.search(new SearchRequest("hello"));
            String body = lastBody.get();
            assertTrue(body.contains("\"text\":\"hello\""));
            assertFalse("unset limit should be omitted", body.contains("limit"));
            assertFalse("unset where should be omitted", body.contains("where"));
            assertFalse("unset datasets should be omitted", body.contains("datasets"));
            assertFalse("unset keywords should be omitted", body.contains("keywords"));
        }
    }

    public void testSearchIncludesOptionalFieldsWhenSet() throws Exception {
        try (SpiceClient client = newClient()) {
            client.search(new SearchRequest("hello")
                    .withDatasets(Arrays.asList("taxi_trips"))
                    .withLimit(5)
                    .withWhere("user_id = 42")
                    .withAdditionalColumns(Arrays.asList("id"))
                    .withKeywords(Arrays.asList("nyc")));
            String body = lastBody.get();
            assertTrue(body.contains("\"datasets\":[\"taxi_trips\"]"));
            assertTrue(body.contains("\"limit\":5"));
            // Gson HTML-escapes "=" by default (=) — assert on the actual wire form.
            assertTrue(body.contains("\"where\":\"user_id \\u003d 42\""));
            assertTrue(body.contains("\"additional_columns\":[\"id\"]"));
            assertTrue(body.contains("\"keywords\":[\"nyc\"]"));
        }
    }

    public void testSearchSendsApiKeyWhenConfigured() throws Exception {
        try (SpiceClient client = newClient("test-app|test-key")) {
            client.search(new SearchRequest("hello"));
            assertEquals("test-app|test-key", lastApiKeyHeader.get());
        }
    }

    public void testSearchOmitsApiKeyWhenNotConfigured() throws Exception {
        try (SpiceClient client = newClient()) {
            client.search(new SearchRequest("hello"));
            assertNull(lastApiKeyHeader.get());
        }
    }

    public void testSearchDecodesFullResponse() throws Exception {
        responseBody = "{"
                + "\"results\":[{"
                + "\"dataset\":\"taxi_trips\","
                + "\"_score\":0.87,"
                + "\"matches\":{\"description\":[\"a\",\"b\"]},"
                + "\"primary_key\":{\"id\":1},"
                + "\"data\":{\"fare\":12.5},"
                + "\"metadata\":{\"source\":\"s3\"}"
                + "}],"
                + "\"duration_ms\":42"
                + "}";
        try (SpiceClient client = newClient()) {
            SearchResponse response = client.search(new SearchRequest("hello"));
            assertEquals(42L, response.getDurationMs());
            List<SearchMatch> results = response.getResults();
            assertEquals(1, results.size());
            SearchMatch match = results.get(0);
            assertEquals("taxi_trips", match.getDataset());
            assertEquals(0.87, match.getScore(), 0.0001);
            assertEquals(2, match.getMatches().get("description").size());
            assertEquals(1.0, ((Number) match.getPrimaryKey().get("id")).doubleValue(), 0.0001);
            assertEquals(12.5, ((Number) match.getData().get("fare")).doubleValue(), 0.0001);
            assertEquals("s3", match.getMetadata().get("source"));
        }
    }

    public void testSearchMatchWithNoMapsReturnsEmptyNotNull() throws Exception {
        responseBody = "{\"results\":[{\"dataset\":\"taxi_trips\",\"_score\":0.5,\"matches\":{}}],\"duration_ms\":1}";
        try (SpiceClient client = newClient()) {
            SearchMatch match = client.search(new SearchRequest("hello")).getResults().get(0);
            assertNotNull(match.getPrimaryKey());
            assertTrue(match.getPrimaryKey().isEmpty());
            assertNotNull(match.getData());
            assertTrue(match.getData().isEmpty());
            assertNotNull(match.getMetadata());
            assertTrue(match.getMetadata().isEmpty());
        }
    }

    public void testSearchNon200SurfacesResponseBody() throws Exception {
        responseCode = 400;
        responseBody = "No data sources provided";
        try (SpiceClient client = newClient()) {
            client.search(new SearchRequest("hello"));
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("400"));
            assertTrue(e.getMessage().contains("No data sources provided"));
        }
    }

    public void testSearchMalformedResponseThrows() throws Exception {
        responseBody = "not json at all {{{";
        try (SpiceClient client = newClient()) {
            client.search(new SearchRequest("hello"));
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("malformed"));
        }
    }

    public void testSearchNullRequestThrowsWithoutHttpCall() throws Exception {
        try (SpiceClient client = newClient()) {
            try {
                client.search(null);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
        assertEquals(0, requestCount.get());
    }

    public void testSearchEmptyTextThrowsWithoutHttpCall() throws Exception {
        try (SpiceClient client = newClient()) {
            try {
                client.search(new SearchRequest(""));
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
        assertEquals(0, requestCount.get());
    }

    public void testSearchNonPositiveLimitThrowsWithoutHttpCall() throws Exception {
        try (SpiceClient client = newClient()) {
            try {
                client.search(new SearchRequest("hello").withLimit(0));
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
        assertEquals(0, requestCount.get());
    }
}
