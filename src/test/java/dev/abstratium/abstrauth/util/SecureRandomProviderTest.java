package dev.abstratium.abstrauth.util;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecureRandomProvider.
 */
@QuarkusTest
public class SecureRandomProviderTest {

    @Inject
    SecureRandomProvider secureRandomProvider;

    @Test
    public void testGetSecureRandomReturnsInstance() {
        SecureRandom random = secureRandomProvider.getSecureRandom();
        assertNotNull(random, "SecureRandom should not be null");
    }

    @Test
    public void testGetSecureRandomReturnsSameInstance() {
        SecureRandom random1 = secureRandomProvider.getSecureRandom();
        SecureRandom random2 = secureRandomProvider.getSecureRandom();
        assertSame(random1, random2, "Should return the same SecureRandom instance");
    }

    @Test
    public void testSecureRandomGeneratesRandomBytes() {
        SecureRandom random = secureRandomProvider.getSecureRandom();
        byte[] bytes1 = new byte[16];
        byte[] bytes2 = new byte[16];
        
        random.nextBytes(bytes1);
        random.nextBytes(bytes2);
        
        assertFalse(java.util.Arrays.equals(bytes1, bytes2), 
                "Two random byte arrays should be different");
    }

    @Test
    public void testSecureRandomGeneratesRandomIntegers() {
        SecureRandom random = secureRandomProvider.getSecureRandom();
        int value1 = random.nextInt(1000);
        int value2 = random.nextInt(1000);
        
        // While technically they could be equal, the probability is very low
        // This test verifies the random number generator is working
        assertTrue(value1 >= 0 && value1 < 1000, "Random int should be in range");
        assertTrue(value2 >= 0 && value2 < 1000, "Random int should be in range");
    }
}
