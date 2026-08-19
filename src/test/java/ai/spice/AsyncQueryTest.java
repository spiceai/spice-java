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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;

import junit.framework.TestCase;

/**
 * Tests for {@link SpiceClient#queryAsync(String)},
 * {@link SpiceClient#queryAsyncWithParams(String, Object...)}, and
 * {@link AsyncQuery} against the in-process {@link TestFlightSqlServer}.
 */
public class AsyncQueryTest extends TestCase {

    private TestFlightSqlServer server;
    private SpiceClient client;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new TestFlightSqlServer();
        client = SpiceClient.builder().withFlightAddress(server.flightUri()).build();
    }

    @Override
    protected void tearDown() throws Exception {
        client.close();
        server.close();
        super.tearDown();
    }

    /** Serializes a single Arrow IPC stream chunk of RESULT_SCHEMA rows starting at idStart. */
    private static byte[] serializeChunk(long idStart, String... names) throws Exception {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
                VectorSchemaRoot root = VectorSchemaRoot.create(TestFlightSqlServer.RESULT_SCHEMA, allocator)) {
            root.allocateNew();
            BigIntVector idVector = (BigIntVector) root.getVector("id");
            VarCharVector nameVector = (VarCharVector) root.getVector("name");
            for (int i = 0; i < names.length; i++) {
                idVector.setSafe(i, idStart + i);
                nameVector.setSafe(i, names[i].getBytes(StandardCharsets.UTF_8));
            }
            root.setRowCount(names.length);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ArrowStreamWriter writer = new ArrowStreamWriter(root, null, out)) {
                writer.start();
                writer.writeBatch();
                writer.end();
                return out.toByteArray();
            }
        }
    }

    public void testQueryAsyncSubmitsAndReturnsHandle() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test");
        assertNotNull(query.getQueryId());
        assertFalse("a fresh query id should not be blank", query.getQueryId().isEmpty());
        assertEquals(QueryStatus.SUCCEEDED, query.status());
        assertEquals(1, server.submitAsyncQueryCalls.get());
    }

    public void testStatusReflectsChangeBetweenPolls() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test");
        server.setAsyncQueryStatusSequence(query.getQueryId(),
                Arrays.asList(QueryStatus.PENDING, QueryStatus.RUNNING, QueryStatus.SUCCEEDED));

        QueryStatus first = query.status();
        QueryStatus second = query.status();
        QueryStatus third = query.status();

        assertEquals(QueryStatus.PENDING, first);
        assertEquals(QueryStatus.RUNNING, second);
        assertEquals(QueryStatus.SUCCEEDED, third);
    }

    public void testWaitForCompletionBlocksThroughPendingRunningSucceeded() throws Exception {
        AsyncQuery query = client.queryAsyncWithParams("SELECT * FROM test WHERE id = $1", 1);
        server.setAsyncQueryStatusSequence(query.getQueryId(),
                Arrays.asList(QueryStatus.PENDING, QueryStatus.RUNNING, QueryStatus.RUNNING, QueryStatus.SUCCEEDED));

        QueryStatus finalStatus = query.waitForCompletion();

        assertEquals(QueryStatus.SUCCEEDED, finalStatus);
    }

    public void testWaitForCompletionTimesOut() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test");
        // RUNNING repeated forever (the sequence holds its last entry) never reaches a terminal status.
        server.setAsyncQueryStatusSequence(query.getQueryId(),
                Arrays.asList(QueryStatus.PENDING, QueryStatus.RUNNING));

        try {
            query.waitForCompletion(Duration.ofMillis(200));
            fail("expected a timeout ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Timed out"));
        }
    }

    public void testResultsMultiChunkReconstructsData() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test");
        byte[] chunk0 = serializeChunk(0, "a", "b");
        byte[] chunk1 = serializeChunk(2, "c");
        server.setAsyncQueryChunks(query.getQueryId(), Arrays.asList(chunk0, chunk1), 3);

        try (ArrowReader reader = query.results()) {
            long rows = 0;
            java.util.List<String> names = new java.util.ArrayList<>();
            while (reader.loadNextBatch()) {
                VectorSchemaRoot root = reader.getVectorSchemaRoot();
                rows += root.getRowCount();
                VarCharVector nameVector = (VarCharVector) root.getVector("name");
                for (int i = 0; i < root.getRowCount(); i++) {
                    names.add(new String(nameVector.get(i), StandardCharsets.UTF_8));
                }
            }
            assertEquals(3, rows);
            assertEquals(Arrays.asList("a", "b", "c"), names);
        }
        assertTrue("should have fetched at least 2 chunks", server.getAsyncQueryResultCalls.get() >= 2);
    }

    public void testResultsEmptyResultReturnsEmptySchema() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test WHERE 1 = 0");
        server.setAsyncQueryChunks(query.getQueryId(), Collections.emptyList(), 0);

        try (ArrowReader reader = query.results()) {
            assertEquals(0, reader.getVectorSchemaRoot().getSchema().getFields().size());
            assertFalse(reader.loadNextBatch());
        }
    }

    public void testResultsFailedThrowsWithErrorMessage() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test");
        server.setAsyncQueryStatusSequence(query.getQueryId(),
                Arrays.asList(QueryStatus.RUNNING, QueryStatus.FAILED));
        server.setAsyncQueryError(query.getQueryId(), "TABLE_NOT_FOUND", "table 'test' does not exist");

        try {
            query.results();
            fail("expected an ExecutionException for a FAILED query");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("TABLE_NOT_FOUND"));
            assertTrue(e.getMessage().contains("table 'test' does not exist"));
        }
    }

    public void testCancelUpdatesStatus() throws Exception {
        AsyncQuery query = client.queryAsync("SELECT * FROM test");
        server.setAsyncQueryStatusSequence(query.getQueryId(),
                Arrays.asList(QueryStatus.PENDING, QueryStatus.RUNNING));

        query.cancel();

        assertEquals(QueryStatus.CANCELLED, query.status());
    }

    public void testQueryAsyncRejectsEmptySql() throws Exception {
        try {
            client.queryAsync("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            client.queryAsync(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals("no RPC should have been made", 0, server.submitAsyncQueryCalls.get());
    }

    public void testQueryAsyncWithParamsRejectsEmptySql() throws Exception {
        try {
            client.queryAsyncWithParams("", 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals("no RPC should have been made", 0, server.submitAsyncQueryCalls.get());
    }
}
