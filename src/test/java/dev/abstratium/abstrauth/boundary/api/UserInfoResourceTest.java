package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Tests for UserInfoResource endpoint.
 * Tests the BFF pattern where the backend provides user info from OIDC ID token.
 * 
 * Note: This endpoint requires OIDC authentication which is difficult to mock in unit tests.
 * The main testing for this endpoint is done through integration/e2e tests where
 * actual OIDC authentication flow is performed.
 */
@QuarkusTest
public class UserInfoResourceTest {

    @Test
    void shouldReturn401WhenNotAuthenticated() {
        // Without OIDC authentication, should return 401
        given()
            .when()
            .get("/api/userinfo")
            .then()
            .statusCode(401);
    }
    
    /**
     * Additional tests for authenticated scenarios would require:
     * 1. Setting up OIDC test configuration
     * 2. Mocking ID token with claims
     * 3. Or using integration tests with actual OAuth flow
     * 
     * These are better covered by:
     * - CompleteOAuthFlowTest (Java integration test)
     * - E2E tests with Playwright
     */
}
