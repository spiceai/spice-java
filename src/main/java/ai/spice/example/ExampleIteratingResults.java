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

package ai.spice.example;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import ai.spice.SpiceClient;

/**
 * Comprehensive example showing how to iterate through query results,
 * access individual fields and rows, and work with different data types.
 * 
 * Run with:
 * _JAVA_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED" mvn exec:java
 * -Dexec.mainClass="ai.spice.example.ExampleIteratingResults"
 * 
 * Requires local Spice OSS running. Follow the quickstart:
 * https://github.com/spiceai/spiceai?tab=readme-ov-file#%EF%B8%8F-quickstart-local-machine
 */
public class ExampleIteratingResults {

    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder().build()) {

            FlightStream stream = client.sql("SELECT * FROM taxi_trips LIMIT 5;");

            // Process each batch of results
            while (stream.next()) {
                try (VectorSchemaRoot root = stream.getRoot()) {
                    // Get schema information
                    Schema schema = root.getSchema();
                    int rowCount = root.getRowCount();

                    System.out.println("=== Schema Information ===");
                    System.out.println("Number of columns: " + schema.getFields().size());
                    System.out.println("Number of rows in batch: " + rowCount);
                    System.out.println();

                    // Print column names and types
                    System.out.println("=== Column Definitions ===");
                    for (Field field : schema.getFields()) {
                        System.out.printf("  %-25s : %s%n", field.getName(), field.getType());
                    }
                    System.out.println();

                    // Example 1: Iterate through all rows and columns generically
                    System.out.println("=== Method 1: Generic Iteration ===");
                    for (int row = 0; row < rowCount; row++) {
                        System.out.println("Row " + row + ":");
                        for (FieldVector vector : root.getFieldVectors()) {
                            String columnName = vector.getName();
                            Object value = vector.isNull(row) ? "NULL" : vector.getObject(row);
                            System.out.printf("  %-25s = %s%n", columnName, value);
                        }
                        System.out.println();
                    }

                    // Example 2: Access specific columns by name with type safety
                    System.out.println("=== Method 2: Typed Column Access ===");

                    // Get vectors by name - cast to appropriate type
                    FieldVector tripDistanceVector = root.getVector("trip_distance_mi");
                    FieldVector fareAmountVector = root.getVector("fare_amount");
                    FieldVector pickupDatetimeVector = root.getVector("pickup_datetime");
                    FieldVector dropoffDatetimeVector = root.getVector("dropoff_datetime");

                    for (int row = 0; row < rowCount; row++) {
                        System.out.println("Trip " + (row + 1) + ":");

                        // Access double values (Float8Vector)
                        if (tripDistanceVector instanceof Float8Vector) {
                            Float8Vector distanceVec = (Float8Vector) tripDistanceVector;
                            if (!distanceVec.isNull(row)) {
                                double distance = distanceVec.get(row);
                                System.out.printf("  Distance: %.2f miles%n", distance);
                            }
                        }

                        // Access fare amount
                        if (fareAmountVector instanceof Float8Vector) {
                            Float8Vector fareVec = (Float8Vector) fareAmountVector;
                            if (!fareVec.isNull(row)) {
                                double fare = fareVec.get(row);
                                System.out.printf("  Fare: $%.2f%n", fare);
                            }
                        }

                        // Access timestamp values
                        if (pickupDatetimeVector instanceof TimeStampMicroTZVector) {
                            TimeStampMicroTZVector pickupVec = (TimeStampMicroTZVector) pickupDatetimeVector;
                            if (!pickupVec.isNull(row)) {
                                // Value is in microseconds since epoch
                                long pickupMicros = pickupVec.get(row);
                                System.out.printf("  Pickup: %d (microseconds since epoch)%n", pickupMicros);
                            }
                        }

                        if (dropoffDatetimeVector instanceof TimeStampMicroTZVector) {
                            TimeStampMicroTZVector dropoffVec = (TimeStampMicroTZVector) dropoffDatetimeVector;
                            if (!dropoffVec.isNull(row)) {
                                long dropoffMicros = dropoffVec.get(row);
                                System.out.printf("  Dropoff: %d (microseconds since epoch)%n", dropoffMicros);
                            }
                        }
                        System.out.println();
                    }

                    // Example 3: Access by column index
                    System.out.println("=== Method 3: Access by Column Index ===");
                    for (int row = 0; row < Math.min(rowCount, 2); row++) {
                        System.out.println("Row " + row + " first 3 columns:");
                        for (int col = 0; col < Math.min(3, root.getFieldVectors().size()); col++) {
                            FieldVector vector = root.getVector(col);
                            String name = vector.getName();
                            Object value = vector.isNull(row) ? "NULL" : vector.getObject(row);
                            System.out.printf("  [%d] %s = %s%n", col, name, value);
                        }
                    }
                    System.out.println();

                    // Example 4: Working with String values
                    System.out.println("=== Method 4: Working with Strings ===");
                    FieldVector vendorVector = root.getVector("vendor_id");
                    if (vendorVector instanceof VarCharVector) {
                        VarCharVector strVec = (VarCharVector) vendorVector;
                        for (int row = 0; row < rowCount; row++) {
                            if (!strVec.isNull(row)) {
                                // Get the byte array and convert to String
                                byte[] bytes = strVec.get(row);
                                String vendorId = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                System.out.printf("  Row %d vendor_id: %s%n", row, vendorId);
                            }
                        }
                    }
                    System.out.println();

                    // Example 5: Working with Long/BigInt values
                    System.out.println("=== Method 5: Working with Long Values ===");
                    FieldVector passengerCountVector = root.getVector("passenger_count");
                    if (passengerCountVector instanceof BigIntVector) {
                        BigIntVector longVec = (BigIntVector) passengerCountVector;
                        for (int row = 0; row < rowCount; row++) {
                            if (!longVec.isNull(row)) {
                                long passengerCount = longVec.get(row);
                                System.out.printf("  Row %d passengers: %d%n", row, passengerCount);
                            }
                        }
                    }

                    // Example 6: Compute aggregates manually
                    System.out.println();
                    System.out.println("=== Method 6: Manual Aggregation ===");
                    if (fareAmountVector instanceof Float8Vector) {
                        Float8Vector fareVec = (Float8Vector) fareAmountVector;
                        double totalFare = 0;
                        int validRows = 0;
                        for (int row = 0; row < rowCount; row++) {
                            if (!fareVec.isNull(row)) {
                                totalFare += fareVec.get(row);
                                validRows++;
                            }
                        }
                        System.out.printf("  Total fare for %d trips: $%.2f%n", validRows, totalFare);
                        System.out.printf("  Average fare: $%.2f%n", validRows > 0 ? totalFare / validRows : 0);
                    }
                }
            }

            System.out.println();
            System.out.println("=== Done! ===");

        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
