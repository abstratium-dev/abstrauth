package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.entity.RevokedToken;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TokenRevocationService.
 * Ensures token revocation logic works correctly for authorization code replay attacks
 * and explicit token revocation.
 */
@QuarkusTest
class TokenRevocationServiceTest {

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    EntityManager em;

    private String testAuthCodeId;
    private String testJti;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up any existing test data
        em.createQuery("DELETE FROM RevokedToken").executeUpdate();
        em.createQuery("DELETE FROM AuthorizationCode").executeUpdate();
        em.createQuery("DELETE FROM AuthorizationRequest").executeUpdate();
        em.createQuery("DELETE FROM Account WHERE email = 'test-revocation@example.com'").executeUpdate();
        
        // Create a test authorization code
        testAuthCodeId = UUID.randomUUID().toString();
        testJti = UUID.randomUUID().toString();
        
        // Create test account
        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setEmail("test-revocation@example.com");
        account.setName("Test User");
        account.setEmailVerified(true);
        account.setCreatedAt(LocalDateTime.now());
        em.persist(account);
        
        // Create test authorization request
        AuthorizationRequest request = new AuthorizationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setClientId("test-client");
        request.setRedirectUri("http://localhost:8080/callback");
        request.setScope("openid profile");
        request.setState("test-state");
        request.setCodeChallenge("test-challenge");
        request.setCodeChallengeMethod("S256");
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        em.persist(request);
        
        // Create test authorization code
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setId(testAuthCodeId);
        authCode.setCode("test-code-" + UUID.randomUUID());
        authCode.setAuthorizationRequestId(request.getId());
        authCode.setAccountId(account.getId());
        authCode.setClientId("test-client");
        authCode.setRedirectUri("http://localhost:8080/callback");
        authCode.setScope("openid profile");
        authCode.setCodeChallenge("test-challenge");
        authCode.setCodeChallengeMethod("S256");
        authCode.setUsed(false);
        authCode.setCreatedAt(LocalDateTime.now());
        authCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        em.persist(authCode);
        
        em.flush();
    }

    @Test
    void testRevokeTokensByAuthorizationCode() {
        // When: Revoke tokens by authorization code
        tokenRevocationService.revokeTokensByAuthorizationCode(testAuthCodeId, "authorization_code_replay_detected");
        
        // Then: Authorization code should be marked as compromised
        assertTrue(tokenRevocationService.isAuthorizationCodeCompromised(testAuthCodeId),
                "Authorization code should be marked as compromised");
        
        // And: A revocation entry should exist in the database
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM RevokedToken r WHERE r.authorizationCodeId = :authCodeId",
                Long.class)
                .setParameter("authCodeId", testAuthCodeId)
                .getSingleResult();
        assertEquals(1L, count, "Should have exactly one revocation entry");
    }

    @Test
    void testRevokeTokensByAuthorizationCode_NonExistentCode() {
        // Given: A non-existent authorization code ID
        String nonExistentId = UUID.randomUUID().toString();
        
        // When: Try to revoke tokens for non-existent code
        tokenRevocationService.revokeTokensByAuthorizationCode(nonExistentId, "test");
        
        // Then: Should not throw exception, but should log warning
        // The authorization code should not be marked as compromised since it doesn't exist
        assertFalse(tokenRevocationService.isAuthorizationCodeCompromised(nonExistentId),
                "Non-existent authorization code should not be marked as compromised");
    }

    @Test
    void testRevokeToken() {
        // When: Revoke a specific token by JTI
        tokenRevocationService.revokeToken(testJti, "user_requested_revocation");
        
        // Then: Token should be marked as revoked
        assertTrue(tokenRevocationService.isTokenRevoked(testJti),
                "Token should be marked as revoked");
        
        // And: A revocation entry should exist in the database
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM RevokedToken r WHERE r.tokenJti = :jti",
                Long.class)
                .setParameter("jti", testJti)
                .getSingleResult();
        assertEquals(1L, count, "Should have exactly one revocation entry");
    }

    @Test
    void testIsTokenRevoked_NotRevoked() {
        // Given: A JTI that has not been revoked
        String nonRevokedJti = UUID.randomUUID().toString();
        
        // When/Then: Check if token is revoked
        assertFalse(tokenRevocationService.isTokenRevoked(nonRevokedJti),
                "Non-revoked token should return false");
    }

    @Test
    void testIsTokenRevoked_NullJti() {
        // When/Then: Check with null JTI
        assertFalse(tokenRevocationService.isTokenRevoked(null),
                "Null JTI should return false");
    }

    @Test
    void testIsTokenRevoked_BlankJti() {
        // When/Then: Check with blank JTI
        assertFalse(tokenRevocationService.isTokenRevoked(""),
                "Blank JTI should return false");
        assertFalse(tokenRevocationService.isTokenRevoked("   "),
                "Whitespace JTI should return false");
    }

    @Test
    void testIsAuthorizationCodeCompromised_NotCompromised() {
        // Given: An authorization code that has not been compromised
        String nonCompromisedId = UUID.randomUUID().toString();
        
        // When/Then: Check if authorization code is compromised
        assertFalse(tokenRevocationService.isAuthorizationCodeCompromised(nonCompromisedId),
                "Non-compromised authorization code should return false");
    }

    @Test
    void testIsAuthorizationCodeCompromised_NullId() {
        // When/Then: Check with null ID
        assertFalse(tokenRevocationService.isAuthorizationCodeCompromised(null),
                "Null authorization code ID should return false");
    }

    @Test
    void testIsAuthorizationCodeCompromised_BlankId() {
        // When/Then: Check with blank ID
        assertFalse(tokenRevocationService.isAuthorizationCodeCompromised(""),
                "Blank authorization code ID should return false");
        assertFalse(tokenRevocationService.isAuthorizationCodeCompromised("   "),
                "Whitespace authorization code ID should return false");
    }

    @Test
    void testMultipleRevocations() {
        // When: Revoke multiple tokens
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();
        String jti3 = UUID.randomUUID().toString();
        
        tokenRevocationService.revokeToken(jti1, "reason1");
        tokenRevocationService.revokeToken(jti2, "reason2");
        tokenRevocationService.revokeToken(jti3, "reason3");
        
        // Then: All tokens should be marked as revoked
        assertTrue(tokenRevocationService.isTokenRevoked(jti1), "Token 1 should be revoked");
        assertTrue(tokenRevocationService.isTokenRevoked(jti2), "Token 2 should be revoked");
        assertTrue(tokenRevocationService.isTokenRevoked(jti3), "Token 3 should be revoked");
        
        // And: A different token should not be revoked
        assertFalse(tokenRevocationService.isTokenRevoked(UUID.randomUUID().toString()),
                "Different token should not be revoked");
    }

    @Test
    void testRevocationReason() {
        // When: Revoke a token with a specific reason
        String jti = UUID.randomUUID().toString();
        String reason = "security_incident_detected";
        
        tokenRevocationService.revokeToken(jti, reason);
        
        // Then: The revocation entry should contain the correct reason
        RevokedToken revocation = em.createQuery(
                "SELECT r FROM RevokedToken r WHERE r.tokenJti = :jti",
                RevokedToken.class)
                .setParameter("jti", jti)
                .getSingleResult();
        
        assertEquals(reason, revocation.getReason(), "Revocation reason should match");
        assertEquals(jti, revocation.getTokenJti(), "JTI should match");
        assertNotNull(revocation.getRevokedAt(), "Revoked timestamp should be set");
        assertNotNull(revocation.getCreatedAt(), "Created timestamp should be set");
    }

    @Test
    void testAuthorizationCodeRevocationReason() {
        // When: Revoke tokens by authorization code
        String reason = "authorization_code_replay_detected";
        
        tokenRevocationService.revokeTokensByAuthorizationCode(testAuthCodeId, reason);
        
        // Then: The revocation entry should contain the correct reason and auth code ID
        RevokedToken revocation = em.createQuery(
                "SELECT r FROM RevokedToken r WHERE r.authorizationCodeId = :authCodeId",
                RevokedToken.class)
                .setParameter("authCodeId", testAuthCodeId)
                .getSingleResult();
        
        assertEquals(reason, revocation.getReason(), "Revocation reason should match");
        assertEquals(testAuthCodeId, revocation.getAuthorizationCodeId(), "Auth code ID should match");
        assertTrue(revocation.getTokenJti().startsWith("AUTH_CODE_"), "JTI should have AUTH_CODE prefix");
        assertNotNull(revocation.getRevokedAt(), "Revoked timestamp should be set");
        assertNotNull(revocation.getCreatedAt(), "Created timestamp should be set");
    }
}
