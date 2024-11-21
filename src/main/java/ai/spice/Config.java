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

/**
 * Provides default configuration for Spice client.
 */
public class Config {

    /** Cloud flight address */
    public static final String CLOUD_FLIGHT_ADDRESS;
    /** Local flight address */
    public static final String LOCAL_FLIGHT_ADDRESS;
    /** Cloud HTTP address */
    public static final String CLOUD_HTTP_ADDRESS;
    /** Local HTTP address */
    public static final String LOCAL_HTTP_ADDRESS;

    static {
        CLOUD_FLIGHT_ADDRESS = System.getenv("SPICE_FLIGHT_URL") != null ? System.getenv("SPICE_FLIGHT_URL")
                : "https://flight.spiceai.io:443";

        LOCAL_FLIGHT_ADDRESS = System.getenv("SPICE_FLIGHT_URL") != null ? System.getenv("SPICE_FLIGHT_URL")
                : "http://localhost:50051";

        CLOUD_HTTP_ADDRESS = System.getenv("SPICE_HTTP_URL") != null ? System.getenv("SPICE_HTTP_URL")
                : "https://data.spiceai.io";

        LOCAL_HTTP_ADDRESS = System.getenv("SPICE_HTTP_URL") != null ? System.getenv("SPICE_HTTP_URL")
                : "http://localhost:8090";
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

    /**
     * Returns the local HTTP address
     *
     * @return URI of the local HTTP address.
     * @throws URISyntaxException if the string could not be parsed as a URI.
     */
    public static URI getLocalHttpAddressUri() throws URISyntaxException {
        return new URI(LOCAL_HTTP_ADDRESS);
    }

    /**
     * Returns the cloud HTTP address
     *
     * @return URI of the cloud HTTP address.
     * @throws URISyntaxException if the string could not be parsed as a URI.
     */
    public static URI getCloudHttpAddressUri() throws URISyntaxException {
        return new URI(CLOUD_HTTP_ADDRESS);
    }

    /**
     * Returns the Spice SDK user agent for this system, including the package
     * version, system OS, version and arch.
     * 
     * @return the Spice SDK user agent string for this system.
     */
    public static String getUserAgent() {
        // change the os arch to match the pattern set in other SDKs
        String osArch = System.getProperty("os.arch");
        if (osArch.equals("amd64")) {
            osArch = "x86_64";
        } else if (osArch.equals("x86")) {
            osArch = "i386";
        }

        // change the os name to match the pattern set in other SDKs
        String osName = System.getProperty("os.name");
        if (osName.equals("Mac OS X")) {
            osName = "Darwin";
        } else if (osName.contains("Windows")) {
            osName = "Windows"; // this renames Windows OS names like "Windows Server 2022" to just "Windows"
        }

        // The Windows OS version strings also include the arch after the version, so we
        // need to remove it
        String osVersion = System.getProperty("os.version");
        if (osName.equals("Windows")) {
            osVersion = osVersion.split(" ")[0];
        }

        return "spice-java/" + Version.SPICE_JAVA_VERSION + " (" + osName + "/"
                + System.getProperty("os.version") + " "
                + osArch + ")";
    }
}