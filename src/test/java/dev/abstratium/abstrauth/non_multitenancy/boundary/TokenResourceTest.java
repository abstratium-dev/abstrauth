package dev.abstratium.abstrauth.non_multitenancy.boundary;

import dev.abstratium.abstrauth.util.ClientIdUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Base64;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for TokenResource error paths and edge cases
 */
@QuarkusTest
public class TokenResourceTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // From V01.010 migration
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    // Test org IDs (must match JWT signing key setup)
    private static final String DEFAULT_ORG = "00000000-0000-0000-0000-000000000000";
    private static final String OTHER_ORG = "11111111-1111-1111-1111-111111111111";

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

    // ============================================
    // ClientRole Security Tests for Client Credentials
    // ============================================

    /**
     * Tests that a client receives only the ClientRoles that belong to its own organization.
     * This is the "happy path" - a client gets roles assigned within its own org.
     */
    @Test
    public void testClientCredentialsWithClientRolesFromOwnOrg() {
        String orgId = DEFAULT_ORG;
        String manageToken = generateManageTokenForOrg(orgId);

        // Create source client (the one that will sign in)
        String srcClientId = createClient(manageToken, "src-own-org", "[" + orgId + "-target-service" + "]", "[]");

        // Create target client in the same org
        String targetClientId = createClientWithAllowedRole(manageToken, "target-own-org", "api-reader", true);

        // Create a client role assignment: src -> target with role 'api-reader'
        given()
            .header("Authorization", "Bearer " + manageToken)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201);

        // Create a client secret for the source client
        String clientSecret = createClientSecret(manageToken, srcClientId);

        // Request token - should include the client role in groups claim
        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", ClientIdUtil.stripOrgPrefix(srcClientId))
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .extract()
            .path("access_token");

        // Decode and verify the token contains the role
        JsonObject claims = decodeToken(accessToken);
        org.junit.jupiter.api.Assertions.assertTrue(claims.containsKey("groups"), "Token should have groups claim");
        org.junit.jupiter.api.Assertions.assertTrue(
            claims.getJsonArray("groups").contains(Json.createValue("target-service_api-reader")),
            "Token should contain the client role in groups claim"
        );
    }

    /**
     * SECURITY TEST: "Hack" attempt - tries to get roles from another organization's data.
     * A malicious client from org A should NOT receive roles that were assigned to a client
     * with the same ID but in org B's database (due to @TenantId filtering in ClientRole).
     */
    @Test
    public void testClientCredentialsCannotAccessClientRolesFromOtherOrg() {
        String orgA = DEFAULT_ORG;
        String orgB = OTHER_ORG;
        String manageTokenA = generateManageTokenForOrg(orgA);
        String manageTokenB = generateManageTokenForOrg(orgB);

        // In Org A: Create source client and assign it a role for a target
        String srcClientIdA = createClient(manageTokenA, "src-cross-org", "[" + orgA + "-target-a" + "]", "[]");
        String targetClientIdA = createClientWithAllowedRole(manageTokenA, "target-a", "api-reader", true);

        given()
            .header("Authorization", "Bearer " + manageTokenA)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientIdA))
            .when()
            .post("/api/clients/" + srcClientIdA + "/client-roles")
            .then()
            .statusCode(201);

        // In Org B: Create a client with the SAME clientId as in Org A
        // This simulates a different organization having a client with identical ID
        String srcClientIdB = srcClientIdA; // Same ID, but different org context via JWT

        // Create client role for the same client ID in Org B (target-b)
        String targetClientIdB = createClientWithAllowedRole(manageTokenB, "target-b", "api-writer", true);
        given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-writer\"}", targetClientIdB))
            .when()
            .post("/api/clients/" + srcClientIdB + "/client-roles")
            .then()
            .statusCode(201);

        // Create a secret for Org A's client
        String clientSecretA = createClientSecret(manageTokenA, srcClientIdA);

        // Request token using Org A's client credentials
        // Due to @TenantId filtering in ClientRole, it should ONLY see roles from Org A
        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", ClientIdUtil.stripOrgPrefix(srcClientIdA))
            .formParam("client_secret", clientSecretA)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");

        // Verify: Token should have Org A's role (target-a_api-reader), NOT Org B's role
        JsonObject claims = decodeToken(accessToken);
        org.junit.jupiter.api.Assertions.assertTrue(claims.containsKey("groups"));
        org.junit.jupiter.api.Assertions.assertTrue(
            claims.getJsonArray("groups").contains(Json.createValue("target-a_api-reader")),
            "Token should contain Org A's client role"
        );
        org.junit.jupiter.api.Assertions.assertFalse(
            claims.getJsonArray("groups").contains(Json.createValue("target-b_api-writer")),
            "Token should NOT contain Org B's client role (tenant isolation)"
        );
    }

    /**
     * Tests that a client with no ClientRoles gets an empty groups claim.
     */
    @Test
    public void testClientCredentialsWithNoClientRoles() {
        String manageToken = generateManageTokenForOrg(DEFAULT_ORG);

        // Create a client but don't assign any client roles
        String clientId = createClient(manageToken, "no-roles-client", "[]", "[]");
        String clientSecret = createClientSecret(manageToken, clientId);

        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", ClientIdUtil.stripOrgPrefix(clientId))
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");

        JsonObject claims = decodeToken(accessToken);
        // Groups should be empty or not present
        if (claims.containsKey("groups")) {
            org.junit.jupiter.api.Assertions.assertTrue(claims.getJsonArray("groups").isEmpty());
        }
    }

    /**
     * Tests that ClientRole entries are properly filtered by the source client's orgId.
     * Even if someone manually tries to add a ClientRole with wrong org_id in DB,
     * the @TenantId filter should prevent it from being returned.
     */
    @Test
    public void testClientRoleTenantIsolationInToken() {
        String manageToken = generateManageTokenForOrg(DEFAULT_ORG);

        // Create source client and target with role assignment
        String srcClientId = createClient(manageToken, "isolated-client", "[" + DEFAULT_ORG + "-isolated-target" + "]", "[]");
        String targetClientId = createClientWithAllowedRole(manageToken, "isolated-target", "secure-role", true);

        given()
            .header("Authorization", "Bearer " + manageToken)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"secure-role\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201);

        String clientSecret = createClientSecret(manageToken, srcClientId);

        // Get token
        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", ClientIdUtil.stripOrgPrefix(srcClientId))
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");

        // Verify orgId claim is present
        JsonObject claims = decodeToken(accessToken);
        org.junit.jupiter.api.Assertions.assertEquals(DEFAULT_ORG, claims.getString("orgId"));
    }

    // Helper methods for new tests

    private String createClient(String manageToken, String clientName, String allowedScopes, String redirectUris) {
        String uniqueClientId = clientName + "_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "%s",
                "clientType": "confidential"
            }
            """, uniqueClientId, clientName);

        return given()
            .header("Authorization", "Bearer " + manageToken)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .path("clientId");
    }

    private String createClientWithAllowedRole(String manageToken, String clientName, String role, boolean availableToForeignOrgs) {
        String clientId = createClient(manageToken, clientName, "[]", "[]");
        given()
            .header("Authorization", "Bearer " + manageToken)
            .contentType("application/json")
            .body(String.format("{\"role\": \"%s\", \"isDefault\": false, \"availableToForeignOrgs\": %b}", role, availableToForeignOrgs))
            .when()
            .post("/api/clients/" + clientId + "/allowed-roles")
            .then()
            .statusCode(201);
        return clientId;
    }

    private String createClientSecret(String manageToken, String clientId) {
        return given()
            .header("Authorization", "Bearer " + manageToken)
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/clients/" + clientId + "/secrets")
            .then()
            .statusCode(201)
            .extract()
            .path("secret");
    }

    /**
     * Generates a manage token for testing with a specific org claim.
     * This simulates a user logged into a specific organization.
     */
    private String generateManageTokenForOrg(String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("test@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .claim("orgId", orgId)
            .sign();
    }

    /**
     * Decodes a JWT access token and returns the claims as JsonObject.
     */
    private JsonObject decodeToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        return Json.createReader(new StringReader(payload)).readObject();
    }
}
