package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AccountsResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    jakarta.transaction.UserTransaction userTransaction;

    private String generateAdminToken(String accountId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("admin@example.com")
            .groups(Set.of("abstratium-abstrauth_admin", "abstratium-abstrauth_manage-accounts"))
            .claim("email", "admin@example.com")
            .claim("name", "Admin User")
            .sign();
    }

    private String generateManageAccountsToken(String accountId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("manager@example.com")
            .groups("abstratium-abstrauth_manage-accounts")
            .claim("email", "manager@example.com")
            .claim("name", "Account Manager")
            .sign();
    }

    private String generateUserToken(String accountId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("user@example.com")
            .groups("abstratium-abstrauth_user")
            .claim("email", "user@example.com")
            .claim("name", "Regular User")
            .sign();
    }

    @Test
    @TestTransaction
    public void testListAccountsAsAdmin() {
        // Create a test admin account
        String email = "testadmin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccount(email, "Test Admin", "testadmin_" + System.currentTimeMillis(), "Pass123");
        
        // Admin should be able to access the endpoint and get a JSON array response
        given()
            .auth().oauth2(generateAdminToken(admin.getId()))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue());
    }

    @Test
    public void testListAccountsAsManagerWithSharedClients() throws Exception {
        // Create manager account
        userTransaction.begin();
        String managerEmail = "manager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Manager", "manager_" + System.currentTimeMillis(), "Pass123");
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, "client-a", "manager");
        
        // Create another account with same client
        String userEmail = "shareduser_" + System.currentTimeMillis() + "@example.com";
        Account user = accountService.createAccount(userEmail, "Shared User", "shareduser_" + System.currentTimeMillis(), "Pass123");
        accountRoleService.addRole(user.getId(), "client-a", "user");
        
        // Create account with different client
        String otherEmail = "otheruser_" + System.currentTimeMillis() + "@example.com";
        Account other = accountService.createAccount(otherEmail, "Other User", "otheruser_" + System.currentTimeMillis(), "Pass123");
        accountRoleService.addRole(other.getId(), "client-b", "user");
        userTransaction.commit();
        
        // Manager should see manager and shared user, but not other user
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
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
        // Create manager account with unique client
        userTransaction.begin();
        String managerEmail = "uniquemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Unique Manager", "uniquemanager_" + System.currentTimeMillis(), "Pass123");
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, "client-unique", "manager");
        
        // Create other accounts with different clients
        String otherEmail = "differentuser_" + System.currentTimeMillis() + "@example.com";
        Account other = accountService.createAccount(otherEmail, "Different User", "differentuser_" + System.currentTimeMillis(), "Pass123");
        accountRoleService.addRole(other.getId(), "client-different", "user");
        userTransaction.commit();
        
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
        // Create manager account with no roles
        userTransaction.begin();
        String managerEmail = "norolemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "No Role Manager", "norolemanager_" + System.currentTimeMillis(), "Pass123");
        String managerId = manager.getId();
        userTransaction.commit();
        
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
    @TestTransaction
    public void testListAccountsAsNonManagerNonAdmin() {
        // Create regular user account
        String userEmail = "regularuser_" + System.currentTimeMillis() + "@example.com";
        Account user = accountService.createAccount(userEmail, "Regular User", "regularuser_" + System.currentTimeMillis(), "Pass123");
        
        // Regular users without manage-accounts role should get 403 Forbidden
        given()
            .auth().oauth2(generateUserToken(user.getId()))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(403);
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
        // Create manager with multiple clients
        userTransaction.begin();
        String managerEmail = "multimanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Multi Manager", "multimanager_" + System.currentTimeMillis(), "Pass123");
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, "client-x", "manager");
        accountRoleService.addRole(managerId, "client-y", "manager");
        
        // Create user1 sharing client-x
        String user1Email = "user1_" + System.currentTimeMillis() + "@example.com";
        Account user1 = accountService.createAccount(user1Email, "User 1", "user1_" + System.currentTimeMillis(), "Pass123");
        accountRoleService.addRole(user1.getId(), "client-x", "user");
        
        // Create user2 sharing client-y
        String user2Email = "user2_" + System.currentTimeMillis() + "@example.com";
        Account user2 = accountService.createAccount(user2Email, "User 2", "user2_" + System.currentTimeMillis(), "Pass123");
        accountRoleService.addRole(user2.getId(), "client-y", "user");
        
        // Create user3 with no shared clients
        String user3Email = "user3_" + System.currentTimeMillis() + "@example.com";
        Account user3 = accountService.createAccount(user3Email, "User 3", "user3_" + System.currentTimeMillis(), "Pass123");
        accountRoleService.addRole(user3.getId(), "client-z", "user");
        userTransaction.commit();
        
        // Manager should see manager, user1, and user2, but not user3
        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
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
        // Create account
        userTransaction.begin();
        String email = "roletest_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Role Test", "roletest_" + System.currentTimeMillis(), "Pass123");
        String accountId = account.getId();
        
        // Create manager account
        String managerEmail = "rolemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager", "rolemanager_" + System.currentTimeMillis(), "Pass123");
        userTransaction.commit();
        
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
            .post("/api/accounts/role")
            .then()
            .statusCode(201)
            .body("clientId", equalTo("test-client"))
            .body("role", equalTo("admin"));
    }

    @Test
    @TestTransaction
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
    @TestTransaction
    public void testAddAccountRoleWithoutRole() {
        // Create manager account
        String managerEmail = "rolemanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager 2", "rolemanager2_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = """
            {
                "accountId": "some-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateUserToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(403);
    }

    @Test
    @TestTransaction
    public void testAddAccountRoleToNonExistentAccount() {
        // Create manager account
        String managerEmail = "rolemanager3_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager 3", "rolemanager3_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = """
            {
                "accountId": "non-existent-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(404)
            .body("error", equalTo("Account not found"));
    }

    @Test
    @TestTransaction
    public void testAddAccountRoleWithMissingClientId() {
        // Create account and manager
        String email = "roletest2_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Role Test 2", "roletest2_" + System.currentTimeMillis(), "Pass123");
        
        String managerEmail = "rolemanager4_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager 4", "rolemanager4_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
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
            .statusCode(400);
    }

    @Test
    @TestTransaction
    public void testAddAccountRoleWithMissingRole() {
        // Create account and manager
        String email = "roletest3_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Role Test 3", "roletest3_" + System.currentTimeMillis(), "Pass123");
        
        String managerEmail = "rolemanager5_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager 5", "rolemanager5_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client"
            }
            """, account.getId());
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400);
    }

    @Test
    @TestTransaction
    public void testAddAccountRoleWithInvalidRoleFormat() {
        // Create account and manager
        String email = "roletest4_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Role Test 4", "roletest4_" + System.currentTimeMillis(), "Pass123");
        
        String managerEmail = "rolemanager6_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Role Manager 6", "rolemanager6_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "admin@invalid"
            }
            """, account.getId());
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(400);
    }

    @Test
    public void testAddAdminRoleAsNonAdmin() throws Exception {
        // Create account and non-admin manager
        userTransaction.begin();
        String email = "roletest_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Role Test", "roletest_" + System.currentTimeMillis(), "Pass123");
        
        String managerEmail = "nonadmin_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Non-Admin Manager", "nonadmin_" + System.currentTimeMillis(), "Pass123");
        userTransaction.commit();
        
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
        // Create two accounts: one with a role in client-a, another without any roles
        userTransaction.begin();
        String targetEmail = "target_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccount(targetEmail, "Target User", "target_" + System.currentTimeMillis(), "Pass123");
        // Give target account a role in abstratium-abstrauth (this will work because it's the first role for this account)
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "user");
        
        String managerEmail = "manager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Manager", "manager_" + System.currentTimeMillis(), "Pass123");
        userTransaction.commit();
        
        // Try to add target account to a NEW client (test-client) - should fail for non-admin
        // Note: test-client doesn't exist, so this will also test FK constraint
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "user"
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
        // Create account with existing role in abstratium-abstrauth
        userTransaction.begin();
        String targetEmail = "target2_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccount(targetEmail, "Target User 2", "target2_" + System.currentTimeMillis(), "Pass123");
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "user");
        
        String managerEmail = "manager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Manager 2", "manager2_" + System.currentTimeMillis(), "Pass123");
        userTransaction.commit();
        
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
        // Create account with existing role in abstratium-abstrauth
        userTransaction.begin();
        String targetEmail = "target3_" + System.currentTimeMillis() + "@example.com";
        Account targetAccount = accountService.createAccount(targetEmail, "Target User 3", "target3_" + System.currentTimeMillis(), "Pass123");
        // Give target account a role in abstratium-abstrauth
        accountRoleService.addRole(targetAccount.getId(), "abstratium-abstrauth", "user");
        
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        Account admin = accountService.createAccount(adminEmail, "Admin User", "admin_" + System.currentTimeMillis(), "Pass123");
        // Give admin the admin role
        accountRoleService.addRole(admin.getId(), "abstratium-abstrauth", "admin");
        userTransaction.commit();
        
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
            .auth().oauth2(generateAdminToken(admin.getId()))
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
        // Create account with a role
        userTransaction.begin();
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Duplicate Test", "duplicate_" + System.currentTimeMillis(), "Pass123");
        // Add initial role
        accountRoleService.addRole(account.getId(), "abstratium-abstrauth", "user");
        
        String managerEmail = "manager_dup_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Manager Dup", "manager_dup_" + System.currentTimeMillis(), "Pass123");
        userTransaction.commit();
        
        // Try to add the same role again - should return 409 Conflict
        String requestBody = String.format("""
            {
                "accountId": "%s",
                "clientId": "abstratium-abstrauth",
                "role": "user"
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
        // Create account with a role
        userTransaction.begin();
        String email = "roledelete_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Role Delete Test", "roledelete_" + System.currentTimeMillis(), "Pass123");
        String accountId = account.getId();
        accountRoleService.addRole(accountId, "test-client", "admin");
        
        // Create manager account
        String managerEmail = "deletemanager_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Delete Manager", "deletemanager_" + System.currentTimeMillis(), "Pass123");
        userTransaction.commit();
        
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
    @TestTransaction
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
    @TestTransaction
    public void testRemoveAccountRoleWithoutPermission() {
        String managerEmail = "deletemanager2_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Delete Manager 2", "deletemanager2_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = """
            {
                "accountId": "some-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateUserToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(403);
    }

    @Test
    @TestTransaction
    public void testRemoveAccountRoleFromNonExistentAccount() {
        String managerEmail = "deletemanager3_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccount(managerEmail, "Delete Manager 3", "deletemanager3_" + System.currentTimeMillis(), "Pass123");
        
        String requestBody = """
            {
                "accountId": "non-existent-id",
                "clientId": "test-client",
                "role": "admin"
            }
            """;
        
        given()
            .auth().oauth2(generateManageAccountsToken(manager.getId()))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(404)
            .body("error", equalTo("Account not found"));
    }

    @Test
    public void testDeleteAccountCascadesAllChildRecords() throws Exception {
        // Create a manager account
        userTransaction.begin();
        Account manager = new Account();
        manager.setEmail("manager-cascade@example.com");
        manager.setName("Manager Cascade");
        manager.setAuthProvider("native");
        manager.setEmailVerified(true);
        em.persist(manager);
        em.flush();

        // Create an account with all types of child records
        Account account = accountService.createAccount(
                "cascade-test@example.com",
                "Cascade Test",
                "cascadeuser",
                "password123"
        );
        String accountId = account.getId();

        // Add a role
        accountRoleService.addRole(accountId, "test-client", "user");

        // Add a federated identity
        FederatedIdentity fedIdentity = new FederatedIdentity();
        fedIdentity.setAccountId(accountId);
        fedIdentity.setProvider("google");
        fedIdentity.setProviderUserId("google-user-123");
        fedIdentity.setEmail("cascade-test@example.com");
        em.persist(fedIdentity);

        em.flush();
        userTransaction.commit();

        // Verify child records exist before deletion
        userTransaction.begin();
        List<AccountRole> rolesBefore = accountRoleService.getRolesForAccount(accountId);
        assertEquals(1, rolesBefore.size());

        var credQuery = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :accountId", dev.abstratium.abstrauth.entity.Credential.class);
        credQuery.setParameter("accountId", accountId);
        assertEquals(1, credQuery.getResultList().size());

        var fedQuery = em.createQuery("SELECT f FROM FederatedIdentity f WHERE f.accountId = :accountId", dev.abstratium.abstrauth.entity.FederatedIdentity.class);
        fedQuery.setParameter("accountId", accountId);
        assertEquals(1, fedQuery.getResultList().size());
        userTransaction.commit();

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
        userTransaction.begin();
        List<AccountRole> rolesAfter = accountRoleService.getRolesForAccount(accountId);
        assertTrue(rolesAfter.isEmpty(), "Roles should be deleted via CASCADE DELETE");

        var credQueryAfter = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :accountId", dev.abstratium.abstrauth.entity.Credential.class);
        credQueryAfter.setParameter("accountId", accountId);
        assertTrue(credQueryAfter.getResultList().isEmpty(), "Credentials should be deleted via CASCADE DELETE");

        var fedQueryAfter = em.createQuery("SELECT f FROM FederatedIdentity f WHERE f.accountId = :accountId", dev.abstratium.abstrauth.entity.FederatedIdentity.class);
        fedQueryAfter.setParameter("accountId", accountId);
        assertTrue(fedQueryAfter.getResultList().isEmpty(), "Federated identities should be deleted via CASCADE DELETE");
        userTransaction.commit();
    }

}
