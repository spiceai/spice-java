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

import java.util.concurrent.ExecutionException;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.VectorSchemaRoot;

import com.google.common.base.Strings;

import junit.framework.TestCase;

public class FlightQueryTest
        extends TestCase {
    public void testQuerySpiceCloudPlatform() throws ExecutionException, InterruptedException {
        try {
            String apiKey = System.getenv("API_KEY");

            if (Strings.isNullOrEmpty(apiKey)) {
                throw new IllegalArgumentException("No API_KEY provided");
            }

            SpiceClient spiceClient = SpiceClient.builder()
                    .withApiKey(apiKey)
                    .withSpiceCloud()
                    .build();

            String sql = "SELECT number, \"timestamp\", base_fee_per_gas, base_fee_per_gas / 1e9 AS base_fee_per_gas_gwei FROM eth.blocks limit 2000";
            FlightStream res = spiceClient.query(sql);

            int totalRows = 0;
            int columnCount = 0;

            while (res.next()) {
                VectorSchemaRoot root = res.getRoot();
                if (totalRows == 0) {
                    columnCount = root.getFieldVectors().size();
                }
                totalRows += root.getRowCount();
            }

            spiceClient.close();

            assertEquals("Expected column count does not match", 4, columnCount);
            assertEquals("Expected row count does not match", 2000, totalRows);

        } catch (Exception e) {
            fail("Should not throw any exception: " + e.getMessage());
        }
    }

    public void testQuerySpiceOSS() throws ExecutionException, InterruptedException {
        try {
            SpiceClient spiceClient = SpiceClient.builder()
                    .build();

            String sql = "SELECT tpep_pickup_datetime, total_amount, passenger_count from taxi_trips limit 10;";
            FlightStream res = spiceClient.query(sql);

            int totalRows = 0;
            int columnCount = 0;

            while (res.next()) {
                VectorSchemaRoot root = res.getRoot();
                if (totalRows == 0) {
                    columnCount = root.getFieldVectors().size();
                }
                totalRows += root.getRowCount();
            }

            spiceClient.close();

            assertEquals("Expected column count does not match", 3, columnCount);
            assertEquals("Expected row count does not match", 10, totalRows);

        } catch (Exception e) {
            fail("Should not throw any exception: " + e.getMessage());
        }
    }

    public void testRefreshSpiceOSS() throws ExecutionException, InterruptedException {
        try {
            SpiceClient spiceClient = SpiceClient.builder()
                    .build();

            spiceClient.refresh_dataset("taxi_trips");

            try {
                spiceClient.refresh_dataset("taxi_trips_does_not_exist");
                fail("Should throw exception when unable to refresh dataset");
            } catch (Exception e) {
                assertTrue("Should correctly pass response message when unable to refresh table",
                        e.getMessage().contains("\"message\":"));
            }

            spiceClient.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    public void testRefreshWithOptionsSpiceOSS() throws ExecutionException, InterruptedException {
        try {
            String sql = "SELECT tpep_pickup_datetime, total_amount, passenger_count from taxi_trips limit 20;";
            SpiceClient spiceClient = SpiceClient.builder()
                    .build();

            FlightStream preRefreshRes = spiceClient.query(sql);

            int preRefreshRows = 0;

            while (preRefreshRes.next()) {
                VectorSchemaRoot root = preRefreshRes.getRoot();
                preRefreshRows += root.getRowCount();
            }

            assertEquals("Expected row count does not match", 20, preRefreshRows);

            RefreshOptions opts = new RefreshOptions().withRefreshSql("SELECT * FROM taxi_trips limit 10");

            spiceClient.refresh_dataset("taxi_trips", opts);

            // wait a couple seconds to let refresh run
            Thread.sleep(10000);

            FlightStream postRefreshRes = spiceClient.query(sql);

            int postRefreshRows = 0;

            while (postRefreshRes.next()) {
                VectorSchemaRoot root = postRefreshRes.getRoot();
                postRefreshRows += root.getRowCount();
            }

            spiceClient.close();

            assertEquals("Expected row count does not match", 10, postRefreshRows);
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
}
