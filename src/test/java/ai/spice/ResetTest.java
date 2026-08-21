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

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * Tests for SpiceClient.reset() and transport resilience features
 * (keep-alive, dns:/// resolution, lazy rebuild).
 */
public class ResetTest extends TestCase {

    private static volatile boolean serverAvailable = false;
    private static volatile boolean serverAvailabilityChecked = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!serverAvailabilityChecked) {
            synchronized (ResetTest.class) {
                if (!serverAvailabilityChecked) {
                    try (SpiceClient probe = SpiceClient.builder().withMaxRetries(1).build()) {
                        // Probe with taxi_trips (not SELECT 1) to ensure
                        // the dataset is loaded and ready, not just that
                        // the server is up.
                        try (FlightStream stream = probe.sql(
                                "SELECT total_amount FROM taxi_trips LIMIT 1")) {
                            stream.next();
                        }
                        serverAvailable = true;
                    } catch (Exception e) {
                        serverAvailable = false;
                    } finally {
                        serverAvailabilityChecked = true;
                    }
                }
            }
        }
    }

    // ==================== reset() Happy Path ====================

    /**
     * reset() on a freshly built unauthenticated client should not throw.
     */
    public void testResetOnFreshClient() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        client.reset(); // should not throw
        client.close();
    }

    /**
     * reset() on a freshly built TLS client should not throw.
     */
    public void testResetOnTlsClient() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("grpc+tls://localhost:443"))
                .build();
        client.reset(); // should not throw
        client.close();
    }

    /**
     * reset() on a freshly built HTTPS client (auto-converted to grpc+tls) should not throw.
     */
    public void testResetOnHttpsClient() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("https://localhost:443"))
                .build();
        client.reset(); // should not throw
        client.close();
    }

    /**
     * After reset(), close() should complete without errors.
     * reset() eagerly rebuilds the Flight client, so close() tears down the new connection.
     */
    public void testCloseAfterReset() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        client.reset();
        client.close(); // should not throw
    }

    /**
     * After reset(), the client should have eagerly rebuilt its Flight connection.
     * A query should work without any NullPointerException.
     * If no local server is available, a connection error is expected.
     * maxRetries=0 keeps the no-server failure path fast (real backoff otherwise).
     */
    public void testQueryAfterResetRebuildsClient() throws Exception {
        SpiceClient client = SpiceClient.builder().withMaxRetries(0).build();
        client.reset();

        try {
            try (FlightStream stream = client.sql("SELECT 1")) {
                // If a local Spice runtime is running, this succeeds
                stream.next();
            }
        } catch (Exception e) {
            // Connection errors are expected when no server is running.
            // A NullPointerException here would indicate the rebuild failed.
            assertFalse("Should not get NullPointerException after reset (rebuild failed)",
                    e instanceof NullPointerException);
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            assertTrue("Expected a connection/transport error, got: " + e.getMessage(),
                    msg.contains("unavailable") || msg.contains("connection refused")
                            || msg.contains("not found") || msg.contains("io exception")
                            || msg.contains("failed to execute"));
        } finally {
            client.close();
        }
    }

    /**
     * After reset(), queryWithParams should work because the Flight channels were
     * eagerly rebuilt (prepared statements are re-created on first use).
     */
    public void testQueryWithParamsAfterResetRebuildsClient() throws Exception {
        SpiceClient client = SpiceClient.builder().withMaxRetries(0).build();
        client.reset();

        try {
            try (ArrowReader reader = client.sqlWithParams("SELECT $1", 42)) {
                while (reader.loadNextBatch()) {
                    // consume
                }
            }
        } catch (Exception e) {
            assertFalse("Should not get NullPointerException after reset (rebuild failed)",
                    e instanceof NullPointerException);
        } finally {
            client.close();
        }
    }

    // ==================== reset() Edge Cases ====================

    /**
     * Calling reset() multiple times in a row should be safe (idempotent).
     */
    public void testMultipleResetsAreIdempotent() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        client.reset();
        client.reset();
        client.reset();
        client.close(); // should not throw
    }

    /**
     * reset() → query → reset() → query cycle should work.
     * Each reset discards the transport; each query rebuilds it.
     */
    public void testResetQueryResetQueryCycle() throws Exception {
        SpiceClient client = SpiceClient.builder().withMaxRetries(0).build();

        for (int i = 0; i < 3; i++) {
            client.reset();
            try {
                FlightStream stream = client.sql("SELECT 1");
                stream.close();
            } catch (Exception e) {
                // Connection errors are fine — we're testing the reset/rebuild cycle,
                // not server availability. NPE would indicate a broken rebuild.
                assertFalse("Cycle " + i + ": NPE after reset means rebuild is broken",
                        e instanceof NullPointerException);
            }
        }

        client.close();
    }

    /**
     * reset() after close() should throw IllegalStateException.
     */
    public void testResetAfterClose() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        client.close();
        try {
            client.reset();
            fail("Expected IllegalStateException when resetting a closed client");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    /**
     * try-with-resources with a reset in between should work cleanly.
     */
    public void testTryWithResourcesAndReset() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertNotNull(client);
            client.reset();
            // auto-close on scope exit
        }
    }

    // ==================== Concurrent reset() ====================

    /**
     * Concurrent calls to reset() from multiple threads should not throw
     * or corrupt the client's internal state (reset() is expected to be thread-safe).
     */
    public void testConcurrentResetDoesNotThrow() throws Exception {
        final SpiceClient client = SpiceClient.builder().build();
        final int threadCount = 8;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicInteger errors = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        client.reset();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
            }

            startLatch.countDown(); // release all threads
            executor.shutdown();
            assertTrue("Concurrent reset should complete within 30s",
                    executor.awaitTermination(30, TimeUnit.SECONDS));

            assertEquals("No threads should have encountered errors", 0, errors.get());
        } finally {
            executor.shutdownNow();
            client.close();
        }
    }

    /**
     * Concurrent reset() and sql() should not throw unexpected errors.
     * (sql may fail with connection errors, but not NPE or IllegalStateException.)
     */
    public void testConcurrentResetAndQuery() throws Exception {
        final SpiceClient client = SpiceClient.builder().withMaxRetries(0).build();
        final int iterations = 5;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Thread 1: repeated resets
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterations; i++) {
                        client.reset();
                        Thread.sleep(10);
                    }
                } catch (InterruptedException ignored) {
                }
            });

            // Thread 2: repeated queries
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterations; i++) {
                        try {
                            FlightStream stream = client.sql("SELECT 1");
                            stream.close();
                        } catch (Exception e) {
                            // Unwrap ExecutionException to inspect the real cause
                            Throwable toCheck = (e instanceof ExecutionException && e.getCause() != null)
                                    ? e.getCause() : e;
                            if (!isExpectedTransportError(toCheck)) {
                                unexpectedErrors.add(toCheck);
                            }
                        }
                        Thread.sleep(10);
                    }
                } catch (InterruptedException ignored) {
                }
            });

            startLatch.countDown();
            executor.shutdown();
            assertTrue("Concurrent reset+query should complete within 30s",
                    executor.awaitTermination(30, TimeUnit.SECONDS));

            assertTrue("Unexpected errors during concurrent reset+query: "
                    + unexpectedErrors, unexpectedErrors.isEmpty());
        } finally {
            executor.shutdownNow();
            client.close();
        }
    }

    /**
     * Returns true if the throwable is an expected transport/connection error
     * that can occur when no server is running or during a mid-reset race.
     * Anything else (NPE, IllegalStateException, etc.) is unexpected.
     */
    private static boolean isExpectedTransportError(Throwable t) {
        if (t instanceof FlightRuntimeException) {
            return true; // gRPC status errors (UNAVAILABLE, etc.)
        }
        if (t instanceof java.net.ConnectException
                || t instanceof java.io.IOException) {
            return true; // connection refused, broken pipe, etc.
        }
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("unavailable")
                    || lower.contains("connection refused")
                    || lower.contains("failed to execute")) {
                return true;
            }
        }
        return false;
    }

    // ==================== Construction / DNS / Keep-alive ====================

    /**
     * Client built with default (plaintext) address constructs successfully.
     * This implicitly validates dns:/// target construction for grpc+tcp.
     */
    public void testDefaultPlaintextConstruction() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        assertNotNull(client);
        client.close();
    }

    /**
     * Client built with grpc+tls address constructs successfully.
     * Validates dns:/// target + TLS + keep-alive configuration.
     */
    public void testGrpcTlsConstruction() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("grpc+tls://localhost:443"))
                .build();
        assertNotNull(client);
        client.close();
    }

    /**
     * Client built with HTTP address (auto-converted to grpc+tcp).
     * Validates scheme conversion + dns:/// target.
     */
    public void testHttpSchemeConversion() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("http://localhost:50051"))
                .build();
        assertNotNull(client);
        client.close();
    }

    /**
     * Client built with HTTPS address (auto-converted to grpc+tls).
     * Validates scheme conversion + dns:/// target + TLS.
     */
    public void testHttpsSchemeConversion() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("https://localhost:443"))
                .build();
        assertNotNull(client);
        client.close();
    }

    /**
     * Client built with no explicit port should use default (443 for TLS, 80 for plaintext).
     */
    public void testDefaultPortForTls() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("grpc+tls://somehost"))
                .build();
        assertNotNull(client);
        client.close();
    }

    public void testDefaultPortForPlaintext() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("grpc+tcp://somehost"))
                .build();
        assertNotNull(client);
        client.close();
    }

    /**
     * Client built with Spice Cloud configuration (production-like scenario).
     * Validates the full TLS + dns:/// + keep-alive path.
     */
    public void testSpiceCloudConstruction() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withSpiceCloud()
                .build();
        assertNotNull(client);
        client.close();
    }

    /**
     * Client with custom memory limit and retries + reset cycle still works.
     */
    public void testResetWithCustomConfig() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withMaxRetries(5)
                .withArrowMemoryLimitMB(256)
                .withUserAgent("TestApp/1.0")
                .build();
        client.reset();

        try {
            FlightStream stream = client.sql("SELECT 1");
            stream.close();
        } catch (Exception e) {
            assertFalse("NPE after reset with custom config",
                    e instanceof NullPointerException);
        }

        client.close();
    }

    // ==================== Integration: reset then query (server required) ====================

    /**
     * If a local Spice runtime is running, verify that
     * reset() followed by sql() actually returns data.
     * Uses taxi_trips which is available in the CI quickstart dataset.
     */
    public void testResetThenQueryIntegration() throws Exception {
        if (!serverAvailable) return;

        try (SpiceClient client = SpiceClient.builder().build()) {
            // First query (establishes connection)
            try (FlightStream stream1 = client.sql(
                    "SELECT total_amount FROM taxi_trips LIMIT 1")) {
                int rows1 = 0;
                while (stream1.next()) {
                    rows1 += stream1.getRoot().getRowCount();
                }
                assertEquals("First query should return 1 row", 1, rows1);
            }

            // Reset (discards transport)
            client.reset();

            // Second query (lazy rebuild)
            try (FlightStream stream2 = client.sql(
                    "SELECT total_amount FROM taxi_trips LIMIT 2")) {
                int rows2 = 0;
                while (stream2.next()) {
                    rows2 += stream2.getRoot().getRowCount();
                }
                assertEquals("Second query after reset should return 2 rows", 2, rows2);
            }
        }
    }

    /**
     * If a local Spice runtime is running, verify that
     * reset() followed by sqlWithParams() actually returns data.
     * Uses taxi_trips which is available in the CI quickstart dataset.
     */
    public void testResetThenQueryWithParamsIntegration() throws Exception {
        if (!serverAvailable) return;

        try (SpiceClient client = SpiceClient.builder().build()) {
            // First query
            try (ArrowReader reader1 = client.sqlWithParams(
                    "SELECT total_amount FROM taxi_trips WHERE total_amount > $1 LIMIT 1",
                    0.0)) {
                int rows1 = 0;
                while (reader1.loadNextBatch()) {
                    rows1 += reader1.getVectorSchemaRoot().getRowCount();
                }
                assertTrue("First parameterized query should return rows", rows1 > 0);
            }

            // Reset
            client.reset();

            // Second query (rebuilds channels and re-prepares the statement)
            try (ArrowReader reader2 = client.sqlWithParams(
                    "SELECT total_amount FROM taxi_trips WHERE total_amount > $1 LIMIT 2",
                    0.0)) {
                int rows2 = 0;
                while (reader2.loadNextBatch()) {
                    rows2 += reader2.getVectorSchemaRoot().getRowCount();
                }
                assertTrue("Second parameterized query after reset should return rows", rows2 > 0);
            }
        }
    }
}
