package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegistrationResource
 */
@QuarkusTest
public class RegistrationResourceTest {

    @Test
    public void testGetRegistrationForm() {
        String html = given()
            .when()
            .get("/api/register")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .extract()
            .asString();
        
        assertTrue(html.contains("Create Account"));
        assertTrue(html.contains("Register for abstrauth"));
        assertTrue(html.contains("email"));
        assertTrue(html.contains("username"));
        assertTrue(html.contains("password"));
    }

    @Test
    public void testRegisterWithValidData() {
        String uniqueEmail = "regtest_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "regtest_" + System.currentTimeMillis();
        
        given()
            .formParam("email", uniqueEmail)
            .formParam("name", "Registration Test")
            .formParam("username", uniqueUsername)
            .formParam("password", "SecurePassword123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("email", equalTo(uniqueEmail))
            .body("name", equalTo("Registration Test"));
    }

    @Test
    public void testRegisterWithMissingEmail() {
        given()
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Email is required"));
    }

    @Test
    public void testRegisterWithBlankEmail() {
        given()
            .formParam("email", "   ")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Email is required"));
    }

    @Test
    public void testRegisterWithMissingUsername() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Username is required"));
    }

    @Test
    public void testRegisterWithBlankUsername() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "   ")
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Username is required"));
    }

    @Test
    public void testRegisterWithMissingPassword() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("Password must be at least 8 characters"));
    }

    @Test
    public void testRegisterWithShortPassword() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "short")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("at least 8 characters"));
    }

    @Test
    public void testRegisterWith7CharacterPassword() {
        given()
            .formParam("email", "test@example.com")
            .formParam("name", "Test User")
            .formParam("username", "testuser")
            .formParam("password", "1234567")
            .when()
            .post("/api/register")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("at least 8 characters"));
    }

    @Test
    public void testRegisterWith8CharacterPassword() {
        String uniqueEmail = "pw8test_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "pw8test_" + System.currentTimeMillis();
        
        given()
            .formParam("email", uniqueEmail)
            .formParam("name", "PW Test")
            .formParam("username", uniqueUsername)
            .formParam("password", "12345678")
            .when()
            .post("/api/register")
            .then()
            .statusCode(201);
    }

    @Test
    public void testRegisterWithDuplicateEmail() {
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        String username1 = "user1_" + System.currentTimeMillis();
        String username2 = "user2_" + System.currentTimeMillis();
        
        // First registration
        given()
            .formParam("email", email)
            .formParam("name", "User One")
            .formParam("username", username1)
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(201);
        
        // Second registration with same email
        given()
            .formParam("email", email)
            .formParam("name", "User Two")
            .formParam("username", username2)
            .formParam("password", "Password456")
            .when()
            .post("/api/register")
            .then()
            .statusCode(409)
            .body("error", equalTo("conflict"))
            .body("error_description", containsString("Email already exists"));
    }

    @Test
    public void testRegisterWithDuplicateUsername() {
        String email1 = "email1_" + System.currentTimeMillis() + "@example.com";
        String email2 = "email2_" + System.currentTimeMillis() + "@example.com";
        String username = "dupuser_" + System.currentTimeMillis();
        
        // First registration
        given()
            .formParam("email", email1)
            .formParam("name", "User One")
            .formParam("username", username)
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(201);
        
        // Second registration with same username
        given()
            .formParam("email", email2)
            .formParam("name", "User Two")
            .formParam("username", username)
            .formParam("password", "Password456")
            .when()
            .post("/api/register")
            .then()
            .statusCode(409)
            .body("error", equalTo("conflict"))
            .body("error_description", containsString("Username already exists"));
    }

    @Test
    public void testRegisterWithoutName() {
        String uniqueEmail = "noname_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "noname_" + System.currentTimeMillis();
        
        // Name is optional
        given()
            .formParam("email", uniqueEmail)
            .formParam("username", uniqueUsername)
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("email", equalTo(uniqueEmail));
    }

    @Test
    public void testRegisterReturnsJsonContentType() {
        String uniqueEmail = "jsontest_" + System.currentTimeMillis() + "@example.com";
        String uniqueUsername = "jsontest_" + System.currentTimeMillis();
        
        given()
            .formParam("email", uniqueEmail)
            .formParam("name", "JSON Test")
            .formParam("username", uniqueUsername)
            .formParam("password", "Password123")
            .when()
            .post("/api/register")
            .then()
            .contentType(containsString("application/json"));
    }

    @Test
    public void testRegisterErrorReturnsJsonContentType() {
        given()
            .formParam("email", "test@example.com")
            .formParam("password", "short")
            .when()
            .post("/api/register")
            .then()
            .contentType(containsString("application/json"));
    }
}
