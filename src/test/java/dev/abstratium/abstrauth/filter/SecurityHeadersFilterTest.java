package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AuthorizationService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SecurityHeadersFilter to verify security headers are added to responses.
 */
@QuarkusTest
class SecurityHeadersFilterTest {

    @Inject
    AuthorizationService authorizationService;

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

    @Test
    void shouldAllowRedirectOriginInFormActionOnConsentPage() {
        // Create an authorization request whose (already validated) redirect URI
        // points at a cross-origin client callback, as in the e2e flow.
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                "test-client",
                "http://localhost:3333/oauth/callback",
                "openid profile email",
                "test-state",
                "test-challenge",
                "S256");

        given()
            .when()
            .get("/signin/" + authRequest.getId())
            .then()
            .statusCode(anyOf(is(200), is(404))) // Quinoa may or may not serve the SPA in tests
            .header("Content-Security-Policy", containsString("form-action 'self' http://localhost:3333"))
            // Both http and https variants are included so that Chrome's
            // form-action enforcement (which covers the entire redirect chain)
            // does not block the chain when a client app behind a
            // TLS-terminating reverse proxy issues an http:// redirect.
            .header("Content-Security-Policy", containsString("https://localhost:3333"));
    }

    @Test
    void shouldAllowBothHttpAndHttpsOriginsForHttpsRedirectUri() {
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                "test-client",
                "https://accounts.example.com/oauth/callback",
                "openid profile email",
                "test-state",
                "test-challenge",
                "S256");

        given()
            .when()
            .get("/signin/" + authRequest.getId())
            .then()
            .statusCode(anyOf(is(200), is(404)))
            .header("Content-Security-Policy", containsString("https://accounts.example.com"))
            .header("Content-Security-Policy", containsString("http://accounts.example.com"));
    }

    @Test
    void shouldKeepDefaultFormActionForUnknownConsentRequest() {
        given()
            .when()
            .get("/signin/does-not-exist")
            .then()
            .statusCode(anyOf(is(200), is(404)))
            .header("Content-Security-Policy", containsString("form-action 'self'"))
            .header("Content-Security-Policy", not(containsString("form-action 'self' http://")));
    }

    @Test
    void shouldKeepDefaultFormActionForNonConsentPaths() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("Content-Security-Policy", containsString("form-action 'self'"))
            .header("Content-Security-Policy", not(containsString("form-action 'self' http://")));
    }

    @Test
    void shouldDeriveCspSourceFromHttpRedirectUri() {
        assertEquals("http://localhost:3333",
                SecurityHeadersFilter.cspSourceForUri("http://localhost:3333/oauth/callback"));
    }

    @Test
    void shouldDeriveCspSourceFromHttpsRedirectUri() {
        assertEquals("https://client.example.com",
                SecurityHeadersFilter.cspSourceForUri("https://client.example.com/cb"));
    }

    @Test
    void shouldKeepNonDefaultPortInCspSource() {
        assertEquals("https://client.example.com:8443",
                SecurityHeadersFilter.cspSourceForUri("https://client.example.com:8443/cb"));
    }

    @Test
    void shouldDropDefaultPortFromCspSource() {
        assertEquals("http://localhost",
                SecurityHeadersFilter.cspSourceForUri("http://localhost:80/cb"));
    }

    @Test
    void shouldDeriveSchemeSourceForCustomScheme() {
        assertEquals("myapp:", SecurityHeadersFilter.cspSourceForUri("myapp://oauth/callback"));
    }

    @Test
    void shouldReturnNullForInvalidOrMissingRedirectUri() {
        assertNull(SecurityHeadersFilter.cspSourceForUri(null));
        assertNull(SecurityHeadersFilter.cspSourceForUri(""));
        assertNull(SecurityHeadersFilter.cspSourceForUri("not a uri"));
        assertNull(SecurityHeadersFilter.cspSourceForUri("relative/path"));
    }

    @Test
    void shouldDeriveBothSchemesFromHttpRedirectUri() {
        String sources = SecurityHeadersFilter.cspSourcesForUri("http://localhost:3333/oauth/callback");
        assertNotNull(sources);
        assertTrue(sources.contains("http://localhost:3333"), "should contain http variant");
        assertTrue(sources.contains("https://localhost:3333"), "should contain https variant");
    }

    @Test
    void shouldDeriveBothSchemesFromHttpsRedirectUri() {
        String sources = SecurityHeadersFilter.cspSourcesForUri("https://accounts.example.com/oauth/callback");
        assertNotNull(sources);
        assertTrue(sources.contains("https://accounts.example.com"), "should contain https variant");
        assertTrue(sources.contains("http://accounts.example.com"), "should contain http variant");
    }

    @Test
    void shouldKeepNonDefaultPortInBothSchemeVariants() {
        String sources = SecurityHeadersFilter.cspSourcesForUri("https://client.example.com:8443/cb");
        assertNotNull(sources);
        assertTrue(sources.contains("https://client.example.com:8443"), "should contain https variant with port");
        assertTrue(sources.contains("http://client.example.com:8443"), "should contain http variant with port");
    }

    @Test
    void shouldReturnSingleSourceForCustomScheme() {
        // Custom URL schemes (e.g. myapp://) don't have http/https variants
        assertEquals("myapp:", SecurityHeadersFilter.cspSourcesForUri("myapp://oauth/callback"));
    }

    @Test
    void shouldReturnNullForInvalidOrMissingRedirectUriInSources() {
        assertNull(SecurityHeadersFilter.cspSourcesForUri(null));
        assertNull(SecurityHeadersFilter.cspSourcesForUri(""));
        assertNull(SecurityHeadersFilter.cspSourcesForUri("not a uri"));
        assertNull(SecurityHeadersFilter.cspSourcesForUri("relative/path"));
    }
}
