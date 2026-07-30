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
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastUserAgentHeader = new AtomicReference<>();
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
        super.tearDown();
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath.set(exchange.getRequestURI().getPath());
        lastUserAgentHeader.set(exchange.getRequestHeaders().getFirst("X-Spice-User-Agent"));
        try (InputStream body = exchange.getRequestBody()) {
            lastBody.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        exchange.sendResponseHeaders(responseCode, -1);
        exchange.close();
    }

    private SpiceClient newClient() throws Exception {
        return SpiceClient.builder()
                .withHttpAddress(new URI("http://localhost:" + httpServer.getAddress().getPort()))
                .build();
    }

    public void testRefreshDataset() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("taxi_trips");
            assertEquals("/v1/datasets/taxi_trips/acceleration/refresh", lastPath.get());
            assertEquals("{}", lastBody.get());
            assertNotNull(lastUserAgentHeader.get());
            assertTrue(lastUserAgentHeader.get().startsWith("spice-java/"));
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

    public void testRefreshDatasetEmptyNameThrows() throws Exception {
        try (SpiceClient client = newClient()) {
            client.refreshDataset("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
