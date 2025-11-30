package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Tests for ClientsResource
 */
@QuarkusTest
public class ClientsResourceTest {

    @Test
    public void testListClientsReturnsOk() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"));
    }

    @Test
    public void testListClientsReturnsArray() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("$", instanceOf(java.util.List.class));
    }

    @Test
    public void testListClientsIncludesDefaultClient() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("clientId", hasItem("abstrauth_admin_app"))
            .body("clientName", hasItem("abstrauth admin app"))
            .body("clientType", hasItem("public"));
    }

    @Test
    public void testListClientsIncludesAllRequiredFields() {
        given()
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
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstrauth_admin_app' }.redirectUris", 
                  containsString("http://localhost:8080/auth-callback"));
    }

    @Test
    public void testListClientsDefaultClientHasCorrectScopes() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstrauth_admin_app' }.allowedScopes", 
                  containsString("openid"));
    }

    @Test
    public void testListClientsDefaultClientRequiresPkce() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstrauth_admin_app' }.requirePkce", equalTo(true));
    }

    @Test
    public void testListClientsDefaultClientHasCreatedAt() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("find { it.clientId == 'abstrauth_admin_app' }.createdAt", notNullValue());
    }

    @Test
    public void testListClientsReturnsAtLeastOneClient() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }
}
