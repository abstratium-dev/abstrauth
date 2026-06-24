package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@QuarkusTest
public class AuditHistoryResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    public void resetDatabase() throws Exception {
        transactionHelper.beginTransaction();
        dbResetHelper.resetDatabase();
        transactionHelper.commitTransaction();
    }

    // ---- Token generators ----

    private String generateManageAccountsToken(String accountId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("manager@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-accounts"))
            .claim("email", "manager@example.com")
            .claim("name", "Account Manager")
            .claim("orgId", defaultOrgId)
            .sign();
    }

    private String generateManageClientsToken(String accountId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("clientmanager@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-clients"))
            .claim("email", "clientmanager@example.com")
            .claim("name", "Client Manager")
            .claim("orgId", defaultOrgId)
            .sign();
    }

    private String generateBothRolesToken(String accountId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("both@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-accounts", "abstratium-abstrauth_manage-clients"))
            .claim("email", "both@example.com")
            .claim("name", "Both Roles")
            .claim("orgId", defaultOrgId)
            .sign();
    }

    private String generateUserOnlyToken(String accountId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("user@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", "user@example.com")
            .claim("name", "Regular User")
            .claim("orgId", defaultOrgId)
            .sign();
    }

    private Account createTestAccount() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        return accountService.createAccountForOrg(
            "auditres_" + ts + "@example.com", "Audit Res " + ts,
            "auditres_" + ts, "Password123", AccountService.NATIVE, defaultOrgId);
    }

    // ---- /api/audit/types tests ----

    @Test
    public void testListTypesWithoutTokenReturns401() {
        given()
            .when()
            .get("/api/audit/types")
            .then()
            .statusCode(401);
    }

    @Test
    public void testListTypesWithInvalidTokenReturns401() {
        given()
            .header("Authorization", "Bearer invalid-token")
            .when()
            .get("/api/audit/types")
            .then()
            .statusCode(401);
    }

    @Test
    public void testListTypesWithUserOnlyRoleReturns403() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateUserOnlyToken(account.getId()))
            .when()
            .get("/api/audit/types")
            .then()
            .statusCode(403);
    }

    @Test
    public void testListTypesWithManageAccountsReturnsOnlyAccountTypes() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/types")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasItems("account", "credential", "account_role", "organisation"))
            .body("$", not(hasItems("oauth_client", "client_secret", "subscription")));
    }

    @Test
    public void testListTypesWithManageClientsReturnsOnlyClientTypes() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageClientsToken(account.getId()))
            .when()
            .get("/api/audit/types")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasItems("oauth_client", "client_secret", "subscription"))
            .body("$", not(hasItems("account", "credential", "organisation")));
    }

    @Test
    public void testListTypesWithBothRolesReturnsAllTypes() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateBothRolesToken(account.getId()))
            .when()
            .get("/api/audit/types")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(10));
    }

    // ---- /api/audit/{entityType}/{pk} tests ----

    @Test
    public void testGetHistoryWithoutTokenReturns401() {
        given()
            .when()
            .get("/api/audit/account/some-id")
            .then()
            .statusCode(401);
    }

    @Test
    public void testGetHistoryWithUserOnlyRoleReturns403() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateUserOnlyToken(account.getId()))
            .when()
            .get("/api/audit/account/" + account.getId())
            .then()
            .statusCode(403);
    }

    @Test
    public void testGetAccountHistoryWithManageAccountsReturns200() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/account/" + account.getId())
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    public void testGetAccountHistoryHasAuditEntries() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/account/" + account.getId())
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].id", equalTo(account.getId()))
            .body("[0].revType", equalTo(0));
    }

    @Test
    public void testGetClientHistoryWithManageAccountsReturnsForbidden() throws Exception {
        Account account = createTestAccount();

        // Create an oauth client so the entity type is valid and has data
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId("forbid-test-client-" + System.currentTimeMillis());
        client.setClientName("Forbid Test Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost/cb\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);
        transactionHelper.commitTransaction();

        // manage-accounts should NOT be able to query oauth_client history
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/oauth_client/" + client.getId())
            .then()
            .statusCode(403)
            .body("error", containsString("Insufficient permissions"));
    }

    @Test
    public void testGetAccountHistoryWithManageClientsReturns403() throws Exception {
        Account account = createTestAccount();
        // manage-clients should NOT be able to query account history
        given()
            .auth().oauth2(generateManageClientsToken(account.getId()))
            .when()
            .get("/api/audit/account/" + account.getId())
            .then()
            .statusCode(403)
            .body("error", containsString("Insufficient permissions"));
    }

    @Test
    public void testGetOAuthClientHistoryWithManageClientsReturns200() throws Exception {
        Account account = createTestAccount();

        // Create an oauth client to have audit data
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId("audit-test-client-" + System.currentTimeMillis());
        client.setClientName("Audit Test Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost/cb\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateManageClientsToken(account.getId()))
            .when()
            .get("/api/audit/oauth_client/" + client.getId())
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    public void testGetHistoryForUnknownEntityTypeReturns400() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/nonexistent_entity/some-id")
            .then()
            .statusCode(400)
            .body("error", containsString("Unknown entity type"));
    }

    @Test
    public void testGetHistoryForNonExistentAccountReturnsEmpty() throws Exception {
        // Verify org filtering indirectly: a random UUID that doesn't exist in this org returns empty
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/account/ffffffff-ffff-ffff-ffff-ffffffffffff")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", equalTo(0));
    }

    // ---- /api/audit/related/{relatedEntityType}/by-{parentEntityType}/{parentKey} tests ----

    @Test
    public void testGetRelatedHistoryWithoutTokenReturns401() {
        given()
            .when()
            .get("/api/audit/related/account_role/by-account/some-id")
            .then()
            .statusCode(401);
    }

    @Test
    public void testGetRelatedHistoryWithUserOnlyRoleReturns403() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateUserOnlyToken(account.getId()))
            .when()
            .get("/api/audit/related/account_role/by-account/" + account.getId())
            .then()
            .statusCode(403);
    }

    @Test
    public void testGetRelatedAccountRoleHistoryReturns200() throws Exception {
        Account account = createTestAccount();

        // Add a role via the REST API which goes through the audited AccountRoleService
        // First, create a client and allowed role so the role can be assigned
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient auditRoleClient = new dev.abstratium.abstrauth.entity.OAuthClient();
        String auditClientId = "audit-role-client-" + System.currentTimeMillis();
        auditRoleClient.setClientId(auditClientId);
        auditRoleClient.setClientName("Audit Role Client");
        auditRoleClient.setClientType("confidential");
        auditRoleClient.setRedirectUris("[\"http://localhost/cb\"]");
        auditRoleClient.setAllowedScopes("[]");
        auditRoleClient.setRequirePkce(true);
        em.persist(auditRoleClient);
        // Add an allowed role for this client
        dev.abstratium.abstrauth.entity.ClientAllowedRole allowedRole = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        allowedRole.setClientId(auditClientId);
        allowedRole.setRole("test-role");
        allowedRole.setIsDefault(false);
        allowedRole.setAvailableToForeignOrgs(false);
        em.persist(allowedRole);
        transactionHelper.commitTransaction();

        // Now add the role via the accounts REST API
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .contentType(ContentType.JSON)
            .body("{\"accountId\":\"" + account.getId() + "\",\"clientId\":\"" + auditClientId + "\",\"role\":\"test-role\"}")
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201);

        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/related/account_role/by-account/" + account.getId())
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].account_id", equalTo(account.getId()));
    }

    @Test
    public void testGetRelatedHistoryForUnknownParentTypeReturns400() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/related/account_role/by-nonexistent/" + account.getId())
            .then()
            .statusCode(400)
            .body("error", containsString("Unknown entity type"));
    }

    @Test
    public void testGetRelatedHistoryForUnknownRelatedTypeReturns400() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/related/nonexistent/by-account/" + account.getId())
            .then()
            .statusCode(400)
            .body("error", containsString("Unknown related entity type"));
    }

    @Test
    public void testGetRelatedHistoryWithManageClientsForAccountRoleReturns403() throws Exception {
        Account account = createTestAccount();
        // manage-clients should NOT be able to query account_role related history (requires manage-accounts)
        given()
            .auth().oauth2(generateManageClientsToken(account.getId()))
            .when()
            .get("/api/audit/related/account_role/by-account/" + account.getId())
            .then()
            .statusCode(403);
    }

    @Test
    public void testGetRelatedHistoryForNonExistentAccountReturnsEmpty() throws Exception {
        Account account = createTestAccount();
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/related/account_role/by-account/ffffffff-ffff-ffff-ffff-ffffffffffff")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", equalTo(0));
    }

    @Test
    public void testGetRelatedHistoryWithInvalidColumnReturns400() throws Exception {
        Account account = createTestAccount();
        // credential has no "account_role_id" column, so this should fail
        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/related/credential/by-account_role/" + account.getId())
            .then()
            .statusCode(400);
    }

    // ---- Credential history test ----

    @Test
    public void testGetCredentialHistoryWithManageAccountsReturns200() throws Exception {
        Account account = createTestAccount();

        // Get the credential ID
        transactionHelper.beginTransaction();
        String credId = (String) em.createNativeQuery(
                "SELECT id FROM T_credentials WHERE account_id = :aid")
            .setParameter("aid", account.getId())
            .getSingleResult();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateManageAccountsToken(account.getId()))
            .when()
            .get("/api/audit/credential/" + credId)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(1));
    }
}
