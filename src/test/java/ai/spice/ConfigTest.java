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

import java.net.URI;

import junit.framework.TestCase;

/**
 * Unit tests for Config class.
 */
public class ConfigTest extends TestCase {

    // ==================== Static Constants Tests ====================

    public void testCloudFlightAddressExists() {
        assertNotNull("CLOUD_FLIGHT_ADDRESS should not be null", Config.CLOUD_FLIGHT_ADDRESS);
        assertTrue("CLOUD_FLIGHT_ADDRESS should not be empty", Config.CLOUD_FLIGHT_ADDRESS.length() > 0);
    }

    public void testLocalFlightAddressExists() {
        assertNotNull("LOCAL_FLIGHT_ADDRESS should not be null", Config.LOCAL_FLIGHT_ADDRESS);
        assertTrue("LOCAL_FLIGHT_ADDRESS should not be empty", Config.LOCAL_FLIGHT_ADDRESS.length() > 0);
    }

    public void testCloudHttpAddressExists() {
        assertNotNull("CLOUD_HTTP_ADDRESS should not be null", Config.CLOUD_HTTP_ADDRESS);
        assertTrue("CLOUD_HTTP_ADDRESS should not be empty", Config.CLOUD_HTTP_ADDRESS.length() > 0);
    }

    public void testLocalHttpAddressExists() {
        assertNotNull("LOCAL_HTTP_ADDRESS should not be null", Config.LOCAL_HTTP_ADDRESS);
        assertTrue("LOCAL_HTTP_ADDRESS should not be empty", Config.LOCAL_HTTP_ADDRESS.length() > 0);
    }

    // ==================== Default Values Tests ====================

    public void testDefaultCloudFlightAddress() {
        // Skip if env var is set
        if (System.getenv("SPICE_FLIGHT_URL") == null) {
            assertEquals("https://flight.spiceai.io:443", Config.CLOUD_FLIGHT_ADDRESS);
        }
    }

    public void testDefaultLocalFlightAddress() {
        // Skip if env var is set
        if (System.getenv("SPICE_FLIGHT_URL") == null) {
            assertEquals("http://localhost:50051", Config.LOCAL_FLIGHT_ADDRESS);
        }
    }

    public void testDefaultCloudHttpAddress() {
        // Skip if env var is set
        if (System.getenv("SPICE_HTTP_URL") == null) {
            assertEquals("https://data.spiceai.io", Config.CLOUD_HTTP_ADDRESS);
        }
    }

    public void testDefaultLocalHttpAddress() {
        // Skip if env var is set
        if (System.getenv("SPICE_HTTP_URL") == null) {
            assertEquals("http://localhost:8090", Config.LOCAL_HTTP_ADDRESS);
        }
    }

    // ==================== URI Method Tests ====================

    public void testGetLocalFlightAddressUri() throws Exception {
        URI uri = Config.getLocalFlightAddressUri();
        assertNotNull("URI should not be null", uri);
        assertEquals("localhost", uri.getHost());
    }

    public void testGetCloudFlightAddressUri() throws Exception {
        URI uri = Config.getCloudFlightAddressUri();
        assertNotNull("URI should not be null", uri);
        // Skip specific assertions if env var overrides
        if (System.getenv("SPICE_FLIGHT_URL") == null) {
            assertEquals("flight.spiceai.io", uri.getHost());
            assertEquals(443, uri.getPort());
        }
    }

    public void testGetLocalHttpAddressUri() throws Exception {
        URI uri = Config.getLocalHttpAddressUri();
        assertNotNull("URI should not be null", uri);
        assertEquals("localhost", uri.getHost());
    }

    public void testGetCloudHttpAddressUri() throws Exception {
        URI uri = Config.getCloudHttpAddressUri();
        assertNotNull("URI should not be null", uri);
        // Skip specific assertions if env var overrides
        if (System.getenv("SPICE_HTTP_URL") == null) {
            assertEquals("data.spiceai.io", uri.getHost());
        }
    }

    // ==================== User Agent Tests ====================

    public void testGetUserAgentNotNull() {
        String userAgent = Config.getUserAgent();
        assertNotNull("User agent should not be null", userAgent);
        assertTrue("User agent should not be empty", userAgent.length() > 0);
    }

    public void testGetUserAgentContainsVersion() {
        String userAgent = Config.getUserAgent();
        assertTrue("User agent should contain 'spice-java'", userAgent.contains("spice-java/"));
    }

    public void testGetUserAgentContainsOs() {
        String userAgent = Config.getUserAgent();
        // Should contain OS info in parentheses
        assertTrue("User agent should contain parentheses", 
                userAgent.contains("(") && userAgent.contains(")"));
    }

    public void testGetUserAgentFormat() {
        String userAgent = Config.getUserAgent();
        // Format: spice-java/VERSION (OS/VERSION ARCH)
        assertTrue("User agent should start with 'spice-java/'", 
                userAgent.startsWith("spice-java/"));
        assertTrue("User agent should contain version number", 
                userAgent.matches("spice-java/\\d+\\.\\d+\\.\\d+ \\(.*\\)"));
    }

    public void testGetUserAgentContainsArch() {
        String userAgent = Config.getUserAgent();
        // Should contain architecture like x86_64, aarch64, i386, etc.
        assertTrue("User agent should contain architecture info",
                userAgent.contains("x86_64") || 
                userAgent.contains("aarch64") || 
                userAgent.contains("arm64") ||
                userAgent.contains("i386") ||
                userAgent.contains("amd64") ||
                // Allow for other architectures we might not know about
                userAgent.matches(".*\\(.*/.* .*\\)"));
    }

    // ==================== URL Scheme Tests ====================

    public void testCloudFlightAddressScheme() throws Exception {
        // Skip if env var is set
        if (System.getenv("SPICE_FLIGHT_URL") == null) {
            URI uri = Config.getCloudFlightAddressUri();
            assertEquals("https", uri.getScheme());
        }
    }

    public void testLocalFlightAddressScheme() throws Exception {
        // Skip if env var is set
        if (System.getenv("SPICE_FLIGHT_URL") == null) {
            URI uri = Config.getLocalFlightAddressUri();
            assertEquals("http", uri.getScheme());
        }
    }

    public void testCloudHttpAddressScheme() throws Exception {
        // Skip if env var is set
        if (System.getenv("SPICE_HTTP_URL") == null) {
            URI uri = Config.getCloudHttpAddressUri();
            assertEquals("https", uri.getScheme());
        }
    }

    public void testLocalHttpAddressScheme() throws Exception {
        // Skip if env var is set
        if (System.getenv("SPICE_HTTP_URL") == null) {
            URI uri = Config.getLocalHttpAddressUri();
            assertEquals("http", uri.getScheme());
        }
    }

    // ==================== Consistency Tests ====================

    public void testGetUserAgentConsistency() {
        // Multiple calls should return the same value
        String ua1 = Config.getUserAgent();
        String ua2 = Config.getUserAgent();
        assertEquals("getUserAgent should return consistent value", ua1, ua2);
    }

    public void testUriMethodsConsistency() throws Exception {
        // Multiple calls should return equivalent URIs
        URI local1 = Config.getLocalFlightAddressUri();
        URI local2 = Config.getLocalFlightAddressUri();
        assertEquals("URIs should be equivalent", local1, local2);
    }
}
