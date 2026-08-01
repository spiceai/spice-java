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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * Long-running soak against a live Spice runtime: a sustained mixed workload
 * (queries, cached parameterized queries, health probes, periodic resets)
 * asserting zero errors, non-empty results throughout, no Arrow memory leaks
 * (a leak makes close() throw), bounded thread growth, and stable tail
 * latency over the whole run.
 *
 * <p>Gated: runs only when SPICE_SOAK_SECONDS is set to a positive number
 * (the nightly workflow uses 1800). Connects to the runtime configured via
 * the standard SPICE_FLIGHT_URL / SPICE_HTTP_URL environment (localhost
 * defaults), and queries SPICE_SOAK_DATASET (default taxi_trips).</p>
 */
public class SoakTest extends TestCase {

    private static final int WORKERS = 4;
    private static final long RESET_INTERVAL_SECONDS = 120;
    /**
     * Latency samples retained per minute. At ~9k ops/s a 30-minute soak would
     * otherwise retain ~16M boxed samples and risk exhausting the heap before
     * the assertions run; a bounded prefix per minute is ample for p99
     * stability comparison. (Concurrent workers may overshoot the cap by at
     * most WORKERS-1 samples — irrelevant at this size.)
     */
    private static final int MAX_SAMPLES_PER_MINUTE = 5_000;

    public void testSoak() throws Exception {
        long soakSeconds = Long.parseLong(System.getenv().getOrDefault("SPICE_SOAK_SECONDS", "0"));
        if (soakSeconds <= 0) {
            return;
        }
        String dataset = System.getenv().getOrDefault("SPICE_SOAK_DATASET", "taxi_trips");
        String querySql = "SELECT * FROM " + dataset + " LIMIT 50";
        String paramSql = "SELECT * FROM " + dataset + " WHERE $1 = 1 LIMIT 10";

        final AtomicLong operations = new AtomicLong();
        final AtomicLong dataOperations = new AtomicLong();
        final AtomicLong rowsRead = new AtomicLong();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        // Bounded latency samples in micros, bucketed by minute of the run.
        final Map<Long, List<Long>> latenciesByMinute = new ConcurrentHashMap<>();

        final long startNanos = System.nanoTime();
        final long deadlineNanos = startNanos + TimeUnit.SECONDS.toNanos(soakSeconds);
        final int warmThreadBaseline;

        try (SpiceClient client = SpiceClient.builder().withMaxRetries(3).build()) {
            // Fail fast (before the long run) if the runtime isn't serving.
            assertTrue("runtime must be ready before soaking", client.isReady());
            try (FlightStream warm = client.query(querySql)) {
                LocalFlightServerTest.countRows(warm);
            }
            // Baseline AFTER warm-up: gRPC/Netty event loops and the JDK HTTP
            // client's selector are shared steady-state pools that appear on
            // first use. The leak signal is growth beyond this warm baseline
            // over the run (e.g. resets leaving channels behind), not the
            // difference from a cold JVM.
            warmThreadBaseline = liveThreadCount();

            ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
            for (int w = 0; w < WORKERS; w++) {
                executor.submit(() -> {
                    int roll = 0;
                    while (System.nanoTime() < deadlineNanos) {
                        long opStart = System.nanoTime();
                        try {
                            int kind = roll++ % 20;
                            if (kind < 16) {
                                try (FlightStream stream = client.query(querySql)) {
                                    rowsRead.addAndGet(LocalFlightServerTest.countRows(stream));
                                }
                                dataOperations.incrementAndGet();
                            } else if (kind < 19) {
                                try (ArrowReader reader = client.queryWithParams(paramSql, 1L)) {
                                    rowsRead.addAndGet(LocalFlightServerTest.countRows(reader));
                                }
                                dataOperations.incrementAndGet();
                            } else {
                                if (!client.isHealthy() || !client.isReady()) {
                                    errors.add("health probe reported unhealthy mid-soak");
                                }
                            }
                            operations.incrementAndGet();
                            long minute = TimeUnit.NANOSECONDS.toMinutes(opStart - startNanos);
                            List<Long> bucket = latenciesByMinute.computeIfAbsent(minute,
                                    m -> Collections.synchronizedList(new ArrayList<>()));
                            if (bucket.size() < MAX_SAMPLES_PER_MINUTE) {
                                bucket.add((System.nanoTime() - opStart) / 1_000);
                            }
                        } catch (Throwable t) {
                            errors.add(t.getClass().getSimpleName() + ": " + t.getMessage());
                            if (errors.size() > 20) {
                                return; // Failing hard; no point continuing.
                            }
                        }
                    }
                });
            }
            executor.shutdown();

            // Periodic reset() proves transport rebuild under sustained load;
            // awaitTermination doubles as the completion join.
            while (!executor.awaitTermination(RESET_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
                if (System.nanoTime() < deadlineNanos) {
                    try {
                        client.reset();
                    } catch (Throwable t) {
                        errors.add("reset: " + t.getMessage());
                    }
                } else {
                    assertTrue("workers must finish shortly after the deadline",
                            executor.awaitTermination(120, TimeUnit.SECONDS));
                    break;
                }
            }

            printSummary(operations.get(), soakSeconds, latenciesByMinute);

            assertTrue("soak must complete with zero errors, got " + errors.size()
                    + " — first: " + errors.peek(), errors.isEmpty());
            // Guard against a silent-empty-results regression: every data
            // operation queries with a LIMIT >= 10 against a populated dataset.
            System.out.printf("[soak] data-ops=%d rows-read=%d%n", dataOperations.get(), rowsRead.get());
            assertTrue("every data operation must return rows: ops=" + dataOperations.get()
                    + " rows=" + rowsRead.get(), rowsRead.get() >= dataOperations.get() * 10);
            assertTailLatencyStable(latenciesByMinute);
        }
        // Reaching here means close() did not throw: no Arrow buffers leaked
        // over the whole run (a leak makes the allocator close throw).

        // Transport threads shut down with the client; allow scavenger slack.
        Thread.sleep(3_000);
        int threadsAfter = liveThreadCount();
        System.out.printf("[soak] threads: warm-baseline=%d after-close=%d%n",
                warmThreadBaseline, threadsAfter);
        assertTrue("thread count should not grow over the soak: warm-baseline=" + warmThreadBaseline
                + " after-close=" + threadsAfter, threadsAfter <= warmThreadBaseline + 8);
    }

    private static int liveThreadCount() {
        return Thread.getAllStackTraces().size();
    }

    private static long percentile(List<Long> sortedMicros, double quantile) {
        return sortedMicros.get((int) Math.min(sortedMicros.size() - 1,
                (long) (sortedMicros.size() * quantile)));
    }

    /** p99 of the last third of the run must stay within 3x of the first third. */
    private static void assertTailLatencyStable(Map<Long, List<Long>> byMinute) {
        List<Long> minutes = new ArrayList<>(byMinute.keySet());
        if (minutes.size() < 3) {
            return; // Too short a run to compare thirds.
        }
        minutes.sort(Long::compare);
        int third = minutes.size() / 3;
        long firstThirdP99 = p99Of(byMinute, minutes.subList(0, third));
        long lastThirdP99 = p99Of(byMinute, minutes.subList(minutes.size() - third, minutes.size()));
        System.out.printf("[soak] p99 first-third=%dus last-third=%dus%n", firstThirdP99, lastThirdP99);
        assertTrue("tail latency degraded over the soak: first-third p99=" + firstThirdP99
                + "us, last-third p99=" + lastThirdP99 + "us",
                lastThirdP99 <= firstThirdP99 * 3);
    }

    private static long p99Of(Map<Long, List<Long>> byMinute, List<Long> minutes) {
        List<Long> samples = new ArrayList<>();
        for (long minute : minutes) {
            samples.addAll(byMinute.get(minute));
        }
        samples.sort(Long::compare);
        return samples.isEmpty() ? 0 : percentile(samples, 0.99);
    }

    private static void printSummary(long operations, long soakSeconds, Map<Long, List<Long>> byMinute) {
        List<Long> all = new ArrayList<>();
        byMinute.values().forEach(all::addAll);
        all.sort(Long::compare);
        long p50 = all.isEmpty() ? 0 : percentile(all, 0.50);
        long p99 = all.isEmpty() ? 0 : percentile(all, 0.99);
        System.out.printf("[soak] %d ops over %ds (%.1f ops/s), p50=%dus p99=%dus, %d minutes sampled%n",
                operations, soakSeconds, operations / (double) soakSeconds, p50, p99, byMinute.size());
        System.out.println("[soak] per-minute p99 (us): "
                + byMinute.keySet().stream().sorted()
                        .map(minute -> String.valueOf(p99Of(byMinute, List.of(minute))))
                        .reduce((a, b) -> a + ", " + b).orElse("none"));
    }
}
