package dev.abstratium.abstrauth.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Regression tests for the BCrypt password encoder.
 *
 * Verifies that the at.favre.lib:bcrypt-based PasswordEncoder remains
 * compatible with hashes previously produced by Spring Security's
 * BCryptPasswordEncoder (both account passwords and client secrets) while
 * generating new hashes in the modern {@code $2b$} format with cost factor 12.
 */
@QuarkusTest
public class PasswordEncoderTest {

    @Inject
    PasswordEncoder passwordEncoder;

    // Hash of the default development client secret, produced by Spring Security's
    // BCryptPasswordEncoder with cost factor 10. This exact hash is stored in the
    // Flyway migration V01.010__updateDefaultClientToConfidential.sql.
    private static final String SPRING_CLIENT_SECRET_HASH =
            "$2a$10$mtwoJ4E6V6XPY8DHrKEpIuV2n0Q1J7FjMZkja5Kv0lYkq36LxcZdO";

    // A hash produced by the modern $2b$ format with cost factor 12 for 'modern-secret'.
    private static final String VERSION_2B_HASH =
            "$2b$12$2I1lxaYkdHUUjU32VwsGIOIxMZVSuzOJkGy0Ba.mowyDyuO8FLU/2";

    @Test
    public void testVerifiesSpringClientSecretHash() {
        assertTrue(passwordEncoder.matches("dev-secret-CHANGE-IN-PROD", SPRING_CLIENT_SECRET_HASH));
    }

    @Test
    public void testRejectsWrongSecretAgainstSpringHash() {
        assertFalse(passwordEncoder.matches("wrong-secret", SPRING_CLIENT_SECRET_HASH));
    }

    @Test
    public void testVerifiesModern2BHash() {
        assertTrue(passwordEncoder.matches("modern-secret", VERSION_2B_HASH));
    }

    @Test
    public void testRejectsWrongSecretAgainst2BHash() {
        assertFalse(passwordEncoder.matches("wrong-secret", VERSION_2B_HASH));
    }

    @Test
    public void testPasswordHashRoundTrip() {
        String hash = passwordEncoder.hashPassword("Pass123!");
        assertTrue(hash.startsWith("$2b$12$"), "New password hashes should use $2b$ format with cost 12");
        assertTrue(passwordEncoder.matches("Pass123!", hash));
        assertFalse(passwordEncoder.matches("wrong-password", hash));
    }

    @Test
    public void testClientSecretHashRoundTrip() {
        String hash = passwordEncoder.hashClientSecret("my-client-secret");
        assertTrue(hash.startsWith("$2b$12$"), "New client secret hashes should use $2b$ format with cost 12");
        assertTrue(passwordEncoder.matches("my-client-secret", hash));
        assertFalse(passwordEncoder.matches("wrong-secret", hash));
    }
}
