package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.SubscriptionService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@QuarkusTest
public class AccountsResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    OrganisationService organisationService;

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    public void resetDatabaseBeforeTest() throws Exception {
        transactionHelper.beginTransaction();

        // Reset database to clean state - deletes all test data in correct order
        dbResetHelper.resetDatabase();

        // Note: Default organization and test clients are created by Flyway migration
        // (src/test/resources/db/migration/R__01__test_default_org_and_clients.sql)

        transactionHelper.commitTransaction();
    }

    private String generateAdminToken(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("admin@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_admin", "abstratium-abstrauth_manage-accounts"))
            .claim("email", "admin@example.com")
            .claim("name", "Admin User")
            .claim("orgId", orgId)
            .sign();
    }

    private String generateManageAccountsToken(String accountId) {
        return generateManageAccountsToken(accountId, defaultOrgId);
    }

    private String generateManageAccountsToken(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("manager@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_manage-accounts"))
            .claim("email", "manager@example.com")
            .claim("name", "Account Manager")
            .claim("orgId", orgId)
            .sign();
    }

    private String generateUserToken(String accountId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("user@example.com")
            .groups("abstratium-abstrauth_user")
            .claim("email", "user@example.com")
            .claim("name", "Regular User")
            .claim("orgId", defaultOrgId)
            .sign();
    }

    @Test
    public void testListAccountsAsAdmin() throws Exception {
        // Create a test admin account in default org
        transactionHelper.beginTransaction();
        String email = "testadmin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccountForOrg(email, "Test Admin", "testadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String adminId = admin.getId();
        transactionHelper.commitTransaction();
        
        // Admin should be able to access the endpoint and get a JSON array response
        given()
            .auth().oauth2(generateAdminToken(adminId, defaultOrgId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue());
    }

    @Test
    public void testListAccountsAsManagerWithSharedClients() throws Exception {
        // All accounts are placed in the default org so that AccountRole rows (also stored in
        // the default org in test context) match the orgId used in the token.
        transactionHelper.beginTransaction();
        String managerEmail = "manager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, "client-a", "manager");

        // Create another account with same client in default org
        String userEmail = "shareduser_" + System.currentTimeMillis() + "@example.com";
        Account user = accountService.createAccountForOrg(userEmail, "Shared User", "shareduser_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(user.getId(), "client-a", "viewer");

        // Create account with different client in default org
        String otherEmail = "otheruser_" + System.currentTimeMillis() + "@example.com";
        Account other = accountService.createAccountForOrg(otherEmail, "Other User", "otheruser_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(other.getId(), "client-b", "viewer");
        transactionHelper.commitTransaction();

        // All org members see all accounts in the org (simplified behavior)
        given()
            .auth().oauth2(generateManageAccountsToken(managerId, defaultOrgId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItems(managerEmail, userEmail, otherEmail));
    }

    @Test
    public void testListAccountsAsManagerWithNoSharedClients() throws Exception {
        // Create manager account with unique client in default org
        transactionHelper.beginTransaction();
        String managerEmail = "uniquemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Unique Manager", "uniquemanager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, "client-unique", "manager");

        // Create other accounts with different clients in default org
        String otherEmail = "differentuser_" + System.currentTimeMillis() + "@example.com";
        Account other = accountService.createAccountForOrg(otherEmail, "Different User", "differentuser_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(other.getId(), "client-different", "viewer");
        transactionHelper.commitTransaction();

        // All org members see all accounts in the org (simplified behavior)
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItems(managerEmail, otherEmail));
    }

    @Test
    public void testListAccountsAsManagerWithNoRoles() throws Exception {
        // Create manager account with no additional roles in default org
        transactionHelper.beginTransaction();
        String managerEmail = "norolemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "No Role Manager", "norolemanager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();

        // All org members see all accounts in the org (simplified behavior)
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItem(managerEmail));
    }

    @Test
    public void testListAccountsAsNonManagerNonAdmin() throws Exception {
        // Create regular user account in default org
        transactionHelper.beginTransaction();
        String userEmail = "regularuser_" + System.currentTimeMillis() + "@example.com";
        Account user = accountService.createAccountForOrg(userEmail, "Regular User", "regularuser_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String userId = user.getId();
        transactionHelper.commitTransaction();

        // Regular users with only USER role can see all accounts in their org (simplified behavior)
        given()
            .auth().oauth2(generateUserToken(userId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItem(userEmail));
    }

    @Test
    public void testListAccountsUnauthenticated() {
        // Unauthenticated users should get 401 Unauthorized
        given()
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(401);
    }

    @Test
    public void testListAccountsAsManagerWithMultipleSharedClients() throws Exception {
        // All accounts are placed in the default org so that AccountRole rows (also stored in
        // the default org in test context) match the orgId used in the token.
        transactionHelper.beginTransaction();
        String managerEmail = "multimanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Multi Manager", "multimanager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, "client-x", "manager");
        accountRoleService.addRole(managerId, "client-y", "manager");

        // Create user1 sharing client-x in default org
        String user1Email = "user1_" + System.currentTimeMillis() + "@example.com";
        Account user1 = accountService.createAccountForOrg(user1Email, "User 1", "user1_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(user1.getId(), "client-x", "viewer");

        // Create user2 sharing client-y in default org
        String user2Email = "user2_" + System.currentTimeMillis() + "@example.com";
        Account user2 = accountService.createAccountForOrg(user2Email, "User 2", "user2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(user2.getId(), "client-y", "viewer");

        // Create user3 with different client in default org
        String user3Email = "user3_" + System.currentTimeMillis() + "@example.com";
        Account user3 = accountService.createAccountForOrg(user3Email, "User 3", "user3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(user3.getId(), "client-z", "viewer");
        transactionHelper.commitTransaction();

        // All org members see all accounts in the org (simplified behavior)
        given()
            .auth().oauth2(generateManageAccountsToken(managerId, defaultOrgId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItems(managerEmail, user1Email, user2Email, user3Email));
    }

    @Test
    public void testAddAccountRoleSuccessfully() throws Exception {
        // Use the default org so that the "test-client" created in @BeforeEach
        // (which lives in the default org) matches the caller's orgId claim.
        transactionHelper.beginTransaction();
        
        // Create admin account in the default org
        String adminEmail = "roleadmin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccountForOrg(adminEmail, "Role Admin", "roleadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String adminId = admin.getId();
        
        // Create target account in the same (default) org
        String email = "roletest_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Test", "roletest_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();

        transactionHelper.commitTransaction();

        // Add 'admin' to test-client's role catalog so it can be assigned
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.ClientAllowedRole allowedRole = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        allowedRole.setClientId("test-client");
        allowedRole.setRole("admin");
        allowedRole.setIsDefault(false);
        em.persist(allowedRole);
        transactionHelper.commitTransaction();

        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "admin"
            }
            """, accountId);

        given()
            .auth().oauth2(generateAdminToken(adminId, defaultOrgId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("test-client"))
            .body("role", equalTo("admin"));
    }

    @Test
    public void testAddAccountRoleWithoutToken() {
        String requestBody = """
            {
                "accountId": "some-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(401);
    }

    @Test
    public void testAddAccountRoleWithoutRole() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "rolemanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Role Manager 2", "rolemanager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = """
            {
                "accountId": "some-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateUserToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(403);
    }

    @Test
    public void testAddAccountRoleToNonExistentAccount() throws Exception {
        // Create manager account in default org to match token
        transactionHelper.beginTransaction();
        String managerEmail = "rolemanager3_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Role Manager 3", "rolemanager3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = """
            {
                "accountId": "non-existent-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(404)
            .body("error", equalTo("Account not found"));
    }

    @Test
    public void testAddAccountRoleWithMissingClientId() throws Exception {
        // Create account and manager in default org to match token
        transactionHelper.beginTransaction();
        String email = "roletest2_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Test 2", "roletest2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        
        String managerEmail = "rolemanager4_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Role Manager 4", "rolemanager4_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "role": "admin"
            }
            """, accountId);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400);
    }

    @Test
    public void testAddAccountRoleWithMissingRole() throws Exception {
        // Create account and manager in default org to match token
        transactionHelper.beginTransaction();
        String email = "roletest3_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Test 3", "roletest3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        
        String managerEmail = "rolemanager5_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Role Manager 5", "rolemanager5_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client"
            }
            """, accountId);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400);
    }

    @Test
    public void testAddAccountRoleWithInvalidRoleFormat() throws Exception {
        // Create account and manager in default org to match token
        transactionHelper.beginTransaction();
        String email = "roletest4_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Test 4", "roletest4_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        
        String managerEmail = "rolemanager6_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Role Manager 6", "rolemanager6_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "admin@invalid"
            }
            """, accountId);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400);
    }

    @Test
    public void testAddAdminRoleAsNonAdmin() throws Exception {
        // Create account and non-admin manager in default org to match token
        transactionHelper.beginTransaction();
        String email = "roletest_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Test", "roletest_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        
        String managerEmail = "nonadmin_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Non-Admin Manager", "nonadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "admin"
            }
            """, account.getId());
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400)
            .body("error", equalTo("Only admin can add the admin role"));
    }

    @Test
    public void testAddRoleToNewClientAsNonAdmin() throws Exception {
        // Non-admin with manage-accounts CAN add allowlisted (or private client) roles
        // to accounts in their own org for any client.
        String newClientId = "new-client-" + System.currentTimeMillis();
        transactionHelper.beginTransaction();
        String targetEmail = "target_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccountForOrg(targetEmail, "Target User", "target_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);

        // Create the client (private — no allowlist, so any role is allowed)
        dev.abstratium.abstrauth.entity.OAuthClient newClient = new dev.abstratium.abstrauth.entity.OAuthClient();
        newClient.setClientId(newClientId);
        newClient.setClientName("New Test Client");
        newClient.setClientType("confidential");
        newClient.setRedirectUris("[\"http://localhost/callback\"]");
        newClient.setAllowedScopes("[\"openid\"]");
        newClient.setRequirePkce(false);
        newClient.setOrgId(defaultOrgId);
        em.persist(newClient);

        // Add 'viewer' to the new client's role catalog
        dev.abstratium.abstrauth.entity.ClientAllowedRole newClientRole = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        newClientRole.setClientId(newClientId);
        newClientRole.setRole("viewer");
        newClientRole.setIsDefault(false);
        em.persist(newClientRole);

        String managerEmail = "manager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        // Non-admin can add a role on a private client (no allowlist)
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "%s",
                "role": "viewer"
            }
            """, targetAccount.getId(), newClientId);

        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201);
    }

    @Test
    public void testAddRoleToExistingClientAsNonAdmin() throws Exception {
        // Create account with existing role in abstratium-abstrauth in default org
        transactionHelper.beginTransaction();
        String targetEmail = "target2_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccountForOrg(targetEmail, "Target User 2", "target2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "viewer");
        
        String managerEmail = "manager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager 2", "manager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        // Try to add another role to the SAME client (abstratium-abstrauth) - should succeed for non-admin
        // Use a role that is in the abstratium-abstrauth allowlist (manage-accounts)
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "manage-accounts"
            }
            """, targetAccount.getId());
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("abstratium-abstrauth"))
            .body("role", equalTo("manage-accounts"));
    }

    @Test
    public void testAddRoleToNewClientAsAdmin() throws Exception {
        // Use the default org so that "abstratium-abstrauth" (which lives in the default org)
        // matches the caller's orgId claim.
        transactionHelper.beginTransaction();
        
        // Create admin account in the default org (first account gets admin role automatically)
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccountForOrg(adminEmail, "Admin User", "admin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        
        // Create target account in the same (default) org
        String targetEmail = "target3_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccountForOrg(targetEmail, "Target User 3", "target3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "viewer");
        
        transactionHelper.commitTransaction();
        
        // Admin should be able to add target account role even if they don't have that client yet
        // Use manage-clients which is in the abstratium-abstrauth allowlist
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "manage-clients"
            }
            """, targetAccount.getId());
        
        given()
            .auth().oauth2(generateAdminToken(admin.getId(), defaultOrgId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("abstratium-abstrauth"))
            .body("role", equalTo("manage-clients"));
    }

    @Test
    public void testAddDuplicateRole() throws Exception {
        // Create account with a role in default org
        transactionHelper.beginTransaction();
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Duplicate Test", "duplicate_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        // Add initial role (user role is already added automatically, use manage-accounts which is in allowlist)
        accountRoleService.addRole(account.getId(), "abstratium-abstrauth", "manage-accounts");
        
        String managerEmail = "manager_dup_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager Dup", "manager_dup_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        // Try to add the same role again - should return 409 Conflict
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "manage-accounts"
            }
            """, account.getId());
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(409)
            .body("error", equalTo("Role already exists"));
    }

    @Test
    public void testRemoveAccountRoleSuccessfully() throws Exception {
        // Create account with a role in default org
        transactionHelper.beginTransaction();
        String email = "roledelete_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Delete Test", "roledelete_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        accountRoleService.addRole(accountId, "test-client", "admin");
        
        // Create manager account in default org
        String managerEmail = "deletemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Delete Manager", "deletemanager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "admin"
            }
            """, accountId);
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(204);
    }

    @Test
    public void testRemoveAccountRoleWithoutToken() {
        String requestBody = """
            {
                "accountId": "some-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(401);
    }

    @Test
    public void testRemoveAccountRoleWithoutPermission() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "deletemanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Delete Manager 2", "deletemanager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = """
            {
                "accountId": "some-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateUserToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(403);
    }

    @Test
    public void testRemoveAccountRoleFromNonExistentAccount() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "deletemanager3_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Delete Manager 3", "deletemanager3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = """
            {
                "accountId": "non-existent-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(404)
            .body("error", equalTo("Account not found"));
    }

    @Test
    public void testCannotRemoveLastAdminRoleForAbstrauthClient() throws Exception {
        // First, remove all existing admin roles for abstratium-abstrauth to ensure clean state
        transactionHelper.beginTransaction();
        var existingAdmins = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            AccountRole.class
        );
        existingAdmins.setParameter("clientId", Roles.CLIENT_ID);
        existingAdmins.setParameter("role", Roles._ADMIN_PLAIN);
        for (var admin : existingAdmins.getResultList()) {
            em.remove(admin);
        }
        
        // Create an account with admin role for abstratium-abstrauth in default org
        // (first account created after deleting admin roles gets admin role automatically)
        String email = "lastadmin_" + System.currentTimeMillis() + "@example.com";
        Account adminAccount = accountService.createAccountForOrg(email, "Last Admin", "lastadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String adminAccountId = adminAccount.getId();
        
        // Create manager account in default org
        String managerEmail = "manager_lastadmin_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_lastadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "%s",
                "role": "%s"
            }
            """, adminAccountId, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(400)
            .body("error", containsString("Cannot delete the last admin role"));
    }

    @Test
    public void testCanRemoveAdminRoleWhenMultipleAdminsExist() throws Exception {
        // Create two accounts with admin role for abstratium-abstrauth in default org
        transactionHelper.beginTransaction();
        String email1 = "admin1_api_" + System.currentTimeMillis() + "@example.com";
        Account admin1 = accountService.createAccountForOrg(email1, "Admin 1", "admin1_api_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String admin1Id = admin1.getId();
        // First account gets admin role automatically; second account needs it explicitly added
        
        String email2 = "admin2_api_" + System.currentTimeMillis() + "@example.com";
        Account admin2 = accountService.createAccountForOrg(email2, "Admin 2", "admin2_api_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        // Create manager account in default org
        String managerEmail = "manager_multi_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_multi_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "%s",
                "role": "%s"
            }
            """, admin1Id, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(204);
    }

    @Test
    public void testCreateAccountWithNativeProvider() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager", "createmanager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String newEmail = "newaccount_" + System.currentTimeMillis() + "@example.com";
        String requestBody = String.format("""
            {
                "email": "%s",
                "authProvider": "native"
            }
            """, newEmail);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            //.log().all()
            .when()
            .post("/api/accounts")
            .then()
            //.log().all()
            .statusCode(201)
            .body("account.email", equalTo(newEmail))
            .body("account.authProvider", equalTo(AccountService.NATIVE))
            .body("inviteToken", notNullValue());
    }

    @Test
    public void testCreateAccountWithGoogleProvider() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager 2", "createmanager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String newEmail = "newgoogleaccount_" + System.currentTimeMillis() + "@example.com";
        String requestBody = String.format("""
            {
                "email": "%s",
                "authProvider": "google"
            }
            """, newEmail);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            //.log().all()
            .post("/api/accounts")
            .then()
            //.log().all()
            .statusCode(201)
            .body("account.email", equalTo(newEmail))
            .body("account.authProvider", equalTo(AccountService.GOOGLE))
            .body("inviteToken", notNullValue());
    }

    @Test
    public void testCreateAccountWithInvalidProvider() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager3_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager 3", "createmanager3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String newEmail = "newinvalidaccount_" + System.currentTimeMillis() + "@example.com";
        String requestBody = String.format("""
            {
                "email": "%s",
                "authProvider": "invalid"
            }
            """, newEmail);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts")
            .then()
            .statusCode(400);
    }

    @Test
    public void testCreateAccountWithDuplicateEmailInSameOrg() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager4_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager 4", "createmanager4_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();

        // Create first account in default org
        String existingEmail = "existing_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(existingEmail, "Existing User", "existing_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        // Try to create account with same email - should fail since already in same org
        String requestBody = String.format("""
            {
                "email": "%s",
                "authProvider": "native"
            }
            """, existingEmail);

        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts")
            .then()
            .statusCode(409)
            .body("error", equalTo("Email already exists and is a member of your organization"));
    }

    @Test
    public void testCreateAccountWithDuplicateEmailInDifferentOrg() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager_diff_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager Diff", "createmanager_diff_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();

        // Create account in a DIFFERENT org (not the default org)
        String existingEmail = "existing_diff_" + System.currentTimeMillis() + "@example.com";
        Account existingAccount = accountService.createAccount(existingEmail, "Existing User Diff", "existing_diff_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Different Org");
        transactionHelper.commitTransaction();

        // Verify the existing account is NOT in the default org
        String existingAccountOrgId = organisationService.listOrganisationsForAccount(existingAccount.getId()).get(0).getId();
        assertTrue(!defaultOrgId.equals(existingAccountOrgId), "Existing account should be in a different org");

        // Subscribe the default org to a test client so we can verify roles are seeded
        transactionHelper.beginTransaction();
        subscriptionService.subscribe(defaultOrgId, "test-client");
        // Add default roles for the test client
        dev.abstratium.abstrauth.entity.ClientAllowedRole defaultRole = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        defaultRole.setClientId("test-client");
        defaultRole.setRole("viewer");
        defaultRole.setIsDefault(true);
        em.persist(defaultRole);
        transactionHelper.commitTransaction();

        // Try to create account with same email - should add to caller's org
        String requestBody = String.format("""
            {
                "email": "%s",
                "authProvider": "native"
            }
            """, existingEmail);

        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts")
            .then()
            .statusCode(200)
            .body("account.email", equalTo(existingEmail))
            .body("message", equalTo("Account added to organization"));

        // Verify the account is now a member of the default org
        assertTrue(organisationService.isMember(defaultOrgId, existingAccount.getId()), "Account should now be a member of the default org");

        // Verify default roles were seeded
        var roles = accountRoleService.findRolesByAccountId(existingAccount.getId());
        boolean hasViewerRole = roles.stream()
            .anyMatch(r -> r.getClientId().equals("test-client") && r.getRole().equals("viewer"));
        assertTrue(hasViewerRole, "Account should have the 'viewer' role for test-client");

        // Cleanup: remove the ClientAllowedRole added in this test to avoid polluting other tests
        transactionHelper.beginTransaction();
        em.createQuery("DELETE FROM ClientAllowedRole r WHERE r.id.clientId = 'test-client' AND r.id.role = 'viewer'")
          .executeUpdate();
        transactionHelper.commitTransaction();
    }

    @Test
    public void testCreateAccountCreatesCredentialForNative() throws Exception {
        // Create manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager5_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager 5", "createmanager5_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        String newEmail = "newcredaccount_" + System.currentTimeMillis() + "@example.com";
        String requestBody = String.format("""
            {
                "email": "%s",
                "authProvider": "native"
            }
            """, newEmail);
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            //.log().all()
            .post("/api/accounts")
            .then()
            //.log().all()
            .statusCode(201)
            .extract().asString();
        
        // Verify credential was created
        var credential = accountService.findCredentialByUsername(newEmail);
        assertTrue(credential.isPresent(), "Credential should be created for native account");
    }

    /**
     * Verifies that a user belonging to their own organisation can still see their abstrauth
     * "user" role when calling GET /api/accounts, even though the AccountRole row in
     * T_account_roles belongs to the default organisation (org_id = '00000000-...').
     *
     * This is a critical cross-org visibility requirement: the abstrauth "user" role is always
     * stored under the default organisation (because addAbstrauthRoles runs before the user's
     * own organisation is created/linked). The /api/accounts endpoint must still return that
     * role regardless of which orgId the user signs in with. 
     * because ant decided that it also makes sense that the new user can see that they are 
     * a user of abstrauth (and potentially other clients outside of their org).
     */
    @Test
    public void testSecondAccountSeesAbstrauthUserRoleDespiteDifferentOrg() throws Exception {
        // Create the first account so that the system is initialized (first account gets default org)
        transactionHelper.beginTransaction();
        String firstEmail = "firstuser_crossorg_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccount(firstEmail, "First User", "firstuser_crossorg_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Default Org");
        transactionHelper.commitTransaction();

        // Create a second account — this one gets its own NEW organisation
        transactionHelper.beginTransaction();
        String secondEmail = "seconduser_crossorg_" + System.currentTimeMillis() + "@example.com";
        Account secondAccount = accountService.createAccount(secondEmail, "Second User", "seconduser_crossorg_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Second Org");
        String secondAccountId = secondAccount.getId();
        // The second account's org is NOT the default org
        String secondOrgId = organisationService.listOrganisationsForAccount(secondAccountId).get(0).getId();
        transactionHelper.commitTransaction();

        // Generate a token with the second user's own orgId (not the default org)
        String token = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(secondAccountId)
            .upn(secondEmail)
            .groups("abstratium-abstrauth_user")
            .claim("email", secondEmail)
            .claim("name", "Second User")
            .claim("orgId", secondOrgId)
            .sign();

        // Call /api/accounts — the user should see their own account with the abstrauth roles,
        // even though that role's row in T_account_roles has org_id = default org, not secondOrgId
        // The account has 3 abstrauth roles: user + manage_accounts + manage_clients (owner gets management roles)
        given()
            .auth().oauth2(token)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", equalTo(1))
            .body("[0].email", equalTo(secondEmail))
            .body("[0].roles.size()", equalTo(3))
            .body("[0].roles.find { it.role == 'user' }.clientId", equalTo(Roles.CLIENT_ID))
            .body("[0].roles.find { it.role == 'manage-accounts' }.clientId", equalTo(Roles.CLIENT_ID))
            .body("[0].roles.find { it.role == 'manage-clients' }.clientId", equalTo(Roles.CLIENT_ID));
    }

    /**
     * Verifies that a user with manage-accounts CAN add a role to any client for accounts
     * in their own org, regardless of which org owns the client. The security boundary is
     * (1) the account must be a member of the caller's org, and (2) the role must be in
     * the client's allowlist (or the client has no allowlist = private client).
     */
    @Test
    public void testAddRoleToClientInDifferentOrgIsAllowed() throws Exception {
        // Create attacker's org and account
        transactionHelper.beginTransaction();
        String attackerEmail = "attacker_" + System.currentTimeMillis() + "@example.com";
        Account attacker = accountService.createAccount(attackerEmail, "Attacker", "attacker_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Attacker Org");
        String attackerId = attacker.getId();
        String attackerOrgId = organisationService.listOrganisationsForAccount(attackerId).get(0).getId();
        transactionHelper.commitTransaction();

        // Create a victim account in the default org
        transactionHelper.beginTransaction();
        String victimEmail = "victim_" + System.currentTimeMillis() + "@example.com";
        Account victim = accountService.createAccountForOrg(victimEmail, "Victim", "victim_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, attackerOrgId);
        String victimId = victim.getId();

        // Create a client that belongs to the DEFAULT org (not the attacker's org).
        // Use a native update to override the @TenantId value after persisting.
        String foreignClientId = "foreign-client-" + System.currentTimeMillis();
        dev.abstratium.abstrauth.entity.OAuthClient foreignClient = new dev.abstratium.abstrauth.entity.OAuthClient();
        foreignClient.setClientId(foreignClientId);
        foreignClient.setClientName("Foreign Client");
        foreignClient.setClientType("confidential");
        foreignClient.setRedirectUris("[\"http://localhost:8080/callback\"]");
        foreignClient.setAllowedScopes("[\"openid\"]");
        foreignClient.setRequirePkce(false);
        em.persist(foreignClient);
        em.flush();
        // Force the client's org_id to the default org (different from attackerOrgId)
        em.createNativeQuery("UPDATE T_oauth_clients SET org_id = :orgId WHERE client_id = :clientId")
            .setParameter("orgId", defaultOrgId)
            .setParameter("clientId", foreignClientId)
            .executeUpdate();
        em.flush();

        // Add 'editor' to the foreign client's role catalog so it can be assigned by foreign orgs
        dev.abstratium.abstrauth.entity.ClientAllowedRole foreignRole = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        foreignRole.setClientId(foreignClientId);
        foreignRole.setRole("editor");
        foreignRole.setIsDefault(false);
        foreignRole.setAvailableToForeignOrgs(true);
        em.persist(foreignRole);

        // Give the victim an existing role on the foreign client so that
        em.createNativeQuery("INSERT INTO T_account_roles (id, account_id, client_id, role, created_at, org_id) VALUES (UUID(), :accountId, :clientId, 'viewer', NOW(), :orgId)")
            .setParameter("accountId", victimId)
            .setParameter("clientId", foreignClientId)
            .setParameter("orgId", defaultOrgId)
            .executeUpdate();
        transactionHelper.commitTransaction();

        // Caller adds a role on a client owned by a different org — allowed because:
        // (1) victim IS in attackerOrgId (isMember check passes)
        // (2) foreignClient has no allowlist (private client, any role allowed)
        // Use 'editor' (not 'viewer' which is already seeded above)
        String body = String.format("{\"accountId\":\"%s\",\"clientId\":\"%s\",\"role\":\"editor\"}", victimId, foreignClientId);
        given()
            .auth().oauth2(generateManageAccountsToken(attackerId, attackerOrgId))
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201);
    }

}
