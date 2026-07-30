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

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStatusCode;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * Tests for retry with backoff, query timeouts, automatic re-authentication,
 * the channel pool, and reset()/query race safety — all against the
 * in-process Flight SQL server.
 */
public class ResilienceTest extends TestCase {

    /**
     * A transient UNAVAILABLE is retried and succeeds, and the retry waits a
     * real backoff (the previous 1-2ms fibonacci wait made retries useless
     * against real outages).
     */
    public void testTransientUnavailableIsRetriedWithBackoff() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                SpiceClient client = SpiceClient.builder().withFlightAddress(server.flightUri()).build()) {
            server.failNextGetFlightInfo(1, CallStatus.UNAVAILABLE);

            long startNanos = System.nanoTime();
            try (FlightStream stream = client.query("SELECT 1")) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

            assertEquals("one failed and one successful attempt", 2, server.getFlightInfoCalls.get());
            assertTrue("retry should wait a real backoff (>=200ms), waited " + elapsedMs + "ms",
                    elapsedMs >= 200);
        }
    }

    public void testRetriesExhaustedSurfaceLastError() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withMaxRetries(1)
                        .build()) {
            server.failNextGetFlightInfo(10, CallStatus.UNAVAILABLE);
            try {
                client.query("SELECT 1");
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof FlightRuntimeException);
                assertEquals(FlightStatusCode.UNAVAILABLE,
                        ((FlightRuntimeException) e.getCause()).status().code());
            }
            assertEquals("initial attempt plus one retry", 2, server.getFlightInfoCalls.get());
        }
    }

    /**
     * Non-retryable errors (e.g. INVALID_ARGUMENT for bad SQL) fail fast
     * without burning retry attempts or backoff time.
     */
    public void testNonRetryableErrorFailsFast() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                SpiceClient client = SpiceClient.builder().withFlightAddress(server.flightUri()).build()) {
            server.failNextGetFlightInfo(1, CallStatus.INVALID_ARGUMENT);

            long startNanos = System.nanoTime();
            try {
                client.query("SELECT invalid");
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertEquals(FlightStatusCode.INVALID_ARGUMENT,
                        ((FlightRuntimeException) e.getCause()).status().code());
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

            assertEquals("no retry for non-retryable status", 1, server.getFlightInfoCalls.get());
            assertTrue("should fail fast, took " + elapsedMs + "ms", elapsedMs < 200);
        }
    }

    /**
     * withQueryTimeout bounds how long query planning may hang. Without it,
     * this query would block for the full server delay.
     */
    public void testQueryTimeoutBoundsPlanning() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withQueryTimeout(Duration.ofMillis(200))
                        .withMaxRetries(0)
                        .build()) {
            server.getFlightInfoDelayMs = 1_500;

            long startNanos = System.nanoTime();
            try {
                client.query("SELECT 1");
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertEquals(FlightStatusCode.TIMED_OUT,
                        ((FlightRuntimeException) e.getCause()).status().code());
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            assertTrue("timeout should fire well before the 1.5s server delay, took " + elapsedMs + "ms",
                    elapsedMs < 1_200);
            // server.close() waits for the still-sleeping handler via awaitTermination.
        }
    }

    /**
     * A long-lived client whose handshake bearer token expires re-handshakes
     * automatically and the query succeeds — no manual reset() required.
     */
    public void testExpiredBearerTokenRecoversAutomatically() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer("testapp", "testapp|secret");
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withApiKey("testapp|secret")
                        .build()) {
            assertEquals("constructor performs the initial handshake", 1, server.basicAuthValidations.get());

            try (FlightStream stream = client.query("SELECT 1")) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
            }

            server.rejectNextBearerToken();

            try (FlightStream stream = client.query("SELECT 2")) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
            }
            assertEquals("expired token must trigger exactly one re-handshake", 2,
                    server.basicAuthValidations.get());
        }
    }

    /**
     * Parameterized queries work against an authenticated server (the
     * prepared-statement path shares the same authenticated channels; with
     * ADBC it used a second unauthenticated-configured connection).
     */
    public void testQueryWithParamsOnAuthenticatedClient() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer("testapp", "testapp|secret");
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withApiKey("testapp|secret")
                        .build()) {
            try (ArrowReader reader = client.queryWithParams("SELECT * FROM t WHERE id=$1", 7L)) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(reader));
            }
            assertEquals("no extra handshake beyond the constructor's", 1,
                    server.basicAuthValidations.get());
        }
    }

    public void testChannelPoolServesQueries() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withChannelCount(4)
                        .build()) {
            for (int i = 0; i < 8; i++) {
                try (FlightStream stream = client.query("SELECT " + i)) {
                    assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
                }
            }
            try (ArrowReader reader = client.queryWithParams("SELECT * FROM t WHERE id=$1", 1L)) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(reader));
            }
            assertEquals(9, server.getFlightInfoCalls.get());

            // reset() rebuilds all channels and queries still work.
            client.reset();
            try (FlightStream stream = client.query("SELECT after_reset")) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
            }
        }
    }

    /**
     * Regression test for the reset()-vs-queryWithParams race: concurrent
     * resets while parameterized queries are in flight must never surface
     * NullPointerException (previously the connection field could be nulled
     * mid-query), and against a healthy server every query must succeed via
     * the retry/fallback path.
     */
    public void testConcurrentResetAndQueryWithParams() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                SpiceClient client = SpiceClient.builder().withFlightAddress(server.flightUri()).build()) {
            final int queries = 20;
            final List<Throwable> failures = new CopyOnWriteArrayList<>();
            final CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 5; i++) {
                        client.reset();
                        Thread.sleep(40);
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < queries; i++) {
                        try (ArrowReader reader = client.queryWithParams(
                                "SELECT * FROM t WHERE id=$1", (long) i)) {
                            LocalFlightServerTest.countRows(reader);
                        }
                        Thread.sleep(10);
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });

            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(120, TimeUnit.SECONDS));

            for (Throwable failure : failures) {
                assertFalse("NPE means the reset race is back: " + failure,
                        failure instanceof NullPointerException);
            }
            assertTrue("all queries and resets should succeed against a healthy server, failures: "
                    + failures, failures.isEmpty());
        }
    }
}
