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

package ai.spice.example;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.TimeUnit;

import ai.spice.Param;
import ai.spice.SpiceClient;

/**
 * Example of using parameterized queries with Spice.ai OSS (Local).
 * 
 * Parameterized queries are the recommended approach for queries with user
 * input to prevent SQL injection attacks.
 * 
 * Run with:
 * _JAVA_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED" mvn exec:java
 * -Dexec.mainClass="ai.spice.example.ExampleParameterizedQueries"
 * 
 * Requires local Spice OSS running. Follow the quickstart:
 * https://github.com/spiceai/spiceai?tab=readme-ov-file#%EF%B8%8F-quickstart-local-machine
 */
public class ExampleParameterizedQueries {

    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder().build()) {

            System.out.println("=== Example 1: Simple parameterized query with type inference ===");
            simpleParameterizedQuery(client);

            System.out.println("\n=== Example 2: Multiple parameters ===");
            multipleParameters(client);

            System.out.println("\n=== Example 3: Explicit parameter types ===");
            explicitParameterTypes(client);

            System.out.println("\n=== Example 4: Mixed inferred and explicit types ===");
            mixedTypes(client);

        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example 1: Simple parameterized query with automatic type inference.
     * Pass Java values directly and the SDK will infer the Arrow types.
     */
    private static void simpleParameterizedQuery(SpiceClient client) throws Exception {
        // Types are automatically inferred from Java values:
        // - 10.0 (Double) -> Float64
        String sql = "SELECT trip_distance, fare_amount FROM taxi_trips WHERE trip_distance > $1 ORDER BY trip_distance LIMIT 5";

        try (ArrowReader reader = client.sqlWithParams(sql, 10.0)) {
            printResults(reader);
        }
    }

    /**
     * Example 2: Query with multiple parameters.
     * Use positional placeholders ($1, $2, etc.) in the SQL query.
     */
    private static void multipleParameters(SpiceClient client) throws Exception {
        // Multiple parameters with automatic type inference
        String sql = "SELECT trip_distance, fare_amount FROM taxi_trips "
                + "WHERE trip_distance > $1 AND fare_amount > $2 "
                + "ORDER BY trip_distance LIMIT 5";

        try (ArrowReader reader = client.sqlWithParams(sql, 5.0, 20.0)) {
            printResults(reader);
        }
    }

    /**
     * Example 3: Using explicit parameter types for precise control.
     * Use Param factory methods when you need control over the exact Arrow type.
     */
    private static void explicitParameterTypes(SpiceClient client) throws Exception {
        // Explicit types give you precise control over Arrow types
        String sql = "SELECT trip_distance, fare_amount, payment_type FROM taxi_trips "
                + "WHERE payment_type = $1 ORDER BY trip_distance LIMIT 5";

        // Use Param.int64() to explicitly specify Int64 type
        try (ArrowReader reader = client.sqlWithParams(sql, Param.int64(1))) {
            printResults(reader);
        }
    }

    /**
     * Example 4: Mixed inferred and explicit parameter types.
     */
    private static void mixedTypes(SpiceClient client) throws Exception {
        String sql = "SELECT trip_distance, fare_amount, store_and_fwd_flag FROM taxi_trips "
                + "WHERE trip_distance > $1 AND store_and_fwd_flag = $2 "
                + "ORDER BY trip_distance LIMIT 5";

        // Mix automatic inference (5.0) with explicit type (Param.string())
        try (ArrowReader reader = client.sqlWithParams(sql,
                5.0, // Inferred as Float64
                Param.string("N") // Explicit String type
        )) {
            printResults(reader);
        }
    }

    /**
     * Demonstrates all available Param factory methods.
     * This is for documentation purposes - not executed.
     */
    @SuppressWarnings("unused")
    private static void availableParamTypes() {
        // === Integer Types ===
        Param int8 = Param.int8((byte) 127);
        Param int16 = Param.int16((short) 32000);
        Param int32 = Param.int32(2000000);
        Param int64 = Param.int64(9000000000L);

        // Unsigned integers
        Param uint8 = Param.uint8((short) 255);
        Param uint16 = Param.uint16(65535);
        Param uint32 = Param.uint32(4294967295L);
        Param uint64 = Param.uint64(Long.MAX_VALUE);

        // === Floating Point Types ===
        Param float32 = Param.float32(3.14f);
        Param float64 = Param.float64(3.14159265359);

        // === String and Binary Types ===
        Param string = Param.string("hello");
        Param largeString = Param.largeString("very large string...");
        Param binary = Param.binary(new byte[] { 1, 2, 3 });
        Param largeBinary = Param.largeBinary(new byte[] { 1, 2, 3, 4, 5 });
        Param fixedBinary = Param.fixedSizeBinary(new byte[] { 1, 2, 3, 4 }, 4);

        // === Boolean Type ===
        Param bool = Param.bool(true);

        // === Temporal Types ===
        Param date32 = Param.date32(LocalDate.of(2024, 1, 15));
        Param date64 = Param.date64(LocalDate.of(2024, 1, 15));
        Param timestamp = Param.timestamp(LocalDateTime.now(), TimeUnit.MICROSECOND, "UTC");
        Param timestampUtc = Param.timestamp(LocalDateTime.now(), TimeUnit.MICROSECOND); // UTC is default

        // === Decimal Types ===
        Param decimal128 = Param.decimal128(new BigDecimal("12345.67"), 10, 2);
        Param decimal256 = Param.decimal256(new BigDecimal("12345678901234567890.12"), 40, 2);

        // === Null Type ===
        Param nullValue = Param.nullValue();

        // === Generic Factory Methods ===
        Param inferred = Param.of(42); // Type will be inferred (Int32)
        Param explicit = Param.of(42, new org.apache.arrow.vector.types.pojo.ArrowType.Int(32, true));
    }

    private static void printResults(ArrowReader reader) throws Exception {
        int totalRows = 0;
        while (reader.loadNextBatch()) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            System.out.println(root.contentToTSVString());
            totalRows += root.getRowCount();
        }
        System.out.println("Total rows: " + totalRows);
    }
}
