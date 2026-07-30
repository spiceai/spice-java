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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import com.google.common.base.Strings;

/**
 * Builder class for creating instances of SpiceClient.
 */
public class SpiceClientBuilder {

    /** Maximum number of gRPC channels a single client may open. */
    private static final int MAX_CHANNEL_COUNT = 16;
    /** Maximum number of idle prepared statements the cache may hold. */
    private static final int MAX_STATEMENT_CACHE_SIZE = 1024;

    private String appId;
    private String apiKey;
    private String userAgent;
    private URI flightAddress;
    private URI httpAddress;
    private int maxRetries = 3;
    private long memoryLimitMB = Long.MAX_VALUE; // Default is all available memory.
    private String tlsClientCertFile;
    private String tlsClientKeyFile;
    private String tlsRootCertFile;
    private int channelCount = SpiceClient.DEFAULT_CHANNEL_COUNT;
    private Duration queryTimeout;
    private int statementCacheSize = SpiceClient.DEFAULT_STATEMENT_CACHE_SIZE;

    /**
     * Constructs a new SpiceClientBuilder instance
     *
     * @throws URISyntaxException if the URI syntax is incorrect.
     */
    SpiceClientBuilder() throws URISyntaxException {
        this.flightAddress = Config.getLocalFlightAddressUri();
        this.httpAddress = Config.getLocalHttpAddressUri();
    }

    /**
     * Sets the client's flight address
     * 
     * @param flightAddress The URI of the flight address
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withFlightAddress(URI flightAddress) {
        if (flightAddress == null) {
            throw new IllegalArgumentException("flightAddress can't be null");
        }
        this.flightAddress = flightAddress;
        return this;
    }

    /**
     * Sets the client's HTTP address
     * 
     * @param httpAddress The URI of the HTTP address
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withHttpAddress(URI httpAddress) {
        if (httpAddress == null) {
            throw new IllegalArgumentException("httpAddress can't be null");
        }
        this.httpAddress = httpAddress;
        return this;
    }

    /**
     * Sets the client's Api Key.
     *
     * @param apiKey The Spice Cloud api key
     * @return The current instance of SpiceClientBuilder for method chaining.
     * @throws IllegalArgumentException Thrown when the apiKey is in wrong format.
     */
    public SpiceClientBuilder withApiKey(String apiKey) {
        if (Strings.isNullOrEmpty(apiKey)) {
            throw new IllegalArgumentException("apiKey can't be null or empty");
        }

        String[] parts = apiKey.split("\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("apiKey is invalid");
        }

        this.appId = parts[0];
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Sets the client's custom User-Agent string
     * 
     * @param userAgent The User-Agent string
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withUserAgent(String userAgent) {
        if (Strings.isNullOrEmpty(userAgent)) {
            throw new IllegalArgumentException("userAgent can't be null or empty");
        }
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Sets the client's flight address to default Spice Cloud address.
     *
     * @return The current instance of SpiceClientBuilder for method chaining.
     * @throws URISyntaxException Thrown when the URI syntax is incorrect.
     */
    public SpiceClientBuilder withSpiceCloud() throws URISyntaxException {
        this.flightAddress = Config.getCloudFlightAddressUri();
        this.httpAddress = Config.getCloudHttpAddressUri();
        return this;
    }

    /**
     * Sets the maximum number of connection retries for the client.
     * 
     * @param maxRetries The maximum number of connection retries
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be greater than or equal to 0");
        }
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Sets the memory limit for Apache Arrow allocator in megabytes.
     * This controls the maximum amount of off-heap memory that can be allocated
     * for Arrow Flight operations. If not set, the allocator will use all available
     * memory.
     *
     * @param memoryLimitMB Maximum memory limit in megabytes. Default is all
     *                      available memory.
     *                      Must be positive.
     * @return The current instance of SpiceClientBuilder for method chaining.
     * @throws IllegalArgumentException if memoryLimitMB is not positive
     *
     * @see org.apache.arrow.memory.RootAllocator
     */
    public SpiceClientBuilder withArrowMemoryLimitMB(long memoryLimitMB) {
        if (memoryLimitMB <= 0) {
            throw new IllegalArgumentException("Memory limit must be positive, got: " + memoryLimitMB + " MB");
        }
        if (memoryLimitMB > Long.MAX_VALUE / 1024L / 1024L) {
            throw new IllegalArgumentException(
                    "Memory limit is too large: " + memoryLimitMB + " MB");
        }

        this.memoryLimitMB = memoryLimitMB;
        return this;
    }

    /**
     * Sets the path to a PEM-encoded client certificate file for mTLS.
     * Must be used together with {@link #withTlsClientKeyFile(String)}.
     *
     * @param certFile Path to the client certificate PEM file
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withTlsClientCertFile(String certFile) {
        this.tlsClientCertFile = certFile;
        return this;
    }

    /**
     * Sets the path to a PEM-encoded client private key file for mTLS.
     * Must be used together with {@link #withTlsClientCertFile(String)}.
     *
     * @param keyFile Path to the client private key PEM file
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withTlsClientKeyFile(String keyFile) {
        this.tlsClientKeyFile = keyFile;
        return this;
    }

    /**
     * Sets the path to a PEM-encoded CA certificate file for server verification.
     * When set, this CA is used instead of the system trust store.
     *
     * @param caFile Path to the CA certificate PEM file
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withTlsRootCertFile(String caFile) {
        this.tlsRootCertFile = caFile;
        return this;
    }

    /**
     * Sets the number of gRPC channels (HTTP/2 connections) the client opens to
     * the Flight endpoint. Queries are distributed round-robin across channels.
     *
     * <p>A single HTTP/2 connection multiplexes all concurrent queries and is
     * limited by the server's MAX_CONCURRENT_STREAMS and the throughput of one
     * TCP connection. Increase this for highly concurrent workloads with large
     * result streams. The default of 1 is appropriate for most applications.</p>
     *
     * @param channelCount Number of connections, between 1 and 16.
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withChannelCount(int channelCount) {
        if (channelCount < 1 || channelCount > MAX_CHANNEL_COUNT) {
            throw new IllegalArgumentException(
                    "channelCount must be between 1 and " + MAX_CHANNEL_COUNT + ", got: " + channelCount);
        }
        this.channelCount = channelCount;
        return this;
    }

    /**
     * Sets a deadline for query control-plane RPCs: query planning
     * (GetFlightInfo), statement preparation, and parameter binding. Without a
     * timeout, a hung server can block the calling thread indefinitely.
     *
     * <p>The timeout intentionally does not apply to result streaming (DoGet) —
     * large results may legitimately stream for longer than any planning
     * deadline. Dead connections during streaming are detected by HTTP/2
     * keep-alive instead.</p>
     *
     * @param queryTimeout The timeout, must be positive.
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withQueryTimeout(Duration queryTimeout) {
        if (queryTimeout == null || queryTimeout.isZero() || queryTimeout.isNegative()) {
            throw new IllegalArgumentException("queryTimeout must be positive, got: " + queryTimeout);
        }
        this.queryTimeout = queryTimeout;
        return this;
    }

    /**
     * Sets the maximum number of idle prepared statements cached for reuse by
     * {@link SpiceClient#queryWithParams(String, Object...)}.
     *
     * <p>Reusing a prepared statement removes the CreatePreparedStatement and
     * ClosePreparedStatement round trips from every repeated parameterized
     * query. Set to 0 to disable caching (each query then prepares and closes
     * its own statement, matching the pre-0.7 behavior).</p>
     *
     * @param statementCacheSize Maximum idle statements, between 0 and 1024. Default is 64.
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withPreparedStatementCacheSize(int statementCacheSize) {
        if (statementCacheSize < 0 || statementCacheSize > MAX_STATEMENT_CACHE_SIZE) {
            throw new IllegalArgumentException(
                    "statementCacheSize must be between 0 and " + MAX_STATEMENT_CACHE_SIZE + ", got: "
                            + statementCacheSize);
        }
        this.statementCacheSize = statementCacheSize;
        return this;
    }

    /**
     * Creates SpiceClient with provided parameters.
     *
     * @return The SpiceClient instance
     */
    public SpiceClient build() {
        // Validate that client cert and key are either both set or both unset
        boolean hasCert = tlsClientCertFile != null && !tlsClientCertFile.isBlank();
        boolean hasKey = tlsClientKeyFile != null && !tlsClientKeyFile.isBlank();
        if (hasCert != hasKey) {
            throw new IllegalArgumentException(
                    "Both tlsClientCertFile and tlsClientKeyFile must be provided together for mTLS. "
                    + (hasCert ? "tlsClientKeyFile is missing." : "tlsClientCertFile is missing."));
        }
        return new SpiceClient(appId, apiKey, flightAddress, httpAddress, maxRetries, userAgent, memoryLimitMB,
                tlsClientCertFile, tlsClientKeyFile, tlsRootCertFile, channelCount, queryTimeout, statementCacheSize);
    }
}
