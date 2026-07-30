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
 * The status of one runtime connection, as reported by {@code /v1/status}.
 *
 * <p>
 * The runtime reports one of these per connection — {@code http}, {@code flight},
 * {@code metrics}, and {@code opentelemetry}. This is strictly more informative
 * than the boolean {@link SpiceClient#isReady()}: it names which component is not
 * ready, and where it is bound.
 */
public class ConnectionDetails {
    private final String name;
    private final String endpoint;
    private final String status;

    /**
     * Creates connection details.
     *
     * @param name     the connection name, for example {@code flight}
     * @param endpoint where the connection is bound, or {@code N/A} when disabled
     * @param status   the raw status the runtime reported
     */
    public ConnectionDetails(String name, String endpoint, String status) {
        this.name = name;
        this.endpoint = endpoint;
        this.status = status;
    }

    /**
     * The connection name — {@code http}, {@code flight}, {@code metrics}, or
     * {@code opentelemetry}.
     *
     * @return the connection name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Where the connection is bound. {@code N/A} when the component is disabled.
     *
     * @return the endpoint
     */
    public String getEndpoint() {
        return this.endpoint;
    }

    /**
     * The status as a {@link ComponentStatus}. A status this SDK does not
     * recognize maps to {@link ComponentStatus#UNKNOWN}; use
     * {@link #getRawStatus()} to read it verbatim.
     *
     * @return the parsed status
     */
    public ComponentStatus getStatus() {
        return ComponentStatus.fromWireValue(this.status);
    }

    /**
     * The status exactly as the runtime reported it.
     *
     * @return the raw status string
     */
    public String getRawStatus() {
        return this.status;
    }

    /**
     * Whether this connection is ready to accept traffic.
     *
     * @return true if the component is ready
     */
    public boolean isReady() {
        return getStatus().isReady();
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %s", this.name, this.endpoint, this.status);
    }
}
