package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Test that exercises Hibernate multi-tenancy (DISCRIMINATOR) SQL generation.
 * 
 * Run with SQL logging enabled to observe the generated queries.
 * The test calls a test-only endpoint that performs CREATE, SELECT (find + JPQL),
 * UPDATE (dirty-check + JPQL), and DELETE (remove + JPQL) operations on OAuthClient.
 *
 * To see the SQL clearly, run with:
 *   python3 scripts/run-java-tests.py
 * 
 * Or for just this test with SQL visible:
 *   ./mvnw test -Dtest=MultiTenancySqlDebugTest -Dquarkus.hibernate-orm.log.sql=true -Dexec.skip=true
 */
@QuarkusTest
public class MultiTenancySqlDebugTest {

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String generateToken(String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
                .subject("test-user")
                .claim("orgId", orgId)
                .groups(Set.of("admin"))
                .sign();
    }

    @Test
    public void testMultiTenancySqlGeneration_defaultOrg() {
        String orgId = defaultOrgId;
        String token = generateToken(orgId);

        given()
            .auth().oauth2(token)
            .header("X-Org-Id", orgId)
            .when()
            .post("/test/mt-sql-debug/run-all")
            .then()
            .statusCode(200)
            .body(containsString("PERSIST done"))
            .body(containsString("FIND done"))
            .body(containsString("JPQL SELECT done"))
            .body(containsString("UPDATE done"))
            .body(containsString("JPQL UPDATE done"))
            .body(containsString("DELETE done"))
            .body(containsString("JPQL DELETE done"));
    }

    @Test
    public void testMultiTenancySqlGeneration_customOrg() {
        String customOrgId = "11111111-1111-1111-1111-111111111111";
        String token = generateToken(customOrgId);

        given()
            .auth().oauth2(token)
            .header("X-Org-Id", customOrgId)
            .when()
            .post("/test/mt-sql-debug/run-all")
            .then()
            .statusCode(200)
            .body(containsString("PERSIST done"))
            .body(containsString("FIND done"))
            .body(containsString("JPQL SELECT done"))
            .body(containsString("UPDATE done"))
            .body(containsString("JPQL UPDATE done"))
            .body(containsString("DELETE done"))
            .body(containsString("JPQL DELETE done"));
    }

    @Test
    public void testTenantIdNullOnPersist() {
        String orgId = defaultOrgId;
        String token = generateToken(orgId);

        given()
            .auth().oauth2(token)
            .header("X-Org-Id", orgId)
            .when()
            .post("/test/mt-sql-debug/run-tenant-null-test")
            .then()
            .statusCode(200)
            .body(containsString("NULL_TEST stored orgId="));
    }

    @Test
    public void testTenantIdWrongOnPersist() {
        String orgId = defaultOrgId;
        String token = generateToken(orgId);

        given()
            .auth().oauth2(token)
            .header("X-Org-Id", orgId)
            .when()
            .post("/test/mt-sql-debug/run-tenant-wrong-test")
            .then()
            .statusCode(200)
            .body(containsString("WRONG_TEST error=PropertyValueException"));
    }
}
