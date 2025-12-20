package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.service.TokenRevocationService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for TokenRevocationFilter.
 * Verifies that revoked tokens cannot be used to access protected resources.
 */
@QuarkusTest
class TokenRevocationFilterTest {

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up test data
        em.createQuery("DELETE FROM RevokedToken").executeUpdate();
    }

    @Test
    void testValidTokenCanAccessProtectedResource() {
        // Given: A valid JWT token
        String token = generateValidToken();

        // When: Accessing a protected resource
        // Then: Should succeed
        given()
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/api/clients")
        .then()
                .statusCode(200);
    }

    @Test
    void testRevokedTokenCannotAccessProtectedResource() {
        // Given: A valid JWT token with a known JTI
        String jti = UUID.randomUUID().toString();
        String token = generateTokenWithJti(jti);
        
        // Verify token works initially
        given()
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/api/clients")
        .then()
                .statusCode(200);

        // When: Token is revoked (in a separate transaction that commits)
        revokeTokenInNewTransaction(jti);

        // Then: Token should no longer work
        given()
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/api/clients")
        .then()
                .statusCode(401)
                .body("error", equalTo("invalid_token"))
                .body("error_description", containsString("revoked"));
    }

    @Transactional
    void revokeTokenInNewTransaction(String jti) {
        tokenRevocationService.revokeToken(jti, "test_revocation");
    }

    @Test
    void testRequestWithoutTokenIsNotAffected() {
        // When: Accessing a protected resource without a token
        // Then: Should get 401 (unauthorized, not revoked)
        given()
        .when()
                .get("/api/clients")
        .then()
                .statusCode(401);
    }

    @Test
    void testPublicEndpointNotAffectedByRevocation() {
        // When: Accessing a public endpoint
        // Then: Should succeed regardless of revocation
        given()
        .when()
                .get("/.well-known/oauth-authorization-server")
        .then()
                .statusCode(200);
    }

    /**
     * Generate a valid JWT token with the required role for testing.
     */
    private String generateValidToken() {
        String jti = UUID.randomUUID().toString();
        return Jwt.issuer("https://abstrauth.abstratium.dev")
                .upn("test@example.com")
                .groups(java.util.Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
                .claim("jti", jti)
                .sign();
    }

    /**
     * Generate a valid JWT token with a specific JTI.
     */
    private String generateTokenWithJti(String jti) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
                .upn("test@example.com")
                .groups(java.util.Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
                .claim("jti", jti)
                .sign();
    }
}
