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

public class Config {

    public static final String CLOUD_FLIGHT_ADDRESS;
    public static final String LOCAL_FLIGHT_ADDRESS;

    static {
        CLOUD_FLIGHT_ADDRESS = System.getenv("SPICE_FLIGHT_URL") != null ? System.getenv("SPICE_FLIGHT_URL")
                : "https://flight.spiceai.io:443";

        LOCAL_FLIGHT_ADDRESS = System.getenv("SPICE_FLIGHT_URL") != null ? System.getenv("SPICE_FLIGHT_URL")
                : "http://localhost:50051";
    }

    /**
     * Returns the local flight address
     *
     * @return URI of the local flight address.
     * @throws URISyntaxException if the string could not be parsed as a URI.
     */
    public static URI getLocalFlightAddressUri() throws URISyntaxException {
        return new URI(LOCAL_FLIGHT_ADDRESS);
    }

    /**
     * Returns the cloud flight address
     *
     * @return URI of the cloud flight address.
     * @throws URISyntaxException if the string could not be parsed as a URI.
     */
    public static URI getCloudFlightAddressUri() throws URISyntaxException {
        return new URI(CLOUD_FLIGHT_ADDRESS);
    }
}