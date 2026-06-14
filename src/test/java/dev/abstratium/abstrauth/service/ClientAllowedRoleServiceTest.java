package dev.abstratium.abstrauth.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;

/**
 * Tests for ClientAllowedRoleService focusing on cross-tenant isolation
 * ("hacks" against the allowed-roles endpoints).
 *
 * These tests exercise the service's verifyClientOwnership check by making
 * authenticated HTTP requests with JWT tokens bearing different orgId claims.
 */
@QuarkusTest
public class ClientAllowedRoleServiceTest {

    private static final String DEFAULT_ORG = "00000000-0000-0000-0000-000000000000";
    private static final String OTHER_ORG = "11111111-1111-1111-1111-111111111111";

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    TestTransactionHelper transactionHelper;

    /**
     * Generate a token for a specific orgId (no subject, so @VerifyOrgMembership
     * interceptor treats it as unauthenticated and proceeds).
     */
    private String generateTokenForOrg(String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("test@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .claim("orgId", orgId)
            .sign();
    }

    private String generateOwnerTokenForOrg(String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("owner@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients", "abstratium-abstrauth_manage-accounts"))
            .claim("email", "owner@example.com")
            .claim("name", "Org Owner")
            .claim("orgId", orgId)
            .sign();
    }

    private String generateUserTokenForOrg(String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("user@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", "user@example.com")
            .claim("name", "User")
            .claim("orgId", orgId)
            .sign();
    }

    @Test
    public void testAddAllowedRoleWithCorrectOrg() {
        String token = generateTokenForOrg(DEFAULT_ORG);
        String uniqueClientId = "test_svc_add_role_" + System.currentTimeMillis();
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

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"role\": \"viewer\", \"isDefault\": true, \"availableToForeignOrgs\": true}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201)
            .body("clientId", equalTo(actualClientId))
            .body("role", equalTo("viewer"))
            .body("isDefault", equalTo(true))
            .body("availableToForeignOrgs", equalTo(true));

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserTokenForOrg(DEFAULT_ORG))
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(200)
            .body("role", hasItem("viewer"))
            .body("isDefault", hasItem(true))
            .body("availableToForeignOrgs", hasItem(true));
    }

    @Test
    public void testAddAllowedRoleWithWrongOrgReturns404() {
        String defaultToken = generateTokenForOrg(DEFAULT_ORG);
        String wrongToken = generateTokenForOrg(OTHER_ORG);
        String uniqueClientId = "test_svc_wrong_add_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Wrong Add",
                "clientType": "confidential"
            }
            """, uniqueClientId);

        String actualClientId = given()
            .header("Authorization", "Bearer " + defaultToken)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("clientId");

        // Attempt to add a role using a token for a different organisation
        given()
            .header("Authorization", "Bearer " + wrongToken)
            .contentType("application/json")
            .body("{\"role\": \"hacker\", \"isDefault\": true, \"availableToForeignOrgs\": true}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testAddAllowedRoleDuplicateReturns409() {
        String token = generateTokenForOrg(DEFAULT_ORG);
        String uniqueClientId = "test_svc_dup_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Dup",
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

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"role\": \"editor\", \"isDefault\": false, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"role\": \"editor\", \"isDefault\": false, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(409)
            .body("error", equalTo("Role already exists in allowlist"));
    }

    @Test
    public void testUpdateAllowedRoleWithWrongOrgReturns404() {
        String defaultToken = generateTokenForOrg(DEFAULT_ORG);
        String wrongToken = generateTokenForOrg(OTHER_ORG);
        String uniqueClientId = "test_svc_wrong_upd_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Wrong Update",
                "clientType": "confidential"
            }
            """, uniqueClientId);

        String actualClientId = given()
            .header("Authorization", "Bearer " + defaultToken)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("clientId");

        given()
            .header("Authorization", "Bearer " + defaultToken)
            .contentType("application/json")
            .body("{\"role\": \"manager\", \"isDefault\": false, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // Attempt to update using a token for a different organisation
        given()
            .header("Authorization", "Bearer " + wrongToken)
            .contentType("application/json")
            .body("{\"isDefault\": true, \"availableToForeignOrgs\": false}")
            .when()
            .put("/api/clients/" + actualClientId + "/allowed-roles/manager")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testUpdateNonExistentRoleReturns404() {
        String token = generateTokenForOrg(DEFAULT_ORG);
        String uniqueClientId = "test_svc_upd_missing_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Update Missing",
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

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"isDefault\": true, \"availableToForeignOrgs\": false}")
            .when()
            .put("/api/clients/" + actualClientId + "/allowed-roles/nonexistent")
            .then()
            .statusCode(404)
            .body("error", equalTo("Role not found in allowlist"));
    }

    @Test
    public void testRemoveAllowedRoleWithWrongOrgReturns404() {
        String defaultToken = generateTokenForOrg(DEFAULT_ORG);
        String wrongToken = generateTokenForOrg(OTHER_ORG);
        String uniqueClientId = "test_svc_wrong_rem_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Wrong Remove",
                "clientType": "confidential"
            }
            """, uniqueClientId);

        String actualClientId = given()
            .header("Authorization", "Bearer " + defaultToken)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("clientId");

        given()
            .header("Authorization", "Bearer " + defaultToken)
            .contentType("application/json")
            .body("{\"role\": \"admin\", \"isDefault\": false, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // Attempt to remove using a token for a different organisation
        given()
            .header("Authorization", "Bearer " + wrongToken)
            .when()
            .delete("/api/clients/" + actualClientId + "/allowed-roles/admin")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testRemoveNonExistentRoleReturns404() {
        String token = generateTokenForOrg(DEFAULT_ORG);
        String uniqueClientId = "test_svc_rem_missing_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Remove Missing",
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

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/" + actualClientId + "/allowed-roles/nonexistent")
            .then()
            .statusCode(404)
            .body("error", equalTo("Role not found in allowlist"));
    }

    /**
     * SECURITY REGRESSION TEST: Foreign orgs should NOT see roles that are not available_to_foreign_orgs.
     * This test verifies that a user from a foreign (subscribed) organization can only see roles
     * marked as availableToForeignOrgs=true, while the client owner can see all roles.
     */
    @Test
    public void testForeignOrgCanOnlySeeAvailableRoles() throws Exception {
        String ownerToken = generateTokenForOrg(DEFAULT_ORG);
        String uniqueClientId = "test_svc_foreign_roles_" + System.currentTimeMillis();

        // Create a public client in DEFAULT_ORG
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Foreign Roles Client",
                "clientType": "confidential",
                "publik": true,
                "autoSubscribe": false
            }
            """, uniqueClientId);

        String actualClientId = given()
            .header("Authorization", "Bearer " + ownerToken)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("clientId");

        // Add two roles: one available to foreign orgs, one not
        given()
            .header("Authorization", "Bearer " + ownerToken)
            .contentType("application/json")
            .body("{\"role\": \"admin\", \"isDefault\": false, \"availableToForeignOrgs\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        given()
            .header("Authorization", "Bearer " + ownerToken)
            .contentType("application/json")
            .body("{\"role\": \"user\", \"isDefault\": true, \"availableToForeignOrgs\": true}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // Owner org should see both roles (admin and user)
        given()
            .auth().oauth2(generateUserTokenForOrg(DEFAULT_ORG))
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(200)
            .body("role", hasItem("admin"))
            .body("role", hasItem("user"));

        // Create a foreign account and org (first account uses default org, so we create another)
        // First, create a temporary account to make sure we're not the first account
        transactionHelper.beginTransaction();
        accountService.createAccount(
                "temp_" + System.currentTimeMillis() + "@example.com",
                "Temp User",
                "temp_" + System.currentTimeMillis(),
                "Pass123!",
                AccountService.NATIVE,
                "Temp Org " + System.currentTimeMillis());
        transactionHelper.commitTransaction();

        // Now create the actual foreign account which will get its own org
        transactionHelper.beginTransaction();
        Account foreignAccount = accountService.createAccount(
                "foreign_test_" + System.currentTimeMillis() + "@example.com",
                "Foreign Test User",
                "foreign_test_" + System.currentTimeMillis(),
                "Pass123!",
                AccountService.NATIVE,
                "Foreign Test Org " + System.currentTimeMillis());
        transactionHelper.commitTransaction();
        String foreignOrgId = organisationService.listOrganisationsForAccount(foreignAccount.getId()).get(0).getId();

        // Create token for the foreign org owner
        String foreignOwnerToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(foreignAccount.getId())
            .upn(foreignAccount.getEmail())
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients", "abstratium-abstrauth_manage-accounts"))
            .claim("email", foreignAccount.getEmail())
            .claim("name", foreignAccount.getName())
            .claim("orgId", foreignOrgId)
            .sign();

        String foreignUserToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(foreignAccount.getId())
            .upn(foreignAccount.getEmail())
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", foreignAccount.getEmail())
            .claim("name", foreignAccount.getName())
            .claim("orgId", foreignOrgId)
            .sign();

        // Foreign org subscribes to the client
        given()
            .auth().oauth2(foreignOwnerToken)
            .contentType("application/json")
            .body("{\"clientId\": \"" + actualClientId + "\"}")
            .when()
            .post("/api/organisations/" + foreignOrgId + "/subscriptions")
            .then()
            .statusCode(201)
            .body("clientId", equalTo(actualClientId))
            .body("orgId", equalTo(foreignOrgId));

        // Foreign org should ONLY see "user" role, NOT "admin" role
        given()
            .auth().oauth2(foreignUserToken)
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(200)
            .body("role", hasItem("user"))
            .body("role.find { it == 'admin' }", equalTo(null)); // admin should NOT be visible
    }
}
