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

import java.util.Collections;

import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.sql.FlightSqlClient;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;

import junit.framework.TestCase;

/**
 * Unit tests for the SDK's FlightInfoReader (edge cases not reachable through
 * the public query API).
 */
public class FlightInfoReaderTest extends TestCase {

    /**
     * A FlightInfo with no endpoints yields an empty reader with the schema
     * taken from the FlightInfo itself.
     */
    public void testEmptyEndpointsYieldsEmptyReader() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer();
                BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
            FlightClient flightClient = FlightClient.builder(allocator,
                    Location.forGrpcInsecure("localhost", server.getPort())).build();
            try (FlightSqlClient client = new FlightSqlClient(flightClient)) {
                FlightInfo info = new FlightInfo(TestFlightSqlServer.RESULT_SCHEMA,
                        FlightDescriptor.command(new byte[0]), Collections.emptyList(), -1, -1);
                try (FlightInfoReader reader = new FlightInfoReader(
                        allocator, client, new CallOption[0], info)) {
                    assertEquals(TestFlightSqlServer.RESULT_SCHEMA,
                            reader.getVectorSchemaRoot().getSchema());
                    assertFalse("no batches expected", reader.loadNextBatch());
                    assertEquals(0, reader.bytesRead());
                }
            }
        }
    }

    /**
     * The reader tracks bytesRead across batches and endpoints.
     */
    public void testBytesReadAccumulatesAcrossEndpoints() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer()) {
            server.endpointCount = 2;
            server.batchesPerEndpoint = 2;
            try (SpiceClient client = SpiceClient.builder().withFlightAddress(server.flightUri()).build();
                    org.apache.arrow.vector.ipc.ArrowReader reader = client.queryWithParams("SELECT 1", 1)) {
                long previous = 0;
                int batches = 0;
                while (reader.loadNextBatch()) {
                    batches++;
                    assertTrue("bytesRead must be monotonically increasing", reader.bytesRead() > previous);
                    previous = reader.bytesRead();
                }
                assertEquals(4, batches);
            }
        }
    }
}
