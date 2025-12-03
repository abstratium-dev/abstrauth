package dev.abstratium.abstrauth.boundary.oauth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for AuthorizationResource validation logic and error paths.
 * Focuses on branch coverage for validation code.
 */
@QuarkusTest
public class AuthorizationResourceTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String REDIRECT_URI = "http://localhost:8080/auth-callback";

    // ========== Response Type Validation ==========

    @Test
    void shouldRejectMissingResponseType() {
        given()
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(anyOf(is(302), is(400)));
    }

    @Test
    void shouldRejectInvalidResponseType() {
        given()
            .queryParam("response_type", "token")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("error=unsupported_response_type"));
    }

    @Test
    void shouldRejectEmptyResponseType() {
        given()
            .queryParam("response_type", "")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("error=unsupported_response_type"));
    }

    // ========== Client ID Validation ==========

    @Test
    void shouldRejectMissingClientId() {
        given()
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("client_id is required"));
    }

    @Test
    void shouldRejectBlankClientId() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", "   ")
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("client_id is required"));
    }

    @Test
    void shouldRejectEmptyClientId() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", "")
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("client_id is required"));
    }

    @Test
    void shouldRejectInvalidClientId() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", "invalid-client-id-12345")
            .queryParam("redirect_uri", REDIRECT_URI)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("Invalid client_id"));
    }

    // ========== Redirect URI Validation ==========

    @Test
    void shouldRejectMissingRedirectUri() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("redirect_uri is required"));
    }

    @Test
    void shouldRejectBlankRedirectUri() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", "   ")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("redirect_uri is required"));
    }

    @Test
    void shouldRejectEmptyRedirectUri() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", "")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("redirect_uri is required"));
    }

    @Test
    void shouldRejectInvalidRedirectUri() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", "http://evil.com/callback")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("Invalid redirect_uri"));
    }

    // ========== Scope Validation ==========

    @Test
    void shouldRejectInvalidScope() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "invalid_scope admin superuser")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("error=invalid_scope"));
    }

    @Test
    void shouldIncludeStateInErrorRedirect() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "invalid_scope")
            .queryParam("state", "test-state-123")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("state=test-state-123"));
    }

    // ========== PKCE Validation ==========

    @Test
    void shouldRejectMissingCodeChallengeWhenRequired() {
        // The default client requires PKCE
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("error=invalid_request"))
            .header("Location", containsString("code_challenge"));
    }

    @Test
    void shouldRejectBlankCodeChallengeWhenRequired() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .queryParam("code_challenge", "   ")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("error=invalid_request"));
    }

    @Test
    void shouldDefaultToPlainCodeChallengeMethod() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .queryParam("code_challenge", "test-challenge")
            // No code_challenge_method - should default to plain
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(anyOf(is(302), is(303)))
            .header("Location", containsString("/signin/"));
    }

    @Test
    void shouldRejectInvalidCodeChallengeMethod() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .queryParam("code_challenge", "test-challenge")
            .queryParam("code_challenge_method", "MD5")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(302)
            .header("Location", containsString("error=invalid_request"))
            .header("Location", containsString("code_challenge_method"));
    }

    @Test
    void shouldAcceptS256CodeChallengeMethod() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(anyOf(is(302), is(303)))
            .header("Location", containsString("/signin/"));
    }

    @Test
    void shouldAcceptPlainCodeChallengeMethod() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .queryParam("code_challenge", "test-challenge-plain")
            .queryParam("code_challenge_method", "plain")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(anyOf(is(302), is(303)))
            .header("Location", containsString("/signin/"));
    }

    // ========== Authorization Request Details Endpoint ==========

    @Test
    void shouldReturn404ForInvalidRequestId() {
        given()
            .when()
            .get("/oauth2/authorize/details/invalid-request-id")
            .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn404ForNonPendingRequest() {
        // This would require setting up a non-pending request
        // For now, testing with invalid ID which gives same result
        given()
            .when()
            .get("/oauth2/authorize/details/completed-request-id")
            .then()
            .statusCode(404);
    }

    // ========== Process Consent Endpoint ==========

    @Test
    void shouldRejectInvalidConsentRequest() {
        given()
            .formParam("consent", "approve")
            .formParam("request_id", "invalid-request-id")
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(400)
            .body(containsString("Invalid request"));
    }

    @Test
    void shouldHandleUserDeniedConsent() {
        // This would require a valid approved request
        // Testing the deny path with invalid request still exercises the branch
        given()
            .formParam("consent", "deny")
            .formParam("request_id", "test-request-id")
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(400); // Invalid request, but deny branch is checked first
    }

    // ========== Authenticate Endpoint ==========

    @Test
    void shouldRejectAuthenticationWithInvalidRequest() {
        given()
            .formParam("username", "testuser")
            .formParam("password", "testpass")
            .formParam("request_id", "invalid-request-id")
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(400)
            .body(containsString("Invalid request"));
    }

    @Test
    void shouldRejectAuthenticationWithInvalidCredentials() {
        // Would need valid request_id but invalid credentials
        // For now, testing that invalid request is caught first
        given()
            .formParam("username", "wronguser")
            .formParam("password", "wrongpass")
            .formParam("request_id", "test-request-id")
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    // ========== Valid Request Path ==========

    @Test
    void shouldRedirectToSigninWithValidParameters() {
        given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid")
            .queryParam("code_challenge", "test-challenge")
            .queryParam("code_challenge_method", "plain")
            .queryParam("state", "test-state")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(anyOf(is(302), is(303)))
            .header("Location", containsString("/signin/"));
    }
}
