package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@QuarkusTest
public class AuthBffResourceTest {

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @Test
    public void testLogin_authenticated_redirectsToRoot() {
        String token = Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
                .subject("test-account-id")
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", defaultOrgId)
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
        String token = Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
                .subject("test-account-id")
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", defaultOrgId)
                .sign();

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/auth/check")
                .then()
                .statusCode(200);
    }
}
