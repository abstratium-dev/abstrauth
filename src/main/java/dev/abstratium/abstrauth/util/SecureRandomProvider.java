package dev.abstratium.abstrauth.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;

/**
 * Provider for SecureRandom instances with lazy initialization.
 * 
 * This class ensures SecureRandom is initialized at runtime rather than
 * at build time, which is required for GraalVM native image compatibility.
 * SecureRandom instances created during native image generation have cached
 * seed values and don't behave as expected at runtime.
 */
@ApplicationScoped
public class SecureRandomProvider {
    
    private SecureRandom secureRandom;
    
    /**
     * Get a SecureRandom instance, initializing lazily on first use.
     * This method is thread-safe through double-checked locking.
     * 
     * @return a SecureRandom instance
     */
    public SecureRandom getSecureRandom() {
        if (secureRandom == null) {
            synchronized (this) {
                if (secureRandom == null) {
                    secureRandom = new SecureRandom();
                }
            }
        }
        return secureRandom;
    }
}
