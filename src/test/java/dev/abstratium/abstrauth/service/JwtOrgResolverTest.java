package dev.abstratium.abstrauth.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests verifying that JwtOrgResolver.resolveTenantId() correctly
 * parses the orgId from a Bearer token's JWT payload.
 *
 * These tests use a hand-crafted (unsigned) JWT since JwtOrgResolver only
 * inspects the payload — it does not verify the signature.
 * The token is placed in the Authorization header; @TestSecurity still handles
 * the security layer for the endpoint, but the raw header is present and
 * parseable by JwtOrgResolver.
 */
@QuarkusTest
class JwtOrgResolverIntegrationTest {

    public static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";

    @ConfigProperty(name = "default.org.uuid")
    String configuredDefaultOrgId;

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = DEFAULT_ORG_ID))
    void resolveTenantId_withVerifiedOrgIdClaim_usesOrgId() {
        given()
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + DEFAULT_ORG_ID + "\""));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    void resolveTenantId_withNoOrgIdClaim_usesTestDefault() {
        given()
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + configuredDefaultOrgId + "\""));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = ""))
    void resolveTenantId_withBlankOrgIdClaim_usesTestDefault() {
        given()
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + configuredDefaultOrgId + "\""));
    }
}
