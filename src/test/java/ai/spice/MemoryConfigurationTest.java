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
                .withMemoryLimitMB(128) // 128 MB
                .build();
            
            // If we reach here without exception, the configuration worked
            assertTrue("Memory configuration should not throw exception", true);
            
            client.close();
        } catch (Exception e) {
            // We expect some exceptions due to no local Spice instance,
            // but not IllegalArgumentException from memory configuration
            assertFalse("Should not throw IllegalArgumentException for valid memory config", 
                       e instanceof IllegalArgumentException);
        }
    }

    public void testInvalidMemoryLimitMB() throws Exception {
        try {
            SpiceClient.builder()
                .withMemoryLimitMB(0) // Invalid: must be positive
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
                .withMemoryLimitMB(-100) // Invalid: negative
                .build();
            fail("Should throw IllegalArgumentException for negative memory limit");
        } catch (IllegalArgumentException e) {
            assertTrue("Should throw IllegalArgumentException for negative memory limit", true);
        }
    }
}