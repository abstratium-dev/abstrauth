package dev.abstratium.abstrauth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class GoogleOAuthFlowTest {

    private static WireMockServer wireMockServer;

    @BeforeAll
    public static void setupWireMock() {
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    public static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    public void testGoogleOAuthFlowNewUser() {
        // Step 1: Initiate OAuth authorization request
        Response authResponse = given()
                .queryParam("response_type", "code")
                .queryParam("client_id", "abstratium-abstrauth")
                .queryParam("redirect_uri", "http://localhost:4200/auth-callback")
                .queryParam("scope", "openid profile email")
                .queryParam("state", "client-state-123")
                .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .queryParam("code_challenge_method", "S256")
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorize");

        // Should redirect to signin page
        assertEquals(303, authResponse.statusCode());
        String location = authResponse.header("Location");
        assertNotNull(location, "Location header should not be null");
        assertTrue(location.contains("/signin/"), "Location should contain /signin/, but was: " + location);
        
        // Extract request ID from redirect
        int signinIndex = location.indexOf("/signin/");
        String requestId = location.substring(signinIndex + "/signin/".length());

        // Step 2: Initiate Google login
        Response googleInitResponse = given()
                .queryParam("request_id", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/federated/google");

        // Should redirect to Google (mocked)
        assertEquals(303, googleInitResponse.statusCode());
        String googleAuthUrl = googleInitResponse.header("Location");
        assertNotNull(googleAuthUrl, "Location header should not be null");
        assertTrue(googleAuthUrl.contains("accounts.google.com/o/oauth2/v2/auth"), "URL should contain Google auth endpoint");
        assertTrue(googleAuthUrl.contains("client_id="), "URL should contain client_id parameter");
        assertTrue(googleAuthUrl.contains("state=" + requestId), "URL should contain state parameter with requestId");

        // Step 3: Mock Google's token endpoint response
        stubFor(post(urlEqualTo("/oauth2/v4/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "ya29.mock_access_token",
                                    "expires_in": 3600,
                                    "token_type": "Bearer",
                                    "scope": "openid email profile",
                                    "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.mock"
                                }
                                """)));

        // Step 4: Mock Google's userinfo endpoint response
        stubFor(get(urlEqualTo("/oauth2/v1/userinfo"))
                .withHeader("Authorization", equalTo("Bearer ya29.mock_access_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "sub": "google-user-123456",
                                    "email": "testuser@gmail.com",
                                    "email_verified": true,
                                    "name": "Test User",
                                    "picture": "https://lh3.googleusercontent.com/a/default-user",
                                    "given_name": "Test",
                                    "family_name": "User"
                                }
                                """)));

        // Step 5: Simulate Google callback
        Response callbackResponse = given()
                .queryParam("code", "google-auth-code-123")
                .queryParam("state", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/callback/google");

        // Should redirect back to client with authorization code
        assertEquals(303, callbackResponse.statusCode());
        String callbackLocation = callbackResponse.header("Location");
        assertTrue(callbackLocation.startsWith("http://localhost:4200/auth-callback"));
        assertTrue(callbackLocation.contains("code="));
        assertTrue(callbackLocation.contains("state=client-state-123"));

        // Extract authorization code
        String authCode = extractQueryParam(callbackLocation, "code");
        assertNotNull(authCode);

        // Step 6: Exchange authorization code for access token
        Response tokenResponse = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .formParam("redirect_uri", "http://localhost:4200/auth-callback")
                .formParam("client_id", "abstratium-abstrauth")
                .formParam("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
                .when()
                .post("/oauth2/token");

        // Should return access token
        assertEquals(200, tokenResponse.statusCode());
        tokenResponse.then()
                .body("access_token", notNullValue())
                .body("token_type", is("Bearer"))
                .body("expires_in", is(3600));

        // Verify WireMock was called
        verify(postRequestedFor(urlEqualTo("/oauth2/v4/token")));
        verify(getRequestedFor(urlEqualTo("/oauth2/v1/userinfo")));
    }

    @Test
    public void testGoogleOAuthFlowExistingUser() {
        // Step 0: Create existing account with unique email
        String uniqueEmail = "existing_" + System.currentTimeMillis() + "@gmail.com";
        String uniqueUsername = "existinguser_" + System.currentTimeMillis();
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("email", uniqueEmail)
                .formParam("name", "Existing User")
                .formParam("username", uniqueUsername)
                .formParam("password", "password123")
                .when()
                .post("/api/signup")
                .then()
                .statusCode(201);

        // Step 1: Initiate OAuth authorization request
        Response authResponse = given()
                .queryParam("response_type", "code")
                .queryParam("client_id", "abstratium-abstrauth")
                .queryParam("redirect_uri", "http://localhost:4200/auth-callback")
                .queryParam("scope", "openid profile email")
                .queryParam("state", "client-state-456")
                .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .queryParam("code_challenge_method", "S256")
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorize");

        assertEquals(303, authResponse.statusCode());
        String location2 = authResponse.header("Location");
        int signinIndex2 = location2.indexOf("/signin/");
        String requestId = location2.substring(signinIndex2 + "/signin/".length());

        // Step 2: Initiate Google login
        given()
                .queryParam("request_id", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/federated/google");

        // Step 3: Mock Google responses for existing user
        stubFor(post(urlEqualTo("/oauth2/v4/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "ya29.mock_access_token_2",
                                    "expires_in": 3600,
                                    "token_type": "Bearer"
                                }
                                """)));

        stubFor(get(urlEqualTo("/oauth2/v1/userinfo"))
                .withHeader("Authorization", equalTo("Bearer ya29.mock_access_token_2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "sub": "google-user-789",
                                    "email": "%s",
                                    "email_verified": true,
                                    "name": "Existing User",
                                    "picture": "https://lh3.googleusercontent.com/a/existing"
                                }
                                """.formatted(uniqueEmail))));

        // Step 4: Simulate Google callback
        Response callbackResponse = given()
                .queryParam("code", "google-auth-code-456")
                .queryParam("state", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/callback/google");

        // Should successfully link Google to existing account
        assertEquals(303, callbackResponse.statusCode());
        String callbackLocation = callbackResponse.header("Location");
        assertTrue(callbackLocation.contains("code="));
    }

    @Test
    public void testGoogleOAuthFlowErrorHandling() {
        // Test error from Google
        Response errorResponse = given()
                .queryParam("error", "access_denied")
                .queryParam("state", "some-request-id")
                .when()
                .get("/oauth2/callback/google");

        assertEquals(400, errorResponse.statusCode());
        assertTrue(errorResponse.body().asString().contains("Google authentication failed"));
    }

    @Test
    public void testGoogleOAuthFlowMissingCode() {
        // Test missing authorization code
        Response errorResponse = given()
                .queryParam("state", "some-request-id")
                .when()
                .get("/oauth2/callback/google");

        assertEquals(400, errorResponse.statusCode());
        assertTrue(errorResponse.body().asString().contains("Missing authorization code"));
    }

    @Test
    public void testGoogleOAuthFlowInvalidState() {
        // Test invalid state parameter
        Response errorResponse = given()
                .queryParam("code", "some-code")
                .queryParam("state", "invalid-request-id")
                .when()
                .get("/oauth2/callback/google");

        assertEquals(400, errorResponse.statusCode());
        assertTrue(errorResponse.body().asString().contains("Invalid or expired authorization request"));
    }

    private String extractQueryParam(String url, String paramName) {
        String[] parts = url.split("\\?");
        if (parts.length < 2) return null;
        
        String[] params = parts[1].split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }
}
