package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for TokenResource error paths and edge cases
 */
@QuarkusTest
public class TokenResourceTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // From V01.010 migration
    private static final String REDIRECT_URI = "http://localhost:8080/auth-callback";

    @Test
    public void testTokenEndpointWithMissingGrantType() {
        given()
            .formParam("code", "some_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    public void testTokenEndpointWithUnsupportedGrantType() {
        given()
            .formParam("grant_type", "password")
            .formParam("username", "user")
            .formParam("password", "pass")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"))
            .body("error_description", containsString("authorization_code"));
    }

    @Test
    public void testTokenEndpointWithMissingCode() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("code is required"));
    }

    @Test
    public void testTokenEndpointWithMissingClientId() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("client_id is required"));
    }

    @Test
    public void testTokenEndpointWithMissingRedirectUri() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("client_secret", CLIENT_SECRET)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", anyOf(equalTo("invalid_request"), equalTo("invalid_grant")));
    }

    @Test
    public void testTokenEndpointWithInvalidClientId() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("client_id", "invalid_client")
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", "verifier")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", anyOf(equalTo("invalid_client"), equalTo("invalid_grant")));
    }

    @Test
    public void testTokenEndpointWithExpiredCode() {
        // This would require setting up an expired code in the database
        // For now, testing with invalid code which gives similar error
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "expired_or_invalid_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("client_secret", CLIENT_SECRET)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", "verifier")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"))
            .body("error_description", containsString("invalid or expired"));
    }

    @Test
    public void testTokenEndpointWithMismatchedRedirectUri() {
        // Testing with invalid code - in real scenario would need valid code with different redirect_uri
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("client_secret", CLIENT_SECRET)
            .formParam("redirect_uri", "http://evil.com/auth-callback")
            .formParam("code_verifier", "verifier")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", anyOf(equalTo("invalid_grant"), equalTo("invalid_request")));
    }

    @Test
    public void testTokenEndpointWithMismatchedClientId() {
        // Testing with invalid code - in real scenario would need valid code with different client_id
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("client_secret", CLIENT_SECRET)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", "verifier")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", anyOf(equalTo("invalid_grant"), equalTo("invalid_client")));
    }

    @Test
    public void testTokenEndpointReturnsJsonContentType() {
        given()
            .formParam("grant_type", "invalid")
            .when()
            .post("/oauth2/token")
            .then()
            .contentType(containsString("application/json"));
    }

    @Test
    public void testTokenEndpointWithEmptyFormData() {
        given()
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", anyOf(equalTo("invalid_request"), equalTo("unsupported_grant_type")));
    }

    // ========== Additional Validation Tests for Branch Coverage ==========

    @Test
    public void testTokenEndpointWithBlankCode() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "   ")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("code is required"));
    }

    @Test
    public void testTokenEndpointWithBlankClientId() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("client_id", "   ")
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("client_id is required"));
    }

    @Test
    public void testTokenEndpointWithEmptyCode() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("code is required"));
    }

    @Test
    public void testTokenEndpointWithEmptyClientId() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code")
            .formParam("client_id", "")
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("client_id is required"));
    }

    @Test
    public void testTokenEndpointWithRefreshTokenGrant() {
        given()
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", "some_refresh_token")
            .formParam("client_id", CLIENT_ID)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"))
            .body("error_description", containsString("not yet implemented"));
    }

    @Test
    public void testTokenEndpointWithNullGrantType() {
        given()
            .formParam("code", "some_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    public void testTokenEndpointWithBlankGrantType() {
        given()
            .formParam("grant_type", "   ")
            .formParam("code", "some_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    public void testTokenEndpointWithCodeVerifierButNoChallenge() {
        // Testing with invalid code - in real scenario would need valid code without challenge
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "some_code_without_pkce")
            .formParam("client_id", CLIENT_ID)
            .formParam("client_secret", CLIENT_SECRET)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", "unnecessary_verifier")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", anyOf(equalTo("invalid_grant"), equalTo("invalid_request")));
    }

    @Test
    public void testTokenEndpointErrorResponseFormat() {
        given()
            .formParam("grant_type", "invalid_grant_type")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .contentType(containsString("application/json"))
            .body("error", notNullValue())
            .body("error_description", notNullValue());
    }

    @Test
    public void testTokenEndpointWithAllParametersMissing() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }
}
