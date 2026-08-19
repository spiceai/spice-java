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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

/**
 * Tests for {@link SpiceClient#nsql(NsqlRequest)} and
 * {@link SpiceClient#nsqlGenerateSql(NsqlRequest)} against a local HTTP
 * server, so they need no live Spice runtime.
 */
public class NsqlTest extends TestCase {

    private HttpServer server;
    private SpiceClient client;
    private TestFlightSqlServer flightServer;

    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAcceptHeader = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseCode = 200;
    private volatile String responseBody = "{}";

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

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastMethod.set(exchange.getRequestMethod());
        lastPath.set(exchange.getRequestURI().getPath());
        lastAcceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
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

    private SpiceClient newClient(String apiKey) throws Exception {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
        SpiceClientBuilder builder = SpiceClient.builder()
                .withHttpAddress(new URI("http://127.0.0.1:" + this.server.getAddress().getPort()));
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
        this.client = builder.build();
        return this.client;
    }

    public void testNsqlRequestAndResponse() throws Exception {
        responseBody = "{"
                + "\"sql\":\"SELECT * FROM taxi_trips LIMIT 1\","
                + "\"row_count\":1,"
                + "\"schema\":{\"fields\":["
                + "  {\"name\":\"id\",\"data_type\":\"Int64\",\"nullable\":false},"
                + "  {\"name\":\"ts\",\"data_type\":{\"Timestamp\":[\"Nanosecond\",null]},\"nullable\":true}"
                + "]},"
                + "\"data\":[{\"id\":1,\"ts\":\"2024-01-01T00:00:00\"}]"
                + "}";

        SpiceClient client = newClient(null);
        NsqlResponse response = client.nsql(new NsqlRequest("how many taxi trips were there"));

        assertEquals("POST", lastMethod.get());
        assertEquals("/v1/nsql", lastPath.get());
        assertEquals("application/vnd.spiceai.nsql.v1+json", lastAcceptHeader.get());
        assertNull(lastApiKeyHeader.get());
        assertTrue(lastBody.get().contains("\"query\":\"how many taxi trips were there\""));

        assertEquals("SELECT * FROM taxi_trips LIMIT 1", response.getSql());
        assertEquals(1, response.getRowCount());
        assertEquals(2, response.getSchema().getFields().size());
        assertEquals("id", response.getSchema().getFields().get(0).getName());
        assertEquals("\"Int64\"", response.getSchema().getFields().get(0).getDataType().toString());
        assertFalse(response.getSchema().getFields().get(0).isNullable());
        assertTrue(response.getSchema().getFields().get(1).isNullable());
        assertEquals("{\"Timestamp\":[\"Nanosecond\",null]}", response.getSchema().getFields().get(1)
                .getDataType().toString());
        assertEquals(1, response.getData().size());
        assertEquals(Double.valueOf(1.0), response.getData().get(0).get("id"));
    }

    public void testNsqlSendsApiKey() throws Exception {
        SpiceClient client = newClient("testapp|secret");
        client.nsql(new NsqlRequest("q"));
        assertEquals("testapp|secret", lastApiKeyHeader.get());
    }

    public void testNsqlRequestBodyOnlyIncludesSetFields() throws Exception {
        SpiceClient client = newClient(null);
        client.nsql(new NsqlRequest("q").withModel("my-model"));
        assertTrue(lastBody.get().contains("\"model\":\"my-model\""));
        assertFalse(lastBody.get().contains("prompt_cache_key"));
        assertFalse(lastBody.get().contains("datasets"));
    }

    public void testNsqlGenerateSqlUsesSqlAcceptHeaderAndReturnsTrimmedText() throws Exception {
        responseCode = 200;
        responseBody = "  SELECT * FROM taxi_trips LIMIT 1  \n";

        SpiceClient client = newClient(null);
        String sql = client.nsqlGenerateSql(new NsqlRequest("how many taxi trips were there"));

        assertEquals("application/sql", lastAcceptHeader.get());
        assertEquals("SELECT * FROM taxi_trips LIMIT 1", sql);
    }

    public void testNsqlNon200ThrowsWithResponseBody() throws Exception {
        responseCode = 400;
        responseBody = "no LLM model configured";

        SpiceClient client = newClient(null);
        try {
            client.nsql(new NsqlRequest("q"));
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("400"));
            assertTrue(e.getMessage().contains("no LLM model configured"));
        }
    }

    public void testNsqlMalformedResponseThrows() throws Exception {
        responseBody = "not json";

        SpiceClient client = newClient(null);
        try {
            client.nsql(new NsqlRequest("q"));
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("malformed"));
        }
    }

    public void testNsqlNullRequestThrowsWithoutHttpCall() throws Exception {
        SpiceClient client = newClient(null);
        try {
            client.nsql(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(0, requestCount.get());
    }

    public void testNsqlEmptyQueryThrowsWithoutHttpCall() throws Exception {
        SpiceClient client = newClient(null);
        try {
            client.nsql(new NsqlRequest(""));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(0, requestCount.get());
    }
}
