package dev.abstratium.abstrauth.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;

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
            .body("{\"role\": \"viewer\", \"isDefault\": true}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201)
            .body("clientId", equalTo(actualClientId))
            .body("role", equalTo("viewer"))
            .body("isDefault", equalTo(true));

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserTokenForOrg(DEFAULT_ORG))
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(200)
            .body("role", hasItem("viewer"))
            .body("isDefault", hasItem(true));
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
            .body("{\"role\": \"hacker\", \"isDefault\": true}")
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
            .body("{\"role\": \"editor\", \"isDefault\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"role\": \"editor\", \"isDefault\": false}")
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
            .body("{\"role\": \"manager\", \"isDefault\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // Attempt to update using a token for a different organisation
        given()
            .header("Authorization", "Bearer " + wrongToken)
            .contentType("application/json")
            .body("{\"isDefault\": true}")
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
            .body("{\"isDefault\": true}")
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
            .body("{\"role\": \"admin\", \"isDefault\": false}")
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
}
