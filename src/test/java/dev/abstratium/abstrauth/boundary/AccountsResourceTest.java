package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;

@QuarkusTest
public class AccountsResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    OrganisationService organisationService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    dev.abstratium.abstrauth.util.TestTransactionHelper transactionHelper;

    @BeforeEach
    public void ensureTestClientsExist() throws Exception {
        // Create test clients if they don't exist
        transactionHelper.beginTransaction();
        
        // Helper to create client if it doesn't exist
        String[] clientIds = {"client-a", "client-b", "test-client", "client-unique", "client-different", 
                              "client-x", "client-y", "client-z"};
        
        for (String clientId : clientIds) {
            var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", 
                                      dev.abstratium.abstrauth.entity.OAuthClient.class);
            query.setParameter("clientId", clientId);
            if (query.getResultList().isEmpty()) {
                dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
                client.setClientId(clientId);
                client.setClientName("Test " + clientId);
                client.setClientType("confidential");
                client.setRedirectUris("[\"http://localhost:8080/callback\"]");
                client.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
                client.setRequirePkce(false);
                em.persist(client);
                
                // Create client secret in new table
                dev.abstratium.abstrauth.entity.ClientSecret secret = new dev.abstratium.abstrauth.entity.ClientSecret();
                secret.setClientId(clientId);
                secret.setSecretHash("$2a$10$dummyhash");
                secret.setDescription("Test secret");
                secret.setActive(true);
                em.persist(secret);
            }
        }
        
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
        return generateManageAccountsToken(accountId, "00000000-0000-0000-0000-000000000000");
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
            .claim("orgId", "00000000-0000-0000-0000-000000000000")
            .sign();
    }

    @Test
    public void testListAccountsAsAdmin() throws Exception {
        // Create a test admin account
        transactionHelper.beginTransaction();
        String email = "testadmin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccount(email, "Test Admin", "testadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Test Org");
        String adminId = admin.getId();
        String adminOrgId = organisationService.listOrganisationsForAccount(adminId).get(0).getId();
        transactionHelper.commitTransaction();
        
        // Admin should be able to access the endpoint and get a JSON array response
        given()
            .auth().oauth2(generateAdminToken(adminId, adminOrgId))
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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

        // Manager should see manager and shared user (same client-a), but not other user (client-b)
        given()
            .auth().oauth2(generateManageAccountsToken(managerId, defaultOrgId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItems(managerEmail, userEmail))
            .body("email", not(hasItem(otherEmail)));
    }

    @Test
    public void testListAccountsAsManagerWithNoSharedClients() throws Exception {
        // Create manager account with unique client in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        
        // Manager should only see their own account
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItem(managerEmail))
            .body("email", not(hasItem(otherEmail)));
    }

    @Test
    public void testListAccountsAsManagerWithNoRoles() throws Exception {
        // Create manager account with no roles in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String managerEmail = "norolemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "No Role Manager", "norolemanager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        // Don't add any roles
        
        // Manager should only see their own account
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String userEmail = "regularuser_" + System.currentTimeMillis() + "@example.com";
        Account user = accountService.createAccountForOrg(userEmail, "Regular User", "regularuser_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String userId = user.getId();
        transactionHelper.commitTransaction();
        
        // Regular users with only USER role can see their own account
        given()
            .auth().oauth2(generateUserToken(userId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItem(userEmail))
            .body("size()", equalTo(1)); // Should only see their own account
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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

        // Create user3 with no shared clients in default org (to test client-based filter)
        String user3Email = "user3_" + System.currentTimeMillis() + "@example.com";
        Account user3 = accountService.createAccountForOrg(user3Email, "User 3", "user3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(user3.getId(), "client-z", "viewer");
        transactionHelper.commitTransaction();

        // Manager should see manager, user1, and user2, but not user3 (different client)
        given()
            .auth().oauth2(generateManageAccountsToken(managerId, defaultOrgId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue())
            .body("email", hasItems(managerEmail, user1Email, user2Email))
            .body("email", not(hasItem(user3Email)));
    }

    @Test
    public void testAddAccountRoleSuccessfully() throws Exception {
        transactionHelper.beginTransaction();
        
        // Create admin account first
        String adminEmail = "roleadmin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccount(adminEmail, "Role Admin", "roleadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Test Org");
        String adminId = admin.getId();
        String adminOrgId = organisationService.listOrganisationsForAccount(adminId).get(0).getId();
        
        // Create target account in admin's org
        String email = "roletest_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Role Test", "roletest_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, adminOrgId);
        String accountId = account.getId();
        
        transactionHelper.commitTransaction();
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "admin"
            }
            """, accountId);
        
        given()
            .auth().oauth2(generateAdminToken(adminId, adminOrgId))
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
        // Create manager account
        transactionHelper.beginTransaction();
        String managerEmail = "rolemanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager 2", "rolemanager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Test Org");
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        // Create two accounts in default org to match token
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String targetEmail = "target_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccountForOrg(targetEmail, "Target User", "target_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "viewer");
        
        String managerEmail = "manager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        // Try to add target account to a NEW client (test-client) - should fail for non-admin
        // Note: test-client doesn't exist, so this will also test FK constraint
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "viewer"
            }
            """, targetAccount.getId());
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400)
            .body("error", equalTo("Only admin can add roles to accounts that are not members of the client"));
    }

    @Test
    public void testAddRoleToExistingClientAsNonAdmin() throws Exception {
        // Create account with existing role in abstratium-abstrauth in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String targetEmail = "target2_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccountForOrg(targetEmail, "Target User 2", "target2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "viewer");
        
        String managerEmail = "manager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager 2", "manager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        // Try to add another role to the SAME client (abstratium-abstrauth) - should succeed for non-admin
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "editor"
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
            .body("role", equalTo("editor"));
    }

    @Test
    public void testAddRoleToNewClientAsAdmin() throws Exception {
        transactionHelper.beginTransaction();
        
        // Create admin account first
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccount(adminEmail, "Admin User", "admin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Test Org");
        // Give admin the admin role
        accountRoleService.addRole(admin.getId(), "abstratium-abstrauth", "admin");
        String adminOrgId = organisationService.listOrganisationsForAccount(admin.getId()).get(0).getId();
        
        // Create target account in admin's org
        String targetEmail = "target3_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccountForOrg(targetEmail, "Target User 3", "target3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, adminOrgId);
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "viewer");
        
        transactionHelper.commitTransaction();
        
        // Admin should be able to add target account role even if they don't have that client yet
        // Since test-client doesn't exist, we'll add another role to the existing client to verify admin bypass
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "admin"
            }
            """, targetAccount.getId());
        
        given()
            .auth().oauth2(generateAdminToken(admin.getId(), adminOrgId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("abstratium-abstrauth"))
            .body("role", equalTo("admin"));
    }

    @Test
    public void testAddDuplicateRole() throws Exception {
        // Create account with a role in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Duplicate Test", "duplicate_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        // Add initial role (user role is already added automatically)
        accountRoleService.addRole(account.getId(), "abstratium-abstrauth", "viewer");
        
        String managerEmail = "manager_dup_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager Dup", "manager_dup_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        // Try to add the same role again - should return 409 Conflict
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "viewer"
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        transactionHelper.beginTransaction();
        String managerEmail = "deletemanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Delete Manager 2", "deletemanager2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, "Test Org");
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String email = "lastadmin_" + System.currentTimeMillis() + "@example.com";
        Account adminAccount = accountService.createAccountForOrg(email, "Last Admin", "lastadmin_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String adminAccountId = adminAccount.getId();
        accountRoleService.addRole(adminAccountId, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String email1 = "admin1_api_" + System.currentTimeMillis() + "@example.com";
        Account admin1 = accountService.createAccountForOrg(email1, "Admin 1", "admin1_api_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String admin1Id = admin1.getId();
        accountRoleService.addRole(admin1Id, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
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
    public void testCannotDeleteAccountWithOnlyAdminRole() throws Exception {
        // First, remove all existing admin roles for abstratium-abstrauth to ensure clean state
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String email = "onlyadmin_api_" + System.currentTimeMillis() + "@example.com";
        Account adminAccount = accountService.createAccountForOrg(email, "Only Admin", "onlyadmin_api_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String adminAccountId = adminAccount.getId();
        accountRoleService.addRole(adminAccountId, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        // Create manager account in default org
        String managerEmail = "manager_delacct_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_delacct_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .when()
            .delete("/api/accounts/" + adminAccountId)
            .then()
            .statusCode(400)
            .body("error", containsString("Cannot delete the account with the only admin role"));
    }

    @Test
    public void testCanDeleteAccountWhenMultipleAdminsExist() throws Exception {
        // Create two accounts with admin role for abstratium-abstrauth in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String email1 = "admin1_delacct_" + System.currentTimeMillis() + "@example.com";
        Account admin1 = accountService.createAccountForOrg(email1, "Admin 1", "admin1_delacct_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String admin1Id = admin1.getId();
        accountRoleService.addRole(admin1Id, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        String email2 = "admin2_delacct_" + System.currentTimeMillis() + "@example.com";
        Account admin2 = accountService.createAccountForOrg(email2, "Admin 2", "admin2_delacct_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        // Create manager account in default org
        String managerEmail = "manager_delacct2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager", "manager_delacct2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();
        
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .when()
            .delete("/api/accounts/" + admin1Id)
            .then()
            .statusCode(204);
    }

    @Test
    public void testCreateAccountWithNativeProvider() throws Exception {
        // Create manager account in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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
    public void testCreateAccountWithDuplicateEmail() throws Exception {
        // Create manager account in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        String managerEmail = "createmanager4_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Create Manager 4", "createmanager4_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        
        // Create first account in default org
        String existingEmail = "existing_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(existingEmail, "Existing User", "existing_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();
        
        // Try to create account with same email
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
            .body("error", equalTo("Email already exists"));
    }

    @Test
    public void testCreateAccountCreatesCredentialForNative() throws Exception {
        // Create manager account in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
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

    @Test
    public void testDeleteAccountCascadesAllChildRecords() throws Exception {
        // Create a manager account in default org
        String defaultOrgId = "00000000-0000-0000-0000-000000000000";
        transactionHelper.beginTransaction();
        Account manager = new Account();
        manager.setEmail("manager-cascade@example.com");
        manager.setName("Manager Cascade");
        manager.setAuthProvider(AccountService.NATIVE);
        manager.setEmailVerified(true);
        em.persist(manager);
        em.flush();
        // Add manager to default org
        organisationService.addMember(defaultOrgId, manager.getId());

        // Create an account with all types of child records in default org
        Account account = accountService.createAccountForOrg(
            "cascade-test@example.com",
            "Cascade Test",
            "cascadeuser",
            "password123",
            AccountService.NATIVE,
            defaultOrgId);
        String accountId = account.getId();

        // Add a role (user role is already added automatically)
        accountRoleService.addRole(accountId, "test-client", "viewer");

        // Add a federated identity
        FederatedIdentity fedIdentity = new FederatedIdentity();
        fedIdentity.setAccountId(accountId);
        fedIdentity.setProvider(AccountService.GOOGLE);
        fedIdentity.setProviderUserId("google-user-123");
        fedIdentity.setEmail("cascade-test@example.com");
        em.persist(fedIdentity);

        em.flush();
        transactionHelper.commitTransaction();

        // Verify child records exist before deletion
        transactionHelper.beginTransaction();
        List<AccountRole> rolesBefore = accountRoleService.findRolesByAccountId(accountId);
        assertEquals(2, rolesBefore.size()); // user role (automatic) + viewer role (manual)

        var credQuery = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :accountId", dev.abstratium.abstrauth.entity.Credential.class);
        credQuery.setParameter("accountId", accountId);
        assertEquals(1, credQuery.getResultList().size());

        var fedQuery = em.createQuery("SELECT f FROM FederatedIdentity f WHERE f.accountId = :accountId", dev.abstratium.abstrauth.entity.FederatedIdentity.class);
        fedQuery.setParameter("accountId", accountId);
        assertEquals(1, fedQuery.getResultList().size());
        transactionHelper.commitTransaction();

        // Delete the account
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .when()
            .delete("/api/accounts/" + accountId)
            .then()
            .statusCode(204);

        // Verify account is deleted
        Account deletedAccount = em.find(Account.class, accountId);
        assertNull(deletedAccount);

        // Verify all child records are deleted via CASCADE DELETE
        transactionHelper.beginTransaction();
        List<AccountRole> rolesAfter = accountRoleService.findRolesByAccountId(accountId);
        assertTrue(rolesAfter.isEmpty(), "Roles should be deleted via CASCADE DELETE");

        var credQueryAfter = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :accountId", dev.abstratium.abstrauth.entity.Credential.class);
        credQueryAfter.setParameter("accountId", accountId);
        assertTrue(credQueryAfter.getResultList().isEmpty(), "Credentials should be deleted via CASCADE DELETE");

        var fedQueryAfter = em.createQuery("SELECT f FROM FederatedIdentity f WHERE f.accountId = :accountId", dev.abstratium.abstrauth.entity.FederatedIdentity.class);
        fedQueryAfter.setParameter("accountId", accountId);
        assertTrue(fedQueryAfter.getResultList().isEmpty(), "Federated identities should be deleted via CASCADE DELETE");
        transactionHelper.commitTransaction();
    }

}
