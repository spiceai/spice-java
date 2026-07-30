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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * Tests for the prepared statement cache: reuse, invalidation, and recovery.
 * The server-side RPC counters make the round-trip savings directly
 * observable and deterministic.
 */
public class StatementCacheTest extends TestCase {

    private static final String SQL = "SELECT * FROM test WHERE id = $1";

    private TestFlightSqlServer server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new TestFlightSqlServer();
    }

    @Override
    protected void tearDown() throws Exception {
        server.close();
        super.tearDown();
    }

    private SpiceClient newClient() throws Exception {
        return SpiceClient.builder().withFlightAddress(server.flightUri()).build();
    }

    private static void runQuery(SpiceClient client, String sql, Object... params) throws Exception {
        try (ArrowReader reader = client.queryWithParams(sql, params)) {
            long rows = LocalFlightServerTest.countRows(reader);
            assertTrue("query should return rows", rows > 0);
        }
    }

    /**
     * The core saving: repeated executions of the same SQL prepare exactly
     * once instead of once per query (2 fewer round trips per call).
     */
    public void testSameSqlPreparesOnce() throws Exception {
        try (SpiceClient client = newClient()) {
            for (int i = 0; i < 5; i++) {
                runQuery(client, SQL, (long) i);
            }
            assertEquals("statement should be prepared exactly once", 1,
                    server.createPreparedStatementCalls.get());
            assertEquals("statement should not be closed between queries", 0,
                    server.closePreparedStatementCalls.get());
            assertEquals("every query still binds its own parameters", 5,
                    server.doPutParameterCalls.get());
            assertEquals(5, server.getFlightInfoCalls.get());
            assertEquals(5, server.doGetCalls.get());
        }
        // close() drains the cache and closes the statement on the server.
        assertEquals(1, server.closePreparedStatementCalls.get());
    }

    public void testDistinctSqlPreparesDistinctStatements() throws Exception {
        try (SpiceClient client = newClient()) {
            runQuery(client, "SELECT * FROM a WHERE id = $1", 1L);
            runQuery(client, "SELECT * FROM b WHERE id = $1", 1L);
            runQuery(client, "SELECT * FROM c WHERE id = $1", 1L);
            assertEquals(3, server.createPreparedStatementCalls.get());
            // Re-run the first: cache hit, no new prepare.
            runQuery(client, "SELECT * FROM a WHERE id = $1", 2L);
            assertEquals(3, server.createPreparedStatementCalls.get());
        }
        assertEquals(3, server.closePreparedStatementCalls.get());
    }

    /**
     * Cache disabled restores the prepare/close-per-query behavior.
     */
    public void testCacheDisabled() throws Exception {
        try (SpiceClient client = SpiceClient.builder()
                .withFlightAddress(server.flightUri())
                .withPreparedStatementCacheSize(0)
                .build()) {
            for (int i = 0; i < 3; i++) {
                runQuery(client, SQL, (long) i);
            }
        }
        assertEquals(3, server.createPreparedStatementCalls.get());
        assertEquals(3, server.closePreparedStatementCalls.get());
    }

    /**
     * When the server no longer recognizes a cached handle (e.g. it
     * restarted), the query transparently re-prepares and succeeds.
     */
    public void testStaleHandleRePreparesTransparently() throws Exception {
        try (SpiceClient client = newClient()) {
            runQuery(client, SQL, 1L);
            assertEquals(1, server.createPreparedStatementCalls.get());

            server.invalidatePreparedStatements();

            runQuery(client, SQL, 2L);
            assertEquals("stale handle must trigger exactly one re-prepare", 2,
                    server.createPreparedStatementCalls.get());
        }
    }

    /**
     * reset() invalidates cached statements along with the transport.
     */
    public void testResetInvalidatesCache() throws Exception {
        try (SpiceClient client = newClient()) {
            runQuery(client, SQL, 1L);
            assertEquals(1, server.createPreparedStatementCalls.get());

            client.reset();
            assertEquals("reset must close the cached statement", 1,
                    server.closePreparedStatementCalls.get());

            runQuery(client, SQL, 2L);
            assertEquals(2, server.createPreparedStatementCalls.get());
        }
    }

    /**
     * Concurrent queries on the same SQL are safe: a statement is used by one
     * thread at a time, extra statements are prepared on cache misses, and
     * everything is closed by client.close() — no server-side leaks.
     */
    public void testConcurrentSameSqlQueries() throws Exception {
        final int threads = 8;
        final int queriesPerThread = 10;
        final AtomicInteger failures = new AtomicInteger();
        final AtomicLong totalRows = new AtomicLong();

        try (SpiceClient client = newClient()) {
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            final CountDownLatch start = new CountDownLatch(1);
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < queriesPerThread; i++) {
                            try (ArrowReader reader = client.queryWithParams(SQL, (long) i)) {
                                totalRows.addAndGet(LocalFlightServerTest.countRows(reader));
                            }
                        }
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

            assertEquals("no query should fail", 0, failures.get());
            assertEquals(threads * queriesPerThread * server.expectedTotalRows(), totalRows.get());
            assertTrue("at most one prepared statement per thread, got "
                    + server.createPreparedStatementCalls.get(),
                    server.createPreparedStatementCalls.get() <= threads);
        }
        assertEquals("every prepared statement must be closed on client.close()",
                server.createPreparedStatementCalls.get(), server.closePreparedStatementCalls.get());
    }
}
