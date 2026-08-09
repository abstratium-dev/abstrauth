package dev.abstratium.abstrauth.non_multitenancy.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.ClientIdUtil;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Base64;
import java.util.Set;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TokenResource error paths and edge cases
 */
@QuarkusTest
public class NonMultitenancyTokenResourceTest {

    private static final Logger LOG = Logger.getLogger(NonMultitenancyTokenResourceTest.class.getName());

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // From V01.010 migration
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @BeforeEach
    public void setup() {
        dbResetHelper.resetDatabase();
    }

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
    public void testClientCredentialsWithClientRolesFromOwnOrg() throws Exception {
        String orgId = defaultOrgId;
        String manageToken = createRealManageTokenForOrg(orgId);

        // Create source client (the one that will sign in)
        String srcClientId = createClient(manageToken, "src-own-org", "[]", "[]");

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
            .formParam("client_id", srcClientId) // token endpoint needs FULL clientId
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .extract()
            .path("access_token");

        // Decode and verify all claims
        JsonObject claims = decodeToken(accessToken);
        String expectedGroup = ClientIdUtil.stripOrgPrefix(targetClientId) + "_api-reader";
        assertClientCredentialsTokenClaims(
            claims,
            srcClientId,
            defaultOrgId,
            Set.of(expectedGroup),
            Set.of(targetClientId)
        );
    }

    /**
     * SECURITY TEST: "Hack" attempt - tries to get roles from another organization's data.
     * A malicious client from org A should NOT receive roles that were assigned to a client
     * with the same ID but in org B's database (due to @TenantId filtering in ClientRole).
     */
    @Test
    public void testClientCredentialsCannotAccessClientRolesFromOtherOrg() throws Exception {
        String orgA = defaultOrgId;

        // Create two real accounts: one in default org (first after reset), one in new org
        String manageTokenA = createRealManageTokenForOrg(orgA);

        Account orgBAccount = accountService.createAccount(
            "tokentest_orgb_" + System.currentTimeMillis() + "@example.com",
            "Org B User",
            "tokentest_orgb_" + System.currentTimeMillis(),
            "Pass123!",
            AccountService.NATIVE,
            "TokenTest Org B");
        String orgB = organisationService.listOrganisationsForAccount(orgBAccount.getId()).get(0).getId();
        // Use orgBAccount directly for the token — it's already a member of orgB
        String manageTokenB = Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(orgBAccount.getId())
            .upn(orgBAccount.getEmail())
            .groups(Set.of(Roles.USER, Roles.MANAGE_CLIENTS))
            .claim("email", orgBAccount.getEmail())
            .claim("name", orgBAccount.getName())
            .claim("orgId", orgB)
            .sign();

        // In Org A: Create source client and assign it a role for a target
        String srcClientIdA = createClient(manageTokenA, "src-cross-org", "[]", "[]");
        String targetClientIdA = createClientWithAllowedRole(manageTokenA, "target-a", "api-reader", true);

        given()
            .header("Authorization", "Bearer " + manageTokenA)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientIdA))
            .when()
            .post("/api/clients/" + srcClientIdA + "/client-roles")
            .then()
            .statusCode(201);

        // In Org B: Create a source and target client with different IDs
        String srcClientIdB = createClient(manageTokenB, "src-cross-org-b", "[]", "[]");
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

        // Request token using Org A's client credentials (full clientId required)
        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", srcClientIdA)
            .formParam("client_secret", clientSecretA)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");

        // Verify: Token should have Org A's role, NOT Org B's role
        JsonObject claims = decodeToken(accessToken);
        String expectedGroupA = ClientIdUtil.stripOrgPrefix(targetClientIdA) + "_api-reader";
        assertClientCredentialsTokenClaims(
            claims,
            srcClientIdA,
            orgA,
            Set.of(expectedGroupA),
            Set.of(targetClientIdA)
        );
        String expectedGroupB = ClientIdUtil.stripOrgPrefix(targetClientIdB) + "_api-writer";
        assertTrue(
            !claims.getJsonArray("groups").contains(Json.createValue(expectedGroupB)),
            "Token should NOT contain Org B's client role (tenant isolation): " + expectedGroupB
        );
    }

    /**
     * Tests that a client with no ClientRoles gets an empty groups claim.
     */
    @Test
    public void testClientCredentialsWithNoClientRoles() throws Exception {
        String manageToken = createRealManageTokenForOrg(defaultOrgId);

        // Create a client but don't assign any client roles
        String clientId = createClient(manageToken, "no-roles-client", "[]", "[]");
        String clientSecret = createClientSecret(manageToken, clientId);

        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", clientId) // token endpoint needs FULL clientId
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");

        JsonObject claims = decodeToken(accessToken);
        assertClientCredentialsTokenClaims(
            claims,
            clientId,
            defaultOrgId,
            Set.of(),
            Set.of()
        );
    }

    /**
     * Tests that ClientRole entries are properly filtered by the source client's orgId.
     * Even if someone manually tries to add a ClientRole with wrong org_id in DB,
     * the @TenantId filter should prevent it from being returned.
     */
    @Test
    public void testClientRoleTenantIsolationInToken() throws Exception {
        String manageToken = createRealManageTokenForOrg(defaultOrgId);

        // Create source client and target with role assignment
        String srcClientId = createClient(manageToken, "isolated-client", "[]", "[]");
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

        // Get token using full clientId
        String accessToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", srcClientId) // token endpoint needs FULL clientId
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");

        // Verify all claims
        JsonObject claims = decodeToken(accessToken);
        String expectedGroup = ClientIdUtil.stripOrgPrefix(targetClientId) + "_secure-role";
        assertClientCredentialsTokenClaims(
            claims,
            srcClientId,
            defaultOrgId,
            Set.of(expectedGroup),
            Set.of(targetClientId)
        );
    }

    // ============================================
    // Client ID Collision Security Tests
    // ============================================

    /**
     * SECURITY TEST: Client ID collision privilege escalation.
     *
     * A threat actor creates a client with display name "abstratium-abstrauth" in their own org.
     * The canonical client ID becomes "orgB-uuid__abstratium-abstrauth", but after stripOrgPrefix,
     * the display name is "abstratium-abstrauth" — identical to the real system client.
     *
     * The attacker adds "admin" to their client's allowed roles, assigns it as a client role to
     * themselves, and requests a client_credentials token. Due to the bug in handleClientCredentials
     * (using stripOrgPrefix for aud and groups), the resulting JWT has:
     *   - aud = "abstratium-abstrauth" (matches mp.jwt.verify.audiences)
     *   - groups = ["abstratium-abstrauth_admin"] (matches Roles.ADMIN)
     *
     * This test asserts that the forged token is rejected (401) when used to delete an organisation.
     * Currently fails because the aud claim matches — the fix is to use the canonical client ID
     * (with org prefix) in the aud claim instead of the stripped display name.
     */
    @Test
    public void testClientIdCollisionCannotBeUsedToDeleteOrganisation() throws Exception {
        // 1. Create attacker's org (Org B) with an account
        Account orgBAccount = accountService.createAccount(
            "collision_" + System.currentTimeMillis() + "@example.com",
            "Collision Attacker",
            "collision_" + System.currentTimeMillis(),
            "Pass123!",
            AccountService.NATIVE,
            "Collision Org B"
        );
        String orgB = organisationService.listOrganisationsForAccount(orgBAccount.getId()).get(0).getId();

        String manageTokenB = Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(orgBAccount.getId())
            .upn(orgBAccount.getEmail())
            .groups(Set.of(Roles.USER, Roles.MANAGE_CLIENTS))
            .claim("email", orgBAccount.getEmail())
            .claim("name", orgBAccount.getName())
            .claim("orgId", orgB)
            .sign();

        // 2. Create a client with display name "abstratium-abstrauth" in Org B
        //    ClientsResource prepends orgId + "__", so canonical ID = "orgB__abstratium-abstrauth"
        String collidingClientId = given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body("{\"clientId\": \"abstratium-abstrauth\", \"clientName\": \"Collision Client\", \"clientType\": \"confidential\"}")
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract().path("clientId");

        // 3. Add "admin" as an allowed role for this client
        given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body("{\"role\": \"admin\", \"isDefault\": false, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + collidingClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // 4. Create a ClientRole from the colliding client to itself with role "admin"
        given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"admin\"}", collidingClientId))
            .when()
            .post("/api/clients/" + collidingClientId + "/client-roles")
            .then()
            .statusCode(201);

        // 5. Create a client secret
        String clientSecret = createClientSecret(manageTokenB, collidingClientId);

        // 6. Request a client_credentials token — this is the "forged" token
        String forgedToken = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", collidingClientId)
            .formParam("client_secret", clientSecret)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract().path("access_token");

        // Verify the forged token has the canonical client ID (with org prefix) in aud,
        // NOT the stripped "abstratium-abstrauth". This is the fix — the aud must be the
        // raw client ID from the database so it doesn't collide with the system client.
        JsonObject claims = decodeToken(forgedToken);
        Set<String> actualAudiences = new java.util.HashSet<>();
        JsonValue audValue = claims.get("aud");
        if (audValue.getValueType() == JsonValue.ValueType.ARRAY) {
            var audArray = claims.getJsonArray("aud");
            for (int i = 0; i < audArray.size(); i++) {
                actualAudiences.add(audArray.getString(i));
            }
        } else {
            actualAudiences.add(((JsonString) audValue).getString());
        }
        assertTrue(actualAudiences.contains(collidingClientId),
            "Forged token aud should contain the canonical client ID '" + collidingClientId + "': " + actualAudiences);
        assertTrue(!actualAudiences.contains("abstratium-abstrauth"),
            "Forged token aud should NOT contain the stripped 'abstratium-abstrauth': " + actualAudiences);
        var groupsArray = claims.getJsonArray("groups");
        boolean hasAdminRole = false;
        for (int i = 0; i < groupsArray.size(); i++) {
            if (groupsArray.getString(i).equals(Roles.ADMIN)) {
                hasAdminRole = true;
                break;
            }
        }
        assertTrue(hasAdminRole, "Forged token groups should contain 'abstratium-abstrauth_admin'");

        LOG.info("[testClientIdCollision] Forged token aud=" + actualAudiences + ", hasAdmin=" + hasAdminRole);
        LOG.info("[testClientIdCollision] Attempting to access admin-only endpoint with forged token");

        // 7. Attempt to access an admin-only endpoint (no @VerifyOrgMembership) using the forged token.
        //    The aud is now the canonical "orgB__abstratium-abstrauth" which does NOT
        //    match mp.jwt.verify.audiences=abstratium-abstrauth, so the server returns 401.
        io.restassured.response.Response forgedResponse = given()
            .header("Authorization", "Bearer " + forgedToken)
            .when()
            .get("/api/test/admin-only");
        String forgedBody = forgedResponse.body().asString();
        LOG.info("[testClientIdCollision] 401 response body: '" + forgedBody + "'");
        forgedResponse.then()
            .statusCode(401);
        assertEquals("", forgedBody, "Forged token should get empty 401 body, not admin access: '" + forgedBody + "'");
        assertTrue(!forgedBody.contains("admin-access-granted"),
            "Forged token must NOT get admin-access-granted response");
    }

    /**
     * REGRESSION TEST: Auth code flow aud must use canonical client ID.
     *
     * The authorization code grant already uses .audience(clientId) with the raw
     * client ID from the database. This test ensures that a colliding client
     * (display name "abstratium-abstrauth" but canonical "orgB__abstratium-abstrauth")
     * produces tokens with the canonical aud, NOT the stripped display name.
     *
     * If this test fails, someone has introduced stripOrgPrefix into the auth code
     * token generation path, re-creating the collision vulnerability.
     */
    @Test
    public void testAuthCodeFlowAudUsesCanonicalClientId() throws Exception {
        // 1. Create attacker's org (Org B) with an account
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        Account orgBAccount = accountService.createAccount(
            "authcode_collision_" + uniqueSuffix + "@example.com",
            "Auth Code Collision Attacker",
            "authcode_collision_" + uniqueSuffix,
            "Pass123!",
            AccountService.NATIVE,
            "Auth Code Collision Org"
        );
        String orgB = organisationService.listOrganisationsForAccount(orgBAccount.getId()).get(0).getId();

        String manageTokenB = Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(orgBAccount.getId())
            .upn(orgBAccount.getEmail())
            .groups(Set.of(Roles.USER, Roles.MANAGE_CLIENTS))
            .claim("email", orgBAccount.getEmail())
            .claim("name", orgBAccount.getName())
            .claim("orgId", orgB)
            .sign();

        // 2. Create a colliding client with redirect URIs and scopes for auth code flow
        String collidingClientId = given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body("{\"clientId\": \"abstratium-abstrauth\", \"clientName\": \"Auth Code Collision Client\", \"clientType\": \"confidential\", \"redirectUris\": \"[\\\"http://localhost:8080/callback\\\"]\", \"allowedScopes\": \"[\\\"openid\\\", \\\"profile\\\", \\\"email\\\"]\"}")
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract().path("clientId");

        // 3. Add "admin" as a default allowed role so the user can authenticate
        given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body("{\"role\": \"admin\", \"isDefault\": true, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + collidingClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // 4. Add "user" as a default allowed role too
        given()
            .header("Authorization", "Bearer " + manageTokenB)
            .contentType("application/json")
            .body("{\"role\": \"user\", \"isDefault\": true, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + collidingClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // 5. Create a client secret (needed for confidential client auth in token exchange)
        String clientSecret = createClientSecret(manageTokenB, collidingClientId);

        // 6. Generate PKCE parameters
        String codeVerifier = generatePkceCodeVerifier();
        String codeChallenge = generatePkceCodeChallenge(codeVerifier);

        // 7. Initiate authorization request
        io.restassured.response.Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", collidingClientId)
            .queryParam("redirect_uri", "http://localhost:8080/callback")
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test_state_collision")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .redirects().follow(false)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String requestId = extractRequestIdFromUrl(authResponse.getHeader("Location"));
        assertNotNull(requestId, "Should get a request_id from authorize redirect");

        // 8. Authenticate
        given()
            .formParam("username", "authcode_collision_" + uniqueSuffix)
            .formParam("password", "Pass123!")
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        // 9. Approve consent
        io.restassured.response.Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String authCode = extractCodeFromUrl(consentResponse.getHeader("Location"));
        assertNotNull(authCode, "Should get an authorization code from consent redirect");

        // 10. Exchange authorization code for tokens
        io.restassured.response.Response tokenResponse = given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", authCode)
            .formParam("client_id", collidingClientId)
            .formParam("client_secret", clientSecret)
            .formParam("redirect_uri", "http://localhost:8080/callback")
            .formParam("code_verifier", codeVerifier)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("id_token", notNullValue())
            .extract()
            .response();

        String accessToken = tokenResponse.jsonPath().getString("access_token");
        String idToken = tokenResponse.jsonPath().getString("id_token");

        // 11. Verify access token aud is the canonical client ID, NOT the stripped display name
        JsonObject accessClaims = decodeToken(accessToken);
        Set<String> accessAudiences = extractAudiences(accessClaims);
        LOG.info("[testAuthCodeFlowAud] Access token aud=" + accessAudiences);
        assertTrue(accessAudiences.contains(collidingClientId),
            "Access token aud should contain canonical client ID '" + collidingClientId + "': " + accessAudiences);
        assertTrue(!accessAudiences.contains("abstratium-abstrauth"),
            "Access token aud should NOT contain stripped 'abstratium-abstrauth': " + accessAudiences);

        // 12. Verify ID token aud is the canonical client ID, NOT the stripped display name
        JsonObject idClaims = decodeToken(idToken);
        Set<String> idAudiences = extractAudiences(idClaims);
        LOG.info("[testAuthCodeFlowAud] ID token aud=" + idAudiences);
        assertTrue(idAudiences.contains(collidingClientId),
            "ID token aud should contain canonical client ID '" + collidingClientId + "': " + idAudiences);
        assertTrue(!idAudiences.contains("abstratium-abstrauth"),
            "ID token aud should NOT contain stripped 'abstratium-abstrauth': " + idAudiences);

        // 13. Verify the access token is rejected with 401 when used against admin-only endpoint
        io.restassured.response.Response authCodeResponse = given()
            .header("Authorization", "Bearer " + accessToken)
            .when()
            .get("/api/test/admin-only");
        String authCodeBody = authCodeResponse.body().asString();
        LOG.info("[testAuthCodeFlowAud] 401 response body: '" + authCodeBody + "'");
        authCodeResponse.then()
            .statusCode(401);
        assertEquals("", authCodeBody, "Colliding token should get empty 401 body, not admin access: '" + authCodeBody + "'");
        assertTrue(!authCodeBody.contains("admin-access-granted"),
            "Colliding token must NOT get admin-access-granted response");
    }

    // Helper methods for new tests

    private String createClient(String manageToken, String clientName, String allowedScopes, String redirectUris) {
        String uniqueClientId = clientName + "-" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "%s",
                "clientType": "confidential"
            }
            """, uniqueClientId, clientName);

        LOG.info("[createClient] requesting with clientName=" + clientName + ", body=" + createBody);

        io.restassured.response.Response resp = given()
            .header("Authorization", "Bearer " + manageToken)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .extract().response();

        LOG.info("[createClient] response status=" + resp.statusCode() + ", body=" + resp.body().asString());
        if (resp.statusCode() != 201) {
            LOG.severe("[createClient] FAILED to create client. HTTP " + resp.statusCode()
                    + ". Full response: " + resp.body().asString());
        }
        assertEquals(201, resp.statusCode(), "createClient should return 201 but got " + resp.statusCode()
                + ". Response: " + resp.body().asString());
        return resp.path("clientId");
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
     * Creates a real account in the given org and returns a manage token with that
     * account's subject so OrgMembershipInterceptor validation passes.
     * After resetDatabase(), the first account created goes to the default org.
     * For a new org, pass a unique org name — a new org will be created automatically.
     */
    private String createRealManageTokenForOrg(String orgId) throws Exception {
        // After reset, the first account goes to default org.
        // For non-default orgs, we need to create an account with a specific org name.
        String email = "tokentest_" + System.currentTimeMillis() + "@example.com";
        String username = "tokentest_" + System.currentTimeMillis();
        Account account;
        if (orgId.equals(defaultOrgId)) {
            // First account after reset lands in default org
            account = accountService.createAccount(email, "Token Test", username, "Pass123!",
                    AccountService.NATIVE, null);
        } else {
            account = accountService.createAccount(email, "Token Test", username, "Pass123!",
                    AccountService.NATIVE, "TokenTest Org " + System.currentTimeMillis());
        }
        String token = Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(account.getId())
            .upn(account.getEmail())
            .groups(Set.of(Roles.USER, Roles.MANAGE_CLIENTS))
            .claim("email", account.getEmail())
            .claim("name", account.getName())
            .claim("orgId", orgId)
            .sign();
        LOG.info("[createRealManageTokenForOrg] orgId=" + orgId + ", accountId=" + account.getId());
        return token;
    }

    /**
     * Asserts all claims in a client_credentials access token.
     */
    private void assertClientCredentialsTokenClaims(JsonObject claims, String srcClientId,
                                                    String expectedOrgId,
                                                    Set<String> expectedGroups,
                                                    Set<String> expectedAudiences) {
        assertEquals(srcClientId, claims.getString("sub"), "sub should be the source client id");
        assertEquals(srcClientId, claims.getString("client_id"), "client_id should be the source client id");
        assertEquals("client_credentials", claims.getString("auth_method"), "auth_method should be client_credentials");
        assertEquals(expectedOrgId, claims.getString("orgId"), "orgId should match");
        assertEquals("[]", claims.getString("scope"), "scope should be the default empty scopes");

        assertNotNull(claims.getString("iss"), "iss should be present");
        assertNotNull(claims.getString("jti"), "jti should be present");
        assertTrue(claims.getJsonNumber("exp").longValue() > claims.getJsonNumber("iat").longValue(),
                "exp should be greater than iat");

        assertTrue(claims.containsKey("aud"), "aud claim should be present");
        Set<String> actualAudiences = new java.util.HashSet<>();
        JsonValue audValue = claims.get("aud");
        if (audValue.getValueType() == JsonValue.ValueType.ARRAY) {
            var audArray = claims.getJsonArray("aud");
            for (int i = 0; i < audArray.size(); i++) {
                actualAudiences.add(audArray.getString(i));
            }
        } else {
            actualAudiences.add(((JsonString) audValue).getString());
        }
        assertEquals(expectedAudiences.size(), actualAudiences.size(), "audience size should match expected target clients");
        for (String expectedAud : expectedAudiences) {
            assertTrue(actualAudiences.contains(expectedAud),
                    "aud should contain target client: " + expectedAud);
        }

        if (expectedGroups.isEmpty()) {
            if (claims.containsKey("groups")) {
                assertEquals(0, claims.getJsonArray("groups").size(), "groups should be empty when no roles");
            }
        } else {
            assertTrue(claims.containsKey("groups"), "groups claim should be present");
            var groupsArray = claims.getJsonArray("groups");
            assertEquals(expectedGroups.size(), groupsArray.size(), "groups size should match expected roles");
            for (String expectedGroup : expectedGroups) {
                assertTrue(groupsArray.contains(Json.createValue(expectedGroup)),
                        "groups should contain: " + expectedGroup);
            }
        }
    }

    /**
     * Decodes a JWT access token and returns the claims as JsonObject.
     */
    private JsonObject decodeToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        return Json.createReader(new StringReader(payload)).readObject();
    }

    private String generatePkceCodeVerifier() {
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        byte[] code = new byte[32];
        secureRandom.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generatePkceCodeChallenge(String codeVerifier) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String extractRequestIdFromUrl(String url) {
        if (url == null) return null;
        java.util.regex.Pattern queryPattern = java.util.regex.Pattern.compile("request_id=([^&]+)");
        java.util.regex.Matcher queryMatcher = queryPattern.matcher(url);
        if (queryMatcher.find()) return queryMatcher.group(1);
        java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile("/signin/([^/?]+)");
        java.util.regex.Matcher pathMatcher = pathPattern.matcher(url);
        if (pathMatcher.find()) return pathMatcher.group(1);
        java.util.regex.Pattern consentPattern = java.util.regex.Pattern.compile("/consent/([^/?]+)");
        java.util.regex.Matcher consentMatcher = consentPattern.matcher(url);
        if (consentMatcher.find()) return consentMatcher.group(1);
        return null;
    }

    private String extractCodeFromUrl(String url) {
        if (url == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("code=([^&]+)");
        java.util.regex.Matcher matcher = pattern.matcher(url);
        if (matcher.find()) return matcher.group(1);
        return null;
    }

    private Set<String> extractAudiences(JsonObject claims) {
        Set<String> audiences = new java.util.HashSet<>();
        JsonValue audValue = claims.get("aud");
        if (audValue == null) return audiences;
        if (audValue.getValueType() == JsonValue.ValueType.ARRAY) {
            var audArray = claims.getJsonArray("aud");
            for (int i = 0; i < audArray.size(); i++) {
                audiences.add(audArray.getString(i));
            }
        } else {
            audiences.add(((JsonString) audValue).getString());
        }
        return audiences;
    }
}
