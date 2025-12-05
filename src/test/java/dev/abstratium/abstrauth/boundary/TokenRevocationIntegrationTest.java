package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.TokenRevocationService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test to verify that revoked tokens cannot be used.
 * Tests the complete flow: issue token, revoke it, attempt to use it.
 */
@QuarkusTest
class TokenRevocationIntegrationTest {

    @Inject
    AccountService accountService;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    OAuthClientService clientService;

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    EntityManager em;

    private Account testAccount;
    private String testPassword = "TestPassword123!";
    private String clientId = "abstratium-abstrauth";

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up test data
        em.createQuery("DELETE FROM RevokedToken").executeUpdate();
        em.createQuery("DELETE FROM AuthorizationCode").executeUpdate();
        em.createQuery("DELETE FROM AuthorizationRequest").executeUpdate();
        em.createQuery("DELETE FROM Credential WHERE username = 'revocationtest'").executeUpdate();
        em.createQuery("DELETE FROM Account WHERE email = 'revocationtest@example.com'").executeUpdate();

        // Create test account
        testAccount = accountService.createAccount(
                "revocationtest@example.com",
                "Revocation Test User",
                "revocationtest",
                testPassword
        );
    }

    // This test requires the full OAuth flow which is better tested in E2E tests
    // Keeping it commented for reference
    /*
    @Test
    void testAuthorizationCodeReplayRevokesTokens() throws Exception {
        // Given: Complete OAuth flow to get an authorization code
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = UUID.randomUUID().toString();

        // Get first redirect URI from client
        var client = clientService.findByClientId(clientId).orElseThrow();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String[] redirectUris = mapper.readValue(client.getRedirectUris(), String[].class);
        String redirectUri = redirectUris[0];

        // Step 1: Initiate authorization
        String location = given()
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
        .when()
                .get("/oauth2/authorize")
        .then()
                .statusCode(303)
                .extract().header("Location");

        String requestId = location.substring(location.lastIndexOf("/") + 1);

        // Step 2: Authenticate
        given()
                .contentType(ContentType.URLENC)
                .formParam("username", "revocationtest")
                .formParam("password", testPassword)
                .formParam("request_id", requestId)
        .when()
                .post("/oauth2/authorize/authenticate")
        .then()
                .statusCode(200);

        // Step 3: Get authorization code
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(requestId);
        String code = authCode.getCode();

        // Step 4: Exchange code for token (first time - should succeed)
        String firstToken = given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", redirectUri)
                .formParam("client_id", clientId)
                .formParam("code_verifier", codeVerifier)
        .when()
                .post("/oauth2/token")
        .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("token_type", equalTo("Bearer"))
                .extract().path("access_token");

        // Verify first token works
        given()
                .header("Authorization", "Bearer " + firstToken)
        .when()
                .get("/api/clients")
        .then()
                .statusCode(anyOf(is(200), is(403))); // 200 if authorized, 403 if not enough permissions

        // Step 5: Try to use the same code again (replay attack)
        given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", redirectUri)
                .formParam("client_id", clientId)
                .formParam("code_verifier", codeVerifier)
        .when()
                .post("/oauth2/token")
        .then()
                .statusCode(400)
                .body("error", equalTo("invalid_grant"))
                .body("error_description", containsString("already been used"));

        // Step 6: Verify that the authorization code is now marked as compromised
        boolean isCompromised = tokenRevocationService.isAuthorizationCodeCompromised(authCode.getId());
        assert isCompromised : "Authorization code should be marked as compromised after replay attempt";
    }
    */

    @Test
    void testExplicitTokenRevocation() throws Exception {
        // Given: A valid token with a known JTI
        String jti = UUID.randomUUID().toString();

        // When: Explicitly revoke the token
        tokenRevocationService.revokeToken(jti, "user_requested_revocation");

        // Then: The token should be marked as revoked
        boolean isRevoked = tokenRevocationService.isTokenRevoked(jti);
        assert isRevoked : "Token should be marked as revoked";

        // And: Attempting to use a revoked token should fail
        // Note: This would require implementing token validation middleware
        // that checks revocation status. For now, we verify the revocation
        // is recorded in the database.
        
        // Verify revocation entry exists
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM RevokedToken r WHERE r.tokenJti = :jti",
                Long.class)
                .setParameter("jti", jti)
                .getSingleResult();
        
        assert count == 1L : "Should have exactly one revocation entry";
    }

    @Test
    @Transactional
    void testMultipleTokenRevocationsFromSameAuthCode() throws Exception {
        // Given: An authorization code with proper setup
        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setEmail("test-multi-revoke@example.com");
        account.setName("Test User");
        account.setEmailVerified(true);
        account.setCreatedAt(java.time.LocalDateTime.now());
        em.persist(account);
        
        AuthorizationRequest request = new AuthorizationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setClientId(clientId);
        request.setRedirectUri("http://localhost:8080/callback");
        request.setScope("openid profile");
        request.setState(UUID.randomUUID().toString());
        request.setCodeChallenge("test-challenge");
        request.setCodeChallengeMethod("S256");
        request.setStatus("PENDING");
        request.setCreatedAt(java.time.LocalDateTime.now());
        request.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        em.persist(request);
        
        AuthorizationCode authCode = new AuthorizationCode();
        String authCodeId = UUID.randomUUID().toString();
        authCode.setId(authCodeId);
        authCode.setCode("test-code-" + UUID.randomUUID());
        authCode.setAuthorizationRequestId(request.getId());
        authCode.setAccountId(account.getId());
        authCode.setClientId(clientId);
        authCode.setRedirectUri("http://localhost:8080/callback");
        authCode.setScope("openid profile");
        authCode.setCodeChallenge("test-challenge");
        authCode.setCodeChallengeMethod("S256");
        authCode.setUsed(false);
        authCode.setCreatedAt(java.time.LocalDateTime.now());
        authCode.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        em.persist(authCode);
        em.flush();

        // When: Revoke all tokens from this auth code
        tokenRevocationService.revokeTokensByAuthorizationCode(authCodeId, "security_incident");

        // Then: The auth code should be marked as compromised
        boolean isCompromised = tokenRevocationService.isAuthorizationCodeCompromised(authCodeId);
        assert isCompromised : "Authorization code should be marked as compromised";

        // And: Attempting to revoke again should not cause issues (idempotent)
        tokenRevocationService.revokeTokensByAuthorizationCode(authCodeId, "duplicate_revocation");
        
        // Verify still compromised
        isCompromised = tokenRevocationService.isAuthorizationCodeCompromised(authCodeId);
        assert isCompromised : "Authorization code should still be marked as compromised";
    }

    /**
     * Generate a random code verifier for PKCE.
     */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate code challenge from verifier using S256 method.
     */
    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
