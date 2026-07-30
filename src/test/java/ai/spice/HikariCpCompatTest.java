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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

/**
 * Verifies that Spice works with HikariCP-pooled JDBC connections.
 *
 * <p>{@link SpiceClient} itself is thread-safe and multiplexes queries over
 * shared gRPC channels, so it does not need — and cannot use — a JDBC pool.
 * Applications that want HikariCP (ORMs, existing JDBC infrastructure)
 * connect to Spice through the Arrow Flight SQL JDBC driver instead; these
 * tests prove that combination works end-to-end against a Flight SQL server,
 * including authentication and prepared statements.</p>
 */
public class HikariCpCompatTest extends TestCase {

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

    private HikariDataSource newPool(int maxPoolSize, String user, String password) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:arrow-flight-sql://localhost:" + server.getPort() + "/?useEncryption=false");
        if (user != null) {
            config.setUsername(user);
            config.setPassword(password);
        }
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        // The Flight SQL JDBC driver does not implement Connection.isValid(),
        // so give Hikari an explicit validation query.
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }

    private static long countRows(ResultSet resultSet) throws Exception {
        long rows = 0;
        while (resultSet.next()) {
            assertNotNull(resultSet.getString("name"));
            rows++;
        }
        return rows;
    }

    public void testPlainStatementThroughPool() throws Exception {
        try (HikariDataSource pool = newPool(2, null, null)) {
            try (Connection connection = pool.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM test")) {
                assertEquals(server.expectedTotalRows(), countRows(resultSet));
            }
        }
    }

    public void testPreparedStatementThroughPool() throws Exception {
        try (HikariDataSource pool = newPool(2, null, null)) {
            try (Connection connection = pool.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("SELECT * FROM test WHERE id > ?")) {
                statement.setLong(1, 5L);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertEquals(server.expectedTotalRows(), countRows(resultSet));
                }
            }
            assertTrue("JDBC prepared statement should bind parameters via DoPut",
                    server.doPutParameterCalls.get() > 0);
        }
    }

    /**
     * Connections are pooled and reused: cycling through the pool repeatedly
     * must not accumulate server-side connections or fail.
     */
    public void testConnectionReuseAcrossBorrows() throws Exception {
        try (HikariDataSource pool = newPool(1, null, null)) {
            for (int i = 0; i < 10; i++) {
                try (Connection connection = pool.getConnection();
                        Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("SELECT " + i)) {
                    assertEquals(server.expectedTotalRows(), countRows(resultSet));
                }
            }
        }
    }

    public void testConcurrentQueriesThroughPool() throws Exception {
        final int threads = 4;
        final int queriesPerThread = 5;
        final AtomicInteger failures = new AtomicInteger();
        final AtomicLong totalRows = new AtomicLong();

        try (HikariDataSource pool = newPool(threads, null, null)) {
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            final CountDownLatch start = new CountDownLatch(1);
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < queriesPerThread; i++) {
                            try (Connection connection = pool.getConnection();
                                    Statement statement = connection.createStatement();
                                    ResultSet resultSet = statement.executeQuery("SELECT * FROM test")) {
                                totalRows.addAndGet(countRows(resultSet));
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

            assertEquals("no pooled query should fail", 0, failures.get());
            assertEquals(threads * queriesPerThread * server.expectedTotalRows(), totalRows.get());
        }
    }

    /**
     * The same basic-auth handshake credentials the native client uses
     * (user = app id, password = full API key) work through Hikari's
     * username/password configuration.
     */
    public void testAuthenticatedPool() throws Exception {
        try (TestFlightSqlServer authServer = new TestFlightSqlServer("testapp", "testapp|secret")) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:arrow-flight-sql://localhost:" + authServer.getPort()
                    + "/?useEncryption=false");
            config.setUsername("testapp");
            config.setPassword("testapp|secret");
            config.setMaximumPoolSize(1);
            config.setConnectionTestQuery("SELECT 1");
            try (HikariDataSource pool = new HikariDataSource(config);
                    Connection connection = pool.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM test")) {
                assertEquals(authServer.expectedTotalRows(), countRows(resultSet));
            }
            assertTrue("pool connections must authenticate via handshake",
                    authServer.basicAuthValidations.get() >= 1);
        }
    }
}
