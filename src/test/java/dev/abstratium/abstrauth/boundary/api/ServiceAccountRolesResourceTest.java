package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.entity.OrganisationAccount;
import dev.abstratium.abstrauth.service.JwtOrgResolver;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.ServiceAccountRoleService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
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
    private String adminAccountId;

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

            // Create admin account for testing
            Account admin = new Account();
            admin.setEmail("roles-admin@test.com");
            admin.setName("Roles Admin");
            admin.setEmailVerified(true);
            em.persist(admin);
            adminAccountId = admin.getId();

            // Link admin to default org so interceptor passes
            OrganisationAccount oa = new OrganisationAccount();
            oa.setId(new OrganisationAccount.Id(JwtOrgResolver.DEFAULT_ORG_ID, admin.getId(), "member"));
            em.persist(oa);

            em.flush();
            
            // Commit transaction before making HTTP requests
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
        
        // Build admin token directly with required role and orgId
        testToken = Jwt.issuer("https://abstrauth.abstratium.dev")
                .upn("roles-admin@test.com")
                .subject(adminAccountId)
                .groups(java.util.Set.of(Roles.MANAGE_CLIENTS, Roles.USER))
                .claim("email", "roles-admin@test.com")
                .claim("name", "Roles Admin")
                .claim("orgId", JwtOrgResolver.DEFAULT_ORG_ID)
                .sign();
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
        
        // Clean up admin account and org membership
        em.createQuery("DELETE FROM OrganisationAccount WHERE id.accountId IN (SELECT id FROM Account WHERE email = 'roles-admin@test.com')")
                .executeUpdate();
        em.createQuery("DELETE FROM Account WHERE email = 'roles-admin@test.com'")
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
