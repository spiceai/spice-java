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

package spiceai;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builder class for creating instances of SpiceClient.
 */
public class SpiceClientBuilder {

    private String appId;
    private String apiKey;
    private URI flightAddress;

    /**
     * Constructs a new SpiceClientBuilder instance
     *
     * @throws URISyntaxException if the URI syntax is incorrect.
     */
    SpiceClientBuilder() throws URISyntaxException {
        this.flightAddress = Config.getLocalFlightAddressUri();
    }

    /**
     * Sets the client's flight address
     * 
     * @param flightAddress
     * @return The current instance of SpiceClientBuilder for method chaining.
     */
    public SpiceClientBuilder withFlightAddress(URI flightAddress) {
        this.flightAddress = flightAddress;
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
        String[] parts = apiKey.split("\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("apiKey is invalid");
        }

        this.appId = parts[0];
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Sets the client's flight address to default Spice Cloud address.
     *
     * @return The current instance of SpiceClientBuilder for method chaining.
     * @throws URISyntaxException
     */
    public SpiceClientBuilder withSpiceCloud() throws URISyntaxException {
        this.flightAddress = Config.getCloudFlightAddressUri();
        return this;
    }

    /**
     * Creates SpiceClient with provided parameters.
     *
     * @return The SpiceClient instance
     */
    public SpiceClient build() {
        return new SpiceClient(appId, apiKey, flightAddress);
    }
}