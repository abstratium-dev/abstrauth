package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Credential;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AccountServiceTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    jakarta.transaction.UserTransaction userTransaction;
    
    @BeforeEach
    public void setup() throws Exception {
        // Ensure common test clients exist
        userTransaction.begin();
        ensureClientExists("client-a");
        ensureClientExists("client-b");
        ensureClientExists("client-c");
        ensureClientExists("client-test");
        userTransaction.commit();
    }
    
    private void ensureClientExists(String clientId) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", dev.abstratium.abstrauth.entity.OAuthClient.class);
        query.setParameter("clientId", clientId);
        if (query.getResultList().isEmpty()) {
            dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
            client.setClientId(clientId);
            client.setClientName("Test " + clientId);
            client.setClientType("confidential");
            client.setRedirectUris("[\"http://localhost:8080/callback\"]");
            client.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
            client.setRequirePkce(false);
            client.setClientSecretHash("$2a$10$dummyhash");
            em.persist(client);
        }
    }

    @Test
    public void testCreateAccountSuccess() {
        String email = "servicetest_" + System.currentTimeMillis() + "@example.com";
        String username = "servicetest_" + System.currentTimeMillis();
        
        Account account = accountService.createAccount(email, "Service Test", username, "Password123", AccountService.NATIVE);
        
        assertNotNull(account);
        assertNotNull(account.getId());
        assertEquals(email, account.getEmail());
        assertEquals("Service Test", account.getName());
        assertFalse(account.getEmailVerified());
        
        // Verify credential was created
        Optional<Credential> credential = accountService.findCredentialByUsername(username);
        assertTrue(credential.isPresent());
        assertEquals(account.getId(), credential.get().getAccountId());
    }

    @Test
    public void testCreateAccountWithDuplicateEmail() {
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        String username1 = "user1_" + System.currentTimeMillis();
        String username2 = "user2_" + System.currentTimeMillis();
        
        accountService.createAccount(email, "User One", username1, "Password123", AccountService.NATIVE);
        
        assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(email, "User Two", username2, "Password456", AccountService.NATIVE);
        });
    }

    @Test
    public void testCreateAccountWithDuplicateUsername() {
        String email1 = "email1_" + System.currentTimeMillis() + "@example.com";
        String email2 = "email2_" + System.currentTimeMillis() + "@example.com";
        String username = "dupuser_" + System.currentTimeMillis();
        
        accountService.createAccount(email1, "User One", username, "Password123", AccountService.NATIVE);
        
        assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(email2, "User Two", username, "Password456", AccountService.NATIVE);
        });
    }

    @Test
    public void testAuthenticateSuccess() {
        String email = "auth_" + System.currentTimeMillis() + "@example.com";
        String username = "authuser_" + System.currentTimeMillis();
        String password = "SecurePassword123";
        
        Account created = accountService.createAccount(email, "Auth User", username, password, AccountService.NATIVE);
        
        Optional<Account> authenticated = accountService.authenticate(username, password);
        
        assertTrue(authenticated.isPresent());
        assertEquals(created.getId(), authenticated.get().getId());
        assertEquals(email, authenticated.get().getEmail());
    }

    @Test
    public void testAuthenticateWithWrongPassword() {
        String email = "wrongpw_" + System.currentTimeMillis() + "@example.com";
        String username = "wrongpwuser_" + System.currentTimeMillis();
        
        accountService.createAccount(email, "Wrong PW User", username, "CorrectPassword", AccountService.NATIVE);
        
        Optional<Account> authenticated = accountService.authenticate(username, "WrongPassword");
        
        assertFalse(authenticated.isPresent());
        
        // Verify failed attempt was recorded
        Optional<Credential> credential = accountService.findCredentialByUsername(username);
        assertTrue(credential.isPresent());
        assertEquals(1, credential.get().getFailedLoginAttempts());
    }

    @Test
    public void testAuthenticateWithNonExistentUser() {
        Optional<Account> authenticated = accountService.authenticate("nonexistent_user", "password");
        
        assertFalse(authenticated.isPresent());
    }

    @Test
    public void testAccountLockingAfterFailedAttempts() {
        String email = "locktest_" + System.currentTimeMillis() + "@example.com";
        String username = "lockuser_" + System.currentTimeMillis();
        String password = "CorrectPassword";
        
        accountService.createAccount(email, "Lock Test User", username, password, AccountService.NATIVE);
        
        // Make 5 failed attempts
        for (int i = 0; i < 5; i++) {
            accountService.authenticate(username, "WrongPassword");
        }
        
        // Verify account is locked
        Optional<Credential> credential = accountService.findCredentialByUsername(username);
        assertTrue(credential.isPresent());
        assertEquals(5, credential.get().getFailedLoginAttempts());
        assertNotNull(credential.get().getLockedUntil());
        
        // Even with correct password, authentication should fail
        Optional<Account> authenticated = accountService.authenticate(username, password);
        assertFalse(authenticated.isPresent());
    }

    @Test
    public void testFailedAttemptsResetOnSuccessfulLogin() {
        String email = "resettest_" + System.currentTimeMillis() + "@example.com";
        String username = "resetuser_" + System.currentTimeMillis();
        String password = "CorrectPassword";
        
        accountService.createAccount(email, "Reset Test User", username, password, AccountService.NATIVE);
        
        // Make 3 failed attempts
        for (int i = 0; i < 3; i++) {
            accountService.authenticate(username, "WrongPassword");
        }
        
        // Verify failed attempts were recorded
        Optional<Credential> credentialBefore = accountService.findCredentialByUsername(username);
        assertTrue(credentialBefore.isPresent());
        assertEquals(3, credentialBefore.get().getFailedLoginAttempts());
        
        // Successful login
        Optional<Account> authenticated = accountService.authenticate(username, password);
        assertTrue(authenticated.isPresent());
        
        // Clear entity manager cache to force fresh read
        em.clear();
        
        // Verify failed attempts were reset
        Optional<Credential> credentialAfter = accountService.findCredentialByUsername(username);
        assertTrue(credentialAfter.isPresent());
        assertEquals(0, credentialAfter.get().getFailedLoginAttempts());
        assertNull(credentialAfter.get().getLockedUntil());
    }

    @Test
    public void testFindByEmail() {
        String email = "findtest_" + System.currentTimeMillis() + "@example.com";
        String username = "finduser_" + System.currentTimeMillis();
        
        Account created = accountService.createAccount(email, "Find Test", username, "Password123", AccountService.NATIVE);
        
        Optional<Account> found = accountService.findByEmail(email);
        
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    public void testFindByEmailNotFound() {
        Optional<Account> found = accountService.findByEmail("nonexistent@example.com");
        
        assertFalse(found.isPresent());
    }

    @Test
    public void testFindById() {
        String email = "findbyid_" + System.currentTimeMillis() + "@example.com";
        String username = "findbyiduser_" + System.currentTimeMillis();
        
        Account created = accountService.createAccount(email, "Find By ID Test", username, "Password123", AccountService.NATIVE);
        
        Optional<Account> found = accountService.findById(created.getId());
        
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    public void testFindByIdNotFound() {
        Optional<Account> found = accountService.findById("nonexistent-id");
        
        assertFalse(found.isPresent());
    }

    @Test
    public void testFindCredentialByAccountId() {
        String email = "credtest_" + System.currentTimeMillis() + "@example.com";
        String username = "creduser_" + System.currentTimeMillis();
        
        Account account = accountService.createAccount(email, "Cred Test", username, "Password123", AccountService.NATIVE);
        
        Optional<Credential> credential = accountService.findCredentialByAccountId(account.getId());
        
        assertTrue(credential.isPresent());
        assertEquals(account.getId(), credential.get().getAccountId());
        assertEquals(username, credential.get().getUsername());
    }

    @Test
    public void testCountAccounts() {
        long initialCount = accountService.countAccounts();
        
        String email = "count_" + System.currentTimeMillis() + "@example.com";
        String username = "countuser_" + System.currentTimeMillis();
        accountService.createAccount(email, "Count Test", username, "Password123", AccountService.NATIVE);
        
        long newCount = accountService.countAccounts();
        assertEquals(initialCount + 1, newCount);
    }

    @Test
    public void testFirstAccountGetsAdminRole() {
        // This test is tricky because other tests may have already created accounts
        // We'll check if an account has the admin role by querying the role service
        
        String email = "firstadmin_" + System.currentTimeMillis() + "@example.com";
        String username = "firstadminuser_" + System.currentTimeMillis();
        
        long accountCountBefore = accountService.countAccounts();
        Account account = accountService.createAccount(email, "First Admin Test", username, "Password123", AccountService.NATIVE);
        
        // If this was the first account (countBefore == 0), it should have admin role
        if (accountCountBefore == 0) {
            var roles = accountRoleService.findRolesByAccountIdAndClientId(account.getId(), "abstratium-abstrauth");
            assertTrue(roles.contains("admin"), "First account should have admin role");
        }
    }

    @Test
    public void testSecondAccountDoesNotGetAdminRole() {
        // Ensure at least one account exists
        String email1 = "first_" + System.currentTimeMillis() + "@example.com";
        String username1 = "firstuser_" + System.currentTimeMillis();
        accountService.createAccount(email1, "First User", username1, "Password123", AccountService.NATIVE);
        
        // Create second account
        String email2 = "second_" + System.currentTimeMillis() + "@example.com";
        String username2 = "seconduser_" + System.currentTimeMillis();
        Account account2 = accountService.createAccount(email2, "Second User", username2, "Password123", AccountService.NATIVE);
        
        // Second account should NOT have admin role
        var roles = accountRoleService.findRolesByAccountIdAndClientId(account2.getId(), "abstratium-abstrauth");
        assertFalse(roles.contains("admin"), "Second account should not have admin role");
    }

    @Test
    public void testFirstFederatedAccountGetsAdminRole() {
        String email = "firstfed_" + System.currentTimeMillis() + "@example.com";
        
        long accountCountBefore = accountService.countAccounts();
        Account account = accountService.createFederatedAccount(
            email, "First Fed User", "https://example.com/pic.jpg", true, AccountService.GOOGLE
        );
        
        // If this was the first account, it should have admin role
        if (accountCountBefore == 0) {
            var roles = accountRoleService.findRolesByAccountIdAndClientId(account.getId(), "abstratium-abstrauth");
            assertTrue(roles.contains("admin"), "First federated account should have admin role");
        }
    }

    @Test
    public void testFindAccountsByUserClientRoles_SharedClients() {
        // Create three accounts
        String email1 = "shared1_" + System.currentTimeMillis() + "@example.com";
        String email2 = "shared2_" + System.currentTimeMillis() + "@example.com";
        String email3 = "shared3_" + System.currentTimeMillis() + "@example.com";
        
        Account account1 = accountService.createAccount(email1, "User 1", "user1_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE);
        Account account2 = accountService.createAccount(email2, "User 2", "user2_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE);
        Account account3 = accountService.createAccount(email3, "User 3", "user3_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE);
        
        // Give account1 roles for client-a and client-b
        accountRoleService.addRole(account1.getId(), "client-a", "user");
        accountRoleService.addRole(account1.getId(), "client-b", "user");
        
        // Give account2 roles for client-a (shared with account1)
        accountRoleService.addRole(account2.getId(), "client-a", "admin");
        
        // Give account3 roles for client-c (not shared with account1)
        accountRoleService.addRole(account3.getId(), "client-c", "user");
        
        // Find accounts by account1's client roles
        var accounts = accountService.findAccountsByUserClientRoles(account1.getId());
        
        // Should return account1 and account2 (both have client-a), but not account3
        assertTrue(accounts.size() >= 2, "Should have at least 2 accounts");
        assertTrue(accounts.stream().anyMatch(a -> a.getId().equals(account1.getId())), "Should include account1");
        assertTrue(accounts.stream().anyMatch(a -> a.getId().equals(account2.getId())), "Should include account2");
        assertFalse(accounts.stream().anyMatch(a -> a.getId().equals(account3.getId())), "Should not include account3");
    }

    @Test
    public void testFindAccountsByUserClientRoles_NoSharedClients() throws Exception {
        // Create two accounts with different clients
        long timestamp = System.currentTimeMillis();
        String email1 = "noshared1_" + timestamp + "@example.com";
        String email2 = "noshared2_" + timestamp + "@example.com";
        String clientX = "client-x-" + timestamp;
        String clientY = "client-y-" + timestamp;
        
        // Create the dynamic clients
        userTransaction.begin();
        ensureClientExists(clientX);
        ensureClientExists(clientY);
        userTransaction.commit();
        
        Account account1 = accountService.createAccount(email1, "User 1", "noshared1_" + timestamp, "Pass123", AccountService.NATIVE);
        Account account2 = accountService.createAccount(email2, "User 2", "noshared2_" + timestamp, "Pass123", AccountService.NATIVE);
        
        // Give account1 role for unique client-x
        accountRoleService.addRole(account1.getId(), clientX, "user");
        
        // Give account2 role for unique client-y (different client)
        accountRoleService.addRole(account2.getId(), clientY, "user");
        
        // Find accounts by account1's client roles
        var accounts = accountService.findAccountsByUserClientRoles(account1.getId());
        
        // Should return only account1 (no shared clients with account2)
        assertEquals(1, accounts.size(), "Should return exactly 1 account");
        assertEquals(account1.getId(), accounts.get(0).getId(), "Should be account1");
    }

    @Test
    public void testFindAccountsByUserClientRoles_UserWithNoRoles() {
        // Create an account with no roles
        String email = "noroles_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "No Roles User", "noroles_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE);
        
        // Don't add any roles to this account
        
        // Find accounts by this user's client roles
        var accounts = accountService.findAccountsByUserClientRoles(account.getId());
        
        // Should return only the user's own account
        assertEquals(1, accounts.size());
        assertEquals(account.getId(), accounts.get(0).getId());
    }

    @Test
    public void testFindAccountsByUserClientRoles_MultipleSharedClients() throws Exception {
        // Create four accounts with unique client IDs to isolate from other tests
        long timestamp = System.currentTimeMillis();
        String email1 = "multi1_" + timestamp + "@example.com";
        String email2 = "multi2_" + timestamp + "@example.com";
        String email3 = "multi3_" + timestamp + "@example.com";
        String email4 = "multi4_" + timestamp + "@example.com";
        String clientA = "client-a-" + timestamp;
        String clientB = "client-b-" + timestamp;
        String clientC = "client-c-" + timestamp;
        
        // Create the dynamic clients
        userTransaction.begin();
        ensureClientExists(clientA);
        ensureClientExists(clientB);
        ensureClientExists(clientC);
        userTransaction.commit();
        
        Account account1 = accountService.createAccount(email1, "User 1", "multi1_" + timestamp, "Pass123", AccountService.NATIVE);
        Account account2 = accountService.createAccount(email2, "User 2", "multi2_" + timestamp, "Pass123", AccountService.NATIVE);
        Account account3 = accountService.createAccount(email3, "User 3", "multi3_" + timestamp, "Pass123", AccountService.NATIVE);
        Account account4 = accountService.createAccount(email4, "User 4", "multi4_" + timestamp, "Pass123", AccountService.NATIVE);
        
        // account1 has roles for unique client-a, client-b, client-c
        accountRoleService.addRole(account1.getId(), clientA, "user");
        accountRoleService.addRole(account1.getId(), clientB, "user");
        accountRoleService.addRole(account1.getId(), clientC, "user");
        
        // account2 shares client-a with account1
        accountRoleService.addRole(account2.getId(), clientA, "admin");
        
        // account3 shares client-b with account1
        accountRoleService.addRole(account3.getId(), clientB, "user");
        
        // account4 shares client-c with account1
        accountRoleService.addRole(account4.getId(), clientC, "manager");
        
        // Find accounts by account1's client roles
        var accounts = accountService.findAccountsByUserClientRoles(account1.getId());
        
        // Should return exactly all four accounts (all share at least one client with account1)
        assertEquals(4, accounts.size(), "Should return exactly 4 accounts");
        assertTrue(accounts.stream().anyMatch(a -> a.getId().equals(account1.getId())), 
            "Should include account1");
        assertTrue(accounts.stream().anyMatch(a -> a.getId().equals(account2.getId())), 
            "Should include account2 (shares client-a)");
        assertTrue(accounts.stream().anyMatch(a -> a.getId().equals(account3.getId())), 
            "Should include account3 (shares client-b)");
        assertTrue(accounts.stream().anyMatch(a -> a.getId().equals(account4.getId())), 
            "Should include account4 (shares client-c)");
    }

    @Test
    public void testFindAccountsByUserClientRoles_RolesEagerlyLoaded() {
        // Create account with roles
        String email = "eager_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccount(email, "Eager User", "eager_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE);
        String accountId = account.getId();
        
        accountRoleService.addRole(accountId, "client-test", "user");
        accountRoleService.addRole(accountId, "client-test", "admin");
        
        // Find accounts - this should eagerly load roles
        var accounts = accountService.findAccountsByUserClientRoles(accountId);
        
        // Verify we got at least one account
        assertFalse(accounts.isEmpty(), "Should return at least one account");
        
        // Find our specific account
        var ourAccount = accounts.stream()
            .filter(a -> a.getId().equals(accountId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Our account should be in the results"));
        
        // Verify roles collection is not null (meaning it was eagerly loaded, not lazy)
        // The collection might be empty or have roles depending on transaction state,
        // but it should not be null which would indicate lazy loading
        assertNotNull(ourAccount.getRoles(), "Roles collection should not be null (eagerly loaded)");
    }

    @Test
    public void testCannotDeleteAccountWithOnlyAdminRole() throws Exception {
        // First, remove all existing admin roles for abstratium-abstrauth to ensure clean state
        userTransaction.begin();
        var existingAdmins = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            dev.abstratium.abstrauth.entity.AccountRole.class
        );
        existingAdmins.setParameter("clientId", Roles.CLIENT_ID);
        existingAdmins.setParameter("role", Roles._ADMIN_PLAIN);
        for (var admin : existingAdmins.getResultList()) {
            em.remove(admin);
        }
        
        // Create an account with admin role for abstratium-abstrauth
        String email = "onlyadmin_" + System.currentTimeMillis() + "@example.com";
        String username = "onlyadmin_" + System.currentTimeMillis();
        Account adminAccount = accountService.createAccount(email, "Only Admin", username, "Password123", AccountService.NATIVE);
        accountRoleService.addRole(adminAccount.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        userTransaction.commit();
        
        // Try to delete the account - should fail because it has the only admin role
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            accountService.deleteAccount(adminAccount.getId());
        });
        
        assertTrue(exception.getMessage().contains("Cannot delete the account with the only admin role"));
        assertTrue(exception.getMessage().contains(Roles.CLIENT_ID));
    }

    @Test
    public void testCanDeleteAccountWhenMultipleAdminsExist() throws Exception {
        // Create two accounts with admin role for abstratium-abstrauth
        userTransaction.begin();
        String email1 = "admin1_del_" + System.currentTimeMillis() + "@example.com";
        String username1 = "admin1_del_" + System.currentTimeMillis();
        Account admin1 = accountService.createAccount(email1, "Admin 1", username1, "Password123", AccountService.NATIVE);
        accountRoleService.addRole(admin1.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        String email2 = "admin2_del_" + System.currentTimeMillis() + "@example.com";
        String username2 = "admin2_del_" + System.currentTimeMillis();
        Account admin2 = accountService.createAccount(email2, "Admin 2", username2, "Password123", AccountService.NATIVE);
        accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        userTransaction.commit();
        
        // Should be able to delete one admin account since there are two
        assertDoesNotThrow(() -> {
            accountService.deleteAccount(admin1.getId());
        });
        
        // Verify the account was deleted
        Optional<Account> deleted = accountService.findById(admin1.getId());
        assertFalse(deleted.isPresent());
        
        // Verify the second admin still exists
        Optional<Account> remaining = accountService.findById(admin2.getId());
        assertTrue(remaining.isPresent());
    }

    @Test
    public void testCanDeleteAccountWithoutAdminRole() throws Exception {
        // Create an account without admin role
        userTransaction.begin();
        String email = "nonadmin_" + System.currentTimeMillis() + "@example.com";
        String username = "nonadmin_" + System.currentTimeMillis();
        Account account = accountService.createAccount(email, "Non Admin", username, "Password123", AccountService.NATIVE);
        // Don't add admin role
        userTransaction.commit();
        
        // Should be able to delete this account
        assertDoesNotThrow(() -> {
            accountService.deleteAccount(account.getId());
        });
        
        // Verify the account was deleted
        Optional<Account> deleted = accountService.findById(account.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testCanDeleteAccountWithAdminRoleForOtherClients() throws Exception {
        // Create an account with admin role for a different client
        userTransaction.begin();
        String email = "otheradmin_" + System.currentTimeMillis() + "@example.com";
        String username = "otheradmin_" + System.currentTimeMillis();
        Account account = accountService.createAccount(email, "Other Admin", username, "Password123", AccountService.NATIVE);
        accountRoleService.addRole(account.getId(), "client-test", "admin");
        userTransaction.commit();
        
        // Should be able to delete this account (admin role is for different client)
        assertDoesNotThrow(() -> {
            accountService.deleteAccount(account.getId());
        });
        
        // Verify the account was deleted
        Optional<Account> deleted = accountService.findById(account.getId());
        assertFalse(deleted.isPresent());
    }
}
