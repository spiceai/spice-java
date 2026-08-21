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

import java.time.Instant;

import com.google.gson.annotations.SerializedName;

/**
 * A synchronous query currently running on the runtime, as reported by
 * {@code GET /v1/sql/active}.
 *
 * <p>
 * Synchronous queries are the ones started by {@link SpiceClient#query(String)},
 * {@link SpiceClient#queryWithParams(String, Object...)}, or issued directly over
 * Flight SQL, HTTP, or NSQL/Search. The runtime does not return a query's ID to the
 * client that submitted it, so {@link SpiceClient#listActiveQueries()} is the only
 * way to discover the ID that {@link SpiceClient#cancelActiveQuery(String)} needs.
 */
public class ActiveQuery {
    @SerializedName("query_id")
    private String queryId;

    @SerializedName("protocol")
    private String protocol;

    @SerializedName("sql_preview")
    private String sqlPreview;

    @SerializedName("started_at_ms")
    private long startedAtMs;

    /**
     * The runtime-assigned query ID. Pass this to
     * {@link SpiceClient#cancelActiveQuery(String)} to cancel the query.
     *
     * @return the query ID
     */
    public String getQueryId() {
        return this.queryId;
    }

    /**
     * The protocol the query arrived on: {@code http}, {@code flight},
     * {@code flightsql}, or {@code internal}.
     *
     * @return the protocol
     */
    public String getProtocol() {
        return this.protocol;
    }

    /**
     * The query's SQL, truncated by the runtime for display.
     *
     * @return the truncated SQL text
     */
    public String getSqlPreview() {
        return this.sqlPreview;
    }

    /**
     * When the query started, in milliseconds since the Unix epoch.
     *
     * @return the start time in epoch milliseconds
     */
    public long getStartedAtMs() {
        return this.startedAtMs;
    }

    /**
     * When the query started.
     *
     * @return the start time
     */
    public Instant getStartedAt() {
        return Instant.ofEpochMilli(this.startedAtMs);
    }

    @Override
    public String toString() {
        return String.format("ActiveQuery{queryId=%s, protocol=%s, startedAt=%s, sqlPreview=%s}",
                this.queryId, this.protocol, getStartedAt(), this.sqlPreview);
    }
}
