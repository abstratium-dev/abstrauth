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
 * Tests for SecurityHeadersFilter with HSTS enabled but without subdomains and preload.
 */
@QuarkusTest
@TestProfile(SecurityHeadersFilterHstsMinimalTest.HstsMinimalProfile.class)
class SecurityHeadersFilterHstsMinimalTest {

    public static class HstsMinimalProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "security.hsts.enabled", "true",
                "security.hsts.max-age", "3600",
                "security.hsts.include-subdomains", "false",
                "security.hsts.preload", "false"
            );
        }
    }

    @Test
    void shouldAddHSTSWithoutSubDomainsAndPreload() {
        String hstsHeader = given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .extract()
            .header("Strict-Transport-Security");

        assertTrue(hstsHeader.contains("max-age=3600"), "HSTS should contain max-age");
        assertTrue(!hstsHeader.contains("includeSubDomains"), "HSTS should not include subdomains");
        assertTrue(!hstsHeader.contains("preload"), "HSTS should not include preload");
    }
}
