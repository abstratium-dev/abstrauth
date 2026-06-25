package dev.abstratium.abstrauth.non_multitenancy.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
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
public class NonMultitenancyTokenRolesTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    OrganisationService organisationService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // From V01.010 migration
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";
    
    private String testUsername;
    private String testEmail;
    private String testPassword = "SecurePassword123";
    private String testAccountId;
    private String testOrgId;

    @BeforeEach
    public void setup() throws Exception {
        // Reset tenant context and clean up any leftover test data
        dbResetHelper.resetDatabase();

        // Ensure test clients exist
        transactionHelper.beginTransaction();
        ensureClientExists(CLIENT_ID);
        ensureClientExists("other_client");
        transactionHelper.commitTransaction();
        
        transactionHelper.beginTransaction();
        // Create unique test user
        long timestamp = System.nanoTime();
        testUsername = "rolestest_" + timestamp;
        testEmail = "rolestest_" + timestamp + "@example.com";
        
        Account account = accountService.createAccount(
            testEmail,
            "Roles Test User",
            testUsername,
            testPassword,
            AccountService.NATIVE,
            "Test Org");
        testAccountId = account.getId();
        transactionHelper.commitTransaction();
        testOrgId = organisationService.listOrganisationsForAccount(testAccountId).get(0).getId();
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
            em.persist(client);
            
            // Create client secret in new table
            dev.abstratium.abstrauth.entity.ClientSecret secret = new dev.abstratium.abstrauth.entity.ClientSecret();
            secret.setClientId(clientId);
            secret.setSecretHash("$2a$10$dummyhash");
            secret.setDescription("Test secret");
            secret.setActive(true);
            em.persist(secret);
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
        assertTrue(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should contain 'admin' role since first account gets it automatically");
    }

    @Test
    public void testTokenContainsAssignedRoles() throws Exception {
        // Assign editor role to the account in a committed transaction
        // Note: "user" and "admin" roles are automatically assigned for first account
        addRolesInTransaction(CLIENT_ID, "editor");

        // Complete OAuth flow
        String accessToken = completeOAuthFlow();

        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);

        // Verify it contains auto-assigned and manually assigned roles with client prefix
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"abstratium-abstrauth_user\""), "JWT should contain 'abstratium-abstrauth_user' role");
        assertTrue(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should contain 'abstratium-abstrauth_admin' role");
        assertTrue(payload.contains("\"abstratium-abstrauth_editor\""), "JWT should contain 'abstratium-abstrauth_editor' role");
    }

    @Test
    public void testTokenContainsOnlyRolesForSpecificClient() throws Exception {
        // Assign roles for this client (admin already auto-assigned for first account)
        addRolesInTransaction(CLIENT_ID, "editor");

        // Assign roles for a different client
        addRolesInTransaction("other_client", "superadmin");

        // Complete OAuth flow
        String accessToken = completeOAuthFlow();

        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);

        // Verify it contains only roles for the current client with full prefix
        assertTrue(payload.contains("\"abstratium-abstrauth_admin\""), "JWT should contain auto-assigned 'abstratium-abstrauth_admin' role for this client");
        assertTrue(payload.contains("\"abstratium-abstrauth_editor\""), "JWT should contain 'abstratium-abstrauth_editor' role for this client");
        assertFalse(payload.contains("\"other_client_superadmin\""), "JWT should not contain roles from other clients");
    }

    @Test
    public void testTokenContainsMultipleRoles() throws Exception {
        // Assign multiple roles (user and admin are automatically assigned for first account)
        addRolesInTransaction(CLIENT_ID, "editor", "viewer");

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
    public void testTokenWithPrefixedClientIdStripsUuidPrefix() throws Exception {
        String prefixedClientId = "550e8400-e29b-41d4-a716-446655440000__myapp";
        String expectedGroup = "myapp_client-role";
        String testSecret = "test-secret-12345";
        
        // Create the prefixed client with a valid secret
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId(prefixedClientId);
        client.setClientName("Test Prefixed Client");
        client.setClientType("confidential");
        client.setRedirectUris("http://localhost:8080/callback");
        client.setAllowedScopes("api:read");
        em.persist(client);
        
        dev.abstratium.abstrauth.entity.ClientSecret secret = new dev.abstratium.abstrauth.entity.ClientSecret();
        secret.setClientId(prefixedClientId);
        secret.setSecretHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(testSecret));
        secret.setDescription("Test secret");
        secret.setActive(true);
        em.persist(secret);
        
        // Add client role to the prefixed client (client credentials uses client roles, not account roles)
        dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientRole clientRole = new dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientRole();
        clientRole.setRole("client-role");
        clientRole.setOrgId(testOrgId);
        clientRole.setSrcClientId(prefixedClientId);
        clientRole.setTargetClientId(prefixedClientId);
        em.persist(clientRole);
        
        transactionHelper.commitTransaction();
        
        // Use client_credentials grant (no user auth or subscription checks required)
        Response tokenResponse = given()
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", prefixedClientId)
            .formParam("client_secret", testSecret)
            .formParam("scope", "api:read")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .extract()
            .response();
        
        String accessToken = tokenResponse.jsonPath().getString("access_token");
        String payload = decodeJwtPayload(accessToken);
        
        // Verify the UUID prefix is stripped in the groups claim
        assertTrue(payload.contains("\"groups\""), "JWT should contain groups claim");
        assertTrue(payload.contains("\"" + expectedGroup + "\""), "JWT should contain stripped group name '" + expectedGroup + "'");
        assertFalse(payload.contains("\"" + prefixedClientId + "_client-role\""), "JWT should not contain the unprefixed group name");
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
            .formParam("client_secret", CLIENT_SECRET)
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
        transactionHelper.beginTransaction();
        for (String role : roles) {
            nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, clientId, role);
        }
        transactionHelper.commitTransaction();
    }
}
