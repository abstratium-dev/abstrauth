package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for ConfigResource
 * Tests the /api/config endpoint
 */
@QuarkusTest
public class ConfigResourceTest {

    @Test
    public void testGetConfigReturnsOk() {
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"));
    }

    @Test
    public void testGetConfigReturnsAllFields() {
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .body("signupAllowed", notNullValue())
            .body("allowNativeSignin", notNullValue())
            .body("sessionTimeoutSeconds", notNullValue());
    }

    @Test
    public void testGetConfigReturnsDefaultSessionTimeout() {
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .body("sessionTimeoutSeconds", equalTo(900)); // Default value
    }

    @Test
    public void testGetConfigReturnsBooleansForSignupFlags() {
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .body("signupAllowed", instanceOf(Boolean.class))
            .body("allowNativeSignin", instanceOf(Boolean.class));
    }

    @Test
    public void testGetConfigReturnsIntegerForSessionTimeout() {
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .body("sessionTimeoutSeconds", instanceOf(Integer.class));
    }

    @Test
    public void testGetConfigIsPubliclyAccessible() {
        // This endpoint should be accessible without authentication
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200);
    }
}
