package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class AccountsResourceTest {

    private String generateAdminToken() {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("admin@example.com")
            .groups("abstratium-abstrauth_admin")
            .claim("email", "admin@example.com")
            .claim("name", "Admin User")
            .sign();
    }

    private String generateUserToken() {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .upn("user@example.com")
            .groups("abstratium-abstrauth_user")
            .claim("email", "user@example.com")
            .claim("name", "Regular User")
            .sign();
    }

    @Test
    public void testListAccountsAsAdmin() {
        given()
            .auth().oauth2(generateAdminToken())
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    public void testListAccountsAsNonAdmin() {
        // Non-admin users should get 403 Forbidden
        given()
            .auth().oauth2(generateUserToken())
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(403);
    }

    @Test
    public void testListAccountsUnauthenticated() {
        // Unauthenticated users should get 401 Unauthorized
        given()
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(401);
    }

}
