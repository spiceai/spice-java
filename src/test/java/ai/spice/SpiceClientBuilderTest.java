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
 * Comprehensive unit tests for SpiceClientBuilder.
 * These tests verify builder configuration without requiring a Spice server.
 */
public class SpiceClientBuilderTest extends TestCase {

    // ==================== Basic Builder Tests ====================

    public void testDefaultBuilder() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder();
        assertNotNull("Builder should not be null", builder);
        
        // Build client - will connect to default localhost
        SpiceClient client = builder.build();
        assertNotNull("Client should not be null", client);
        client.close();
    }

    public void testWithApiKeyValidFormat() throws Exception {
        // API key must have format appId|key
        try {
            SpiceClient.builder().withApiKey("myAppId|mySecretKey");
            // Should succeed
        } catch (Exception e) {
            fail("Valid API key format should not throw");
        }
    }

    public void testWithNullApiKey() throws Exception {
        try {
            SpiceClient.builder().withApiKey(null);
            fail("Should throw exception for null API key");
        } catch (IllegalArgumentException e) {
            // Expected - null API key throws
        }
    }

    public void testWithEmptyApiKey() throws Exception {
        try {
            SpiceClient.builder().withApiKey("");
            fail("Should throw exception for empty API key");
        } catch (IllegalArgumentException e) {
            // Expected - empty API key throws
        }
    }

    public void testWithInvalidApiKeyFormat() throws Exception {
        try {
            SpiceClient.builder().withApiKey("invalidformat");
            fail("Should throw exception for API key without pipe");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention invalid", e.getMessage().contains("invalid"));
        }
    }

    // ==================== Flight Address Tests ====================

    public void testWithFlightAddressUri() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withFlightAddress(new URI("grpc://localhost:50051"));
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with flight URI", client);
        client.close();
    }

    public void testWithHttpsFlightAddress() throws Exception {
        // Should convert https to grpc+tls
        SpiceClientBuilder builder = SpiceClient.builder()
                .withFlightAddress(new URI("https://localhost:443"));
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with https flight address", client);
        client.close();
    }

    public void testWithGrpcTlsFlightAddress() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withFlightAddress(new URI("grpc+tls://localhost:443"));
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with grpc+tls address", client);
        client.close();
    }

    public void testWithNullFlightAddress() throws Exception {
        try {
            SpiceClient.builder().withFlightAddress(null);
            fail("Should throw exception for null flight address");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention null", e.getMessage().contains("null"));
        }
    }

    // ==================== HTTP Address Tests ====================

    public void testWithHttpAddressUri() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withHttpAddress(new URI("http://localhost:8090"));
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with HTTP URI", client);
        client.close();
    }

    public void testWithNullHttpAddress() throws Exception {
        try {
            SpiceClient.builder().withHttpAddress(null);
            fail("Should throw exception for null HTTP address");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention null", e.getMessage().contains("null"));
        }
    }

    // ==================== Memory Configuration Tests ====================

    public void testWithArrowMemoryLimitMB() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withArrowMemoryLimitMB(256);
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with memory limit", client);
        client.close();
    }

    public void testWithMinimumMemoryLimit() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withArrowMemoryLimitMB(1); // 1 MB minimum
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with 1 MB limit", client);
        client.close();
    }

    public void testWithLargeMemoryLimit() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withArrowMemoryLimitMB(8192); // 8 GB
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with 8 GB limit", client);
        client.close();
    }

    public void testWithZeroMemoryLimit() throws Exception {
        try {
            SpiceClient.builder()
                    .withArrowMemoryLimitMB(0);
            fail("Should throw exception for zero memory limit");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testWithNegativeMemoryLimit() throws Exception {
        try {
            SpiceClient.builder()
                    .withArrowMemoryLimitMB(-100);
            fail("Should throw exception for negative memory limit");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    // ==================== Retry Configuration Tests ====================

    public void testWithMaxRetries() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withMaxRetries(5);
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with custom retry count", client);
        client.close();
    }

    public void testWithZeroRetries() throws Exception {
        // Zero retries means no retries (immediate failure)
        SpiceClientBuilder builder = SpiceClient.builder()
                .withMaxRetries(0);
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with 0 retries", client);
        client.close();
    }

    public void testWithNegativeRetries() throws Exception {
        try {
            SpiceClient.builder()
                    .withMaxRetries(-1);
            fail("Should throw exception for negative retries");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testWithHighRetries() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withMaxRetries(100);
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with 100 retries", client);
        client.close();
    }

    // ==================== User Agent Tests ====================

    public void testWithUserAgent() throws Exception {
        SpiceClientBuilder builder = SpiceClient.builder()
                .withUserAgent("TestApp/1.0");
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with custom user agent", client);
        client.close();
    }

    public void testWithNullUserAgent() throws Exception {
        try {
            SpiceClient.builder().withUserAgent(null);
            fail("Should throw exception for null user agent");
        } catch (IllegalArgumentException e) {
            // Expected - null user agent throws
        }
    }

    public void testWithEmptyUserAgent() throws Exception {
        try {
            SpiceClient.builder().withUserAgent("");
            fail("Should throw exception for empty user agent");
        } catch (IllegalArgumentException e) {
            // Expected - empty user agent throws
        }
    }

    public void testWithLongUserAgent() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("TestApp/1.0 ");
        }
        
        SpiceClientBuilder builder = SpiceClient.builder()
                .withUserAgent(sb.toString());
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created with long user agent", client);
        client.close();
    }

    // ==================== Spice Cloud Configuration Tests ====================

    public void testWithSpiceCloud() throws Exception {
        // SpiceCloud changes the default addresses
        SpiceClientBuilder builder = SpiceClient.builder()
                .withSpiceCloud();
        
        SpiceClient client = builder.build();
        assertNotNull("Client should be created for Spice Cloud", client);
        client.close();
    }

    // ==================== Chained Configuration Tests ====================

    public void testChainedConfiguration() throws Exception {
        SpiceClient client = SpiceClient.builder()
                .withFlightAddress(new URI("grpc://localhost:50051"))
                .withHttpAddress(new URI("http://localhost:8090"))
                .withMaxRetries(3)
                .withArrowMemoryLimitMB(512)
                .withUserAgent("TestApp/1.0")
                .build();
        
        assertNotNull("Client should be created with chained config", client);
        client.close();
    }

    public void testMultipleConfigOverrides() throws Exception {
        // Later calls should override earlier ones
        SpiceClient client = SpiceClient.builder()
                .withMaxRetries(1)
                .withMaxRetries(5)  // Override
                .withArrowMemoryLimitMB(128)
                .withArrowMemoryLimitMB(256)  // Override
                .build();
        
        assertNotNull("Client should be created with overridden config", client);
        client.close();
    }

    // ==================== Channel Count / Timeout / Statement Cache ====================

    public void testWithChannelCountValid() throws Exception {
        try (SpiceClient client = SpiceClient.builder().withChannelCount(2).build()) {
            assertNotNull(client);
        }
    }

    public void testWithChannelCountInvalid() throws Exception {
        try {
            SpiceClient.builder().withChannelCount(0);
            fail("Expected IllegalArgumentException for channelCount=0");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            SpiceClient.builder().withChannelCount(17);
            fail("Expected IllegalArgumentException for channelCount=17");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testWithQueryTimeoutValid() throws Exception {
        try (SpiceClient client = SpiceClient.builder()
                .withQueryTimeout(java.time.Duration.ofSeconds(30)).build()) {
            assertNotNull(client);
        }
    }

    public void testWithQueryTimeoutInvalid() throws Exception {
        try {
            SpiceClient.builder().withQueryTimeout(null);
            fail("Expected IllegalArgumentException for null timeout");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            SpiceClient.builder().withQueryTimeout(java.time.Duration.ZERO);
            fail("Expected IllegalArgumentException for zero timeout");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            SpiceClient.builder().withQueryTimeout(java.time.Duration.ofSeconds(-1));
            fail("Expected IllegalArgumentException for negative timeout");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testWithPreparedStatementCacheSizeValid() throws Exception {
        try (SpiceClient client = SpiceClient.builder().withPreparedStatementCacheSize(0).build()) {
            assertNotNull(client);
        }
        try (SpiceClient client = SpiceClient.builder().withPreparedStatementCacheSize(1024).build()) {
            assertNotNull(client);
        }
    }

    public void testWithPreparedStatementCacheSizeInvalid() throws Exception {
        try {
            SpiceClient.builder().withPreparedStatementCacheSize(-1);
            fail("Expected IllegalArgumentException for negative cache size");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            SpiceClient.builder().withPreparedStatementCacheSize(1025);
            fail("Expected IllegalArgumentException for oversized cache");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // ==================== Close/Resource Management Tests ====================

    public void testMultipleClose() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        
        // Close should be idempotent
        client.close();
        client.close();  // Should not throw
        client.close();  // Should not throw
    }

    public void testTryWithResources() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertNotNull("Client should be created", client);
        }
        // Auto-close should work
    }
}
