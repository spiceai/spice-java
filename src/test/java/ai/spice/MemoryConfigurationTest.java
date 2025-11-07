package ai.spice;

import junit.framework.TestCase;

/**
 * Test memory configuration functionality
 */
public class MemoryConfigurationTest extends TestCase {

    public void testMemoryLimitMBConfiguration() throws Exception {
        try {
            // Test that memory limit in MB is properly configured
            SpiceClient client = SpiceClient.builder()
                    .withArrowMemoryLimitMB(128) // 128 MB
                    .build();

            // If we reach here without exception, the configuration worked
            assertTrue("Memory configuration should not throw exception", true);

            client.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    public void testInvalidMemoryLimitMB() throws Exception {
        try {
            SpiceClient.builder()
                    .withArrowMemoryLimitMB(0) // Invalid: must be positive
                    .build();
            fail("Should throw IllegalArgumentException for zero memory limit");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertTrue("Should throw IllegalArgumentException for zero memory limit", true);
        }
    }

    public void testNegativeMemoryLimitMB() throws Exception {
        try {
            SpiceClient.builder()
                    .withArrowMemoryLimitMB(-100) // Invalid: negative
                    .build();
            fail("Should throw IllegalArgumentException for negative memory limit");
        } catch (IllegalArgumentException e) {
            assertTrue("Should throw IllegalArgumentException for negative memory limit", true);
        }
    }

    public void testOverflowProtection() throws Exception {
        // Test overflow protection - values that would cause overflow when converted to
        // bytes
        long maxSafeMB = Long.MAX_VALUE / (1024L * 1024L);
        long overflowValue = maxSafeMB + 1;

        try {
            SpiceClient.builder()
                    .withArrowMemoryLimitMB(overflowValue) // Would cause overflow
                    .build();
            fail("Should throw IllegalArgumentException for overflow-causing memory limit");
        } catch (IllegalArgumentException e) {
            assertTrue("Should throw IllegalArgumentException for overflow protection", true);
        }
    }

    public void testMaxSafeMemoryLimit() throws Exception {
        // Test that the maximum safe value works without throwing
        long maxSafeMB = Long.MAX_VALUE / (1024L * 1024L);

        try {
            SpiceClient client = SpiceClient.builder()
                    .withArrowMemoryLimitMB(maxSafeMB) // Maximum safe value
                    .build();

            assertTrue("Maximum safe memory limit should work", true);
            client.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
}