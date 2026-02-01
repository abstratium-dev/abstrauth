package dev.abstratium.abstrauth.boundary.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.ServiceAccountRoleService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Integration tests for OAuth 2.0 Client Credentials Grant (RFC 6749 Section 4.4)
 * Tests service-to-service authentication with roles and scopes
 */
@QuarkusTest
public class ClientCredentialsFlowTest {

    @Inject
    EntityManager em;

    @Inject
    ServiceAccountRoleService serviceAccountRoleService;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up test data
        em.createQuery("DELETE FROM ServiceAccountRole WHERE clientId LIKE 'test-service-%'").executeUpdate();
        em.createQuery("DELETE FROM ClientSecret WHERE clientId LIKE 'test-service-%'").executeUpdate();
        em.createQuery("DELETE FROM OAuthClient WHERE clientId LIKE 'test-service-%'").executeUpdate();
        em.flush();
    }

    /**
     * Helper method to create a service client with secret
     */
    @Transactional
    protected void createServiceClient(String clientId, String plainSecret, String allowedScopes) {
        // Create service client
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Test Service Client");
        client.setClientType("confidential");
        client.setRedirectUris("");  // Service clients don't use redirect URIs
        client.setAllowedScopes(allowedScopes);
        client.setRequirePkce(false);
        em.persist(client);

        // Create client secret
        ClientSecret secret = new ClientSecret();
        secret.setClientId(clientId);
        secret.setSecretHash(new BCryptPasswordEncoder().encode(plainSecret));
        secret.setDescription("Test secret");
        secret.setActive(true);
        em.persist(secret);
        em.flush();
    }

    /**
     * Test successful client credentials flow with scopes and roles
     */
    @Test
    public void testClientCredentialsFlowSuccess() {
        String clientId = "test-service-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read api:write");

        // Add service roles
        serviceAccountRoleService.addRole(clientId, "service-reader");
        serviceAccountRoleService.addRole(clientId, "service-writer");

        // Request token with client credentials
        Response response = given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                .formParam("scope", "api:read api:write")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(3600))
                .body("scope", anyOf(equalTo("api:read api:write"), equalTo("api:write api:read")))
                .body("refresh_token", nullValue())  // No refresh token for client credentials
                .extract()
                .response();

        String accessToken = response.jsonPath().getString("access_token");
        assertNotNull(accessToken);

        // Verify token contains expected claims
        // In a real test, you would decode and verify the JWT
        assertTrue(accessToken.length() > 100);
    }

    /**
     * Test client credentials with subset of allowed scopes
     */
    @Test
    public void testClientCredentialsWithSubsetOfScopes() {
        String clientId = "test-service-subset-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read api:write api:delete");

        // Request token with only subset of scopes
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                .formParam("scope", "api:read")  // Only request one scope
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("scope", equalTo("api:read"));
    }

    /**
     * Test client credentials with no scope parameter (should get all allowed scopes)
     */
    @Test
    public void testClientCredentialsWithNoScopeParameter() {
        String clientId = "test-service-noscope-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read api:write");

        // Request token without scope parameter
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                // No scope parameter
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("scope", anyOf(equalTo("api:read api:write"), equalTo("api:write api:read")));
    }

    /**
     * Test client credentials with invalid scope
     */
    @Test
    public void testClientCredentialsWithInvalidScope() {
        String clientId = "test-service-badscope-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read");

        // Request token with scope not in allowed list
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                .formParam("scope", "api:write")  // Not allowed
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(400)
                .body("error", equalTo("invalid_scope"));
    }

    /**
     * Test client credentials with wrong secret
     */
    @Test
    public void testClientCredentialsWithWrongSecret() {
        String clientId = "test-service-wrongsecret-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read");

        // Request token with wrong secret
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", "wrong-secret-12345")
                .formParam("scope", "api:read")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    /**
     * Test client credentials with non-existent client
     */
    @Test
    public void testClientCredentialsWithNonExistentClient() {
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", "non-existent-client")
                .formParam("client_secret", "some-secret")
                .formParam("scope", "api:read")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    /**
     * Test client credentials with missing client_id
     */
    @Test
    public void testClientCredentialsWithMissingClientId() {
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_secret", "some-secret")
                .formParam("scope", "api:read")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(400)
                .body("error", equalTo("invalid_request"));
    }

    /**
     * Test client credentials with missing client_secret
     */
    @Test
    public void testClientCredentialsWithMissingClientSecret() {
        given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", "some-client")
                .formParam("scope", "api:read")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(400)
                .body("error", equalTo("invalid_request"));
    }

    /**
     * Test client credentials with HTTP Basic Auth
     */
    @Test
    public void testClientCredentialsWithBasicAuth() {
        String clientId = "test-service-basic-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read");

        // Request token using HTTP Basic Auth
        given()
                .auth().preemptive().basic(clientId, plainSecret)
                .formParam("grant_type", "client_credentials")
                .formParam("scope", "api:read")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue());
    }

    /**
     * Test that client credentials token works with /api/auth/check endpoint
     * This verifies the M2M authentication flow works end-to-end
     */
    @Test
    public void testClientCredentialsTokenWorksWithAuthCheck() {
        String clientId = "test-service-authcheck-" + System.currentTimeMillis();
        String plainSecret = "test-secret-" + System.currentTimeMillis();

        createServiceClient(clientId, plainSecret, "api:read");

        // Add service roles for authorization
        serviceAccountRoleService.addRole(clientId, "service-reader");

        // Get token via client credentials
        Response tokenResponse = given()
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", plainSecret)
                .formParam("scope", "api:read")
                .when()
                .post("/oauth2/token")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .extract()
                .response();

        String accessToken = tokenResponse.jsonPath().getString("access_token");

        // Verify token works with /api/auth/check endpoint
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/auth/check")
                .then()
                .statusCode(200);
    }

    /**
     * Test that /api/auth/check rejects invalid tokens
     */
    @Test
    public void testAuthCheckRejectsInvalidToken() {
        // Test with completely invalid token
        given()
                .header("Authorization", "Bearer invalid-token-12345")
                .redirects().follow(false)
                .when()
                .get("/api/auth/check")
                .then()
                .statusCode(anyOf(is(302), is(303), is(401)));

        // Test with no token
        given()
                .redirects().follow(false)
                .when()
                .get("/api/auth/check")
                .then()
                .statusCode(anyOf(is(302), is(303), is(401)));
    }

}
