package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;

/**
 * Tests for NonMultitenancyOrganisationsResource.
 *
 * These tests verify the cross-tenant organisation creation endpoint that uses
 * non-multitenancy services to assign initial roles before the orgId is known to Hibernate.
 */
@QuarkusTest
public class NonMultitenancyOrganisationsResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    TestTransactionHelper transactionHelper;

    private String userToken(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
                .subject(accountId)
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", orgId)
                .sign();
    }

    private Account createAccount(String suffix) throws Exception {
        transactionHelper.beginTransaction();
        Account account = accountService.createAccount(
                "nmorgtest_" + suffix + "@example.com",
                "NMOrgTest " + suffix,
                "nmorgtest_" + suffix,
                "Pass123!",
                AccountService.NATIVE,
                "Test Org " + suffix);
        transactionHelper.commitTransaction();
        return account;
    }

    private String accountOrgId(Account account) {
        return organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/organisations (cross-tenant organisation creation)
    // ─────────────────────────────────────────────────────────

    @Test
    public void testCreateOrganisation_success() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_create");
        String token = userToken(account.getId(), accountOrgId(account));

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"New Org " + System.currentTimeMillis() + "\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("name", notNullValue());
    }

    @Test
    public void testCreateOrganisation_assignsManagementRoles() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_roles");
        String token = userToken(account.getId(), accountOrgId(account));

        // Create organisation
        String orgId = given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Role Test Org " + System.currentTimeMillis() + "\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Verify management roles were assigned using non-multitenancy service
        var roles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
                account.getId(), Roles.CLIENT_ID, orgId);

        assertTrue(roles.contains(Roles._USER_PLAIN),
                "Account should have user role in new org");
        assertTrue(roles.contains(Roles._MANAGE_ACCOUNTS_PLAIN),
                "Account should have manage-accounts role in new org");
        assertTrue(roles.contains(Roles._MANAGE_CLIENTS_PLAIN),
                "Account should have manage-clients role in new org");
    }

    @Test
    public void testCreateOrganisation_blankName_returns400() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_blankname");
        String token = userToken(account.getId(), accountOrgId(account));

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(400);
    }

    @Test
    public void testCreateOrganisation_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Org\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(401);
    }
}
