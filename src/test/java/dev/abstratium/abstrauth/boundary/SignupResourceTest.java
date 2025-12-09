package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for SignupResource
 */
@QuarkusTest
public class SignupResourceTest {

    @Test
    public void testSignupWithValidData() {
        String uniqueEmail = "signuptest_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "signuptest_" + System.currentTimeMillis();
        
        given()
            .formParam("email", uniqueEmail)
            .formParam("name", "Signup Test")
            .formParam("username", uniqueUsername)
            .formParam("password", "SecurePassword123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("email", equalTo(uniqueEmail))
            .body("name", equalTo("Signup Test"));
    }

    @Test
    public void testSignupWithMissingEmail() {
        given()
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Email is required"));
    }

    @Test
    public void testSignupWithBlankEmail() {
        given()
            .formParam("email", "   ")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Email is required"));
    }

    @Test
    public void testSignupWithMissingUsername() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Username is required"));
    }

    @Test
    public void testSignupWithBlankUsername() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "   ")
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Username is required"));
    }

    @Test
    public void testSignupWithMissingPassword() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Password must be at least 8 characters"));
    }

    @Test
    public void testSignupWithShortPassword() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "short")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("at least 8 characters"));
    }

    @Test
    public void testSignupWith7CharacterPassword() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "1234567")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("at least 8 characters"));
    }

    @Test
    public void testSignupWith8CharacterPassword() {
        String uniqueEmail = "pw8test_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "pw8test_" + System.currentTimeMillis();
        
        given()
            .formParam("email", uniqueEmail)
            .formParam("name", "PW Test")
            .formParam("username", uniqueUsername)
            .formParam("password", "12345678")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201);
    }

    @Test
    public void testSignupWithDuplicateEmail() {
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        String username1 = "user1_" + System.currentTimeMillis();
        String username2 = "user2_" + System.currentTimeMillis();
        
        // First sign up
        given()
            .formParam("email", email)
            .formParam("name", "User One")
            .formParam("username", username1)
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201);
        
        // Second sign up with same email
        given()
            .formParam("email", email)
            .formParam("name", "User Two")
            .formParam("username", username2)
            .formParam("password", "Password456")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(409)
            .body("error", equalTo("conflict"))
            .body("error_description", containsString("Email already exists"));
    }

    @Test
    public void testSignupWithDuplicateUsername() {
        String email1 = "email1_" + System.currentTimeMillis() + "@example.com";
        String email2 = "email2_" + System.currentTimeMillis() + "@example.com";
        String username = "dupuser_" + System.currentTimeMillis();
        
        // First sign up
        given()
            .formParam("email", email1)
            .formParam("name", "User One")
            .formParam("username", username)
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201);
        
        // Second sign up with same username
        given()
            .formParam("email", email2)
            .formParam("name", "User Two")
            .formParam("username", username)
            .formParam("password", "Password456")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(409)
            .body("error", equalTo("conflict"))
            .body("error_description", containsString("Username already exists"));
    }

    @Test
    public void testSignupWithoutName() {
        String uniqueEmail = "noname_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "noname_" + System.currentTimeMillis();
        
        // Name is optional
        given()
            .formParam("email", uniqueEmail)
            .formParam("username", uniqueUsername)
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("email", equalTo(uniqueEmail));
    }

    @Test
    public void testSignupReturnsJsonContentType() {
        String uniqueEmail = "jsontest_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "jsontest_" + System.currentTimeMillis();
        
        given()
            .formParam("email", uniqueEmail)
            .formParam("name", "JSON Test")
            .formParam("username", uniqueUsername)
            .formParam("password", "Password123")
            .when()
            .post("/api/signup")
            .then()
            .contentType(containsString("application/json"));
    }

    @Test
    public void testSignupErrorReturnsJsonContentType() {
        given()
            .formParam("email", "test@example.com")
            .formParam("password", "short")
            .when()
            .post("/api/signup")
            .then()
            .contentType(containsString("application/json"));
    }

    @Test
    public void testSignupAllowedEndpoint() {
        given()
            .when()
            .get("/api/signup/allowed")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("allowed", notNullValue());
    }
}
