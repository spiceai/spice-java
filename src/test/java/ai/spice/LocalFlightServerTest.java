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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.util.Text;

import junit.framework.TestCase;

/**
 * End-to-end tests for both query paths against an in-process Flight SQL
 * server. These run without any external Spice runtime.
 */
public class LocalFlightServerTest extends TestCase {

    private TestFlightSqlServer server;
    private SpiceClient client;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new TestFlightSqlServer();
        client = SpiceClient.builder()
                .withFlightAddress(server.flightUri())
                .build();
    }

    @Override
    protected void tearDown() throws Exception {
        client.close();
        server.close();
        super.tearDown();
    }

    static long countRows(FlightStream stream) throws Exception {
        long rows = 0;
        while (stream.next()) {
            rows += stream.getRoot().getRowCount();
        }
        return rows;
    }

    static long countRows(ArrowReader reader) throws Exception {
        long rows = 0;
        while (reader.loadNextBatch()) {
            rows += reader.getVectorSchemaRoot().getRowCount();
        }
        return rows;
    }

    public void testPlainQueryReturnsAllRows() throws Exception {
        try (FlightStream stream = client.query("SELECT * FROM test")) {
            assertNotNull(stream.getSchema().findField("id"));
            assertNotNull(stream.getSchema().findField("name"));
            assertEquals(server.expectedTotalRows(), countRows(stream));
        }
        assertEquals(1, server.getFlightInfoCalls.get());
        assertEquals(1, server.doGetCalls.get());
    }

    public void testQueryWithParamsReturnsAllRows() throws Exception {
        try (ArrowReader reader = client.queryWithParams("SELECT * FROM test WHERE id > $1", 5L)) {
            assertNotNull(reader.getVectorSchemaRoot().getSchema().findField("id"));
            assertEquals(server.expectedTotalRows(), countRows(reader));
            assertTrue("bytesRead should be positive", reader.bytesRead() > 0);
        }
        assertEquals(1, server.createPreparedStatementCalls.get());
        assertEquals(1, server.doPutParameterCalls.get());
        assertEquals(1, server.doGetCalls.get());
    }

    public void testQueryWithParamsWithoutParameters() throws Exception {
        try (ArrowReader reader = client.queryWithParams("SELECT 1")) {
            assertEquals(server.expectedTotalRows(), countRows(reader));
        }
        // No parameters bound: no DoPut should have happened.
        assertEquals(0, server.doPutParameterCalls.get());
    }

    /**
     * queryWithParams (ArrowReader) consumes every endpoint of a partitioned
     * result.
     */
    public void testQueryWithParamsReadsAllEndpoints() throws Exception {
        server.endpointCount = 3;
        server.batchesPerEndpoint = 2;
        server.rowsPerBatch = 7;
        try (ArrowReader reader = client.queryWithParams("SELECT * FROM test", 1)) {
            assertEquals(3 * 2 * 7, countRows(reader));
        }
        assertEquals("one DoGet per endpoint", 3, server.doGetCalls.get());
    }

    /**
     * Documents the known limitation of the FlightStream-returning query()
     * API: only the first endpoint of a partitioned result is consumed.
     */
    public void testPlainQueryConsumesOnlyFirstEndpoint() throws Exception {
        server.endpointCount = 3;
        try (FlightStream stream = client.query("SELECT * FROM test")) {
            assertEquals(server.batchesPerEndpoint * server.rowsPerBatch, countRows(stream));
        }
        assertEquals(1, server.doGetCalls.get());
    }

    public void testParameterValuesArriveAtServer() throws Exception {
        try (ArrowReader reader = client.queryWithParams(
                "SELECT * FROM test WHERE a=$1 AND b=$2 AND c=$3 AND d=$4 AND e=$5 AND f=$6 AND g=$7 AND h=$8",
                42, 42L, "hello", 3.5, true, new byte[] { 1, 2, 3 },
                LocalDate.of(2026, 7, 30), new BigDecimal("12.34"))) {
            countRows(reader);
        }
        List<Object> bound = server.lastBoundParameters;
        assertNotNull(bound);
        assertEquals(8, bound.size());
        assertEquals(42, bound.get(0));
        assertEquals(42L, bound.get(1));
        assertEquals(new Text("hello"), bound.get(2));
        assertEquals(3.5, bound.get(3));
        assertEquals(Boolean.TRUE, bound.get(4));
        assertTrue(bound.get(5) instanceof byte[]);
        assertEquals(3, ((byte[]) bound.get(5)).length);
        // DateDayVector surfaces the raw day count since the Unix epoch.
        assertEquals((int) LocalDate.of(2026, 7, 30).toEpochDay(), bound.get(6));
        assertEquals(new BigDecimal("12.34"), bound.get(7));
    }

    public void testExplicitParamTypesArriveAtServer() throws Exception {
        try (ArrowReader reader = client.queryWithParams(
                "SELECT * FROM test WHERE a=$1 AND b=$2 AND c=$3",
                Param.int32(7), Param.string("typed"), Param.float64(2.25))) {
            countRows(reader);
        }
        List<Object> bound = server.lastBoundParameters;
        assertNotNull(bound);
        assertEquals(3, bound.size());
        assertEquals(7, bound.get(0));
        assertEquals(new Text("typed"), bound.get(1));
        assertEquals(2.25, bound.get(2));
    }

    public void testRepeatedQueriesReturnConsistentResults() throws Exception {
        for (int i = 0; i < 5; i++) {
            try (ArrowReader reader = client.queryWithParams("SELECT * FROM test WHERE id = $1", (long) i)) {
                assertEquals(server.expectedTotalRows(), countRows(reader));
            }
            try (FlightStream stream = client.query("SELECT " + i)) {
                assertEquals(server.expectedTotalRows(), countRows(stream));
            }
        }
    }

    public void testEmptySqlThrowsIllegalArgument() throws Exception {
        try {
            client.query("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            client.queryWithParams("", 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testQueryAfterCloseThrowsIllegalState() throws Exception {
        SpiceClient shortLived = SpiceClient.builder().withFlightAddress(server.flightUri()).build();
        shortLived.close();
        try {
            shortLived.query("SELECT 1");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
        try {
            shortLived.queryWithParams("SELECT $1", 1);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    public void testUnsupportedParameterTypeFailsWithoutRpc() throws Exception {
        long infoCallsBefore = server.getFlightInfoCalls.get();
        try {
            client.queryWithParams("SELECT $1", new Object());
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue("cause should be IllegalArgumentException, got: " + e.getCause(),
                    e.getCause() instanceof IllegalArgumentException);
        }
        assertEquals("type inference failures must not reach the server",
                infoCallsBefore, server.getFlightInfoCalls.get());
    }
}
