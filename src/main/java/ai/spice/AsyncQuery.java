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
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.apache.arrow.vector.ipc.ArrowReader;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A handle to a query submitted for asynchronous execution via
 * {@link SpiceClient#queryAsync(String)} or
 * {@link SpiceClient#queryAsyncWithParams(String, Object...)}.
 *
 * <p>
 * Async queries require the Spice runtime to be running in
 * distributed/scheduler mode.
 *
 * <p>
 * An {@code AsyncQuery} is not safe for concurrent use by multiple threads.
 */
public final class AsyncQuery {

    private static final long ASYNC_POLL_INTERVAL_MS = 500;

    private final SpiceClient client;
    private final String queryId;
    private volatile QueryStatus status;

    AsyncQuery(SpiceClient client, String queryId, QueryStatus status) {
        this.client = client;
        this.queryId = queryId;
        this.status = status;
    }

    /**
     * The server-assigned query identifier.
     *
     * @return the query ID
     */
    public String getQueryId() {
        return this.queryId;
    }

    /**
     * Performs a single poll and returns the current status of the query.
     *
     * @return the current status
     * @throws ExecutionException if the status could not be fetched
     */
    public QueryStatus status() throws ExecutionException {
        return pollStatus(null);
    }

    /**
     * Performs a single status poll, bounding the RPC to {@code perCallTimeout}
     * when given so a stalled poll cannot outlive the caller's remaining wait
     * budget.
     */
    private QueryStatus pollStatus(Duration perCallTimeout) throws ExecutionException {
        JsonObject response = this.client.asyncQueryStatus(this.queryId, perCallTimeout);
        this.status = QueryStatus.fromWireValue(getString(response, "status"));
        return this.status;
    }

    /**
     * Polls the runtime until the query reaches a terminal status
     * ({@code SUCCEEDED}, {@code FAILED}, {@code CANCELLED}, or
     * {@code CLOSED}). Blocks indefinitely; use
     * {@link #waitForCompletion(Duration)} to bound the wait.
     *
     * @return the terminal status
     * @throws ExecutionException if polling fails or the wait is interrupted
     */
    public QueryStatus waitForCompletion() throws ExecutionException {
        return waitForCompletion(null);
    }

    /**
     * Polls the runtime until the query reaches a terminal status, or until
     * {@code timeout} elapses.
     *
     * @param timeout the maximum time to wait, or null to wait indefinitely
     * @return the terminal status
     * @throws ExecutionException if polling fails, the wait is interrupted, or
     *                            {@code timeout} elapses before the query
     *                            reaches a terminal status
     */
    public QueryStatus waitForCompletion(Duration timeout) throws ExecutionException {
        long deadlineNanos = (timeout == null) ? -1 : System.nanoTime() + timeout.toNanos();
        while (true) {
            Duration remaining = null;
            if (deadlineNanos >= 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new ExecutionException(
                            "Timed out waiting for async query " + this.queryId
                                    + " to complete (last status: " + this.status + ")",
                            null);
                }
                // Bounds the status RPC itself to what's left of the caller's
                // budget, so a stalled poll cannot block past the deadline.
                remaining = Duration.ofNanos(remainingNanos);
            }
            QueryStatus current = pollStatus(remaining);
            if (current.isTerminal()) {
                return current;
            }
            long sleepMillis = ASYNC_POLL_INTERVAL_MS;
            if (deadlineNanos >= 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new ExecutionException(
                            "Timed out waiting for async query " + this.queryId
                                    + " to complete (last status: " + current + ")",
                            null);
                }
                // Cap the sleep so it cannot itself overshoot a short deadline.
                sleepMillis = Math.min(sleepMillis, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos));
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExecutionException("Interrupted while waiting for async query " + this.queryId, e);
            }
        }
    }

    /**
     * Waits for the query to complete and returns its results as an Arrow
     * {@link ArrowReader}. The caller is responsible for closing the reader.
     *
     * <p>
     * If the query does not succeed, this throws an {@link ExecutionException}
     * describing the terminal status (including the runtime's error message
     * when available) instead of returning a reader.
     *
     * @return an ArrowReader over the query's results
     * @throws ExecutionException if the query fails, is cancelled, or its
     *                            results cannot be read
     */
    public ArrowReader results() throws ExecutionException {
        QueryStatus finalStatus = waitForCompletion();
        JsonObject statusResponse = this.client.asyncQueryStatus(this.queryId);

        if (finalStatus != QueryStatus.SUCCEEDED) {
            throw new ExecutionException(describeFailure(finalStatus, statusResponse), null);
        }

        int chunkCount = 0;
        JsonObject result = getObject(statusResponse, "result");
        if (result != null) {
            chunkCount = getInt(result, "total_chunk_count", 0);
        }

        try {
            return new AsyncQueryResultReader(this.client.allocator(), this.client, this.queryId, chunkCount);
        } catch (IOException e) {
            throw new ExecutionException("Failed to read results for async query " + this.queryId, e);
        }
    }

    /**
     * Requests cancellation of the query. This is best-effort: a query that has
     * already reached a terminal status is not cancelled, which is not
     * reported as an error. Call {@link #status()} to observe the outcome.
     *
     * @throws ExecutionException if the cancellation request could not be sent
     */
    public void cancel() throws ExecutionException {
        JsonObject response = this.client.asyncQueryCancel(this.queryId);
        this.status = QueryStatus.fromWireValue(getString(response, "status"));
    }

    private String describeFailure(QueryStatus finalStatus, JsonObject statusResponse) {
        JsonObject error = getObject(statusResponse, "error");
        if (error != null) {
            String message = getString(error, "message");
            String errorCode = getString(error, "error_code");
            String detail = Strings.isNullOrEmpty(errorCode) ? message : (errorCode + ": " + message);
            return "Async query " + this.queryId + " " + finalStatus + ": " + detail;
        }
        return "Async query " + this.queryId + " did not succeed (status: " + finalStatus + ")";
    }

    private static String getString(JsonObject object, String member) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(member);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static JsonObject getObject(JsonObject object, String member) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static int getInt(JsonObject object, String member, int defaultValue) {
        if (object == null) {
            return defaultValue;
        }
        JsonElement element = object.get(member);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        return element.getAsInt();
    }
}
