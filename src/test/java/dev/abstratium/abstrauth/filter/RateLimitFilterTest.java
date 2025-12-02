package dev.abstratium.abstrauth.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for RateLimitFilter to verify rate limiting is applied to OAuth endpoints.
 * Note: Rate limiting is disabled by default in test profile, so we override it here.
 */
@QuarkusTest
@TestProfile(RateLimitFilterTest.RateLimitTestProfile.class)
class RateLimitFilterTest {

    @Inject
    RateLimitFilter rateLimitFilter;

    /**
     * Test profile with stricter rate limits for easier testing.
     * Explicitly enables rate limiting (disabled by default in test profile).
     */
    public static class RateLimitTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "rate-limit.enabled", "true",  // Override test profile default (false)
                "rate-limit.oauth.max-requests", "3",  // Low limit for testing
                "rate-limit.oauth.window-seconds", "60",
                "rate-limit.oauth.ban-duration-seconds", "5"  // Short ban for testing
            );
        }
    }

    @BeforeEach
    void setUp() {
        // Clear rate limit state before each test
        rateLimitFilter.clearAll();
    }

    @Test
    void shouldAllowRequestsUnderRateLimit() {
        // First request should succeed
        given()
            .when()
            .get("/oauth2/authorize?client_id=test")
            .then()
            .statusCode(anyOf(is(200), is(302), is(400))) // Any non-429 is fine
            .header("X-RateLimit-Limit", notNullValue());
    }

    @Test
    void shouldAddRateLimitHeaders() {
        given()
            .when()
            .get("/oauth2/authorize?client_id=test")
            .then()
            .statusCode(anyOf(is(200), is(302), is(400)))
            .header("X-RateLimit-Limit", "3")
            .header("X-RateLimit-Remaining", notNullValue())
            .header("X-RateLimit-Reset", notNullValue());
    }

    @Test
    void shouldBlockRequestsExceedingRateLimit() {
        // Make requests up to the limit
        for (int i = 0; i < 3; i++) {
            given()
                .when()
                .get("/oauth2/authorize?client_id=test&request=" + i)
                .then()
                .statusCode(anyOf(is(200), is(302), is(400)));
        }

        // Next request should be rate limited
        given()
            .when()
            .get("/oauth2/authorize?client_id=test&request=exceeded")
            .then()
            .statusCode(429)
            .header("Retry-After", notNullValue())
            .body(containsString("Rate limit exceeded"));
    }

    @Test
    void shouldApplyRateLimitToTokenEndpoint() {
        given()
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(anyOf(is(400), is(401), is(429))) // Should have rate limit headers
            .header("X-RateLimit-Limit", notNullValue());
    }

    @Test
    void shouldApplyRateLimitToSignupEndpoint() {
        given()
            .when()
            .post("/api/signup")
            .then()
            .statusCode(anyOf(is(400), is(429))) // Should have rate limit headers
            .header("X-RateLimit-Limit", notNullValue());
    }

    @Test
    void shouldNotApplyRateLimitToNonOAuthEndpoints() {
        // Regular API endpoints that are not OAuth-related should not be rate limited
        // (though they'll still get security headers)
        given()
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(401), is(403)))
            .header("X-RateLimit-Limit", nullValue()); // No rate limit headers
    }

    @Test
    void shouldDecrementRemainingCount() {
        // First request
        int remaining1 = Integer.parseInt(
            given()
                .when()
                .get("/oauth2/authorize?client_id=test&req=1")
                .then()
                .statusCode(anyOf(is(200), is(302), is(400)))
                .extract()
                .header("X-RateLimit-Remaining")
        );

        // Second request
        int remaining2 = Integer.parseInt(
            given()
                .when()
                .get("/oauth2/authorize?client_id=test&req=2")
                .then()
                .statusCode(anyOf(is(200), is(302), is(400)))
                .extract()
                .header("X-RateLimit-Remaining")
        );

        // Remaining should decrease
        org.junit.jupiter.api.Assertions.assertTrue(
            remaining2 < remaining1,
            "Remaining count should decrease with each request"
        );
    }
}
