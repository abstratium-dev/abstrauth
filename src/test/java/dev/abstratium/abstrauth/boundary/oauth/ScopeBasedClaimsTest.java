package dev.abstratium.abstrauth.boundary.oauth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
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
 * Tests RFC-compliant scope-based claim filtering in access tokens and ID tokens.
 * 
 * According to OpenID Connect Core 1.0 Section 5.4:
 * - 'profile' scope: Grants access to name and other profile claims
 * - 'email' scope: Grants access to email, email_verified
 * - 'openid' scope: Required for ID tokens
 * 
 * This test verifies that claims are only included when the corresponding scope is requested.
 */
@QuarkusTest
public class ScopeBasedClaimsTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD";
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";
    private static final String TEST_USERNAME = "scopetest_" + System.currentTimeMillis();
    private static final String TEST_EMAIL = "scopetest_" + System.currentTimeMillis() + "@example.com";
    private static final String TEST_PASSWORD = "SecurePassword123";
    private static final String TEST_NAME = "Scope Test User";

    @BeforeEach
    public void setup() {
        // Sign up a test user
        given()
            .formParam("email", TEST_EMAIL)
            .formParam("name", TEST_NAME)
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .when()
            .post("/api/signup")
            .then()
            .statusCode(anyOf(is(201), is(409)));
    }

    @Test
    public void testAccessTokenWithAllScopes_ContainsAllClaims() throws Exception {
        String accessToken = performOAuthFlow("openid profile email");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify all claims are present
        assertTrue(payload.contains("\"email\":"), "Access token should contain email claim with 'email' scope");
        assertTrue(payload.contains("\"email_verified\":"), "Access token should contain email_verified claim with 'email' scope");
        assertTrue(payload.contains("\"name\":"), "Access token should contain name claim with 'profile' scope");
        assertTrue(payload.contains("\"sub\":"), "Access token should always contain sub claim");
        assertTrue(payload.contains("\"groups\":"), "Access token should always contain groups claim");
        assertTrue(payload.contains(TEST_EMAIL), "Access token should contain the user's email");
        assertTrue(payload.contains(TEST_NAME), "Access token should contain the user's name");
    }

    @Test
    public void testAccessTokenWithOnlyOpenId_ExcludesProfileAndEmailClaims() throws Exception {
        String accessToken = performOAuthFlow("openid");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify profile and email claims are NOT present
        assertFalse(payload.contains("\"email\":"), "Access token should NOT contain email claim without 'email' scope");
        assertFalse(payload.contains("\"email_verified\":"), "Access token should NOT contain email_verified claim without 'email' scope");
        assertFalse(payload.contains("\"name\":"), "Access token should NOT contain name claim without 'profile' scope");
        
        // Verify mandatory claims are still present
        assertTrue(payload.contains("\"sub\":"), "Access token should always contain sub claim");
        assertTrue(payload.contains("\"groups\":"), "Access token should always contain groups claim");
        assertTrue(payload.contains("\"scope\":\"openid\""), "Access token should contain the requested scope");
    }

    @Test
    public void testAccessTokenWithProfileOnly_ContainsNameButNotEmail() throws Exception {
        String accessToken = performOAuthFlow("openid profile");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify profile claims are present
        assertTrue(payload.contains("\"name\":"), "Access token should contain name claim with 'profile' scope");
        assertTrue(payload.contains(TEST_NAME), "Access token should contain the user's name");
        
        // Verify email claims are NOT present
        assertFalse(payload.contains("\"email\":"), "Access token should NOT contain email claim without 'email' scope");
        assertFalse(payload.contains("\"email_verified\":"), "Access token should NOT contain email_verified claim without 'email' scope");
        
        // Verify mandatory claims are present
        assertTrue(payload.contains("\"sub\":"), "Access token should always contain sub claim");
        assertTrue(payload.contains("\"groups\":"), "Access token should always contain groups claim");
    }

    @Test
    public void testAccessTokenWithEmailOnly_ContainsEmailButNotName() throws Exception {
        String accessToken = performOAuthFlow("openid email");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(accessToken);
        
        // Verify email claims are present
        assertTrue(payload.contains("\"email\":"), "Access token should contain email claim with 'email' scope");
        assertTrue(payload.contains("\"email_verified\":"), "Access token should contain email_verified claim with 'email' scope");
        assertTrue(payload.contains(TEST_EMAIL), "Access token should contain the user's email");
        
        // Verify profile claims are NOT present
        assertFalse(payload.contains("\"name\":"), "Access token should NOT contain name claim without 'profile' scope");
        
        // Verify mandatory claims are present
        assertTrue(payload.contains("\"sub\":"), "Access token should always contain sub claim");
        assertTrue(payload.contains("\"groups\":"), "Access token should always contain groups claim");
    }

    @Test
    public void testIdTokenWithAllScopes_ContainsAllClaims() throws Exception {
        String idToken = performOAuthFlowAndGetIdToken("openid profile email");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(idToken);
        
        // Verify all claims are present
        assertTrue(payload.contains("\"email\":"), "ID token should contain email claim with 'email' scope");
        assertTrue(payload.contains("\"email_verified\":"), "ID token should contain email_verified claim with 'email' scope");
        assertTrue(payload.contains("\"name\":"), "ID token should contain name claim with 'profile' scope");
        assertTrue(payload.contains("\"sub\":"), "ID token should always contain sub claim");
        assertTrue(payload.contains("\"aud\":"), "ID token should always contain aud claim");
        assertTrue(payload.contains("\"iss\":"), "ID token should always contain iss claim");
        assertTrue(payload.contains(TEST_EMAIL), "ID token should contain the user's email");
        assertTrue(payload.contains(TEST_NAME), "ID token should contain the user's name");
    }

    @Test
    public void testIdTokenWithOnlyOpenId_ExcludesProfileAndEmailClaims() throws Exception {
        String idToken = performOAuthFlowAndGetIdToken("openid");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(idToken);
        
        // Verify profile and email claims are NOT present
        assertFalse(payload.contains("\"email\":"), "ID token should NOT contain email claim without 'email' scope");
        assertFalse(payload.contains("\"email_verified\":"), "ID token should NOT contain email_verified claim without 'email' scope");
        assertFalse(payload.contains("\"name\":"), "ID token should NOT contain name claim without 'profile' scope");
        
        // Verify mandatory claims are still present
        assertTrue(payload.contains("\"sub\":"), "ID token should always contain sub claim");
        assertTrue(payload.contains("\"aud\":"), "ID token should always contain aud claim");
        assertTrue(payload.contains("\"iss\":"), "ID token should always contain iss claim");
        assertTrue(payload.contains("\"groups\":"), "ID token should contain groups claim");
    }

    @Test
    public void testIdTokenWithProfileOnly_ContainsNameButNotEmail() throws Exception {
        String idToken = performOAuthFlowAndGetIdToken("openid profile");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(idToken);
        
        // Verify profile claims are present
        assertTrue(payload.contains("\"name\":"), "ID token should contain name claim with 'profile' scope");
        assertTrue(payload.contains(TEST_NAME), "ID token should contain the user's name");
        
        // Verify email claims are NOT present
        assertFalse(payload.contains("\"email\":"), "ID token should NOT contain email claim without 'email' scope");
        assertFalse(payload.contains("\"email_verified\":"), "ID token should NOT contain email_verified claim without 'email' scope");
        
        // Verify mandatory claims are present
        assertTrue(payload.contains("\"sub\":"), "ID token should always contain sub claim");
        assertTrue(payload.contains("\"aud\":"), "ID token should always contain aud claim");
    }

    @Test
    public void testIdTokenWithEmailOnly_ContainsEmailButNotName() throws Exception {
        String idToken = performOAuthFlowAndGetIdToken("openid email");
        
        // Decode JWT payload
        String payload = decodeJwtPayload(idToken);
        
        // Verify email claims are present
        assertTrue(payload.contains("\"email\":"), "ID token should contain email claim with 'email' scope");
        assertTrue(payload.contains("\"email_verified\":"), "ID token should contain email_verified claim with 'email' scope");
        assertTrue(payload.contains(TEST_EMAIL), "ID token should contain the user's email");
        
        // Verify profile claims are NOT present
        assertFalse(payload.contains("\"name\":"), "ID token should NOT contain name claim without 'profile' scope");
        
        // Verify mandatory claims are present
        assertTrue(payload.contains("\"sub\":"), "ID token should always contain sub claim");
        assertTrue(payload.contains("\"aud\":"), "ID token should always contain aud claim");
    }

    @Test
    public void testNoIdTokenWithoutOpenIdScope() throws Exception {
        // Request without 'openid' scope should not generate an ID token
        Response tokenResponse = performOAuthFlowAndGetTokenResponse("profile email");
        
        // Verify no ID token is returned
        String idToken = tokenResponse.jsonPath().getString("id_token");
        assertNull(idToken, "ID token should NOT be generated without 'openid' scope");
        
        // Verify access token is still generated
        String accessToken = tokenResponse.jsonPath().getString("access_token");
        assertNotNull(accessToken, "Access token should still be generated without 'openid' scope");
    }

    // Helper methods

    /**
     * Performs complete OAuth flow and returns the access token
     */
    private String performOAuthFlow(String scope) throws Exception {
        Response tokenResponse = performOAuthFlowAndGetTokenResponse(scope);
        return tokenResponse.jsonPath().getString("access_token");
    }

    /**
     * Performs complete OAuth flow and returns the ID token
     */
    private String performOAuthFlowAndGetIdToken(String scope) throws Exception {
        Response tokenResponse = performOAuthFlowAndGetTokenResponse(scope);
        String idToken = tokenResponse.jsonPath().getString("id_token");
        assertNotNull(idToken, "ID token should be present when 'openid' scope is requested");
        return idToken;
    }

    /**
     * Performs complete OAuth flow and returns the token response
     */
    private Response performOAuthFlowAndGetTokenResponse(String scope) throws Exception {
        // Generate PKCE parameters
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // Initiate authorization request
        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", scope)
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
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
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

        // Exchange authorization code for tokens
        return given()
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
            .body("token_type", equalTo("Bearer"))
            .extract()
            .response();
    }

    /**
     * Decodes the JWT payload (middle part) and returns it as a string
     */
    private String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
        
        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        return new String(decodedBytes, StandardCharsets.UTF_8);
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
}
