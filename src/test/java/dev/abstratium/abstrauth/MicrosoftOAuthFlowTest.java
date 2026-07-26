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
public class MicrosoftOAuthFlowTest {

    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // From V01.010 migration
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
    public void testMicrosoftOAuthFlowNewUser() {
        // Step 1: Initiate OAuth authorization request
        Response authResponse = given()
                .queryParam("response_type", "code")
                .queryParam("client_id", "abstratium-abstrauth")
                .queryParam("redirect_uri", "http://localhost:8080/api/auth/callback")
                .queryParam("scope", "openid profile email")
                .queryParam("state", "client-state-123")
                .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .queryParam("code_challenge_method", "S256")
                .redirects().follow(false)
                .log().all()
                .when()
                .get("/oauth2/authorize")
                .then()
                .log().all()
                .statusCode(303)
                .extract().response();

        // Should redirect to signin page
        String location = authResponse.header("Location");
        assertNotNull(location, "Location header should not be null");
        assertTrue(location.contains("/signin/"), "Location should contain /signin/, but was: " + location);

        // Extract request ID from redirect
        int signinIndex = location.indexOf("/signin/");
        String requestId = location.substring(signinIndex + "/signin/".length());

        // Step 2: Initiate Microsoft login
        Response microsoftInitResponse = given()
                .queryParam("request_id", requestId)
                .redirects().follow(false)
                .log().all()
                .when()
                .get("/oauth2/federated/microsoft")
                .then()
                .log().all()
                .statusCode(303)
                .extract().response();

        // Should redirect to Microsoft (mocked)
        String microsoftAuthUrl = microsoftInitResponse.header("Location");
        assertNotNull(microsoftAuthUrl, "Location header should not be null");
        assertTrue(microsoftAuthUrl.contains("login.microsoftonline.com"), "URL should contain Microsoft auth endpoint");
        assertTrue(microsoftAuthUrl.contains("client_id="), "URL should contain client_id parameter");
        assertTrue(microsoftAuthUrl.contains("state=" + requestId), "URL should contain state parameter with requestId");

        // Step 3: Mock Microsoft's token endpoint response
        stubFor(post(urlEqualTo("/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "mock_microsoft_access_token",
                                    "expires_in": 3600,
                                    "token_type": "Bearer",
                                    "scope": "openid email profile User.Read",
                                    "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbF92ZXJpZmllZCI6dHJ1ZX0.mock"
                                }
                                """)));

        // Step 4: Mock Microsoft's Graph /me endpoint response
        stubFor(get(urlEqualTo("/me"))
                .withHeader("Authorization", equalTo("Bearer mock_microsoft_access_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "microsoft-user-123456",
                                    "displayName": "Test User",
                                    "mail": "testuser@outlook.com",
                                    "userPrincipalName": "testuser@outlook.com"
                                }
                                """)));

        // Step 5: Simulate Microsoft callback
        Response callbackResponse = given()
                .queryParam("code", "microsoft-auth-code-123")
                .queryParam("state", requestId)
                .redirects().follow(false)
                .log().all()
                .when()
                .get("/oauth2/callback/microsoft")
                .then()
                .log().all()
                .statusCode(303)
                .extract().response();

        // Should redirect back to client with authorization code
        String callbackLocation = callbackResponse.header("Location");
        assertTrue(callbackLocation.startsWith("http://localhost:8080/api/auth/callback"));
        assertTrue(callbackLocation.contains("code="));
        assertTrue(callbackLocation.contains("state=client-state-123"));

        // Extract authorization code
        String authCode = extractQueryParam(callbackLocation, "code");
        assertNotNull(authCode);

        // Step 6: Exchange authorization code for access token
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "authorization_code")
                .formParam("code", authCode)
                .formParam("redirect_uri", "http://localhost:8080/api/auth/callback")
                .formParam("client_id", "abstratium-abstrauth")
                .formParam("client_secret", CLIENT_SECRET)
                .formParam("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
                .log().all()
                .when()
                .post("/oauth2/token")
                .then()
                .log().all()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("token_type", is("Bearer"))
                .body("expires_in", is(900)); // Default from abstrauth.session.timeout.seconds

        // Verify WireMock was called
        verify(postRequestedFor(urlEqualTo("/oauth2/v2.0/token")));
        verify(getRequestedFor(urlEqualTo("/me")));
    }

    @Test
    public void testMicrosoftOAuthFlowExistingUser() {
        // Step 0: Create existing account with unique email
        String uniqueEmail = "existing_" + System.currentTimeMillis() + "@outlook.com";
        String uniqueUsername = "existinguser_" + System.currentTimeMillis();
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("email", uniqueEmail)
                .formParam("name", "Existing User")
                .formParam("username", uniqueUsername)
                .formParam("password", "password123")
                .formParam("organisationName", "Test Organisation")
                .when()
                .post("/api/signup")
                .then()
                .statusCode(201);

        // Step 1: Initiate OAuth authorization request
        Response authResponse = given()
                .queryParam("response_type", "code")
                .queryParam("client_id", "abstratium-abstrauth")
                .queryParam("redirect_uri", "http://localhost:8080/api/auth/callback")
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

        // Step 2: Initiate Microsoft login
        given()
                .queryParam("request_id", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/federated/microsoft");

        // Step 3: Mock Microsoft responses for existing user
        stubFor(post(urlEqualTo("/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "mock_microsoft_access_token_2",
                                    "expires_in": 3600,
                                    "token_type": "Bearer"
                                }
                                """)));

        stubFor(get(urlEqualTo("/me"))
                .withHeader("Authorization", equalTo("Bearer mock_microsoft_access_token_2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "microsoft-user-789",
                                    "displayName": "Existing User",
                                    "mail": "%s",
                                    "userPrincipalName": "%s"
                                }
                                """.formatted(uniqueEmail, uniqueEmail))));

        // Step 4: Simulate Microsoft callback
        Response callbackResponse = given()
                .queryParam("code", "microsoft-auth-code-456")
                .queryParam("state", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/callback/microsoft");

        // Should successfully link Microsoft to existing account
        assertEquals(303, callbackResponse.statusCode());
        String callbackLocation = callbackResponse.header("Location");
        assertTrue(callbackLocation.contains("code="));
    }

    @Test
    public void testMicrosoftOAuthFlowErrorHandling() {
        Response errorResponse = given()
                .queryParam("error", "access_denied")
                .queryParam("state", "some-request-id")
                .when()
                .get("/oauth2/callback/microsoft");

        assertEquals(400, errorResponse.statusCode());
        assertTrue(errorResponse.body().asString().contains("Microsoft authentication failed"));
    }

    @Test
    public void testMicrosoftOAuthFlowMissingCode() {
        Response errorResponse = given()
                .queryParam("state", "some-request-id")
                .when()
                .get("/oauth2/callback/microsoft");

        assertEquals(400, errorResponse.statusCode());
        assertTrue(errorResponse.body().asString().contains("Missing authorization code"));
    }

    @Test
    public void testMicrosoftOAuthFlowInvalidState() {
        Response errorResponse = given()
                .queryParam("code", "some-code")
                .queryParam("state", "invalid-request-id")
                .when()
                .get("/oauth2/callback/microsoft");

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
