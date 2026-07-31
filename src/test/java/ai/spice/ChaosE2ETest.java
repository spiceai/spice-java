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
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStream;

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

    private SpicedProcess spiced;

    private static boolean chaosEnabled() {
        return "1".equals(System.getenv("SPICE_E2E_CHAOS")) && SpicedProcess.findBinary() != null;
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
        try (FlightStream stream = client.query(sql)) {
            long rows = 0;
            while (stream.next()) {
                rows += stream.getRoot().getRowCount();
            }
            return rows;
        }
    }

    /** Runs the callable with a hang guard so a broken SDK cannot wedge the suite. */
    private static <T> T guarded(Callable<T> callable) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = executor.submit(callable);
            try {
                return future.get(CALL_GUARD_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                fail("Call did not complete within " + CALL_GUARD_SECONDS + "s — likely hung");
                throw new IllegalStateException("unreachable");
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                }
                throw e;
            }
        } finally {
            executor.shutdownNow();
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
        spiced = SpicedProcess.start();

        try (SpiceClient client = newClient(3)) {
            assertEquals(1, countRows(client, "SELECT 1"));
            // Prime the prepared-statement cache so the restart invalidates a live handle.
            try (org.apache.arrow.vector.ipc.ArrowReader reader = client.queryWithParams("SELECT $1", 42L)) {
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
            try (org.apache.arrow.vector.ipc.ArrowReader reader = guarded(
                    () -> client.queryWithParams("SELECT $1", 43L))) {
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
        spiced = SpicedProcess.start();

        try (SpiceClient client = newClient(5)) {
            assertEquals(1, countRows(client, "SELECT 1"));

            spiced.kill();
            // Bring the runtime back concurrently, inside the retry window.
            Thread restarter = new Thread(() -> {
                try {
                    Thread.sleep(1_500);
                    spiced = spiced.restart();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            restarter.start();

            long start = System.nanoTime();
            assertEquals("query issued during downtime should succeed via retries",
                    1, (long) guarded(() -> countRows(client, "SELECT 1")));
            long elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000L;
            assertTrue("recovery should happen within the retry budget, took " + elapsedSeconds + "s",
                    elapsedSeconds < 30);
            restarter.join(TimeUnit.SECONDS.toMillis(30));
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
        spiced = SpicedProcess.start();

        // ~10^7 rows from pure SQL92 cross joins — no version-specific functions.
        String bigSql = "WITH t AS (SELECT * FROM (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10)) v(x)) "
                + "SELECT a.x FROM t a, t b, t c, t d, t e, t f, t g";

        try (SpiceClient client = newClient(0)) {
            assertEquals(1, countRows(client, "SELECT 1"));

            try {
                guarded(() -> {
                    try (FlightStream stream = client.query(bigSql)) {
                        assertTrue("stream should produce at least one batch", stream.next());
                        spiced.kill();
                        long rows = 0;
                        while (stream.next()) {
                            rows += stream.getRoot().getRowCount();
                        }
                        return rows;
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
     * transport error instead of hanging forever, and the client recovers when
     * the runtime thaws.
     */
    public void testFrozenRuntimeDetectedByKeepAlive() throws Exception {
        if (!chaosEnabled()) {
            return;
        }
        spiced = SpicedProcess.start();

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
                    // Keep-alive is 30s interval + 10s timeout; the call must fail in
                    // that order of magnitude — not instantly (that would mean a
                    // connection refusal, not detection) and never hang.
                    assertTrue("keep-alive detection took " + elapsedSeconds + "s",
                            elapsedSeconds >= 2 && elapsedSeconds <= 100);
                }
            } finally {
                spiced.thaw();
            }

            // After thawing, the same client works again (retries allowed for
            // the first call while the transport re-establishes).
            try (SpiceClient recovered = newClient(3)) {
                assertEquals(1, (long) guarded(() -> countRows(recovered, "SELECT 1")));
            }
        }
    }

    // ==================== spiced process management ====================

    /**
     * A spiced process bound to fixed localhost ports with a dataset-free
     * spicepod. Restarts reuse the same ports so clients reconnect to the
     * same address, mirroring a crashed server replaced behind a stable VIP.
     */
    private static final class SpicedProcess {
        final Path workspace;
        final int httpPort;
        final int flightPort;
        final int metricsPort;
        private Process process;

        private SpicedProcess(Path workspace, int httpPort, int flightPort, int metricsPort) {
            this.workspace = workspace;
            this.httpPort = httpPort;
            this.flightPort = flightPort;
            this.metricsPort = metricsPort;
        }

        static String findBinary() {
            String env = System.getenv("SPICED_BIN");
            if (env != null && Files.isExecutable(Path.of(env))) {
                return env;
            }
            Path home = Path.of(System.getProperty("user.home"), ".spice", "bin", "spiced");
            return Files.isExecutable(home) ? home.toString() : null;
        }

        static SpicedProcess start() throws Exception {
            Path workspace = Files.createTempDirectory("spice-chaos");
            Files.writeString(workspace.resolve("spicepod.yaml"),
                    "version: v1\nkind: Spicepod\nname: chaos\n", StandardCharsets.UTF_8);
            SpicedProcess spiced = new SpicedProcess(
                    workspace, freePort(), freePort(), freePort());
            spiced.launch();
            return spiced;
        }

        /** Starts a fresh process on the same ports and workspace. */
        SpicedProcess restart() throws Exception {
            SpicedProcess fresh = new SpicedProcess(workspace, httpPort, flightPort, metricsPort);
            fresh.launch();
            return fresh;
        }

        private void launch() throws Exception {
            ProcessBuilder builder = new ProcessBuilder(
                    findBinary(),
                    "--http", "127.0.0.1:" + httpPort,
                    "--flight", "127.0.0.1:" + flightPort,
                    "--metrics", "127.0.0.1:" + metricsPort);
            builder.directory(workspace.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(workspace.resolve("spiced.log").toFile());
            process = builder.start();
            waitUntilHealthy();
        }

        private void waitUntilHealthy() throws Exception {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://127.0.0.1:" + httpPort + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    throw new IllegalStateException("spiced exited during startup; see "
                            + workspace.resolve("spiced.log"));
                }
                try {
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return;
                    }
                } catch (IOException | InterruptedException retry) {
                    // Not up yet.
                }
                Thread.sleep(250);
            }
            throw new IllegalStateException("spiced did not become healthy within 60s");
        }

        /** SIGKILL — an abrupt crash, no graceful shutdown. */
        void kill() throws Exception {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }

        /** SIGSTOP — the process is alive but completely unresponsive. */
        void freeze() throws Exception {
            signal("STOP");
        }

        /** SIGCONT — resume a frozen process. */
        void thaw() throws Exception {
            signal("CONT");
        }

        private void signal(String name) throws Exception {
            Process kill = new ProcessBuilder("kill", "-" + name, Long.toString(process.pid())).start();
            if (!kill.waitFor(5, TimeUnit.SECONDS) || kill.exitValue() != 0) {
                throw new IllegalStateException("kill -" + name + " failed for pid " + process.pid());
            }
        }

        void destroy() throws Exception {
            if (process != null && process.isAlive()) {
                // A frozen process ignores SIGKILL delivery ordering with SIGSTOP
                // pending on some platforms; thaw first, best-effort.
                try {
                    signal("CONT");
                } catch (Exception ignored) {
                    // Already dead or never frozen.
                }
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }

        private static int freePort() throws IOException {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            }
        }
    }
}
