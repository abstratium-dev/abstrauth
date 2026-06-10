package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;

@QuarkusTest
public class AuthBffResourceTest {

    @Test
    public void testLogin_authenticated_redirectsToRoot() {
        String token = Jwt.issuer("https://abstrauth.abstratium.dev")
                .subject("test-account-id")
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", "00000000-0000-0000-0000-000000000000")
                .sign();

        given()
                .auth().oauth2(token)
                .redirects().follow(false)
                .when()
                .get("/api/auth/login")
                .then()
                .statusCode(303)
                .header("Location", endsWith("/"));
    }

    @Test
    public void testCheckAuth_authenticated_returns200() {
        String token = Jwt.issuer("https://abstrauth.abstratium.dev")
                .subject("test-account-id")
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", "00000000-0000-0000-0000-000000000000")
                .sign();

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/auth/check")
                .then()
                .statusCode(200);
    }
}
