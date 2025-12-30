package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;;

/**
 * Tests for AuthCallbackResource
 */
@QuarkusTest
public class AuthCallbackResourceTest {

    @Test
    public void testCallbackRedirectsToHome() {
        RestAssured.given()
                .when()
                .redirects().follow(false)  // Don't follow redirects
                .get("/api/auth/callback")
                .then()
                .log().all()
                .statusCode(303)  // See Other
                .header("Location", equalTo("http://localhost:8081/"));
    }
}
