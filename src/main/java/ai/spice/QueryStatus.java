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

/**
 * The lifecycle status of an async query submitted via
 * {@link SpiceClient#query(String)} or
 * {@link SpiceClient#queryWithParams(String, Object...)}.
 *
 * <p>
 * The runtime serializes these as plain strings such as {@code "SUCCEEDED"}. A
 * value this SDK does not recognize maps to {@link #UNKNOWN} rather than
 * failing, so a newer runtime can add a status without breaking an older
 * client.
 */
public enum QueryStatus {
    /** The query has been submitted but has not started running. */
    PENDING("PENDING"),

    /** The query is currently executing. */
    RUNNING("RUNNING"),

    /** The query completed successfully; results can be fetched. */
    SUCCEEDED("SUCCEEDED"),

    /** The query failed. */
    FAILED("FAILED"),

    /** The query was cancelled before it reached a terminal status. */
    CANCELLED("CANCELLED"),

    /** The query's resources have been released by the runtime. */
    CLOSED("CLOSED"),

    /** A status this version of the SDK does not recognize. */
    UNKNOWN("UNKNOWN");

    private final String wireValue;

    QueryStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * The value the runtime uses on the wire.
     *
     * @return the wire representation of this status
     */
    public String getWireValue() {
        return this.wireValue;
    }

    /**
     * Maps a wire value onto a status.
     *
     * @param value the value the runtime reported, may be null
     * @return the matching status, or {@link #UNKNOWN} if it is not recognized
     */
    public static QueryStatus fromWireValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (QueryStatus status : values()) {
            if (status.wireValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * Whether this status means the query will not transition further.
     *
     * <p>
     * An unrecognized status is treated as terminal too, defensively, so a
     * polling loop cannot spin forever on a status this version of the SDK
     * does not know about.
     *
     * @return true if the status is terminal
     */
    public boolean isTerminal() {
        switch (this) {
            case SUCCEEDED:
            case FAILED:
            case CANCELLED:
            case CLOSED:
            case UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return this.wireValue;
    }
}
