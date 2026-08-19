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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * An {@link ArrowReader} that streams the results of an async query
 * ({@link AsyncQuery}), fetching and decoding each result chunk in order via
 * {@code GetAsyncQueryResult}.
 *
 * <p>
 * Each chunk is a self-contained Arrow IPC stream, decoded with its own
 * {@link ArrowStreamReader}. Record batches are transferred from that reader
 * into this reader's root via unload/load, which moves buffer ownership
 * without copying the data — the same technique {@link FlightInfoReader} uses
 * for multi-endpoint Flight results.
 */
final class AsyncQueryResultReader extends ArrowReader {

    private final SpiceClient client;
    private final String queryId;
    private final int chunkCount;
    private final Schema schema;
    private int nextChunkIndex;
    private ArrowStreamReader currentChunkReader;
    private long bytesRead;

    /**
     * Creates a reader over {@code chunkCount} result chunks of {@code queryId}.
     * Opens the first chunk eagerly so failures surface here rather than
     * mid-consumption.
     *
     * <p>
     * A genuinely empty result (no chunks were produced) is detected by chunk
     * 0 failing while {@code chunkCount} is 1 (the caller always passes at
     * least 1 so a schema can be obtained) — in that case this reader exposes
     * an empty schema and no batches, matching {@link FlightInfoReader}'s
     * fallback for a FlightInfo with zero endpoints.
     */
    AsyncQueryResultReader(BufferAllocator allocator, SpiceClient client, String queryId, int chunkCount)
            throws IOException {
        super(allocator);
        this.client = client;
        this.queryId = queryId;
        this.chunkCount = chunkCount;
        try {
            if (openChunk(0)) {
                this.nextChunkIndex = 1;
                this.schema = this.currentChunkReader.getVectorSchemaRoot().getSchema();
            } else {
                this.schema = new Schema(Collections.emptyList());
            }
            ensureInitialized();
        } catch (IOException | RuntimeException e) {
            closeCurrentChunkQuietly(e);
            throw e;
        }
    }

    @Override
    public boolean loadNextBatch() throws IOException {
        while (currentChunkReader != null) {
            if (currentChunkReader.loadNextBatch()) {
                VectorSchemaRoot chunkRoot = currentChunkReader.getVectorSchemaRoot();
                VectorUnloader unloader = new VectorUnloader(chunkRoot);
                try (ArrowRecordBatch batch = unloader.getRecordBatch()) {
                    bytesRead += batch.computeBodyLength();
                    loadRecordBatch(batch);
                }
                return true;
            }
            advanceToNextChunk();
        }
        return false;
    }

    /**
     * Opens chunk {@code index}, returning false only when this is chunk 0 of
     * a genuinely empty result (the runtime never produced it because
     * {@code chunkCount} was declared as 0, and the caller bumped it to 1 to
     * request a schema anyway). Any other failure propagates as an
     * {@link IOException}.
     */
    private boolean openChunk(int index) throws IOException {
        byte[] chunk;
        try {
            chunk = client.asyncQueryResultChunk(queryId, index);
        } catch (ExecutionException e) {
            if (index == 0 && chunkCount <= 1) {
                return false;
            }
            throw new IOException("Failed to fetch result chunk " + index + " for async query " + queryId, e);
        }
        this.currentChunkReader = new ArrowStreamReader(new ByteArrayInputStream(chunk), this.allocator);
        try {
            // Force the schema message to be read now so a malformed chunk
            // surfaces here rather than on the first loadNextBatch().
            this.currentChunkReader.getVectorSchemaRoot();
        } catch (IOException | RuntimeException e) {
            closeCurrentChunkQuietly(e);
            throw e;
        }
        return true;
    }

    private void advanceToNextChunk() throws IOException {
        closeCurrentChunkQuietly(null);
        if (nextChunkIndex < chunkCount) {
            int index = nextChunkIndex++;
            if (openChunk(index)) {
                Schema chunkSchema = currentChunkReader.getVectorSchemaRoot().getSchema();
                if (!schema.equals(chunkSchema)) {
                    closeCurrentChunkQuietly(null);
                    throw new IOException(
                            "Result chunk " + index + " returned inconsistent schema. Expected: " + schema
                                    + ", got: " + chunkSchema);
                }
            }
        }
    }

    @Override
    public long bytesRead() {
        return bytesRead;
    }

    @Override
    protected void closeReadSource() throws IOException {
        closeCurrentChunkQuietly(null);
    }

    @Override
    protected Schema readSchema() throws IOException {
        return schema;
    }

    private void closeCurrentChunkQuietly(Exception pending) {
        if (currentChunkReader != null) {
            try {
                currentChunkReader.close();
            } catch (Exception closeEx) {
                if (pending != null) {
                    pending.addSuppressed(closeEx);
                }
            } finally {
                currentChunkReader = null;
            }
        }
    }
}
