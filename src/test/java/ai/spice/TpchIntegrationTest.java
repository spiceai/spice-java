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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;

import junit.framework.TestCase;

/**
 * Comprehensive integration tests using TPC-H dataset.
 * These tests verify query execution, result validation, and database operations.
 * 
 * Requires a local Spice OSS runtime with TPC-H data loaded.
 */
public class TpchIntegrationTest extends TestCase {

    private SpiceClient client;
    private boolean tpchAvailable = true;

    // Availability is probed once for the whole class (with retries disabled)
    // so a missing local runtime doesn't cost retry backoff per test method.
    private static volatile Boolean tpchAvailableCached;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (tpchAvailableCached == null) {
            synchronized (TpchIntegrationTest.class) {
                if (tpchAvailableCached == null) {
                    try (SpiceClient probe = SpiceClient.builder().withMaxRetries(1).build();
                            FlightStream stream = probe.query("SELECT c_custkey FROM tpch.customer LIMIT 1")) {
                        stream.next();
                        tpchAvailableCached = Boolean.TRUE;
                    } catch (Exception e) {
                        // TPC-H tables not available (either no server or no TPC-H data)
                        tpchAvailableCached = Boolean.FALSE;
                    }
                }
            }
        }
        tpchAvailable = tpchAvailableCached;
        if (tpchAvailable) {
            client = SpiceClient.builder().build();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        super.tearDown();
    }

    // ==================== SHOW TABLES Tests ====================

    public void testShowTables() throws Exception {
        if (!tpchAvailable) return;

        try (FlightStream stream = client.query("SHOW TABLES")) {
            Set<String> tableNames = new HashSet<>();
            int columnCount = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                columnCount = root.getFieldVectors().size();
                
                // Get table names from the result
                for (int i = 0; i < root.getRowCount(); i++) {
                    FieldVector tableVector = root.getVector("table_name");
                    if (tableVector instanceof VarCharVector) {
                        byte[] bytes = ((VarCharVector) tableVector).get(i);
                        if (bytes != null) {
                            tableNames.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            
            assertTrue("SHOW TABLES should return columns", columnCount > 0);
            // TPC-H tables should be present
            assertTrue("Should have tpch.customer table", 
                    tableNames.stream().anyMatch(t -> t.contains("customer")));
        }
    }

    // ==================== Customer Table Tests ====================

    public void testCustomerQuery() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT c_custkey, c_name, c_nationkey FROM tpch.customer LIMIT 10";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            boolean hasExpectedColumns = false;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                
                // Validate schema on first batch
                if (!hasExpectedColumns) {
                    assertNotNull("Should have c_custkey", root.getSchema().findField("c_custkey"));
                    assertNotNull("Should have c_name", root.getSchema().findField("c_name"));
                    assertNotNull("Should have c_nationkey", root.getSchema().findField("c_nationkey"));
                    hasExpectedColumns = true;
                }
                
                totalRows += root.getRowCount();
                
                // Validate data types
                for (int i = 0; i < root.getRowCount(); i++) {
                    // c_custkey should be a positive integer
                    FieldVector custKeyVector = root.getVector("c_custkey");
                    validatePositiveId(custKeyVector, i, "c_custkey");
                }
            }
            
            assertTrue("Should have columns", hasExpectedColumns);
            assertTrue("Should return data rows", totalRows > 0);
            assertEquals("LIMIT 10 should return 10 rows", 10, totalRows);
        }
    }

    public void testCustomerWithFilter() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT c_custkey, c_nationkey FROM tpch.customer WHERE c_nationkey = 1 LIMIT 5";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
                
                // All nationkey values should be 1
                for (int i = 0; i < root.getRowCount(); i++) {
                    FieldVector nationKeyVector = root.getVector("c_nationkey");
                    int nationKey = getIntValue(nationKeyVector, i);
                    assertEquals("c_nationkey should be 1", 1, nationKey);
                }
            }
            
            assertTrue("Should return some rows", totalRows > 0);
        }
    }

    // ==================== Orders Table Tests ====================

    public void testOrdersQuery() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT o_orderkey, o_custkey, o_totalprice FROM tpch.orders LIMIT 10";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
                
                for (int i = 0; i < root.getRowCount(); i++) {
                    // o_orderkey should be positive
                    validatePositiveId(root.getVector("o_orderkey"), i, "o_orderkey");
                    
                    // o_totalprice should be non-negative
                    FieldVector priceVector = root.getVector("o_totalprice");
                    double price = getDoubleValue(priceVector, i);
                    assertTrue("o_totalprice should be non-negative", price >= 0);
                }
            }
            
            assertEquals("LIMIT 10 should return 10 rows", 10, totalRows);
        }
    }

    public void testOrdersWithPriceFilter() throws Exception {
        if (!tpchAvailable) return;

        // Use a lower threshold that works with any scale factor
        String sql = "SELECT o_orderkey, o_totalprice FROM tpch.orders WHERE o_totalprice > 1000 LIMIT 5";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
                
                // All prices should be > 1000
                for (int i = 0; i < root.getRowCount(); i++) {
                    FieldVector priceVector = root.getVector("o_totalprice");
                    double price = getNumericValue(priceVector, i);
                    assertTrue("o_totalprice should be > 1000, got: " + price, price > 1000);
                }
            }
            
            assertTrue("Should return some rows", totalRows > 0);
        }
    }

    // ==================== Aggregation Tests ====================

    public void testCountQuery() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT COUNT(*) as cnt FROM tpch.customer";
        try (FlightStream stream = client.query(sql)) {
            long count = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                if (root.getRowCount() > 0) {
                    count = getLongValue(root.getVector("cnt"), 0);
                }
            }
            
            assertTrue("Customer count should be positive", count > 0);
        }
    }

    public void testSumQuery() throws Exception {
        if (!tpchAvailable) return;

        // Use direct SUM without LIMIT (LIMIT doesn't work on aggregation)
        String sql = "SELECT SUM(o_totalprice) as total FROM tpch.orders";
        try (FlightStream stream = client.query(sql)) {
            Double sum = null;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                if (root.getRowCount() > 0) {
                    FieldVector totalVector = root.getVector("total");
                    if (!totalVector.isNull(0)) {
                        sum = getNumericValue(totalVector, 0);
                    }
                }
            }
            
            assertNotNull("Sum should not be null", sum);
            assertTrue("Sum of order prices should be positive, got: " + sum, sum > 0);
        }
    }

    public void testGroupByQuery() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT o_orderstatus, COUNT(*) as cnt FROM tpch.orders GROUP BY o_orderstatus";
        try (FlightStream stream = client.query(sql)) {
            int statusCount = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                statusCount += root.getRowCount();
                
                for (int i = 0; i < root.getRowCount(); i++) {
                    long cnt = getLongValue(root.getVector("cnt"), i);
                    assertTrue("Each status count should be positive", cnt > 0);
                }
            }
            
            assertTrue("Should have at least one status group", statusCount > 0);
        }
    }

    // ==================== JOIN Tests ====================

    public void testSimpleJoin() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT c.c_name, o.o_orderkey " +
                "FROM tpch.customer c " +
                "JOIN tpch.orders o ON c.c_custkey = o.o_custkey " +
                "LIMIT 5";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
                
                assertNotNull("Should have c_name column", root.getVector("c_name"));
                assertNotNull("Should have o_orderkey column", root.getVector("o_orderkey"));
            }
            
            assertTrue("JOIN should return rows", totalRows > 0);
        }
    }

    // ==================== DESCRIBE Tests ====================

    public void testDescribeTable() throws Exception {
        if (!tpchAvailable) return;

        String sql = "DESCRIBE tpch.customer";
        try (FlightStream stream = client.query(sql)) {
            int columnCount = 0;
            Set<String> columnNames = new HashSet<>();
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                columnCount += root.getRowCount();
                
                // Get column names
                FieldVector nameVector = root.getVector("column_name");
                if (nameVector instanceof VarCharVector) {
                    for (int i = 0; i < root.getRowCount(); i++) {
                        byte[] bytes = ((VarCharVector) nameVector).get(i);
                        if (bytes != null) {
                            columnNames.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            
            assertTrue("DESCRIBE should return columns", columnCount > 0);
            assertTrue("Should have c_custkey column", columnNames.contains("c_custkey"));
            assertTrue("Should have c_name column", columnNames.contains("c_name"));
        }
    }

    // ==================== ORDER BY Tests ====================

    public void testOrderByAsc() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT c_custkey FROM tpch.customer ORDER BY c_custkey ASC LIMIT 5";
        try (FlightStream stream = client.query(sql)) {
            long previousKey = -1;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                for (int i = 0; i < root.getRowCount(); i++) {
                    long currentKey = getLongValue(root.getVector("c_custkey"), i);
                    assertTrue("Keys should be in ascending order", currentKey >= previousKey);
                    previousKey = currentKey;
                }
            }
            
            assertTrue("Should have returned at least one row", previousKey >= 0);
        }
    }

    public void testOrderByDesc() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT c_custkey FROM tpch.customer ORDER BY c_custkey DESC LIMIT 5";
        try (FlightStream stream = client.query(sql)) {
            long previousKey = Long.MAX_VALUE;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                for (int i = 0; i < root.getRowCount(); i++) {
                    long currentKey = getLongValue(root.getVector("c_custkey"), i);
                    assertTrue("Keys should be in descending order", currentKey <= previousKey);
                    previousKey = currentKey;
                }
            }
            
            assertTrue("Should have returned at least one row", previousKey < Long.MAX_VALUE);
        }
    }

    // ==================== NULL Handling Tests ====================

    public void testNullHandling() throws Exception {
        if (!tpchAvailable) return;

        // Query that might return nulls
        String sql = "SELECT c_custkey, c_phone FROM tpch.customer LIMIT 10";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
                
                // Just verify we can read the data without exceptions
                for (int i = 0; i < root.getRowCount(); i++) {
                    // Check nullness
                    FieldVector phoneVector = root.getVector("c_phone");
                    if (phoneVector.isNull(i)) {
                        // Null is valid
                    } else if (phoneVector instanceof VarCharVector) {
                        byte[] bytes = ((VarCharVector) phoneVector).get(i);
                        assertNotNull("Non-null phone should have value", bytes);
                    }
                }
            }
            
            assertTrue("Should return rows", totalRows > 0);
        }
    }

    // ==================== Large Result Set Tests ====================

    public void testLargeResultSet() throws Exception {
        if (!tpchAvailable) return;

        String sql = "SELECT c_custkey, c_name FROM tpch.customer LIMIT 1000";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
            }
            
            assertEquals("Should return 1000 rows", 1000, totalRows);
        }
    }

    // ==================== Empty Result Tests ====================

    public void testEmptyResult() throws Exception {
        if (!tpchAvailable) return;

        // Query that should return no results
        String sql = "SELECT c_custkey FROM tpch.customer WHERE c_custkey < 0";
        try (FlightStream stream = client.query(sql)) {
            int totalRows = 0;
            
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                totalRows += root.getRowCount();
            }
            
            assertEquals("Should return no rows", 0, totalRows);
        }
    }

    // ==================== Error Handling Tests ====================

    public void testInvalidTableName() {
        if (!tpchAvailable) return;

        try {
            try (FlightStream stream = client.query("SELECT * FROM nonexistent_table")) {
                while (stream.next()) {
                    // Should not get here
                }
            }
            fail("Should throw exception for invalid table");
        } catch (Exception e) {
            // Expected - table doesn't exist
            assertTrue("Error should mention table", 
                    e.getMessage().toLowerCase().contains("table") || 
                    e.getMessage().toLowerCase().contains("not found") ||
                    e.getMessage().toLowerCase().contains("nonexistent"));
        }
    }

    public void testInvalidColumnName() {
        if (!tpchAvailable) return;

        try {
            try (FlightStream stream = client.query("SELECT nonexistent_column FROM tpch.customer")) {
                while (stream.next()) {
                    // Should not get here
                }
            }
            fail("Should throw exception for invalid column");
        } catch (Exception e) {
            // Expected - column doesn't exist
            assertTrue("Error should mention column or field", 
                    e.getMessage().toLowerCase().contains("column") || 
                    e.getMessage().toLowerCase().contains("field") ||
                    e.getMessage().toLowerCase().contains("nonexistent"));
        }
    }

    public void testSyntaxError() {
        if (!tpchAvailable) return;

        try {
            try (FlightStream stream = client.query("SELEC * FROM tpch.customer")) {
                while (stream.next()) {
                    // Should not get here
                }
            }
            fail("Should throw exception for syntax error");
        } catch (Exception e) {
            // Expected - syntax error
            assertNotNull("Should have error message", e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private void validatePositiveId(FieldVector vector, int index, String name) {
        if (vector instanceof BigIntVector) {
            long value = ((BigIntVector) vector).get(index);
            assertTrue(name + " should be positive", value > 0);
        } else if (vector instanceof IntVector) {
            int value = ((IntVector) vector).get(index);
            assertTrue(name + " should be positive", value > 0);
        }
    }

    private int getIntValue(FieldVector vector, int index) {
        if (vector instanceof BigIntVector) {
            return (int) ((BigIntVector) vector).get(index);
        } else if (vector instanceof IntVector) {
            return ((IntVector) vector).get(index);
        }
        return 0;
    }

    private long getLongValue(FieldVector vector, int index) {
        if (vector instanceof BigIntVector) {
            return ((BigIntVector) vector).get(index);
        } else if (vector instanceof IntVector) {
            return ((IntVector) vector).get(index);
        }
        return 0;
    }

    private double getDoubleValue(FieldVector vector, int index) {
        if (vector instanceof Float8Vector) {
            return ((Float8Vector) vector).get(index);
        } else if (vector instanceof BigIntVector) {
            return ((BigIntVector) vector).get(index);
        } else if (vector instanceof IntVector) {
            return ((IntVector) vector).get(index);
        }
        return 0;
    }

    /**
     * Get a numeric value from any numeric vector type, including DecimalVector.
     */
    private double getNumericValue(FieldVector vector, int index) {
        if (vector instanceof Float8Vector) {
            return ((Float8Vector) vector).get(index);
        } else if (vector instanceof BigIntVector) {
            return ((BigIntVector) vector).get(index);
        } else if (vector instanceof IntVector) {
            return ((IntVector) vector).get(index);
        } else if (vector instanceof DecimalVector) {
            BigDecimal bd = ((DecimalVector) vector).getObject(index);
            return bd != null ? bd.doubleValue() : 0;
        } else {
            // Fallback: try getObject and convert
            Object obj = vector.getObject(index);
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            }
        }
        return 0;
    }
}
