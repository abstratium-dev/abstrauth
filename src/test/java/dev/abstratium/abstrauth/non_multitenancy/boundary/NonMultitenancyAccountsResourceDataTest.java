package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.FederatedIdentityService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tests for the right-of-access endpoints GET /api/accounts/me/data and
 * GET /api/accounts/me/data/export.
 */
@QuarkusTest
public class NonMultitenancyAccountsResourceDataTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    FederatedIdentityService federatedIdentityService;

    @Inject
    OrganisationService organisationService;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    public void resetDatabaseBeforeTest() throws Exception {
        transactionHelper.beginTransaction();
        dbResetHelper.resetDatabase();
        transactionHelper.commitTransaction();
    }

    private String generateUserToken(String accountId, String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("user@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", "user@example.com")
            .claim("name", "Regular User")
            .claim("orgId", orgId)
            .sign();
    }

    @Test
    public void testGetMyDataReturnsAccountDetails() throws Exception {
        transactionHelper.beginTransaction();
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        String email = "mydata_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "My Data User", "mydata" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, defaultOrgId))
            .when()
            .get("/api/accounts/me/data")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("account.id", equalTo(accountId))
            .body("account.email", equalTo(email))
            .body("account.name", equalTo("My Data User"))
            .body("account.authProvider", equalTo("native"))
            .body("exportTimestamp", notNullValue());
    }

    @Test
    public void testGetMyDataIncludesCredentialsAndRoles() throws Exception {
        transactionHelper.beginTransaction();
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        String email = "mydata2_" + System.currentTimeMillis() + "@example.com";
        String username = "mydata2" + System.currentTimeMillis();
        Account account = accountService.createAccountForOrg(email, "My Data User 2", username, "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        accountRoleService.addRole(accountId, "test-client", "viewer");
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, defaultOrgId))
            .when()
            .get("/api/accounts/me/data")
            .then()
            .statusCode(200)
            .body("credentials.size()", equalTo(1))
            .body("credentials[0].username", equalTo(username))
            .body("credentials[0].passwordHash", equalTo(null))
            .body("roles.size()", equalTo(2))
            .body("roles.find { it.clientId == 'test-client' && it.role == 'viewer' }", notNullValue());
    }

    @Test
    public void testGetMyDataIncludesFederatedIdentityAndMemberships() throws Exception {
        transactionHelper.beginTransaction();
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        String email = "mydata3_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "My Data User 3", "mydata3" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        federatedIdentityService.createFederatedIdentity(accountId, "google", "google-123", "google@example.com");
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, defaultOrgId))
            .when()
            .get("/api/accounts/me/data")
            .then()
            .statusCode(200)
            .body("federatedIdentities.size()", equalTo(1))
            .body("federatedIdentities[0].provider", equalTo("google"))
            .body("federatedIdentities[0].providerUserId", equalTo("google-123"))
            .body("organisationMemberships.size()", equalTo(1))
            .body("organisationMemberships[0].orgId", equalTo(defaultOrgId))
            .body("organisationMemberships[0].role", equalTo("member"));
    }

    @Test
    public void testExportMyDataReturnsAttachment() throws Exception {
        transactionHelper.beginTransaction();
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        String email = "export_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Export User", "export" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, defaultOrgId))
            .when()
            .get("/api/accounts/me/data/export")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .header("Content-Disposition", containsString("attachment"))
            .header("Content-Disposition", containsString("abstrauth-personal-data-" + accountId))
            .body("account.id", equalTo(accountId))
            .body("account.email", equalTo(email));
    }

    @Test
    public void testGetMyDataWithoutTokenReturns401() {
        given()
            .when()
            .get("/api/accounts/me/data")
            .then()
            .statusCode(401);
    }

    @Test
    public void testExportMyDataWithoutTokenReturns401() {
        given()
            .when()
            .get("/api/accounts/me/data/export")
            .then()
            .statusCode(401);
    }
}
