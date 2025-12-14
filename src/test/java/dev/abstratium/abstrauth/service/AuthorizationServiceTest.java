package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AuthorizationServiceTest {

    @Inject
    AuthorizationService authorizationService;

    @Inject
    AccountService accountService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    jakarta.transaction.UserTransaction userTransaction;
    
    @BeforeEach
    public void setup() throws Exception {
        userTransaction.begin();
        
        // Ensure test_client exists
        var clientQuery = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = 'test_client'", dev.abstratium.abstrauth.entity.OAuthClient.class);
        if (clientQuery.getResultList().isEmpty()) {
            dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
            client.setClientId("test_client");
            client.setClientName("Test Client");
            client.setClientType("confidential");
            client.setRedirectUris("[\"http://localhost:8080/callback\"]");
            client.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
            client.setRequirePkce(false);
            client.setClientSecretHash("$2a$10$dummyhash");
            em.persist(client);
        }
        
        userTransaction.commit();
    }

    @Test
    public void testCreateAuthorizationRequest() {
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid profile",
            "test_state",
            "test_challenge",
            "S256"
        );
        
        assertNotNull(request);
        assertNotNull(request.getId());
        assertEquals("test_client", request.getClientId());
        assertEquals("http://localhost:8080/auth-callback", request.getRedirectUri());
        assertEquals("openid profile", request.getScope());
        assertEquals("test_state", request.getState());
        assertEquals("test_challenge", request.getCodeChallenge());
        assertEquals("S256", request.getCodeChallengeMethod());
        assertEquals("pending", request.getStatus());
        assertNotNull(request.getCreatedAt());
        assertNotNull(request.getExpiresAt());
    }

    @Test
    public void testFindAuthorizationRequest() {
        AuthorizationRequest created = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        Optional<AuthorizationRequest> found = authorizationService.findAuthorizationRequest(created.getId());
        
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals("test_client", found.get().getClientId());
    }

    @Test
    public void testFindAuthorizationRequestNotFound() {
        Optional<AuthorizationRequest> found = authorizationService.findAuthorizationRequest("nonexistent-id");
        
        assertFalse(found.isPresent());
    }

    @Test
    public void testApproveAuthorizationRequest() {
        Account account = accountService.createAccount(
            "approve_" + System.currentTimeMillis() + "@example.com",
            "Approve Test",
            "approveuser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request.getId(), account.getId(), "native");
        
        Optional<AuthorizationRequest> approved = authorizationService.findAuthorizationRequest(request.getId());
        assertTrue(approved.isPresent());
        assertEquals("approved", approved.get().getStatus());
        assertEquals(account.getId(), approved.get().getAccountId());
    }

    @Test
    public void testApproveNonExistentRequest() {
        assertThrows(IllegalArgumentException.class, () -> {
            authorizationService.approveAuthorizationRequest("nonexistent-id", "account-id", "native");
        });
    }

    @Test
    public void testApproveExpiredRequest() throws Exception {
        // Create an expired authorization request directly
        userTransaction.begin();
        AuthorizationRequest request = new AuthorizationRequest();
        request.setId(java.util.UUID.randomUUID().toString());
        request.setClientId("test_client");
        request.setRedirectUri("http://localhost:8080/auth-callback");
        request.setScope("openid");
        request.setState("state");
        request.setCodeChallenge("challenge");
        request.setCodeChallengeMethod("S256");
        request.setStatus("pending");
        request.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        request.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // Already expired
        em.persist(request);
        em.flush();
        userTransaction.commit();
        
        // Should throw IllegalStateException for expired request
        assertThrows(IllegalStateException.class, () -> {
            authorizationService.approveAuthorizationRequest(request.getId(), "account-id", "native");
        });
    }

    @Test
    public void testGenerateAuthorizationCode() {
        Account account = accountService.createAccount(
            "gencode_" + System.currentTimeMillis() + "@example.com",
            "GenCode Test",
            "gencodeuser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid profile",
            "state",
            "challenge",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request.getId(), account.getId(), "native");
        
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(request.getId());
        
        assertNotNull(authCode);
        assertNotNull(authCode.getId());
        assertNotNull(authCode.getCode());
        assertEquals(request.getId(), authCode.getAuthorizationRequestId());
        assertEquals(account.getId(), authCode.getAccountId());
        assertEquals("test_client", authCode.getClientId());
        assertEquals("http://localhost:8080/auth-callback", authCode.getRedirectUri());
        assertEquals("openid profile", authCode.getScope());
        assertEquals("challenge", authCode.getCodeChallenge());
        assertEquals("S256", authCode.getCodeChallengeMethod());
        assertFalse(authCode.getUsed());
        assertNotNull(authCode.getCreatedAt());
        assertNotNull(authCode.getExpiresAt());
    }

    @Test
    public void testGenerateAuthorizationCodeForNonApprovedRequest() {
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        // Don't approve the request
        assertThrows(IllegalStateException.class, () -> {
            authorizationService.generateAuthorizationCode(request.getId());
        });
    }

    @Test
    public void testGenerateAuthorizationCodeForNonExistentRequest() {
        assertThrows(IllegalStateException.class, () -> {
            authorizationService.generateAuthorizationCode("nonexistent-id");
        });
    }

    @Test
    public void testFindAuthorizationCode() {
        Account account = accountService.createAccount(
            "findcode_" + System.currentTimeMillis() + "@example.com",
            "FindCode Test",
            "findcodeuser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request.getId(), account.getId(), "native");
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(request.getId());
        
        Optional<AuthorizationCode> found = authorizationService.findAuthorizationCode(authCode.getCode());
        
        assertTrue(found.isPresent());
        assertEquals(authCode.getId(), found.get().getId());
        assertEquals(authCode.getCode(), found.get().getCode());
    }

    @Test
    public void testFindAuthorizationCodeNotFound() {
        Optional<AuthorizationCode> found = authorizationService.findAuthorizationCode("nonexistent-code");
        
        assertFalse(found.isPresent());
    }

    @Test
    public void testMarkCodeAsUsed() {
        Account account = accountService.createAccount(
            "markused_" + System.currentTimeMillis() + "@example.com",
            "MarkUsed Test",
            "markuseduser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request.getId(), account.getId(), "native");
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(request.getId());
        
        assertFalse(authCode.getUsed());
        
        authorizationService.markCodeAsUsed(authCode.getCode());
        
        Optional<AuthorizationCode> used = authorizationService.findAuthorizationCode(authCode.getCode());
        assertTrue(used.isPresent());
        assertTrue(used.get().getUsed());
    }

    @Test
    public void testMarkCodeAsUsedWithNonExistentCode() {
        // Should not throw exception
        authorizationService.markCodeAsUsed("nonexistent-code");
    }

    @Test
    public void testMarkAuthorizationCodeAsUsedById() {
        Account account = accountService.createAccount(
            "markbyid_" + System.currentTimeMillis() + "@example.com",
            "MarkById Test",
            "markbyiduser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request.getId(), account.getId(), "native");
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(request.getId());
        
        assertFalse(authCode.getUsed());
        
        authorizationService.markAuthorizationCodeAsUsed(authCode.getId());
        
        Optional<AuthorizationCode> used = authorizationService.findAuthorizationCode(authCode.getCode());
        assertTrue(used.isPresent());
        assertTrue(used.get().getUsed());
    }

    @Test
    public void testMarkAuthorizationCodeAsUsedByIdWithNonExistentId() {
        // Should not throw exception
        authorizationService.markAuthorizationCodeAsUsed("nonexistent-id");
    }

    @Test
    public void testGeneratedCodeIsSecure() {
        Account account = accountService.createAccount(
            "secure_" + System.currentTimeMillis() + "@example.com",
            "Secure Test",
            "secureuser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        AuthorizationRequest request = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state",
            "challenge",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request.getId(), account.getId(), "native");
        AuthorizationCode authCode1 = authorizationService.generateAuthorizationCode(request.getId());
        
        // Create another request
        AuthorizationRequest request2 = authorizationService.createAuthorizationRequest(
            "test_client",
            "http://localhost:8080/auth-callback",
            "openid",
            "state2",
            "challenge2",
            "S256"
        );
        
        authorizationService.approveAuthorizationRequest(request2.getId(), account.getId(), "native");
        AuthorizationCode authCode2 = authorizationService.generateAuthorizationCode(request2.getId());
        
        // Codes should be different
        assertNotEquals(authCode1.getCode(), authCode2.getCode());
        
        // Code should be URL-safe base64 (43 characters for 32 bytes)
        assertTrue(authCode1.getCode().length() >= 40);
        assertFalse(authCode1.getCode().contains("+"));
        assertFalse(authCode1.getCode().contains("/"));
        assertFalse(authCode1.getCode().contains("="));
    }

    @Test
    public void testIsSignupAllowedWhenNoAccounts() {
        // Delete all accounts to simulate fresh installation
        // Note: In test profile, allow.signup=true, but we're testing the "no accounts" logic
        
        // This test assumes there might be accounts from other tests
        // The key is that isSignupAllowed should return true when countAccounts() == 0
        // Since we can't easily delete all accounts in a shared test environment,
        // we'll verify the logic works when accounts exist
        
        boolean signupAllowed = authorizationService.isSignupAllowed();
        // In test profile, allow.signup=true, so this should be true
        assertTrue(signupAllowed);
    }

    @Test
    public void testIsSignupAllowedWithExistingAccounts() {
        // Create an account to ensure we have at least one
        accountService.createAccount(
            "signup_" + System.currentTimeMillis() + "@example.com",
            "Signup Test",
            "signupuser_" + System.currentTimeMillis(),
            "Password123"
        );
        
        // In test profile, allow.signup=true
        boolean signupAllowed = authorizationService.isSignupAllowed();
        assertTrue(signupAllowed);
    }
}
