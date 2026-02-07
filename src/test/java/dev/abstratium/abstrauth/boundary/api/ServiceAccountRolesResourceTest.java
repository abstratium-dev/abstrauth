package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.entity.ServiceAccountRole;
import dev.abstratium.abstrauth.service.ServiceAccountRoleService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
public class ServiceAccountRolesResourceTest {

    @Inject
    EntityManager em;

    @Inject
    ServiceAccountRoleService roleService;

    private static final String TEST_CLIENT_PREFIX = "test-roles-client-";
    
    private String testClientId;
    private String testToken;
    private String adminClientId;

    @BeforeEach
    public void setup() throws Exception {
        // Start transaction for cleanup and setup
        jakarta.transaction.UserTransaction tx = com.arjuna.ats.jta.UserTransaction.userTransaction();
        tx.begin();
        
        try {
            cleanupTestData();
            
            // Create test client (the client we'll be managing roles for)
            testClientId = TEST_CLIENT_PREFIX + System.currentTimeMillis();
            createTestClient(testClientId);

            // Use bootstrap client as admin for testing
            // Temporarily set its scopes to empty for M2M (role-based) auth
            adminClientId = "abstratium-abstrauth";
            em.createQuery("UPDATE OAuthClient SET allowedScopes = '[]' WHERE clientId = :clientId")
                    .setParameter("clientId", adminClientId)
                    .executeUpdate();
            
            // Ensure bootstrap client has manage-clients role
            ServiceAccountRole adminRole = new ServiceAccountRole();
            adminRole.setClientId(adminClientId);
            adminRole.setRole("manage-clients");
            em.persist(adminRole);
            em.flush();
            
            // Commit transaction before making HTTP request
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
        
        // Create admin client and get token (after transaction commits)
        testToken = createAdminClientAndGetToken();
    }
    
    @AfterEach
    public void teardown() throws Exception {
        // Ensure cleanup runs after each test to restore bootstrap client
        jakarta.transaction.UserTransaction tx = com.arjuna.ats.jta.UserTransaction.userTransaction();
        tx.begin();
        try {
            cleanupTestData();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }
    
    protected void cleanupTestData() {
        // Clean up test client roles
        em.createQuery("DELETE FROM ServiceAccountRole WHERE clientId LIKE :prefix")
                .setParameter("prefix", TEST_CLIENT_PREFIX + "%")
                .executeUpdate();
        
        // Clean up test client secrets and clients
        em.createQuery("DELETE FROM ClientSecret WHERE clientId LIKE :prefix")
                .setParameter("prefix", TEST_CLIENT_PREFIX + "%")
                .executeUpdate();
        em.createQuery("DELETE FROM OAuthClient WHERE clientId LIKE :prefix")
                .setParameter("prefix", TEST_CLIENT_PREFIX + "%")
                .executeUpdate();
        
        // Restore bootstrap client scopes to default
        em.createQuery("UPDATE OAuthClient SET allowedScopes = '[\"openid\", \"profile\", \"email\"]' WHERE clientId = 'abstratium-abstrauth'")
                .executeUpdate();
        
        // Clean up the manage-clients role we added to bootstrap client for testing
        em.createQuery("DELETE FROM ServiceAccountRole WHERE clientId = 'abstratium-abstrauth' AND role = 'manage-clients'")
                .executeUpdate();
        
        em.flush();
    }

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
        // Get token via client credentials using the bootstrap client
        // No scope parameter since we've set it to empty scopes for role-based M2M auth
        String response = given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", adminClientId)
                .formParam("client_secret", "dev-secret-CHANGE-IN-PROD")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
        return response;
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
