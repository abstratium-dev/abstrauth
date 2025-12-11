package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

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
            .groups("abstratium-abstrauth_admin")
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

}
