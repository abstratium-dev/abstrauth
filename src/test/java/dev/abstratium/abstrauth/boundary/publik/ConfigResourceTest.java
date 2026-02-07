package dev.abstratium.abstrauth.boundary.publik;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for ConfigResource
 * Tests the /public/config endpoint
 */
@QuarkusTest
public class ConfigResourceTest {

    @Test
    public void testGetConfigReturnsOk() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"));
    }

    @Test
    public void testGetConfigReturnsAllFields() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("signupAllowed", notNullValue())
            .body("allowNativeSignin", notNullValue())
            .body("sessionTimeoutSeconds", notNullValue())
            .body("insecureClientSecret", notNullValue())
            .body("warningMessage", notNullValue());
    }

    @Test
    public void testGetConfigReturnsDefaultSessionTimeout() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("sessionTimeoutSeconds", equalTo(900)); // Default value
    }

    @Test
    public void testGetConfigReturnsBooleansForSignupFlags() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("signupAllowed", instanceOf(Boolean.class))
            .body("allowNativeSignin", instanceOf(Boolean.class))
            .body("insecureClientSecret", instanceOf(Boolean.class));
    }

    @Test
    public void testGetConfigReturnsIntegerForSessionTimeout() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("sessionTimeoutSeconds", instanceOf(Integer.class));
    }

    @Test
    public void testGetConfigIsPubliclyAccessible() {
        // This endpoint should be accessible without authentication
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200);
    }

    @Test
    public void testInsecureClientSecretDetectsDefaultSecret() {
        // With default secret "dev-secret-CHANGE-IN-PROD", insecureClientSecret should be true
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("insecureClientSecret", equalTo(true));
    }

    @Test
    public void testGetConfigReturnsStringForWarningMessage() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("warningMessage", instanceOf(String.class));
    }

    @Test
    public void testGetConfigReturnsTestWarningMessage() {
        // In test environment, should return "You are in the test environment"
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("warningMessage", equalTo("You are in the test environment"));
    }
}
