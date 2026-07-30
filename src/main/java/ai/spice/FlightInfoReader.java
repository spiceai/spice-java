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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.sql.FlightSqlClient;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * An {@link ArrowReader} that streams the results described by a
 * {@link FlightInfo}, consuming every endpoint in order on the connection
 * that produced it.
 *
 * <p>Record batches are transferred from the underlying {@link FlightStream}
 * into this reader's root via unload/load, which moves buffer ownership
 * without copying the data.</p>
 */
final class FlightInfoReader extends ArrowReader {

    private final FlightSqlClient client;
    private final CallOption[] streamOptions;
    private final List<FlightEndpoint> endpoints;
    private final Schema schema;
    private int nextEndpointIndex;
    private FlightStream currentStream;
    private long bytesRead;

    /**
     * Creates a reader over all endpoints of the given FlightInfo. Opens the
     * first stream eagerly so transient failures surface here (and can be
     * retried by the caller) rather than mid-consumption.
     */
    FlightInfoReader(BufferAllocator allocator, FlightSqlClient client, CallOption[] streamOptions,
            FlightInfo flightInfo) throws IOException {
        super(allocator);
        this.client = client;
        this.streamOptions = streamOptions;
        this.endpoints = flightInfo.getEndpoints();
        try {
            if (endpoints.isEmpty()) {
                this.schema = flightInfo.getSchemaOptional()
                        .orElseGet(() -> new Schema(Collections.emptyList()));
            } else {
                this.currentStream = client.getStream(endpoints.get(0).getTicket(), streamOptions);
                this.nextEndpointIndex = 1;
                this.schema = currentStream.getSchema();
            }
            ensureInitialized();
        } catch (Exception e) {
            closeStreamQuietly(e);
            throw e;
        }
    }

    @Override
    public boolean loadNextBatch() throws IOException {
        while (currentStream != null) {
            if (currentStream.next()) {
                VectorSchemaRoot streamRoot = currentStream.getRoot();
                VectorUnloader unloader = new VectorUnloader(streamRoot);
                try (ArrowRecordBatch batch = unloader.getRecordBatch()) {
                    bytesRead += batch.computeBodyLength();
                    loadRecordBatch(batch);
                }
                return true;
            }
            advanceToNextEndpoint();
        }
        return false;
    }

    private void advanceToNextEndpoint() throws IOException {
        try {
            currentStream.close();
        } catch (Exception e) {
            throw asIOException(e);
        }
        currentStream = null;
        if (nextEndpointIndex < endpoints.size()) {
            FlightEndpoint endpoint = endpoints.get(nextEndpointIndex++);
            currentStream = client.getStream(endpoint.getTicket(), streamOptions);
            if (!schema.equals(currentStream.getSchema())) {
                Schema streamSchema = currentStream.getSchema();
                closeStreamQuietly(null);
                throw new IOException(
                        "Endpoint returned inconsistent schema. Expected: " + schema + ", got: " + streamSchema);
            }
        }
    }

    @Override
    public long bytesRead() {
        return bytesRead;
    }

    @Override
    protected void closeReadSource() throws IOException {
        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (Exception e) {
                throw asIOException(e);
            } finally {
                currentStream = null;
            }
        }
    }

    @Override
    protected Schema readSchema() throws IOException {
        return schema;
    }

    private void closeStreamQuietly(Exception pending) {
        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (Exception closeEx) {
                if (pending != null) {
                    pending.addSuppressed(closeEx);
                }
            } finally {
                currentStream = null;
            }
        }
    }

    private static IOException asIOException(Exception e) {
        return (e instanceof IOException) ? (IOException) e : new IOException(e);
    }
}
