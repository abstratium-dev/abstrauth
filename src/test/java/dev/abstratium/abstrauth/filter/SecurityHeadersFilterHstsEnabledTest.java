package dev.abstratium.abstrauth.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SecurityHeadersFilter with HSTS enabled.
 */
@QuarkusTest
@TestProfile(SecurityHeadersFilterHstsEnabledTest.HstsEnabledProfile.class)
class SecurityHeadersFilterHstsEnabledTest {

    public static class HstsEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "security.hsts.enabled", "true",
                "security.hsts.max-age", "31536000",
                "security.hsts.include-subdomains", "true",
                "security.hsts.preload", "true"
            );
        }
    }

    @Test
    void shouldAddHSTSHeaderWhenEnabled() {
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("Strict-Transport-Security", notNullValue());
    }

    @Test
    void shouldIncludeSubDomainsInHSTS() {
        String hstsHeader = given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .extract()
            .header("Strict-Transport-Security");

        assertTrue(hstsHeader.contains("max-age=31536000"), "HSTS should contain max-age");
        assertTrue(hstsHeader.contains("includeSubDomains"), "HSTS should include subdomains");
        assertTrue(hstsHeader.contains("preload"), "HSTS should include preload");
    }
}
