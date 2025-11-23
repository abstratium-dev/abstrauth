package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

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
 * AuthorizationFlowTest: Tests the authorization request/consent phase thoroughly
 */
@QuarkusTest
public class AuthorizationFlowTest {

    private static final String CLIENT_ID = "abstrauth_admin_app";
    private static final String REDIRECT_URI = "http://localhost:8080/auth-callback";
    private static final String TEST_USERNAME = "testuser_" + System.currentTimeMillis();
    private static final String TEST_EMAIL = "test_" + System.currentTimeMillis() + "@example.com";
    private static final String TEST_PASSWORD = "SecurePassword123";
    private static final String TEST_NAME = "Test User";

    @BeforeEach
    public void setup() {
        // sign a test user up
        given()
            .formParam("email", TEST_EMAIL)
            .formParam("name", TEST_NAME)
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .when()
            .post("/api/signup")
            .then()
            .statusCode(anyOf(is(201), is(409))); // 201 Created or 409 if already exists
    }

    @Test
    public void testCompleteAuthorizationCodeFlowWithPKCE() throws Exception {
        // Generate PKCE parameters
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // Step 1: Initiate authorization request
        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test_state_123")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .extract()
            .response();

        String loginHtml = authResponse.asString();
        assertTrue(loginHtml.contains("Login to Abstratium"), "Should show login form");

        // Extract request_id from the login form
        String requestId = extractRequestId(loginHtml);
        assertNotNull(requestId, "Request ID should be present in login form");

        // Step 2: Submit login credentials
        Response loginResponse = given()
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .extract()
            .response();

        String consentHtml = loginResponse.asString();
        assertTrue(consentHtml.contains("Authorize Application"), "Should show consent form");
        assertTrue(consentHtml.contains(TEST_NAME), "Should show user name");

        // Step 3: Approve consent
        Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303) // See Other redirect
            .header("Location", notNullValue())
            .extract()
            .response();

        String redirectLocation = consentResponse.getHeader("Location");
        assertTrue(redirectLocation.startsWith(REDIRECT_URI), "Should redirect to callback URI");
        assertTrue(redirectLocation.contains("code="), "Should contain authorization code");
        assertTrue(redirectLocation.contains("state=test_state_123"), "Should contain state parameter");

        // Extract authorization code
        String authCode = extractParameter(redirectLocation, "code");
        assertNotNull(authCode, "Authorization code should be present");
        assertFalse(authCode.isEmpty(), "Authorization code should not be empty");

        System.out.println("Authorization code: " + authCode);
        System.out.println("Code verifier for token exchange: " + codeVerifier);
    }

    @Test
    public void testAuthorizationWithoutPKCEFails() {
        // Attempt authorization without PKCE (should fail for public client)
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile")
            .queryParam("state", "test_state")
            .redirects().follow(false)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(303)
            .header("Location", containsString("error=invalid_request"))
            .header("Location", containsString("code_challenge"));
    }

    @Test
    public void testAuthorizationWithInvalidClientId() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", "invalid_client")
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("code_challenge", "test_challenge")
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("Invalid client_id"));
    }

    @Test
    public void testAuthorizationWithInvalidRedirectUri() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", "https://evil.com/auth-callback")
            .queryParam("code_challenge", "test_challenge")
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("Invalid redirect_uri"));
    }

    @Test
    public void testAuthenticationWithInvalidCredentials() throws Exception {
        // Generate PKCE and initiate authorization
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.asString());

        // Attempt login with wrong password
        given()
            .formParam("username", TEST_USERNAME)
            .formParam("password", "WrongPassword")
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(401)
            .body(containsString("Invalid username or password"));
    }

    @Test
    public void testUserDeniesConsent() throws Exception {
        // Generate PKCE and initiate authorization
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("state", "test_state")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.asString());

        // Login successfully
        given()
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        // Deny consent
        Response denyResponse = given()
            .formParam("consent", "deny")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String redirectLocation = denyResponse.getHeader("Location");
        assertTrue(redirectLocation.contains("error=access_denied"), "Should contain access_denied error");
        assertTrue(redirectLocation.contains("state=test_state"), "Should preserve state parameter");
    }


    // Helper methods

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

    private String extractRequestId(String html) {
        Pattern pattern = Pattern.compile("name='request_id'\\s+value='([^']+)'");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
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
