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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import junit.framework.TestCase;

/**
 * In-process micro-benchmarks for the two query paths.
 *
 * <p>Latency numbers are printed for humans (loopback RTT makes wall-clock
 * savings look small; against a real network every saved round trip is a full
 * RTT). The assertions are on server-side RPC counts, which are deterministic
 * and are the metric that dominates real-world latency.</p>
 */
public class PerfBenchmarkTest extends TestCase {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURED_ITERATIONS = 300;
    private static final String SQL = "SELECT * FROM bench WHERE id > $1";

    private TestFlightSqlServer server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new TestFlightSqlServer();
        server.rowsPerBatch = 100;
    }

    @Override
    protected void tearDown() throws Exception {
        server.close();
        super.tearDown();
    }

    private static long[] measure(int iterations, Callable<?> op) throws Exception {
        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            op.call();
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        return samples;
    }

    private static String stats(String label, long[] sortedNanos) {
        long p50 = sortedNanos[sortedNanos.length / 2];
        long p95 = sortedNanos[(int) (sortedNanos.length * 0.95)];
        long p99 = sortedNanos[(int) (sortedNanos.length * 0.99)];
        return String.format("%-28s p50=%6dus  p95=%6dus  p99=%6dus", label,
                p50 / 1_000, p95 / 1_000, p99 / 1_000);
    }

    private static long p50Micros(long[] sortedNanos) {
        return sortedNanos[sortedNanos.length / 2] / 1_000;
    }

    /**
     * Appends a data point to the file named by the BENCH_JSON env var, in
     * github-action-benchmark's "customSmallerIsBetter" format. No-op when the
     * variable is unset (normal test runs).
     */
    private static synchronized void recordBench(String name, String unit, long value) throws Exception {
        String path = System.getenv("BENCH_JSON");
        if (path == null || path.isEmpty()) {
            return;
        }
        Path file = Path.of(path);
        JsonArray entries = Files.exists(file)
                ? JsonParser.parseString(Files.readString(file)).getAsJsonArray()
                : new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("unit", unit);
        entry.addProperty("value", value);
        entries.add(entry);
        Files.writeString(file, entries.toString());
    }

    /**
     * Counts rows and asserts the full expected result arrived — a regression
     * that drops results must never publish an "improved" latency.
     */
    private static long checkedCountRows(FlightStream stream, long expectedRows) throws Exception {
        long rows = LocalFlightServerTest.countRows(stream);
        assertEquals(expectedRows, rows);
        return rows;
    }

    private static long checkedCountRows(ArrowReader reader, long expectedRows) throws Exception {
        long rows = LocalFlightServerTest.countRows(reader);
        assertEquals(expectedRows, rows);
        return rows;
    }

    private static Callable<Long> paramsOp(SpiceClient client, long expectedRows) {
        return () -> {
            try (ArrowReader reader = client.queryWithParams(SQL, 5L)) {
                return checkedCountRows(reader, expectedRows);
            }
        };
    }

    public void testBenchmarkPlainQuery() throws Exception {
        try (SpiceClient client = SpiceClient.builder().withFlightAddress(server.flightUri()).build()) {
            final long expectedRows = server.expectedTotalRows();
            Callable<?> op = () -> {
                try (FlightStream stream = client.query("SELECT * FROM bench")) {
                    return checkedCountRows(stream, expectedRows);
                }
            };
            measure(WARMUP_ITERATIONS, op);
            long getFlightInfoBefore = server.getFlightInfoCalls.get();
            long doGetBefore = server.doGetCalls.get();
            long[] samples = measure(MEASURED_ITERATIONS, op);
            System.out.println("[bench] " + stats("query()", samples));
            recordBench("query() p50", "us", p50Micros(samples));

            // The plain query path is exactly 2 RPCs: GetFlightInfo + DoGet.
            assertEquals(MEASURED_ITERATIONS, server.getFlightInfoCalls.get() - getFlightInfoBefore);
            assertEquals(MEASURED_ITERATIONS, server.doGetCalls.get() - doGetBefore);
        }
    }

    public void testBenchmarkParameterizedQueryCachedVsUncached() throws Exception {
        try (SpiceClient cachedClient = SpiceClient.builder()
                .withFlightAddress(server.flightUri())
                .build();
                SpiceClient uncachedClient = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withPreparedStatementCacheSize(0)
                        .build()) {
            final long expectedRows = server.expectedTotalRows();
            Callable<Long> cachedOp = paramsOp(cachedClient, expectedRows);
            Callable<Long> uncachedOp = paramsOp(uncachedClient, expectedRows);

            // Warm both paths before measuring either, so JIT compilation of the
            // shared code doesn't bias whichever block runs second.
            measure(WARMUP_ITERATIONS, cachedOp);
            measure(WARMUP_ITERATIONS, uncachedOp);
            measure(WARMUP_ITERATIONS, cachedOp);
            measure(WARMUP_ITERATIONS, uncachedOp);

            long preparesBeforeCached = server.createPreparedStatementCalls.get();
            long[] cachedSamples = measure(MEASURED_ITERATIONS, cachedOp);
            long cachedPrepares = server.createPreparedStatementCalls.get() - preparesBeforeCached;

            long preparesBeforeUncached = server.createPreparedStatementCalls.get();
            long closesBeforeUncached = server.closePreparedStatementCalls.get();
            long[] uncachedSamples = measure(MEASURED_ITERATIONS, uncachedOp);
            long uncachedPrepares = server.createPreparedStatementCalls.get() - preparesBeforeUncached;
            long uncachedCloses = server.closePreparedStatementCalls.get() - closesBeforeUncached;

            System.out.println("[bench] " + stats("queryWithParams (cached)", cachedSamples));
            System.out.println("[bench] " + stats("queryWithParams (uncached)", uncachedSamples));
            recordBench("queryWithParams cached p50", "us", p50Micros(cachedSamples));
            recordBench("queryWithParams uncached p50", "us", p50Micros(uncachedSamples));
            System.out.printf(
                    "[bench] RPCs per %d queries: cached=%d prepares, uncached=%d prepares + %d closes "
                            + "(2 round trips saved per query on a real network)%n",
                    MEASURED_ITERATIONS, cachedPrepares, uncachedPrepares, uncachedCloses);

            // The RPC counts are the deterministic performance contract.
            assertEquals("cached path must reuse the prepared statement", 0, cachedPrepares);
            assertEquals("uncached path prepares on every query",
                    MEASURED_ITERATIONS, uncachedPrepares);
            assertEquals("uncached path closes on every query",
                    MEASURED_ITERATIONS, uncachedCloses);
        }
    }

    /**
     * Guards the parameter-root allocation fix: binding parameters for a
     * query must stay in the low-kilobyte range, not Arrow's ~48KB-per-string
     * default-capacity allocation.
     */
    public void testParameterBindAllocationStaysSmall() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            long totalCapacityBytes = 0;
            for (int i = 0; i < 100; i++) {
                try (org.apache.arrow.vector.VectorSchemaRoot root =
                        client.createParameterRoot(42L, "hello-" + i, 3.14)) {
                    for (org.apache.arrow.vector.FieldVector vector : root.getFieldVectors()) {
                        totalCapacityBytes += vector.getBufferSize();
                    }
                }
            }
            System.out.printf("[bench] param-root buffer bytes for 100 binds of (long,string,double): %d%n",
                    totalCapacityBytes);
            recordBench("param-root bytes per 100 binds", "bytes", totalCapacityBytes);
            // Old behavior: >48KB per string vector alone → many MB over 100 binds.
            assertTrue("parameter roots should stay small, used " + totalCapacityBytes + " bytes",
                    totalCapacityBytes < 200_000);
        }
    }
}
