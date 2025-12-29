package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class AuthErrorResourceTest {

    @Test
    public void testErrorHandlerWithInvalidScope() {
        given()
            .queryParam("error", "invalid_scope")
            .queryParam("error_description", "Requested scope is not allowed")
            .queryParam("state", "test-state-123")
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .contentType(containsString("text/html"))
            .body(containsString("Authentication Error"))
            .body(containsString("Requested scope is not allowed"))
            .body(containsString("invalid_scope"));
    }

    @Test
    public void testErrorHandlerWithAccessDenied() {
        given()
            .queryParam("error", "access_denied")
            .queryParam("error_description", "User cancelled the authorization")
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .contentType(containsString("text/html"))
            .body(containsString("Authentication Error"))
            .body(containsString("User cancelled the authorization"))
            .body(containsString("access_denied"));
    }

    @Test
    public void testErrorHandlerWithoutDescription() {
        given()
            .queryParam("error", "invalid_scope")
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .contentType(containsString("text/html"))
            .body(containsString("Authentication Error"))
            .body(containsString("requested permissions are not allowed"))
            .body(containsString("invalid_scope"));
    }

    @Test
    public void testErrorHandlerWithUnknownError() {
        given()
            .queryParam("error", "unknown_error_code")
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .contentType(containsString("text/html"))
            .body(containsString("Authentication Error"))
            .body(containsString("authentication error occurred"))
            .body(containsString("unknown_error_code"));
    }

    @Test
    public void testErrorHandlerWithNoParameters() {
        given()
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .contentType(containsString("text/html"))
            .body(containsString("Authentication Error"))
            .body(containsString("unknown_error"));
    }

    @Test
    public void testErrorHandlerWithServerError() {
        given()
            .queryParam("error", "server_error")
            .queryParam("error_description", "Internal server error occurred")
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .contentType(containsString("text/html"))
            .body(containsString("Authentication Error"))
            .body(containsString("Internal server error occurred"))
            .body(containsString("server_error"));
    }

    @Test
    public void testErrorHandlerHasReturnHomeLink() {
        given()
            .queryParam("error", "invalid_scope")
            .when()
            .get("/api/auth/error")
            .then()
            .statusCode(401)
            .body(containsString("href=\"/\""))
            .body(containsString("Return to Home"));
    }
}
