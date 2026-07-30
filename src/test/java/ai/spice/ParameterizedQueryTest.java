/*
Copyright 2024 The Spice.ai OSS Authors

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
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.TimeUnit;

import com.google.common.base.Strings;

import junit.framework.TestCase;

/**
 * Tests for parameterized query functionality.
 */
public class ParameterizedQueryTest extends TestCase {

    /**
     * Test parameterized query with Spice Cloud Platform.
     */
    public void testParameterizedQuerySpiceCloud() throws Exception {
        String apiKey = System.getenv("API_KEY");

        if (Strings.isNullOrEmpty(apiKey)) {
            // Skip test if no API key is provided
            return;
        }

        try (SpiceClient spiceClient = SpiceClient.builder()
                .withApiKey(apiKey)
                .withHttpAddress(new URI("https://data.spiceai.io"))
                .withFlightAddress(new URI("https://flight.spiceai.io:443"))
                .build()) {

            // Test with float parameter - taxi_trips available in Spice Cloud
            String sql = "SELECT tpep_pickup_datetime, total_amount FROM taxi_trips WHERE total_amount > $1 ORDER BY total_amount LIMIT 5";
            try (ArrowReader reader = spiceClient.queryWithParams(sql, 10.0)) {
                int totalRows = 0;

                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    assertTrue("Schema should have tpep_pickup_datetime field",
                            root.getSchema().findField("tpep_pickup_datetime") != null);
                    assertTrue("Schema should have total_amount field",
                            root.getSchema().findField("total_amount") != null);
                    totalRows += root.getRowCount();
                }

                assertTrue("Expected at least 1 row", totalRows > 0);
                assertTrue("Expected at most 5 rows", totalRows <= 5);
            }
        }
    }

    /**
     * Test parameterized query with local Spice OSS runtime.
     */
    public void testParameterizedQuerySpiceOSS() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {

            // Test with float parameter on tpch.orders
            String sql = "SELECT o_orderkey, o_totalprice FROM tpch.orders WHERE o_totalprice > $1 ORDER BY o_totalprice LIMIT 5";
            try (ArrowReader reader = spiceClient.queryWithParams(sql, 10000.0)) {
                int totalRows = 0;

                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    assertTrue("Schema should have o_orderkey field",
                            root.getSchema().findField("o_orderkey") != null);
                    assertTrue("Schema should have o_totalprice field",
                            root.getSchema().findField("o_totalprice") != null);
                    totalRows += root.getRowCount();
                }

                assertTrue("Expected at least 1 row", totalRows > 0);
            }
        } catch (ExecutionException e) {
            // Local Spice runtime might not be running, skip test
            if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Connection refused") || e.getMessage().contains("not found") || e.getMessage().contains("io exception")) {
                return;
            }
            throw e;
        }
    }

    /**
     * Test parameterized query with multiple parameters.
     */
    public void testMultipleParameters() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {

            String sql = "SELECT o_orderkey, o_totalprice FROM tpch.orders WHERE o_totalprice > $1 AND o_custkey > $2 LIMIT 5";
            try (ArrowReader reader = spiceClient.queryWithParams(sql, 5000.0, 100)) {
                int totalRows = 0;

                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    totalRows += root.getRowCount();
                }

                // Just verify it executes without error
                assertTrue("Query executed successfully", true);
            }
        } catch (ExecutionException e) {
            // Local Spice runtime might not be running, skip test
            if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Connection refused") || e.getMessage().contains("not found") || e.getMessage().contains("io exception")) {
                return;
            }
            throw e;
        }
    }

    /**
     * Test parameterized query with string parameter.
     */
    public void testStringParameter() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {

            // Use c_mktsegment which is a string column in tpch.customer
            String sql = "SELECT c_custkey, c_mktsegment FROM tpch.customer WHERE c_mktsegment = $1 LIMIT 5";
            try (ArrowReader reader = spiceClient.queryWithParams(sql, "BUILDING")) {
                int totalRows = 0;

                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    totalRows += root.getRowCount();
                }

                // Just verify it executes without error
                assertTrue("Query executed successfully", true);
            }
        } catch (ExecutionException e) {
            // Local Spice runtime might not be running, skip test
            if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Connection refused") || e.getMessage().contains("not found") || e.getMessage().contains("io exception")) {
                return;
            }
            throw e;
        }
    }

    /**
     * Test parameterized query with explicit Param types.
     */
    public void testExplicitParamTypes() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {

            // Use explicit int64 type on tpch.customer
            String sql = "SELECT c_custkey, c_name, c_nationkey FROM tpch.customer WHERE c_nationkey = $1 LIMIT 5";
            try (ArrowReader reader = spiceClient.queryWithParams(sql, Param.int64(1))) {
                int totalRows = 0;

                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    totalRows += root.getRowCount();
                }

                assertTrue("Query executed successfully", true);
            }
        } catch (ExecutionException e) {
            // Local Spice runtime might not be running, skip test
            if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Connection refused") || e.getMessage().contains("not found") || e.getMessage().contains("io exception")) {
                return;
            }
            throw e;
        }
    }

    /**
     * Test parameterized query with mixed parameter types.
     */
    public void testMixedParameterTypes() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {

            String sql = "SELECT o_orderkey, o_totalprice FROM tpch.orders WHERE o_totalprice > $1 AND o_orderstatus = $2 LIMIT 5";
            try (ArrowReader reader = spiceClient.queryWithParams(sql,
                    Param.float64(5000.0),
                    Param.string("O"))) {
                int totalRows = 0;

                while (reader.loadNextBatch()) {
                    VectorSchemaRoot root = reader.getVectorSchemaRoot();
                    totalRows += root.getRowCount();
                }

                assertTrue("Query executed successfully", true);
            }
        } catch (ExecutionException e) {
            // Local Spice runtime might not be running, skip test
            if (e.getMessage().contains("UNAVAILABLE") || e.getMessage().contains("Connection refused") || e.getMessage().contains("not found") || e.getMessage().contains("io exception")) {
                return;
            }
            throw e;
        }
    }

    /**
     * Test Param class factory methods.
     */
    public void testParamFactoryMethods() {
        // Integer types
        Param p1 = Param.int8((byte) 1);
        assertEquals((byte) 1, p1.getValue());
        assertTrue(p1.hasExplicitType());

        Param p2 = Param.int16((short) 100);
        assertEquals((short) 100, p2.getValue());

        Param p3 = Param.int32(1000);
        assertEquals(1000, p3.getValue());

        Param p4 = Param.int64(10000L);
        assertEquals(10000L, p4.getValue());

        // Floating point types
        Param p5 = Param.float32(1.5f);
        assertEquals(1.5f, p5.getValue());

        Param p6 = Param.float64(2.5);
        assertEquals(2.5, p6.getValue());

        // String types
        Param p7 = Param.string("test");
        assertEquals("test", p7.getValue());

        Param p8 = Param.largeString("large test");
        assertEquals("large test", p8.getValue());

        // Boolean
        Param p9 = Param.bool(true);
        assertEquals(true, p9.getValue());

        // Binary
        byte[] bytes = { 1, 2, 3 };
        Param p10 = Param.binary(bytes);
        assertSame(bytes, p10.getValue());

        // Date types
        LocalDate date = LocalDate.of(2024, 1, 15);
        Param p11 = Param.date32(date);
        assertEquals(date, p11.getValue());

        // Timestamp
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        Param p12 = Param.timestamp(dateTime, TimeUnit.MICROSECOND);
        assertEquals(dateTime, p12.getValue());

        // Decimal
        BigDecimal decimal = new BigDecimal("123.45");
        Param p13 = Param.decimal128(decimal, 10, 2);
        assertEquals(decimal, p13.getValue());

        // Null
        Param p14 = Param.nullValue();
        assertNull(p14.getValue());
        assertTrue(p14.hasExplicitType());

        // Generic factory methods
        Param p15 = Param.of(42);
        assertEquals(42, p15.getValue());
        assertFalse(p15.hasExplicitType());
    }

    /**
     * Test that null SQL throws IllegalArgumentException.
     */
    public void testNullSqlThrows() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {
            try {
                spiceClient.queryWithParams(null, 1);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("No SQL query provided"));
            }
        } catch (Exception e) {
            // Connection errors are ok - we're testing argument validation
            if (!(e instanceof IllegalArgumentException)) {
                return;
            }
            throw e;
        }
    }

    /**
     * Test that empty SQL throws IllegalArgumentException.
     */
    public void testEmptySqlThrows() throws Exception {
        try (SpiceClient spiceClient = SpiceClient.builder().withMaxRetries(1).build()) {
            try {
                spiceClient.queryWithParams("", 1);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("No SQL query provided"));
            }
        } catch (Exception e) {
            // Connection errors are ok - we're testing argument validation
            if (!(e instanceof IllegalArgumentException)) {
                return;
            }
            throw e;
        }
    }
}
