package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    private static final String CLIENT_ID = "abstrauth_admin_app";
    private static final String REDIRECT_URI = "http://localhost:8080/auth-callback";
    
    private String testUsername;
    private String testEmail;
    private String testPassword = "SecurePassword123";
    private String testAccountId;

    @BeforeEach
    @Transactional
    public void setup() {
        // Create unique test user
        long timestamp = System.nanoTime();
        testUsername = "rolestest_" + timestamp;
        testEmail = "rolestest_" + timestamp + "@example.com";
        
        Account account = accountService.createAccount(
            testEmail,
            "Roles Test User",
            testUsername,
            testPassword
        );
        testAccountId = account.getId();
    }

    @Test
    public void testTokenContainsDefaultUserRoleWhenNoRolesAssigned() throws Exception {
        // Complete OAuth flow without assigning any roles
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify it contains default "user" role
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"user\""), "JWT should contain default 'user' role");
        assertFalse(payload.contains("\"admin\""), "JWT should not contain 'admin' role");
    }

    @Test
    public void testTokenContainsAssignedRoles() throws Exception {
        // Assign roles to the account in a committed transaction
        addRolesInTransaction(CLIENT_ID, "user", "admin");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify it contains both roles
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"user\""), "JWT should contain 'user' role");
        assertTrue(payload.contains("\"admin\""), "JWT should contain 'admin' role");
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
        
        // Verify it contains only roles for the current client
        assertTrue(payload.contains("\"admin\""), "JWT should contain 'admin' role for this client");
        assertFalse(payload.contains("\"superadmin\""), "JWT should not contain roles from other clients");
    }

    @Test
    public void testTokenContainsMultipleRoles() throws Exception {
        // Assign multiple roles
        addRolesInTransaction(CLIENT_ID, "user", "admin", "editor", "viewer");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify all roles are present
        assertTrue(payload.contains("\"user\""), "JWT should contain 'user' role");
        assertTrue(payload.contains("\"admin\""), "JWT should contain 'admin' role");
        assertTrue(payload.contains("\"editor\""), "JWT should contain 'editor' role");
        assertTrue(payload.contains("\"viewer\""), "JWT should contain 'viewer' role");
    }

    @Test
    public void testTokenWithCustomRoleName() throws Exception {
        // Assign a custom role
        addRolesInTransaction(CLIENT_ID, "custom_role_123");
        
        // Complete OAuth flow
        String accessToken = completeOAuthFlow();
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify custom role is present
        assertTrue(payload.contains("\"custom_role_123\""), "JWT should contain custom role");
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

    @Transactional
    void addRolesInTransaction(String clientId, String... roles) {
        for (String role : roles) {
            accountRoleService.addRole(testAccountId, clientId, role);
        }
    }
}
