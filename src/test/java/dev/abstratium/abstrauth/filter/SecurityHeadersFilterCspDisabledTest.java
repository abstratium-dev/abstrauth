package dev.abstratium.abstrauth.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for SecurityHeadersFilter with CSP disabled.
 */
@QuarkusTest
@TestProfile(SecurityHeadersFilterCspDisabledTest.CspDisabledProfile.class)
class SecurityHeadersFilterCspDisabledTest {

    public static class CspDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("security.csp.enabled", "false");
        }
    }

    @Test
    void shouldNotAddCSPHeaderWhenDisabled() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("Content-Security-Policy", nullValue());
    }

    @Test
    void shouldStillAddOtherSecurityHeaders() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Frame-Options", "DENY")
            .header("X-XSS-Protection", "1; mode=block");
    }
}
