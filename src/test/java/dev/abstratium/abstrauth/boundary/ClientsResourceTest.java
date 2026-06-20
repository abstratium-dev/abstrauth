package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.service.SubscriptionService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Tests for ClientsResource
 * Tests JWT-based security for the /api/clients endpoint
 */
@QuarkusTest
public class ClientsResourceTest {

    @Inject
    SubscriptionService subscriptionService;


    /**
     * Generate a JWT token with only the user role (no manage-clients)
     */
    private String generateUserOnlyToken() {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("user@example.com")
            .groups(java.util.Set.of("abstratium-abstrauth_user"))
            .claim("email", "user@example.com")
            .claim("name", "User Only")
            .claim("orgId", "00000000-0000-0000-0000-000000000000")
            .sign();
    }

    /**
     * Generate a valid JWT token with the required role for testing
     */
    private String generateValidToken() {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("test@example.com")
            .groups(java.util.Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .claim("orgId", "00000000-0000-0000-0000-000000000000")
            .sign();
    }

    /**
     * Generate a JWT token without the required role
     */
    private String generateTokenWithoutRole() {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("test@example.com")
            .groups("some-other-role")
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .sign();
    }

    @Test
    public void testListClientsWithoutTokenReturns401() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(401);
    }

    @Test
    public void testListClientsWithInvalidTokenReturns401() {
        given()
            .header("Authorization", "Bearer invalid-token")
            .when()
            .get("/api/clients")
            .then()
            .statusCode(401);
    }

    @Test
    public void testListClientsWithoutRequiredRoleReturns403() {
        String token = generateTokenWithoutRole();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(403);
    }

    @Test
    public void testListClientsReturnsOk() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"));
    }

    @Test
    public void testListClientsReturnsArray() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("$", instanceOf(java.util.List.class));
    }

    @Test
    public void testListClientsIncludesDefaultClient() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("clientId", hasItem("abstratium-abstrauth"))
            .body("clientName", hasItem("abstratium abstrauth"))
            .body("clientType", hasItem("confidential"));
    }

    @Test
    public void testListClientsIncludesAllRequiredFields() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("[0].id", notNullValue())
            .body("[0].clientId", notNullValue())
            .body("[0].clientName", notNullValue())
            .body("[0].clientType", notNullValue())
            .body("[0].redirectUris", notNullValue())
            .body("[0].allowedScopes", notNullValue())
            .body("[0].requirePkce", notNullValue())
            .body("[0].clientSecret", nullValue());  // clientSecret should NOT be present in list responses
    }

    @Test
    public void testListClientsDefaultClientHasCorrectRedirectUris() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstratium-abstrauth' }.redirectUris", 
                  containsString("http://localhost:8080/api/auth/callback"));
    }

    @Test
    public void testListClientsDefaultClientHasCorrectScopes() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstratium-abstrauth' }.allowedScopes", 
                  containsString("openid"));
    }

    @Test
    public void testListClientsDefaultClientRequiresPkce() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstratium-abstrauth' }.requirePkce", equalTo(true));
    }

    @Test
    public void testListClientsDefaultClientHasCreatedAt() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstratium-abstrauth' }.createdAt", notNullValue());
    }

    @Test
    public void testListClientsReturnsAtLeastOneClient() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    public void testCreateClientWithoutTokenReturns401() {
        String requestBody = """
            {
                "clientId": "test-client",
                "clientName": "Test Client",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]",
                "requirePkce": true
            }
            """;
        
        given()
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(401);
    }

    @Test
    public void testCreateClientWithoutRequiredRoleReturns403() {
        String token = generateTokenWithoutRole();
        String requestBody = """
            {
                "clientId": "test-client",
                "clientName": "Test Client",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]",
                "requirePkce": true
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(403);
    }

    @Test
    public void testCreateClientSuccessfully() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]",
                "requirePkce": true
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("00000000-0000-0000-0000-000000000000__" + uniqueClientId))
            .body("clientName", equalTo("Test Client"))
            .body("clientType", equalTo("confidential"))
            .body("requirePkce", equalTo(true))
            .body("id", notNullValue())
            .body("createdAt", notNullValue())
            .body("clientSecret", notNullValue());  // Verify secret is returned
    }

    @Test
    public void testCreateClientWithMissingClientIdReturns400() {
        String token = generateValidToken();
        String requestBody = """
            {
                "clientName": "Test Client",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]"
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400);
            // Hibernate Validator returns validation errors in a different format
            // The important thing is that it returns 400
    }

    @Test
    public void testCreateClientWithMissingClientNameReturns400() {
        String token = generateValidToken();
        String requestBody = """
            {
                "clientId": "test-client",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]"
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400);
    }

    @Test
    public void testCreateClientWithMissingClientTypeReturns400() {
        String token = generateValidToken();
        String requestBody = """
            {
                "clientId": "test-client",
                "clientName": "Test Client",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]"
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400);
    }

    @Test
    public void testCreateClientWithMissingRedirectUrisReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_no_redirect_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client",
                "clientType": "confidential",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]"
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400)
            .body("error", equalTo("Redirect URIs are required when scopes are configured"));
    }

    @Test
    public void testCreateClientWithEmptyScopesAndRedirectsReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_empty_scopes_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Empty Scopes",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[]"
            }
            """, uniqueClientId);
        
        var response = given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400);
        
        // Only check body if content-type is present
        String contentType = response.extract().contentType();
        if (contentType != null && contentType.contains("json")) {
            response.body("error", equalTo("Scopes are required when redirect URIs are configured"));
        }
    }

    @Test
    public void testCreateClientWithNeitherScopesNorRedirectsSucceeds() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_m2m_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test M2M Client",
                "clientType": "confidential"
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("00000000-0000-0000-0000-000000000000__" + uniqueClientId))
            .body("clientName", equalTo("Test M2M Client"))
            .body("clientType", equalTo("confidential"))
            .body("id", notNullValue())
            .body("createdAt", notNullValue())
            .body("clientSecret", notNullValue());  // Verify secret is returned
    }

    @Test
    public void testCreateClientWithMissingAllowedScopesReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_missing_scopes_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Missing Scopes",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]"
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400)
            .body("error", equalTo("Scopes are required when redirect URIs are configured"));
    }

    @Test
    public void testCreateClientWithDuplicateClientIdReturns409() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_dup_" + System.currentTimeMillis();

        // First create a client successfully
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Original Client",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": true
            }
            """, uniqueClientId);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201);

        // Try to create another client with the same ID
        String duplicateRequestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Duplicate Client",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": true
            }
            """, uniqueClientId);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(duplicateRequestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(409)
            .body("error", equalTo("Client ID already exists"));
    }

    @Test
    public void testUpdateClientSuccessfully() {
        String token = generateValidToken();
        // First get the default client's ID
        String clientId = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("[0].id");

        String requestBody = """
            {
                "clientName": "Updated Client Name",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:5000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"email\\"]",
                "requirePkce": true
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/" + clientId)
            .then()
            .statusCode(200)
            .body("clientName", equalTo("Updated Client Name"))
            .body("clientType", equalTo("confidential"))
            .body("requirePkce", equalTo(true));
    }

    @Test
    public void testUpdateClientWithoutTokenReturns401() {
        String requestBody = """
            {
                "clientName": "Updated Name",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]"
            }
            """;
        
        given()
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/some-id")
            .then()
            .statusCode(401);
    }

    @Test
    public void testUpdateClientWithoutRoleReturns403() {
        String token = generateTokenWithoutRole();
        String requestBody = """
            {
                "clientName": "Updated Name",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]"
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/some-id")
            .then()
            .statusCode(403);
    }

    @Test
    public void testUpdateClientWithNonExistentIdReturns404() {
        String token = generateValidToken();
        String requestBody = """
            {
                "clientName": "Updated Name",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]"
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/non-existent-id")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testUpdateClientWithMissingClientNameReturns400() {
        String token = generateValidToken();
        String requestBody = """
            {
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]"
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/some-id")
            .then()
            .statusCode(400);
    }

    @Test
    public void testCreateClientDefaultsRequirePkceToTrue() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_pkce_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client PKCE",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]"
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .body("requirePkce", equalTo(true));
    }

    @Test
    public void testCreateClientWithPublicTypeReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_public_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Public Client",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]",
                "requirePkce": true
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400)
            .body("error", equalTo("Only confidential clients are allowed"));
    }

    @Test
    public void testCreateClientWithInvalidClientIdCharactersReturns400() {
        String token = generateValidToken();
        // Client ID with invalid characters (dash is not allowed)
        String requestBody = """
            {
                "clientId": "my-client-id",
                "clientName": "Test Client",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400)
            .body("error", equalTo("Client ID must contain only letters, numbers, and underscores"));
    }

    @Test
    public void testCreateClientWithSpecialCharactersInClientIdReturns400() {
        String token = generateValidToken();
        // Client ID with special characters
        String requestBody = """
            {
                "clientId": "my@client#id!",
                "clientName": "Test Client",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400)
            .body("error", equalTo("Client ID must contain only letters, numbers, and underscores"));
    }

    @Test
    public void testCreateClientWithValidClientIdUnderscoresSucceeds() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client With Underscores",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": true
            }
            """, uniqueClientId);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("00000000-0000-0000-0000-000000000000__" + uniqueClientId))
            .body("clientName", equalTo("Test Client With Underscores"));
    }

    @Test
    public void testCreateClientWithRequirePkceFalseReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_no_pkce_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client No PKCE",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]",
                "requirePkce": false
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(400)
            .body("error", equalTo("PKCE is required for all clients"));
    }

    @Test
    public void testUpdateClientWithPublicTypeReturns400() {
        String token = generateValidToken();
        String clientId = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("[0].id");

        String requestBody = """
            {
                "clientName": "Updated Name",
                "clientType": "public",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": true
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/" + clientId)
            .then()
            .statusCode(400)
            .body("error", equalTo("Only confidential clients are allowed"));
    }

    @Test
    public void testUpdateClientWithRequirePkceFalseReturns400() {
        String token = generateValidToken();
        String clientId = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("[0].id");

        String requestBody = """
            {
                "clientName": "Updated Name",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]",
                "requirePkce": false
            }
            """;
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .put("/api/clients/" + clientId)
            .then()
            .statusCode(400)
            .body("error", equalTo("PKCE is required for all clients"));
    }

    @Test
    public void testCreateClientGeneratesValidSecret() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_secret_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client Secret",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\", \\"profile\\"]",
                "requirePkce": true
            }
            """, uniqueClientId);
        
        String clientSecret = given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .body("clientSecret", notNullValue())
            .extract()
            .jsonPath()
            .getString("clientSecret");

        // Verify the secret is a base64-encoded string with reasonable length
        // Base64 encoding of 32 bytes should be 43 characters (without padding)
        assert clientSecret != null;
        assert clientSecret.length() >= 40; // At least 40 characters
        assert clientSecret.matches("[A-Za-z0-9_-]+"); // URL-safe base64 pattern
    }

    @Test
    public void testCreateClientCreatesSubscriptionForOwningOrg() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_sub_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Subscription Client",
                "clientType": "confidential"
            }
            """, uniqueClientId);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201);

        String fullClientId = "00000000-0000-0000-0000-000000000000__" + uniqueClientId;
        assertTrue(
            subscriptionService.subscriptionExists("00000000-0000-0000-0000-000000000000", fullClientId),
            "Owning org should be subscribed to the newly created client"
        );
    }

    @Test
    public void testUserWithoutManageClientsCanSeeOwnOrgClients() {
        String managerToken = generateValidToken();
        String uniqueClientId = "test_client_visibility_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Visibility Test Client",
                "clientType": "confidential"
            }
            """, uniqueClientId);

        given()
            .header("Authorization", "Bearer " + managerToken)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201);

        String fullClientId = "00000000-0000-0000-0000-000000000000__" + uniqueClientId;

        String userToken = generateUserOnlyToken();
        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("clientId", hasItem(fullClientId));
    }

    @Test
    public void testListClientsDoesNotIncludeSecret() {
        String token = generateValidToken();
        
        // Create a client first
        String uniqueClientId = "test_client_no_secret_" + System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client No Secret",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]"
            }
            """, uniqueClientId);
        
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .body("clientSecret", notNullValue());

        // List clients and verify secret is NOT included
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == '" + uniqueClientId + "' }.clientSecret", nullValue());
    }

    // ─────────────────────────────────────────────────────────
    // Allowed Roles Management
    // ─────────────────────────────────────────────────────────

    @Test
    public void testAddAllowedRoleSuccessfully() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_add_role_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Add Role",
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

        String requestBody = """
            {
                "role": "viewer",
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201)
            .body("clientId", equalTo(actualClientId))
            .body("role", equalTo("viewer"))
            .body("isDefault", equalTo(true));

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserOnlyToken())
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles-for-users-in-clients-org")
            .then()
            .statusCode(200)
            .body("role", hasItem("viewer"))
            .body("isDefault", hasItem(true));
    }

    @Test
    public void testAddAllowedRoleDuplicateReturns409() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_dup_role_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Dup Role",
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

        String requestBody = """
            {
                "role": "editor",
                "isDefault": false
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(409)
            .body("error", equalTo("Role already exists in allowlist"));
    }

    @Test
    public void testAddAllowedRoleWithoutTokenReturns401() {
        String requestBody = """
            {
                "role": "viewer",
                "isDefault": true
            }
            """;

        given()
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/some-client/allowed-roles")
            .then()
            .statusCode(401);
    }

    @Test
    public void testAddAllowedRoleWithoutRoleReturns403() {
        String token = generateTokenWithoutRole();
        String requestBody = """
            {
                "role": "viewer",
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/some-client/allowed-roles")
            .then()
            .statusCode(403);
    }

    @Test
    public void testAddAllowedRoleForNonExistentClientReturns404() {
        String token = generateValidToken();
        String requestBody = """
            {
                "role": "viewer",
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/non-existent-client-id/allowed-roles")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testAddAllowedRoleWithInvalidCharactersReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_invalid_role_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Invalid Role",
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

        // Role with space and exclamation mark violates the pattern
        String requestBody = """
            {
                "role": "invalid role!",
                "isDefault": false
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(400);
    }

    @Test
    public void testUpdateAllowedRoleSuccessfully() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_update_role_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Update Role",
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

        // Add role first
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"role\": \"manager\", \"isDefault\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // Update to default
        String updateBody = """
            {
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(updateBody)
            .when()
            .put("/api/clients/" + actualClientId + "/allowed-roles/manager")
            .then()
            .statusCode(200)
            .body("clientId", equalTo(actualClientId))
            .body("role", equalTo("manager"))
            .body("isDefault", equalTo(true));

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserOnlyToken())
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles-for-users-in-clients-org")
            .then()
            .statusCode(200)
            .body("role", hasItem("manager"))
            .body("isDefault", hasItem(true));
    }

    @Test
    public void testUpdateAllowedRoleNonExistentRoleReturns404() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_update_missing_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Update Missing",
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

        String updateBody = """
            {
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(updateBody)
            .when()
            .put("/api/clients/" + actualClientId + "/allowed-roles/nonexistent")
            .then()
            .statusCode(404)
            .body("error", equalTo("Role not found in allowlist"));
    }

    @Test
    public void testUpdateAllowedRoleWithoutTokenReturns401() {
        String updateBody = """
            {
                "isDefault": true
            }
            """;

        given()
            .contentType("application/json")
            .body(updateBody)
            .when()
            .put("/api/clients/some-client/allowed-roles/viewer")
            .then()
            .statusCode(401);
    }

    @Test
    public void testUpdateAllowedRoleWithoutRoleReturns403() {
        String token = generateTokenWithoutRole();
        String updateBody = """
            {
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(updateBody)
            .when()
            .put("/api/clients/some-client/allowed-roles/viewer")
            .then()
            .statusCode(403);
    }

    @Test
    public void testUpdateAllowedRoleForNonExistentClientReturns404() {
        String token = generateValidToken();
        String updateBody = """
            {
                "isDefault": true
            }
            """;

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(updateBody)
            .when()
            .put("/api/clients/non-existent-client-id/allowed-roles/viewer")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testRemoveAllowedRoleSuccessfully() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_remove_role_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Remove Role",
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

        // Add role first
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"role\": \"admin\", \"isDefault\": false}")
            .when()
            .post("/api/clients/" + actualClientId + "/allowed-roles")
            .then()
            .statusCode(201);

        // Remove role
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/" + actualClientId + "/allowed-roles/admin")
            .then()
            .statusCode(204);

        // Verify via GET
        given()
            .header("Authorization", "Bearer " + generateUserOnlyToken())
            .when()
            .get("/api/clients/" + actualClientId + "/allowed-roles-for-users-in-clients-org")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    public void testRemoveAllowedRoleNonExistentRoleReturns404() {
        String token = generateValidToken();
        String uniqueClientId = "test_client_remove_missing_" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Test Client for Remove Missing",
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

    @Test
    public void testRemoveAllowedRoleWithoutTokenReturns401() {
        given()
            .when()
            .delete("/api/clients/some-client/allowed-roles/viewer")
            .then()
            .statusCode(401);
    }

    @Test
    public void testRemoveAllowedRoleWithoutRoleReturns403() {
        String token = generateTokenWithoutRole();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/some-client/allowed-roles/viewer")
            .then()
            .statusCode(403);
    }

    @Test
    public void testRemoveAllowedRoleForNonExistentClientReturns404() {
        String token = generateValidToken();
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/non-existent-client-id/allowed-roles/viewer")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }
}
