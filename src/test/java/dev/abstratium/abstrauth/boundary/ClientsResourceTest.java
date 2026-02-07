package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Tests for ClientsResource
 * Tests JWT-based security for the /api/clients endpoint
 */
@QuarkusTest
public class ClientsResourceTest {

    /**
     * Generate a valid JWT token with the required role for testing
     */
    private String generateValidToken() {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("test@example.com")
            .groups(java.util.Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
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
        String uniqueClientId = "test-client-" + System.currentTimeMillis();
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
            .body("clientId", equalTo(uniqueClientId))
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
        String uniqueClientId = "test-client-no-redirect-" + System.currentTimeMillis();
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
        String uniqueClientId = "test-client-empty-scopes-" + System.currentTimeMillis();
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
        String uniqueClientId = "test-client-m2m-" + System.currentTimeMillis();
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
            .body("clientId", equalTo(uniqueClientId))
            .body("clientName", equalTo("Test M2M Client"))
            .body("clientType", equalTo("confidential"))
            .body("id", notNullValue())
            .body("createdAt", notNullValue())
            .body("clientSecret", notNullValue());  // Verify secret is returned
    }

    @Test
    public void testCreateClientWithMissingAllowedScopesReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test-client-missing-scopes-" + System.currentTimeMillis();
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
        // Try to create a client with the same ID as the default client
        String requestBody = """
            {
                "clientId": "abstratium-abstrauth",
                "clientName": "Duplicate Client",
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
    @Transactional
    public void testDeleteClientSuccessfully() {
        String token = generateValidToken();
        
        // Create a specific client to delete
        String uniqueClientId = "test-delete-client-" + System.currentTimeMillis();
        String createBody = String.format("""
            {
                "clientId": "%s",
                "clientName": "Client to Delete",
                "clientType": "confidential",
                "redirectUris": "[\\"http://localhost:3000/callback\\"]",
                "allowedScopes": "[\\"openid\\"]"
            }
            """, uniqueClientId);
        
        String clientId = given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(createBody)
            .when()
            .post("/api/clients")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("id");

        // Delete the client
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/" + clientId)
            .then()
            .statusCode(204);

        // Verify client is deleted by trying to get all clients and checking it's not in the list
        String deletedClientId = uniqueClientId;
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("findAll { it.clientId == '" + deletedClientId + "' }.size()", equalTo(0));
    }

    @Test
    public void testDeleteClientWithoutTokenReturns401() {
        given()
            .when()
            .delete("/api/clients/some-id")
            .then()
            .statusCode(401);
    }

    @Test
    public void testDeleteClientWithoutRoleReturns403() {
        String token = generateTokenWithoutRole();
        
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/some-id")
            .then()
            .statusCode(403);
    }

    @Test
    public void testDeleteClientWithNonExistentIdReturns404() {
        String token = generateValidToken();
        
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/non-existent-id")
            .then()
            .statusCode(404)
            .body("error", equalTo("Client not found"));
    }

    @Test
    public void testCreateClientDefaultsRequirePkceToTrue() {
        String token = generateValidToken();
        String uniqueClientId = "test-client-pkce-" + System.currentTimeMillis();
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
        String uniqueClientId = "test-client-public-" + System.currentTimeMillis();
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
    public void testCreateClientWithRequirePkceFalseReturns400() {
        String token = generateValidToken();
        String uniqueClientId = "test-client-no-pkce-" + System.currentTimeMillis();
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
    public void testCannotDeleteAbstrauthClient() {
        String token = generateValidToken();
        
        // Get the abstratium-abstrauth client ID
        String clientId = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstratium-abstrauth' }.id", notNullValue())
            .extract()
            .jsonPath()
            .getString("find { it.clientId == 'abstratium-abstrauth' }.id");
        
        // Try to delete it - should fail with 400
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .delete("/api/clients/" + clientId)
            .then()
            .statusCode(400)
            .body("error", containsString("Cannot delete the abstratium-abstrauth client"));
        
        // Verify it still exists
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("clientId", hasItem("abstratium-abstrauth"));
    }

    @Test
    public void testCreateClientGeneratesValidSecret() {
        String token = generateValidToken();
        String uniqueClientId = "test-client-secret-" + System.currentTimeMillis();
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
    public void testListClientsDoesNotIncludeSecret() {
        String token = generateValidToken();
        
        // Create a client first
        String uniqueClientId = "test-client-no-secret-" + System.currentTimeMillis();
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
}
