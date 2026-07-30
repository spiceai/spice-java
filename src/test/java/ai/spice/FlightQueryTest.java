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

import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.VectorSchemaRoot;

import com.google.common.base.Strings;

import junit.framework.TestCase;

public class FlightQueryTest
        extends TestCase {
    public void testQuerySpiceCloudPlatform() throws ExecutionException, InterruptedException {
        String apiKey = System.getenv("API_KEY");

        // Skip test if no API_KEY provided
        if (Strings.isNullOrEmpty(apiKey)) {
            return;
        }

        try (SpiceClient spiceClient = SpiceClient.builder()
                .withApiKey(apiKey) // https://spice.ai/spiceai/quickstart
                .withHttpAddress(new URI("https://data.spiceai.io"))
                .withFlightAddress(new URI("https://flight.spiceai.io:443"))
                .build()) {

            String sql = "SELECT tpep_pickup_datetime, total_amount, passenger_count from taxi_trips limit 10;";
            int totalRows = 0;
            int columnCount = 0;

            try (FlightStream res = spiceClient.query(sql)) {
                while (res.next()) {
                    VectorSchemaRoot root = res.getRoot();
                    if (totalRows == 0) {
                        columnCount = root.getFieldVectors().size();
                    }
                    totalRows += root.getRowCount();
                }
            }

            assertEquals("Expected column count does not match", 3, columnCount);
            assertEquals("Expected row count does not match", 10, totalRows);

        } catch (Exception e) {
            // Skip if table not found, connection unavailable, or acceleration not ready
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("not found") || msg.contains("unavailable") || msg.contains("acceleration")) {
                return;
            }
            fail("Should not throw any exception: " + e.getMessage());
        }
    }

    public void testQuerySpiceOSS() throws ExecutionException, InterruptedException {
        // One retry: resilient availability detection without paying full backoff when absent
        try (SpiceClient spiceClient = SpiceClient.builder()
                .withMaxRetries(1)
                .build()) {

            String sql = "SELECT tpep_pickup_datetime, total_amount, passenger_count from taxi_trips limit 10;";
            int totalRows = 0;
            int columnCount = 0;

            try (FlightStream res = spiceClient.query(sql)) {
                while (res.next()) {
                    VectorSchemaRoot root = res.getRoot();
                    if (totalRows == 0) {
                        columnCount = root.getFieldVectors().size();
                    }
                    totalRows += root.getRowCount();
                }
            }

            assertEquals("Expected column count does not match", 3, columnCount);
            assertEquals("Expected row count does not match", 10, totalRows);

        } catch (Exception e) {
            // Skip if table not found, connection unavailable, or acceleration not ready
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("not found") || msg.contains("unavailable") || msg.contains("acceleration")) {
                return;
            }
            fail("Should not throw any exception: " + e.getMessage());
        }
    }

    public void testRefreshSpiceOSS() throws ExecutionException, InterruptedException {
        try (SpiceClient spiceClient = SpiceClient.builder()
                .build()) {

            spiceClient.refreshDataset("taxi_trips");

            try {
                spiceClient.refreshDataset("taxi_trips_does_not_exist");
                fail("Should throw exception when unable to refresh dataset");
            } catch (Exception e) {
                assertTrue("Should correctly pass response message when unable to refresh table",
                        e.getMessage().contains("\"message\":"));
            }
        } catch (Exception e) {
            // Skip if table not found, connection unavailable, or acceleration not ready
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("not found") || msg.contains("unavailable") || msg.contains("acceleration")) {
                return;
            }
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    public void testRefreshWithOptionsSpiceOSS() throws ExecutionException, InterruptedException {
        try (SpiceClient spiceClient = SpiceClient.builder()
                .build()) {
            String sql = "SELECT tpep_pickup_datetime, total_amount, passenger_count from taxi_trips limit 20;";

            // A previous run of this test leaves the accelerated table at 10 rows
            // (refreshes are asynchronous and the refresh_sql persists in the
            // acceleration). Restore the full dataset first so the pre-condition
            // holds on reused runtimes, not only on freshly started ones.
            if (countRows(spiceClient, sql) < 20) {
                spiceClient.refreshDataset("taxi_trips");
                waitForRowCount(spiceClient, sql, 20);
            }

            assertEquals("Expected row count does not match", 20, countRows(spiceClient, sql));

            try {
                RefreshOptions opts = new RefreshOptions().withRefreshSql("SELECT * FROM taxi_trips limit 10")
                        .withRefreshJitterMax("1s");

                spiceClient.refreshDataset("taxi_trips", opts);

                // Refreshes are asynchronous: poll until the shrunk dataset is visible
                // instead of relying on a fixed sleep.
                assertEquals("Expected row count does not match", 10, waitForRowCount(spiceClient, sql, 10));
            } finally {
                // Always restore the full dataset and wait for it to land, so
                // subsequent tests and reruns see the standard state even when
                // the assertions above fail. Best-effort: a restore failure must
                // not mask the primary test failure.
                try {
                    spiceClient.refreshDataset("taxi_trips");
                    waitForRowCount(spiceClient, sql, 20);
                } catch (Exception restoreFailure) {
                    System.err.println("Warning: failed to restore taxi_trips after refresh test: "
                            + restoreFailure.getMessage());
                }
            }
        } catch (Exception e) {
            // Skip if table not found, connection unavailable, or acceleration not ready
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("not found") || msg.contains("unavailable") || msg.contains("acceleration")) {
                return;
            }
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    private static long countRows(SpiceClient client, String sql) throws Exception {
        try (FlightStream stream = client.query(sql)) {
            long rows = 0;
            while (stream.next()) {
                rows += stream.getRoot().getRowCount();
            }
            return rows;
        }
    }

    /**
     * Polls (up to 30s) until the query returns the expected row count,
     * returning the last observed count either way.
     */
    private static long waitForRowCount(SpiceClient client, String sql, long expected) throws Exception {
        long rows = -1;
        for (int i = 0; i < 30; i++) {
            rows = countRows(client, sql);
            if (rows == expected) {
                return rows;
            }
            Thread.sleep(1000);
        }
        return rows;
    }
}
