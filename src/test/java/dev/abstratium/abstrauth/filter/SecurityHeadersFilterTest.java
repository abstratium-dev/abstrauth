package dev.abstratium.abstrauth.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SecurityHeadersFilter to verify security headers are added to responses.
 */
@QuarkusTest
class SecurityHeadersFilterTest {

    @Test
    void shouldAddContentSecurityPolicyHeader() {
        given()
            .when()
            .get("/api/clients") // Use actual JAX-RS endpoint
            .then()
            .statusCode(anyOf(is(200), is(401), is(403))) // Any response is fine
            .header("Content-Security-Policy", notNullValue())
            .header("Content-Security-Policy", containsString("default-src 'self'"));
    }

    @Test
    void shouldAddXContentTypeOptionsHeader() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("X-Content-Type-Options", "nosniff");
    }

    @Test
    void shouldAddXFrameOptionsHeader() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("X-Frame-Options", "DENY");
    }

    @Test
    void shouldAddXXSSProtectionHeader() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("X-XSS-Protection", "1; mode=block");
    }

    @Test
    void shouldAddReferrerPolicyHeader() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Test
    void shouldAddPermissionsPolicyHeader() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("Permissions-Policy", notNullValue())
            .header("Permissions-Policy", containsString("geolocation=()"));
    }

    @Test
    void shouldNotAddHSTSHeaderInTestProfile() {
        // HSTS should be disabled in test profile (only enabled in prod)
        given()
            .when()
            .get("/api/clients")
            .then()
            //.log().all()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("Strict-Transport-Security", nullValue());
    }

    @Test
    void shouldAddSecurityHeadersToAPIEndpoints() {
        given()
            .when()
            .get("/api/signup")
            .then()
            .statusCode(anyOf(is(200), is(400), is(405))) // Method not allowed is fine
            .header("Content-Security-Policy", notNullValue())
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Frame-Options", "DENY");
    }

    @Test
    void shouldAddSecurityHeadersToOAuthEndpoints() {
        given()
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(anyOf(is(200), is(302), is(400)))
            .header("Content-Security-Policy", notNullValue())
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Frame-Options", "DENY");
    }

    @Test
    void shouldIncludeAllCSPDirectives() {
        String cspHeader = given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .extract()
            .header("Content-Security-Policy");

        // Verify key CSP directives are present
        assertTrue(cspHeader.contains("default-src 'self'"), "CSP should contain default-src");
        assertTrue(cspHeader.contains("script-src"), "CSP should contain script-src");
        assertTrue(cspHeader.contains("style-src"), "CSP should contain style-src");
        assertTrue(cspHeader.contains("img-src"), "CSP should contain img-src");
        assertTrue(cspHeader.contains("font-src"), "CSP should contain font-src");
        assertTrue(cspHeader.contains("connect-src"), "CSP should contain connect-src");
        assertTrue(cspHeader.contains("frame-ancestors 'none'"), "CSP should contain frame-ancestors");
        assertTrue(cspHeader.contains("base-uri 'self'"), "CSP should contain base-uri");
        assertTrue(cspHeader.contains("form-action 'self'"), "CSP should contain form-action");
    }
}
