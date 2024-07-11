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

import junit.framework.TestCase;

public class FlightQueryTest 
    extends TestCase
{
    private SpiceClient spiceClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        String apiKey = System.getenv("API_KEY");

        if (apiKey == null) {
            throw new IllegalArgumentException("No API_KEY provided");
        }

        spiceClient = SpiceClient.builder()
                .withApiKey(apiKey)
                .withSpiceCloud()
                .build();
    }

    public void testQuery() throws ExecutionException, InterruptedException {
        try {
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

            assertEquals("Expected column count does not match", 4, columnCount);
            assertEquals("Expected row count does not match", 2000, totalRows);

        } catch (Exception e) {
            fail("Should not throw any exception: " + e.getMessage());
        }
    }

}
