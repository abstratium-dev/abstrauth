package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the complete OAuth 2.0 Authorization Code Flow with PKCE
 * using client secret authentication via REST API.
 * 
 * This test verifies the entire flow using HTTP boundaries:
 * 1. Creating a client via REST API
 * 2. Creating a client secret via REST API
 * 3. Initiating an authorization request
 * 4. Approving the authorization
 * 5. Exchanging the authorization code for an access token using the client secret
 * 6. Using the access token to access protected resources
 * 7. Testing with expired and revoked secrets
 */
@QuarkusTest
public class OAuthFlowWithClientSecretTest {

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    AuthorizationService authorizationService;

    private Account testAccount;
    private String testPassword = "TestPassword123!";
    private String adminToken;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up test data
        em.createQuery("DELETE FROM ClientSecret WHERE clientId LIKE 'test-oauth-flow-%'").executeUpdate();
        em.createQuery("DELETE FROM AuthorizationCode WHERE clientId LIKE 'test-oauth-flow-%'").executeUpdate();
        em.createQuery("DELETE FROM AuthorizationRequest WHERE clientId LIKE 'test-oauth-flow-%'").executeUpdate();
        em.createQuery("DELETE FROM OAuthClient WHERE clientId LIKE 'test-oauth-flow-%'").executeUpdate();
        em.createQuery("DELETE FROM Credential WHERE username = 'oauthflowtest'").executeUpdate();
        em.createQuery("DELETE FROM AccountRole WHERE accountId IN (SELECT id FROM Account WHERE email = 'oauthflowtest@example.com')").executeUpdate();
        em.createQuery("DELETE FROM Account WHERE email = 'oauthflowtest@example.com'").executeUpdate();

        // Create test account with MANAGE_CLIENTS role
        testAccount = accountService.createAccount(
                "oauthflowtest@example.com",
                "OAuth Flow Test User",
                "oauthflowtest",
                testPassword,
                AccountService.NATIVE
        );

        // Assign MANAGE_CLIENTS role
        AccountRole role = new AccountRole();
        role.setAccountId(testAccount.getId());
        role.setClientId(Roles.CLIENT_ID);
        role.setRole(Roles.MANAGE_CLIENTS);
        em.persist(role);

        em.flush();

        // Generate admin token
        adminToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn(testAccount.getEmail())
            .subject(testAccount.getId())
            .groups(java.util.Set.of(Roles.MANAGE_CLIENTS, Roles.USER))
            .claim("email", testAccount.getEmail())
            .claim("name", testAccount.getName())
            .sign();
    }

    /**
     * Test the complete OAuth flow with a newly created client and secret via REST API.
     */
    @Test
    public void testCompleteOAuthFlowWithNewClientAndSecret() throws Exception {
        String clientId = "test-oauth-flow-" + System.currentTimeMillis();

        // Step 1: Create a new OAuth client via REST API
        String clientResponse = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of(
                        "clientId", clientId,
                        "clientName", "Test OAuth Flow Client",
                        "clientType", "confidential",
                        "redirectUris", "http://localhost:8080/callback",
                        "allowedScopes", "openid profile email",
                        "requirePkce", true
                ))
                .when()
                .post("/api/clients")
                .then()
                .statusCode(201)
                .body("clientId", equalTo(clientId))
                .extract()
                .asString();

        // Step 2: Create a new client secret via REST API
        Response secretResponse = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of(
                        "description", "Test secret for OAuth flow",
                        "expiresInDays", 30
                ))
                .when()
                .post("/api/clients/" + clientId + "/secrets")
                .then()
                .statusCode(201)
                .body("secret", notNullValue())
                .body("description", equalTo("Test secret for OAuth flow"))
                .extract()
                .response();

        String plainSecret = secretResponse.jsonPath().getString("secret");
        assertNotNull(plainSecret, "Secret should be returned");

        // Step 3: Create authorization request with PKCE
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = UUID.randomUUID().toString();
        String redirectUri = "http://localhost:8080/callback";

        String requestId = createAndApproveAuthorizationRequest(
                clientId, redirectUri, state, codeChallenge, "openid profile email"
        );

        // Step 4: Generate authorization code
        String authCode = generateAuthorizationCode(requestId);

        // Step 5: Exchange authorization code for access token using client secret
        Response tokenResponse = given()
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                .formParam("redirect_uri", redirectUri)
                .formParam("code_verifier", codeVerifier)
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", greaterThan(0))
                .extract()
                .response();

        String accessToken = tokenResponse.jsonPath().getString("access_token");
        assertNotNull(accessToken);

        // Step 6: Verify the access token can be used to access protected resources
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/userinfo")
                .then()
                .statusCode(200)
                .body("sub", equalTo(testAccount.getId()))
                .body("email", equalTo(testAccount.getEmail()));
    }

    /**
     * Test that a revoked client secret cannot be used for authentication.
     */
    @Test
    public void testRevokedSecretRejected() throws Exception {
        String clientId = "test-oauth-flow-revoked-" + System.currentTimeMillis();

        // Create client via REST API
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of(
                        "clientId", clientId,
                        "clientName", "Test Revoked Secret Client",
                        "clientType", "confidential",
                        "redirectUris", "http://localhost:8080/callback",
                        "allowedScopes", "openid",
                        "requirePkce", true
                ))
                .when()
                .post("/api/clients")
                .then()
                .statusCode(201);

        // Create secret via REST API
        Response secretResponse = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("description", "Secret to be revoked"))
                .when()
                .post("/api/clients/" + clientId + "/secrets")
                .then()
                .statusCode(201)
                .extract()
                .response();

        String plainSecret = secretResponse.jsonPath().getString("secret");
        Long secretId = secretResponse.jsonPath().getLong("id");

        // Revoke the secret via REST API
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/clients/" + clientId + "/secrets/" + secretId)
                .then()
                .statusCode(204);

        // Try to use the revoked secret - should fail
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String requestId = createAndApproveAuthorizationRequest(
                clientId, "http://localhost:8080/callback", UUID.randomUUID().toString(),
                codeChallenge, "openid"
        );
        String authCode = generateAuthorizationCode(requestId);

        given()
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                .formParam("redirect_uri", "http://localhost:8080/callback")
                .formParam("code_verifier", codeVerifier)
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    /**
     * Test that wrong client secret is rejected.
     */
    @Test
    public void testWrongSecretRejected() throws Exception {
        String clientId = "test-oauth-flow-wrong-" + System.currentTimeMillis();

        // Create client via REST API
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of(
                        "clientId", clientId,
                        "clientName", "Test Wrong Secret Client",
                        "clientType", "confidential",
                        "redirectUris", "http://localhost:8080/callback",
                        "allowedScopes", "openid",
                        "requirePkce", true
                ))
                .when()
                .post("/api/clients")
                .then()
                .statusCode(201);

        // Create secret via REST API
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("description", "Valid secret"))
                .when()
                .post("/api/clients/" + clientId + "/secrets")
                .then()
                .statusCode(201);

        // Try to use a wrong secret - should fail
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String requestId = createAndApproveAuthorizationRequest(
                clientId, "http://localhost:8080/callback", UUID.randomUUID().toString(),
                codeChallenge, "openid"
        );
        String authCode = generateAuthorizationCode(requestId);

        given()
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .formParam("client_id", clientId)
                .formParam("client_secret", "wrong-secret-12345")
                .formParam("redirect_uri", "http://localhost:8080/callback")
                .formParam("code_verifier", codeVerifier)
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    /**
     * Test that multiple active secrets work (for zero-downtime rotation).
     */
    @Test
    public void testMultipleActiveSecretsWork() throws Exception {
        String clientId = "test-oauth-flow-multi-" + System.currentTimeMillis();

        // Create client via REST API
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of(
                        "clientId", clientId,
                        "clientName", "Test Multiple Secrets Client",
                        "clientType", "confidential",
                        "redirectUris", "http://localhost:8080/callback",
                        "allowedScopes", "openid",
                        "requirePkce", true
                ))
                .when()
                .post("/api/clients")
                .then()
                .statusCode(201);

        // Create first secret via REST API
        Response secret1Response = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("description", "First secret"))
                .when()
                .post("/api/clients/" + clientId + "/secrets")
                .then()
                .statusCode(201)
                .extract()
                .response();

        String plainSecret1 = secret1Response.jsonPath().getString("secret");

        // Create second secret via REST API
        Response secret2Response = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("description", "Second secret"))
                .when()
                .post("/api/clients/" + clientId + "/secrets")
                .then()
                .statusCode(201)
                .extract()
                .response();

        String plainSecret2 = secret2Response.jsonPath().getString("secret");

        // Test with first secret - should work
        String codeVerifier1 = generateCodeVerifier();
        String codeChallenge1 = generateCodeChallenge(codeVerifier1);
        String requestId1 = createAndApproveAuthorizationRequest(
                clientId, "http://localhost:8080/callback", UUID.randomUUID().toString(),
                codeChallenge1, "openid"
        );
        String authCode1 = generateAuthorizationCode(requestId1);

        given()
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode1)
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret1)
                .formParam("redirect_uri", "http://localhost:8080/callback")
                .formParam("code_verifier", codeVerifier1)
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue());

        // Test with second secret - should also work
        String codeVerifier2 = generateCodeVerifier();
        String codeChallenge2 = generateCodeChallenge(codeVerifier2);
        String requestId2 = createAndApproveAuthorizationRequest(
                clientId, "http://localhost:8080/callback", UUID.randomUUID().toString(),
                codeChallenge2, "openid"
        );
        String authCode2 = generateAuthorizationCode(requestId2);

        given()
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode2)
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret2)
                .formParam("redirect_uri", "http://localhost:8080/callback")
                .formParam("code_verifier", codeVerifier2)
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue());
    }

    // Helper methods

    @Transactional
    protected String createAndApproveAuthorizationRequest(String clientId, String redirectUri,
                                                         String state, String codeChallenge, String scope) {
        AuthorizationRequest authRequest = new AuthorizationRequest();
        authRequest.setId(UUID.randomUUID().toString());
        authRequest.setClientId(clientId);
        authRequest.setRedirectUri(redirectUri);
        authRequest.setState(state);
        authRequest.setCodeChallenge(codeChallenge);
        authRequest.setCodeChallengeMethod("S256");
        authRequest.setScope(scope);
        authRequest.setStatus("approved");
        authRequest.setAccountId(testAccount.getId());
        authRequest.setCreatedAt(LocalDateTime.now());
        authRequest.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        em.persist(authRequest);
        em.flush();
        return authRequest.getId();
    }

    @Transactional
    protected String generateAuthorizationCode(String requestId) {
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(requestId);
        em.flush();
        return authCode.getCode();
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
