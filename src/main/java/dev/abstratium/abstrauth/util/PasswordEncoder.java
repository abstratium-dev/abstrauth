package dev.abstratium.abstrauth.util;

import java.security.SecureRandom;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application-scoped password encoder using BCrypt.
 *
 * This implementation uses the at.favre.lib:bcrypt library instead of
 * Spring Security's BCryptPasswordEncoder to avoid native image build issues
 * caused by Spring's static SecureRandom holder. The existing SecureRandomProvider
 * ensures the SecureRandom instance is created at runtime, not during image build.
 *
 * New hashes use the modern {@code $2b$} bcrypt format (via
 * {@link BCrypt.Version#VERSION_2B}) with cost factor 12. The verifyer is
 * version-agnostic, so existing database hashes produced by Spring's
 * {@code BCryptPasswordEncoder} (which uses the {@code $2a$} format) continue
 * to be verifiable.
 */
@ApplicationScoped
public class PasswordEncoder {

    // OWASP-recommended cost factor for both account passwords and client secrets
    private static final int COST = 12;

    @Inject
    SecureRandomProvider secureRandomProvider;

    private BCrypt.Hasher hasher;
    private BCrypt.Verifyer verifyer;

    @PostConstruct
    void init() {
        SecureRandom secureRandom = secureRandomProvider.getSecureRandom();
        hasher = BCrypt.with(BCrypt.Version.VERSION_2B, secureRandom, LongPasswordStrategies.truncate(BCrypt.Version.VERSION_2B));
        // Null version lets the verifyer parse the version identifier from the hash,
        // so both $2a$ (Spring) and $2b$ (new) hashes are accepted.
        verifyer = BCrypt.verifyer(null, LongPasswordStrategies.truncate(BCrypt.Version.VERSION_2B));
    }

    /**
     * Hash an account password with cost factor 12.
     */
    public String hashPassword(String password) {
        return hasher.hashToString(COST, password.toCharArray());
    }

    /**
     * Hash a client secret with cost factor 12.
     */
    public String hashClientSecret(String secret) {
        return hasher.hashToString(COST, secret.toCharArray());
    }

    /**
     * Verify a plain value against a BCrypt hash.
     * Returns false for invalid hash formats or mismatches.
     */
    public boolean matches(String plain, String hash) {
        return verifyer.verify(plain.toCharArray(), hash.toCharArray()).verified;
    }
}
