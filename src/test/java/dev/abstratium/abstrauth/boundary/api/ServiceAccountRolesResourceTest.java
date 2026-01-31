package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.ServiceAccountRoleService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@QuarkusTest
public class ServiceAccountRolesResourceTest {

    @Inject
    EntityManager em;

    @Inject
    ServiceAccountRoleService roleService;

    private static final String TEST_CLIENT_PREFIX = "test-roles-client-";
    private String testClientId;
    private String testToken;

    @BeforeEach
    public void setup() {
        cleanupTestData();
        
        // Create test client
        testClientId = TEST_CLIENT_PREFIX + System.currentTimeMillis();
        createTestClient(testClientId);

        // Create admin client and get token
        testToken = createAdminClientAndGetToken();
    }
    
    @Transactional
    protected void cleanupTestData() {
        // Clean up any existing test data
        em.createQuery("DELETE FROM ServiceAccountRole WHERE clientId LIKE :prefix")
                .setParameter("prefix", TEST_CLIENT_PREFIX + "%")
                .executeUpdate();
        em.createQuery("DELETE FROM ServiceAccountRole WHERE clientId LIKE 'test-admin-%'")
                .executeUpdate();
        em.createQuery("DELETE FROM ServiceAccountRole WHERE clientId = 'abstratium-abstrauth'")
                .executeUpdate();
        em.createQuery("DELETE FROM ClientSecret WHERE clientId LIKE :prefix")
                .setParameter("prefix", TEST_CLIENT_PREFIX + "%")
                .executeUpdate();
        em.createQuery("DELETE FROM ClientSecret WHERE clientId LIKE 'test-admin-%'")
                .executeUpdate();
        em.createQuery("DELETE FROM OAuthClient WHERE clientId LIKE :prefix")
                .setParameter("prefix", TEST_CLIENT_PREFIX + "%")
                .executeUpdate();
        em.createQuery("DELETE FROM OAuthClient WHERE clientId LIKE 'test-admin-%'")
                .executeUpdate();
        
        // Reset bootstrap client allowed_scopes to null
        em.createQuery("UPDATE OAuthClient SET allowedScopes = NULL WHERE clientId = 'abstratium-abstrauth'")
                .executeUpdate();
        em.flush();
    }

    @Transactional
    protected void createTestClient(String clientId) {
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Test Client for Roles");
        client.setClientType("confidential");
        client.setRedirectUris("http://localhost:3000/callback");
        client.setAllowedScopes("api:read api:write");
        client.setRequirePkce(true);
        em.persist(client);

        // Create a secret for the client
        ClientSecret secret = new ClientSecret();
        secret.setClientId(clientId);
        secret.setSecretHash(new BCryptPasswordEncoder().encode("test-secret"));
        secret.setDescription("Test secret");
        secret.setActive(true);
        em.persist(secret);
        em.flush();
    }

    private String createAdminClientAndGetToken() {
        // Use the bootstrap client which already has manage-clients role
        // Just need to add allowed_scopes so it can use client_credentials flow
        setupBootstrapClientForClientCredentials();
        
        // Get token via client credentials using the bootstrap client
        String response = given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", "abstratium-abstrauth")
                .formParam("client_secret", "dev-secret-CHANGE-IN-PROD")
                .formParam("scope", "openid")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
        return response;
    }
    
    @Transactional
    protected void setupBootstrapClientForClientCredentials() {
        // Add allowed_scopes to bootstrap client so it can use client_credentials
        em.createQuery("UPDATE OAuthClient SET allowedScopes = 'openid profile email' WHERE clientId = 'abstratium-abstrauth'")
                .executeUpdate();
        
        // Add manage-clients role if not exists
        Set<String> existingRoles = roleService.findRolesByClientId("abstratium-abstrauth");
        if (!existingRoles.contains("manage-clients")) {
            roleService.addRole("abstratium-abstrauth", "manage-clients");
        }
        em.flush();
    }
    
    @Transactional
    protected void createServiceClientWithScopes(String clientId, String scopes) {
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Test Admin Client");
        client.setClientType("confidential");
        client.setRedirectUris("");
        client.setAllowedScopes(scopes);
        client.setRequirePkce(false);
        em.persist(client);

        ClientSecret secret = new ClientSecret();
        secret.setClientId(clientId);
        secret.setSecretHash(new BCryptPasswordEncoder().encode("test-secret"));
        secret.setDescription("Test secret");
        secret.setActive(true);
        em.persist(secret);
        em.flush();
    }

    @Test
    public void testListRolesEmpty() {
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .get("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(200)
                .body("clientId", equalTo(testClientId))
                .body("roles", hasSize(0));
    }

    @Test
    public void testAddRole() {
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(201)
                .body("clientId", equalTo(testClientId))
                .body("role", equalTo("api-reader"))
                .body("groupName", equalTo(testClientId + "_api-reader"));

        // Verify role was added
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .get("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(200)
                .body("roles", hasSize(1))
                .body("roles[0]", equalTo("api-reader"));
    }

    @Test
    public void testAddMultipleRoles() {
        // Add first role
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(201);

        // Add second role
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-writer\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(201);

        // Verify both roles exist (sorted alphabetically)
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .get("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(200)
                .body("roles", hasSize(2))
                .body("roles", containsInAnyOrder("api-reader", "api-writer"));
    }

    @Test
    public void testAddDuplicateRole() {
        // Add role first time
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(201);

        // Try to add same role again
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(400)
                .body("error", containsString("already exists"));
    }

    @Test
    public void testAddRoleInvalidName() {
        // Test uppercase (should fail)
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"API-READER\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(400);

        // Test special characters (should fail)
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api_reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(400);

        // Test spaces (should fail)
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(400);
    }

    @Test
    public void testAddRoleClientNotFound() {
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/non-existent-client/roles")
                .then()
                .statusCode(404)
                .body("error", containsString("not found"));
    }

    @Test
    public void testRemoveRole() {
        // Add a role first
        given()
                .header("Authorization", "Bearer " + testToken)
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(201);

        // Remove the role
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .delete("/api/clients/" + testClientId + "/roles/api-reader")
                .then()
                .statusCode(204);

        // Verify role was removed
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .get("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(200)
                .body("roles", hasSize(0));
    }

    @Test
    public void testRemoveNonExistentRole() {
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .delete("/api/clients/" + testClientId + "/roles/non-existent-role")
                .then()
                .statusCode(404)
                .body("error", containsString("not found"));
    }

    @Test
    public void testRemoveRoleClientNotFound() {
        given()
                .header("Authorization", "Bearer " + testToken)
                .when()
                .delete("/api/clients/non-existent-client/roles/api-reader")
                .then()
                .statusCode(404)
                .body("error", containsString("not found"));
    }

    @Test
    public void testUnauthorizedAccess() {
        // Try without token
        given()
                .when()
                .get("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(401);

        // Try to add role without token
        given()
                .contentType(ContentType.JSON)
                .body("{\"role\": \"api-reader\"}")
                .when()
                .post("/api/clients/" + testClientId + "/roles")
                .then()
                .statusCode(401);

        // Try to remove role without token
        given()
                .when()
                .delete("/api/clients/" + testClientId + "/roles/api-reader")
                .then()
                .statusCode(401);
    }
}
