package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
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
            .groups("abstratium-abstrauth_manage-clients")
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
            .body("clientType", hasItem("public"));
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
            .body("[0].requirePkce", notNullValue());
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
                  containsString("http://localhost:8080/auth-callback"));
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
}
