package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Tests for ClientSecretsResource.
 * Tests secret rotation, listing, creation, and revocation.
 */
@QuarkusTest
public class ClientSecretsResourceTest {

    @Inject
    EntityManager em;

    private String adminToken;
    private String testClientId;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up test data
        em.createQuery("DELETE FROM ClientSecret WHERE clientId LIKE 'test-secrets-%'").executeUpdate();
        em.createQuery("DELETE FROM OAuthClient WHERE clientId LIKE 'test-secrets-%'").executeUpdate();
        em.createQuery("DELETE FROM AccountRole WHERE accountId IN (SELECT id FROM Account WHERE email = 'secrets-admin@test.com')").executeUpdate();
        em.createQuery("DELETE FROM Account WHERE email = 'secrets-admin@test.com'").executeUpdate();

        // Create admin account
        Account admin = new Account();
        admin.setEmail("secrets-admin@test.com");
        admin.setName("Secrets Admin");
        admin.setEmailVerified(true);
        em.persist(admin);

        // Assign admin role
        AccountRole role = new AccountRole();
        role.setAccountId(admin.getId());
        role.setClientId(Roles.CLIENT_ID);
        role.setRole(Roles.MANAGE_CLIENTS);
        em.persist(role);

        // Create test client
        testClientId = "test-secrets-client-" + System.currentTimeMillis();
        OAuthClient client = new OAuthClient();
        client.setClientId(testClientId);
        client.setClientName("Test Secrets Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:8080/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);

        // Create initial secret
        ClientSecret secret = new ClientSecret();
        secret.setClientId(testClientId);
        secret.setSecretHash("$2a$10$dummyhash");
        secret.setDescription("Initial secret");
        secret.setActive(true);
        em.persist(secret);

        em.flush();

        // Generate admin token with proper JWT
        adminToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn(admin.getEmail())
            .subject(admin.getId())
            .groups(java.util.Set.of(Roles.MANAGE_CLIENTS, Roles.USER))
            .claim("email", admin.getEmail())
            .claim("name", admin.getName())
            .sign();
    }

    @Test
    public void testListSecrets() {
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/api/clients/" + testClientId + "/secrets")
            .then()
            .statusCode(200)
            .body("$", hasSize(greaterThan(0)))
            .body("[0].id", notNullValue())
            .body("[0].description", notNullValue())
            .body("[0].active", equalTo(true));
    }

    @Test
    public void testListSecretsNotFound() {
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/api/clients/non-existent-client/secrets")
            .then()
            .statusCode(404);
    }

    @Test
    public void testCreateSecret() {
        String requestBody = """
            {
                "description": "New rotated secret",
                "expiresInDays": 90
            }
            """;

        String secret = given()
            .header("Authorization", "Bearer " + adminToken)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/clients/" + testClientId + "/secrets")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("secret", notNullValue())
            .body("description", equalTo("New rotated secret"))
            .body("expiresAt", notNullValue())
            .extract()
            .path("secret");

        // Verify secret was created
        assertNotNull(secret);
        assertTrue(secret.length() >= 32, "Secret should be at least 32 characters");
    }

    @Test
    public void testCreateSecretWithoutExpiration() {
        String requestBody = """
            {
                "description": "Permanent secret"
            }
            """;

        given()
            .header("Authorization", "Bearer " + adminToken)
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/clients/" + testClientId + "/secrets")
            .then()
            .statusCode(201)
            .body("secret", notNullValue())
            .body("expiresAt", equalTo(null));
    }

    @Test
    public void testRevokeSecret() {
        // Create a second secret first (can't revoke the last one)
        createSecondSecret();

        // Get the first secret ID
        Long secretId = getFirstSecretId();

        // Revoke it
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .delete("/api/clients/" + testClientId + "/secrets/" + secretId)
            .then()
            .statusCode(204);

        // Verify it was deactivated
        em.clear();
        ClientSecret revoked = em.find(ClientSecret.class, secretId);
        assertNotNull(revoked);
        assertFalse(revoked.isActive(), "Secret should be deactivated");
    }

    @Test
    @Transactional
    public void testCannotRevokeLastSecret() {
        // Get the only secret ID
        Long secretId = em.createQuery(
            "SELECT cs.id FROM ClientSecret cs WHERE cs.clientId = :clientId", 
            Long.class)
            .setParameter("clientId", testClientId)
            .getSingleResult();

        // Try to revoke it - should fail
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .delete("/api/clients/" + testClientId + "/secrets/" + secretId)
            .then()
            .statusCode(400)
            .body("error", equalTo("Bad request"));
    }

    @Test
    public void testRevokeSecretNotFound() {
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .delete("/api/clients/" + testClientId + "/secrets/99999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testRevokeSecretWrongClient() {
        // Get a secret ID
        Long secretId = em.createQuery(
            "SELECT cs.id FROM ClientSecret cs WHERE cs.clientId = :clientId", 
            Long.class)
            .setParameter("clientId", testClientId)
            .getSingleResult();

        // Try to revoke it with wrong client ID
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .delete("/api/clients/wrong-client/secrets/" + secretId)
            .then()
            .statusCode(404);
    }

    @Transactional
    void createSecondSecret() {
        ClientSecret secret2 = new ClientSecret();
        secret2.setClientId(testClientId);
        secret2.setSecretHash("$2a$10$anotherhash");
        secret2.setDescription("Second secret");
        secret2.setActive(true);
        em.persist(secret2);
    }

    Long getFirstSecretId() {
        return em.createQuery(
            "SELECT cs.id FROM ClientSecret cs WHERE cs.clientId = :clientId ORDER BY cs.id", 
            Long.class)
            .setParameter("clientId", testClientId)
            .setMaxResults(1)
            .getSingleResult();
    }
}
