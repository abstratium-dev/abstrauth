package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify JWT tokens contain correct groups/roles based on account_roles table
 */
@QuarkusTest
public class TokenRolesTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    jakarta.transaction.UserTransaction userTransaction;

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String REDIRECT_URI = "http://localhost:8080/auth-callback";
    
    private String testUsername;
    private String testEmail;
    private String testPassword = "SecurePassword123";
    private String testAccountId;

    @BeforeEach
    public void setup() throws Exception {
        // Ensure test clients exist
        userTransaction.begin();
        ensureClientExists(CLIENT_ID);
        ensureClientExists("other_client");
        userTransaction.commit();
        
        userTransaction.begin();
        // Create unique test user
        long timestamp = System.nanoTime();
        testUsername = "rolestest_" + timestamp;
        testEmail = "rolestest_" + timestamp + "@example.com";
        
        Account account = accountService.createAccount(
            testEmail,
            "Roles Test User",
            testUsername,
            testPassword,
            AccountService.NATIVE
        );
        testAccountId = account.getId();
        userTransaction.commit();
    }
    
    private void ensureClientExists(String clientId) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", dev.abstratium.abstrauth.entity.OAuthClient.class);
        query.setParameter("clientId", clientId);
        if (query.getResultList().isEmpty()) {
            dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
            client.setClientId(clientId);
            client.setClientName("Test " + clientId);
            client.setClientType("confidential");
            client.setRedirectUris("[\"http://localhost:8080/callback\"]");
            client.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
            client.setRequirePkce(false);
            client.setClientSecretHash("$2a$10$dummyhash");
            em.persist(client);
        }
    }

    @Test
    public void testTokenContainsDefaultUserRoleWhenNoRolesAssigned() throws Exception {
        // Complete OAuth flow without assigning any roles
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify it contains default "user" role with client prefix
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"abstratium-abstrauth_user\""), "JWT should contain default 'abstratium-abstrauth_user' role");
        assertFalse(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should not contain 'admin' role");
    }

    @Test
    public void testTokenContainsAssignedRoles() throws Exception {
        // Assign admin role to the account in a committed transaction
        // Note: "user" role is automatically assigned, so we only add "admin"
        addRolesInTransaction(CLIENT_ID, "admin");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify it contains both roles with client prefix
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"abstratium-abstrauth_user\""), "JWT should contain 'abstratium-abstrauth_user' role");
        assertTrue(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should contain 'abstratium-abstrauth_admin' role");
    }

    @Test
    public void testTokenContainsOnlyRolesForSpecificClient() throws Exception {
        // Assign roles for this client
        addRolesInTransaction(CLIENT_ID, "admin");
        
        // Assign roles for a different client
        addRolesInTransaction("other_client", "superadmin");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify it contains only roles for the current client with full prefix
        assertTrue(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should contain 'abstratium-abstrauth_admin' role for this client");
        assertFalse(payload.contains("\"other_client_superadmin\""), "JWT should not contain roles from other clients");
    }

    @Test
    public void testTokenContainsMultipleRoles() throws Exception {
        // Assign multiple roles (user role is automatically assigned)
        addRolesInTransaction(CLIENT_ID, "admin", "editor", "viewer");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify it contains all roles with client prefix
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"abstratium-abstrauth_user\""), "JWT should contain 'abstratium-abstrauth_user' role");
        assertTrue(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should contain 'abstratium-abstrauth_admin' role");
        assertTrue(payload.contains("\"abstratium-abstrauth_editor\""), "JWT should contain 'abstratium-abstrauth_editor' role");
        assertTrue(payload.contains("\"abstratium-abstrauth_viewer\""), "JWT should contain 'abstratium-abstrauth_viewer' role");
    }

    @Test
    public void testTokenWithCustomRoleName() throws Exception {
        // Assign a custom role
        addRolesInTransaction(CLIENT_ID, "custom_role_123");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify custom role is present with client prefix
        assertTrue(payload.contains("\"abstratium-abstrauth_custom_role_123\""), "JWT should contain custom role with client prefix");
    }

    // Helper methods

    private String completeOAuthFlow() throws Exception {
        // Generate PKCE parameters
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // Initiate authorization request
        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test_state")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .redirects().follow(false)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.getHeader("Location"));

        // Submit login credentials
        given()
            .formParam("username", testUsername)
            .formParam("password", testPassword)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        // Approve consent
        Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String authCode = extractParameter(consentResponse.getHeader("Location"), "code");

        // Exchange authorization code for access token
        Response tokenResponse = given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", authCode)
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", codeVerifier)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .extract()
            .response();

        return tokenResponse.jsonPath().getString("access_token");
    }

    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] code = new byte[32];
        secureRandom.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String extractRequestId(String url) {
        Pattern queryPattern = Pattern.compile("request_id=([^&]+)");
        Matcher queryMatcher = queryPattern.matcher(url);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        
        Pattern pathPattern = Pattern.compile("/signin/([^/?]+)");
        Matcher pathMatcher = pathPattern.matcher(url);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        
        return null;
    }

    private String extractParameter(String url, String paramName) {
        Pattern pattern = Pattern.compile(paramName + "=([^&]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
        
        // Decode the payload (second part)
        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    void addRolesInTransaction(String clientId, String... roles) throws Exception {
        userTransaction.begin();
        for (String role : roles) {
            accountRoleService.addRole(testAccountId, clientId, role);
        }
        userTransaction.commit();
    }
}
