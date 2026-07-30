/*
Copyright 2024-2026 The Spice.ai OSS Authors

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
 * The status of a single runtime component, as reported by {@code /v1/status}.
 *
 * <p>
 * The runtime serializes these as plain strings such as {@code "Ready"}. A value
 * this SDK does not recognize maps to {@link #UNKNOWN} rather than failing, so a
 * newer runtime can add a status without breaking an older client.
 */
public enum ComponentStatus {
    /** The component is initializing and not yet ready. */
    INITIALIZING("Initializing"),

    /** The component is ready to accept connections. */
    READY("Ready"),

    /** The component is disabled and not running. */
    DISABLED("Disabled"),

    /** An error occurred in the component. */
    ERROR("Error"),

    /** The component is refreshing its state. */
    REFRESHING("Refreshing"),

    /** The component is shutting down. */
    SHUTTING_DOWN("ShuttingDown"),

    /** The component is configured but not loaded yet. */
    NOT_LOADED("NotLoaded"),

    /** A status this version of the SDK does not recognize. */
    UNKNOWN("Unknown");

    private final String wireValue;

    ComponentStatus(String wireValue) {
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
    public static ComponentStatus fromWireValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ComponentStatus status : values()) {
            if (status.wireValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * Whether this status means the component is ready to accept connections.
     *
     * @return true if the component is ready
     */
    public boolean isReady() {
        return this == READY;
    }

    /**
     * Whether this status means the component is in an error state.
     *
     * @return true if the component errored
     */
    public boolean isError() {
        return this == ERROR;
    }

    @Override
    public String toString() {
        return this.wireValue;
    }
}
