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

import com.google.common.base.Strings;

/**
 * Builder class for creating instances of SpiceClient.
 */
public class SpiceClientBuilder {

    private String appId;
    private String apiKey;
    private String userAgent;
    private URI flightAddress;
    private URI httpAddress;
    private int maxRetries = 3;

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
     * Creates SpiceClient with provided parameters.
     *
     * @return The SpiceClient instance
     */
    public SpiceClient build() {
        return new SpiceClient(appId, apiKey, flightAddress, httpAddress, maxRetries, userAgent);
    }
}