package dev.abstratium.abstrauth.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tests for ClientRoleService focusing on cross-tenant isolation
 * and role validation against target client catalogs.
 */
@QuarkusTest
public class ClientRoleServiceTest {

    private static final String OTHER_ORG = "11111111-1111-1111-1111-111111111111";

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    ClientRoleService clientRoleService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    public void resetDatabase() {
        dbResetHelper.resetDatabase();
    }

    /**
     * Generate a token for a specific orgId with manage-clients role.
     */
    private String generateManageTokenForOrg(String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .upn("test@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .claim("orgId", orgId)
            .sign();
    }

    /**
     * Generate a token for a specific orgId with only user role.
     */
    private String generateUserTokenForOrg(String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .upn("user@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", "user@example.com")
            .claim("name", "User")
            .claim("orgId", orgId)
            .sign();
    }

    private String createClientWithAllowedRole(String token, String clientIdPrefix, String role, boolean availableToForeignOrgs) {
        String uniqueClientId = clientIdPrefix + "-" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client",
                "clientType": "confidential"
            }
            """, uniqueClientId);

        String actualClientId = given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("clientId");

        // Add the role to the client's allowed roles catalog
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(String.format("{\"role\": \"%s\", \"defaultAssignment\": \"not_default\", \"availableToForeignOrgs\": %b}",
                    role, availableToForeignOrgs))
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        return actualClientId;
    }

    @Test
    public void testAddClientRoleWithCorrectOrg() {
        String token = generateManageTokenForOrg(defaultOrgId);

        // Create source client
        String srcClientId = createClientWithAllowedRole(token, "src-client", "caller-role", true);

        // Create target client with an allowed role
        String targetClientId = createClientWithAllowedRole(token, "target-client", "api-reader", true);

        // Add a client role assignment (src can call target with api-reader role)
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201)
            .body("targetClientId", equalTo(targetClientId))
            .body("role", equalTo("api-reader"));

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserTokenForOrg(defaultOrgId))
            .when()
            .get("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(200)
            .body("srcClientId", equalTo(srcClientId))
            .body("roles.targetClientId", hasItem(targetClientId))
            .body("roles.role", hasItem("api-reader"));
    }

    @Test
    public void testAddClientRoleWithWrongOrgReturns404() {
        String defaultToken = generateManageTokenForOrg(defaultOrgId);
        String wrongToken = generateManageTokenForOrg(OTHER_ORG);

        // Create source client in default org
        String srcClientId = createClientWithAllowedRole(defaultToken, "src-wrong", "caller-role", true);

        // Create target client in default org
        String targetClientId = createClientWithAllowedRole(defaultToken, "target-wrong", "api-reader", true);

        // Attempt to add a client role using token for different org
        given()
            .header("Authorization", "Bearer " + wrongToken)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(404)
            .body("error", equalTo("Source client not found"));
    }

    @Test
    public void testAddClientRoleNotInTargetCatalogReturns400() {
        String token = generateManageTokenForOrg(defaultOrgId);

        // Create source client
        String srcClientId = createClientWithAllowedRole(token, "src-not-in-catalog", "caller-role", true);

        // Create target client WITHOUT the role in its catalog
        String uniqueTargetId = "target-no-role-" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Target Without Role",
                "clientType": "confidential"
            }
            """, uniqueTargetId);

        String targetClientId = given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("clientId");

        // Try to assign a role that doesn't exist in target's catalog
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"nonexistent-role\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(400)
            .body("error", equalTo("Role 'nonexistent-role' is not in the target client's allowed roles catalog"));
    }

    @Test
    public void testAddDuplicateClientRoleReturns409() {
        String token = generateManageTokenForOrg(defaultOrgId);

        // Create source and target clients
        String srcClientId = createClientWithAllowedRole(token, "src-dup", "caller-role", true);
        String targetClientId = createClientWithAllowedRole(token, "target-dup", "api-writer", true);

        // Add first role assignment
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-writer\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201);

        // Try to add duplicate
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-writer\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(409)
            .body("error", equalTo("Role already assigned to this client for the target"));
    }

    @Test
    public void testRemoveClientRoleWithCorrectOrg() {
        String token = generateManageTokenForOrg(defaultOrgId);

        // Create source and target clients
        String srcClientId = createClientWithAllowedRole(token, "src-remove", "caller-role", true);
        String targetClientId = createClientWithAllowedRole(token, "target-remove", "api-reader", true);

        // Add role assignment
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201);

        // Verify it exists
        given()
            .header("Authorization", "Bearer " + generateUserTokenForOrg(defaultOrgId))
            .when()
            .get("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(200)
            .body("roles.role", hasItem("api-reader"));

        // Remove the role assignment
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete(String.format("/api/clients/%s/client-roles/%s/api-reader", srcClientId, targetClientId))
            .then()
            .statusCode(204);

        // Verify it's gone
        given()
            .header("Authorization", "Bearer " + generateUserTokenForOrg(defaultOrgId))
            .when()
            .get("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(200)
            .body("roles.role", not(hasItem("api-reader")));
    }

    @Test
    public void testRemoveClientRoleWithWrongOrgReturns404() {
        String defaultToken = generateManageTokenForOrg(defaultOrgId);
        String wrongToken = generateManageTokenForOrg(OTHER_ORG);

        // Create source and target clients
        String srcClientId = createClientWithAllowedRole(defaultToken, "src-rem-wrong", "caller-role", true);
        String targetClientId = createClientWithAllowedRole(defaultToken, "target-rem-wrong", "api-reader", true);

        // Add role assignment
        given()
            .header("Authorization", "Bearer " + defaultToken)
            .contentType("application/json")
            .body(String.format("{\"targetClientId\": \"%s\", \"role\": \"api-reader\"}", targetClientId))
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201);

        // Try to remove with wrong org token
        given()
            .header("Authorization", "Bearer " + wrongToken)
            .when()
            .delete(String.format("/api/clients/%s/client-roles/%s/api-reader", srcClientId, targetClientId))
            .then()
            .statusCode(404)
            .body("error", equalTo("Source client not found"));
    }

    @Test
    public void testRemoveNonExistentClientRoleReturns404() {
        String token = generateManageTokenForOrg(defaultOrgId);

        // Create source and target clients
        String srcClientId = createClientWithAllowedRole(token, "src-rem-missing", "caller-role", true);
        String targetClientId = createClientWithAllowedRole(token, "target-rem-missing", "api-reader", true);

        // Try to remove non-existent role
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete(String.format("/api/clients/%s/client-roles/%s/nonexistent-role", srcClientId, targetClientId))
            .then()
            .statusCode(404)
            .body("error", equalTo("Role not found for this client and target"));
    }

    @Test
    public void testListClientRolesRequiresUserRoleOrHigher() {
        String manageToken = generateManageTokenForOrg(defaultOrgId);
        String userToken = generateUserTokenForOrg(defaultOrgId);

        // Create clients
        String srcClientId = createClientWithAllowedRole(manageToken, "src-list", "caller-role", true);

        // User can list (read-only access)
        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .get("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(200)
            .body("srcClientId", equalTo(srcClientId));
    }

    /**
     * Test that a user can assign a role to abstratium-abstrauth (a public client owned by another org)
     * when their org has a subscription to it.
     */
    @Test
    public void testAddClientRoleForAbstratiumAbstrauthTargetWithSubscription() {
        String token = generateManageTokenForOrg(defaultOrgId);

        // Create source client
        String srcClientId = createClientWithAllowedRole(token, "src-abstrauth", "caller-role", true);

        // Assign role to abstratium-abstrauth (target client)
        // The default org has a subscription to abstratium-abstrauth seeded by migration
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"targetClientId\": \"abstratium-abstrauth\", \"role\": \"manage-accounts\"}")
            .when()
            .post("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(201)
            .body("targetClientId", equalTo("abstratium-abstrauth"))
            .body("role", equalTo("manage-accounts"));

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserTokenForOrg(defaultOrgId))
            .when()
            .get("/api/clients/" + srcClientId + "/client-roles")
            .then()
            .statusCode(200)
            .body("roles.targetClientId", hasItem("abstratium-abstrauth"))
            .body("roles.role", hasItem("manage-accounts"));
    }

    // ─────────────────────────────────────────────────────────
    // Service-level tests for the 3-arg addRole overload (assigningOrgId=null)
    // ─────────────────────────────────────────────────────────

    private String createClientDirectly(String clientId, String orgId) throws Exception {
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Direct " + clientId);
        client.setClientType("confidential");
        client.setRedirectUris("[]");
        client.setAllowedScopes("[]");
        client.setRequirePkce(false);
        client.setOrgId(orgId);
        em.persist(client);
        transactionHelper.commitTransaction();
        return clientId;
    }

    private void addAllowedRoleDirectly(String clientId, String role, boolean availableToForeignOrgs) throws Exception {
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.ClientAllowedRole allowedRole =
                new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        allowedRole.setClientId(clientId);
        allowedRole.setRole(role);
        allowedRole.setDefaultAssignment(dev.abstratium.abstrauth.entity.DefaultAssignment.NOT_DEFAULT);
        allowedRole.setAvailableToForeignOrgs(availableToForeignOrgs);
        em.persist(allowedRole);
        transactionHelper.commitTransaction();
    }

    /**
     * isRoleAllowed with null assigningOrgId must return true for a catalog role
     * that is NOT available to foreign orgs, because null means "owner" (skip
     * foreign-org validation). The 3-arg ClientRoleService.addRole overload
     * passes null, so this must not NPE.
     */
    @Test
    public void testIsRoleAllowed_nullAssigningOrgId_treatsAsOwner() throws Exception {
        long ts = System.currentTimeMillis();
        String clientId = "isroleallowed-null-" + ts;
        String role = "private-role";

        createClientDirectly(clientId, defaultOrgId);
        addAllowedRoleDirectly(clientId, role, false); // NOT available to foreign orgs

        assertTrue(clientAllowedRoleService.isRoleAllowed(clientId, role, null),
                "null assigningOrgId should be treated as owner — any catalog role is allowed");
    }

    /**
     * isRoleAllowed with null assigningOrgId must still return false for a role
     * that does not exist in the catalog at all.
     */
    @Test
    public void testIsRoleAllowed_nullAssigningOrgId_roleNotInCatalog_returnsFalse() throws Exception {
        long ts = System.currentTimeMillis();
        String clientId = "isroleallowed-nocat-" + ts;

        createClientDirectly(clientId, defaultOrgId);

        assertFalse(clientAllowedRoleService.isRoleAllowed(clientId, "nonexistent-role", null),
                "null assigningOrgId should still return false for a role not in the catalog");
    }

}
