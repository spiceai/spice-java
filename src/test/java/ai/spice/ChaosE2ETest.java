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

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * End-to-end chaos tests against a real spiced process whose lifecycle the
 * tests control: crash (SIGKILL), restart, and freeze (SIGSTOP). These prove
 * the SDK's resilience behaviors — retry with backoff, reconnection after
 * restart, prepared-statement re-prepare on stale handles, keep-alive
 * detection of unresponsive peers — against the real runtime rather than a
 * mock.
 *
 * <p>Gated: runs only when SPICE_E2E_CHAOS=1 and a spiced binary is available
 * (SPICED_BIN, or ~/.spice/bin/spiced). Uses a dataset-free spicepod, so
 * startup is fast and there is no network dependency beyond localhost.</p>
 */
public class ChaosE2ETest extends TestCase {

    /** Wall-clock guard for calls that would hang forever if a feature is broken. */
    private static final long CALL_GUARD_SECONDS = 120;

    /**
     * Daemon threads: if a guarded call ignores interruption (the very
     * regression being hunted), the stuck worker must not keep the JVM alive.
     */
    private static final ExecutorService GUARD_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "chaos-guard");
        thread.setDaemon(true);
        return thread;
    });

    private SpicedProcess spiced;

    /**
     * Skips silently when chaos testing isn't requested, but fails loudly when
     * it IS requested and no spiced binary can be found — otherwise a broken
     * CI install would turn the whole chaos suite into a passing no-op.
     */
    private static boolean chaosEnabled() {
        if (!"1".equals(System.getenv("SPICE_E2E_CHAOS"))) {
            return false;
        }
        assertNotNull("SPICE_E2E_CHAOS=1 but no spiced binary found (set SPICED_BIN or install the Spice CLI)",
                SpicedProcess.findBinary());
        return true;
    }

    @Override
    protected void tearDown() throws Exception {
        if (spiced != null) {
            spiced.destroy();
            spiced = null;
        }
        super.tearDown();
    }

    private SpiceClient newClient(int maxRetries) throws Exception {
        return SpiceClient.builder()
                .withFlightAddress(new URI("grpc://127.0.0.1:" + spiced.flightPort))
                .withHttpAddress(new URI("http://127.0.0.1:" + spiced.httpPort))
                .withMaxRetries(maxRetries)
                .build();
    }

    private static long countRows(SpiceClient client, String sql) throws Exception {
        try (FlightStream stream = client.sql(sql)) {
            return LocalFlightServerTest.countRows(stream);
        }
    }

    /** Runs the callable with a hang guard so a broken SDK cannot wedge the suite. */
    private static <T> T guarded(Callable<T> callable) throws Exception {
        Future<T> future = GUARD_EXECUTOR.submit(callable);
        try {
            return future.get(CALL_GUARD_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AssertionError(
                    "Call did not complete within " + CALL_GUARD_SECONDS + "s — likely hung");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /**
     * The SDK survives a runtime crash and restart on the same address with no
     * manual intervention: plain queries reconnect (fresh DNS/TCP), and cached
     * prepared statements transparently re-prepare after their server-side
     * handles died with the old process.
     */
    public void testSurvivesRuntimeRestart() throws Exception {
        if (!chaosEnabled()) {
            return;
        }
        spiced = SpicedProcess.start(SpicedProcess.DATASET_FREE_SPICEPOD);

        try (SpiceClient client = newClient(3)) {
            assertEquals(1, countRows(client, "SELECT 1"));
            // Prime the prepared-statement cache so the restart invalidates a live handle.
            try (ArrowReader reader = client.sqlWithParams("SELECT $1", 42L)) {
                assertTrue(reader.loadNextBatch());
            }

            spiced.kill();

            // With the runtime down, queries must fail cleanly (no hang, no NPE).
            try {
                guarded(() -> countRows(client, "SELECT 1"));
                fail("Expected query failure while the runtime is down");
            } catch (ExecutionException e) {
                assertTrue("cause should be a Flight transport error, got: " + e.getCause(),
                        e.getCause() instanceof FlightRuntimeException);
            }

            spiced = spiced.restart();

            // Same client, no reset(): reconnect + re-prepare must be automatic.
            assertEquals(1, (long) guarded(() -> countRows(client, "SELECT 1")));
            try (ArrowReader reader = guarded(() -> client.sqlWithParams("SELECT $1", 43L))) {
                assertTrue("cached statement must transparently re-prepare after restart",
                        reader.loadNextBatch());
            }
        }
    }

    /**
     * A query issued while the runtime is down succeeds without any caller-side
     * handling, as long as the runtime returns within the retry backoff budget
     * (~7.75s for 5 retries) — the load-balancer-failover scenario.
     */
    public void testQueryDuringDowntimeRecoversViaRetries() throws Exception {
        if (!chaosEnabled()) {
            return;
        }
        spiced = SpicedProcess.start(SpicedProcess.DATASET_FREE_SPICEPOD);

        try (SpiceClient client = newClient(5)) {
            assertEquals(1, countRows(client, "SELECT 1"));

            spiced.kill();
            // Bring the runtime back concurrently, inside the retry window.
            // The restarter is always joined (finally) so a failing assertion
            // can't leave it racing teardown, and its failure is surfaced.
            final AtomicReference<Exception> restartFailure = new AtomicReference<>();
            Thread restarter = new Thread(() -> {
                try {
                    Thread.sleep(1_500);
                    spiced = spiced.restart();
                } catch (Exception e) {
                    restartFailure.set(e);
                }
            }, "chaos-restarter");
            restarter.start();
            try {
                long start = System.nanoTime();
                assertEquals("query issued during downtime should succeed via retries",
                        1, (long) guarded(() -> countRows(client, "SELECT 1")));
                long elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000L;
                assertTrue("recovery should happen within the retry budget, took " + elapsedSeconds + "s",
                        elapsedSeconds < 30);
            } finally {
                restarter.join(TimeUnit.SECONDS.toMillis(90));
                assertFalse("restarter thread must finish before teardown", restarter.isAlive());
            }
            if (restartFailure.get() != null) {
                throw restartFailure.get();
            }
        }
    }

    /**
     * Killing the runtime while a large result is streaming surfaces a clean
     * transport error mid-consumption (never a hang), and the same client
     * recovers once the runtime returns.
     */
    public void testKillMidStreamFailsCleanlyAndRecovers() throws Exception {
        if (!chaosEnabled()) {
            return;
        }
        spiced = SpicedProcess.start(SpicedProcess.DATASET_FREE_SPICEPOD);

        // ~10^7 rows from pure SQL92 cross joins — no version-specific functions.
        String bigSql = "WITH t AS (SELECT * FROM (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10)) v(x)) "
                + "SELECT a.x FROM t a, t b, t c, t d, t e, t f, t g";

        try (SpiceClient client = newClient(0)) {
            assertEquals(1, countRows(client, "SELECT 1"));

            try {
                guarded(() -> {
                    try (FlightStream stream = client.sql(bigSql)) {
                        assertTrue("stream should produce at least one batch", stream.next());
                        spiced.kill();
                        return LocalFlightServerTest.countRows(stream);
                    }
                });
                fail("Expected a transport error when the runtime dies mid-stream");
            } catch (FlightRuntimeException expected) {
                // Clean, classifiable failure — exactly what callers should see.
            }

            spiced = spiced.restart();
            assertEquals("client must recover after the runtime returns",
                    1, (long) guarded(() -> countRows(client, "SELECT 1")));
        }
    }

    /**
     * A frozen (SIGSTOP) runtime — the unresponsive-peer case TCP alone never
     * detects — is caught by HTTP/2 keep-alive: the in-flight call fails with a
     * transport error instead of hanging forever. Detection is measured on a
     * retry-free client so the window is one keep-alive cycle; recovery after
     * the thaw is then verified with a fresh client that retries while the
     * transport re-establishes.
     */
    public void testFrozenRuntimeDetectedByKeepAlive() throws Exception {
        if (!chaosEnabled()) {
            return;
        }
        spiced = SpicedProcess.start(SpicedProcess.DATASET_FREE_SPICEPOD);

        // Derived from the SDK's actual keep-alive tuning plus generous
        // scheduler slack; not instant (that would be a refusal, not detection).
        long detectionUpperBound = (SpiceClient.KEEPALIVE_TIME_SECONDS
                + SpiceClient.KEEPALIVE_TIMEOUT_SECONDS) * 2 + 20;

        try (SpiceClient client = newClient(0)) {
            assertEquals(1, countRows(client, "SELECT 1"));

            spiced.freeze();
            try {
                long start = System.nanoTime();
                try {
                    guarded(() -> countRows(client, "SELECT 1"));
                    fail("Expected keep-alive to fail the call against a frozen runtime");
                } catch (ExecutionException e) {
                    long elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000L;
                    assertTrue("cause should be a Flight transport error, got: " + e.getCause(),
                            e.getCause() instanceof FlightRuntimeException);
                    assertTrue("keep-alive detection took " + elapsedSeconds + "s (bound: "
                            + detectionUpperBound + "s)",
                            elapsedSeconds >= 2 && elapsedSeconds <= detectionUpperBound);
                }
            } finally {
                spiced.thaw();
            }
        }

        try (SpiceClient recovered = newClient(3)) {
            assertEquals(1, (long) guarded(() -> countRows(recovered, "SELECT 1")));
        }
    }
}
