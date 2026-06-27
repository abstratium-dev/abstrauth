package dev.abstratium.abstrauth.boundary.publik;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * Tests for ConfigResource
 * Tests the /public/config endpoint
 */
@QuarkusTest
@TestProfile(ConfigResourceTest.TestProfile.class)
public class ConfigResourceTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("abstratium.stage", "test");
        }
    }

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
            .body("warningMessage", notNullValue())
            .body("auditRetentionDays", notNullValue());
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
    public void testGetConfigReturnsIntegerForAuditRetentionDays() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("auditRetentionDays", instanceOf(Integer.class));
    }

    @Test
    public void testGetConfigReturnsDefaultAuditRetentionDays() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .body("auditRetentionDays", equalTo(90)); // Default value
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

    @Test
    void testLegalContentIsNullWhenFileNotConfigured() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("legalContent", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void testConfigEndpointReturnsBrandFields() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("brandLogoUrl", notNullValue())
            .body("brandLogoUrl", equalTo("https://abstratium.dev/abstratium-logo-small.png"))
            .body("brandLogoAlt", notNullValue())
            .body("brandLogoAlt", equalTo("Abstratium Logo"))
            .body("brandName", notNullValue())
            .body("brandName", equalTo("ABSTRATIUM"));
    }

    @Test
    void testConfigEndpointReturnsStage() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("stage", notNullValue())
            .body("stage", is("test"));
    }
}
