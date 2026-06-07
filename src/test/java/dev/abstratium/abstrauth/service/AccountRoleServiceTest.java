package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AccountRoleServiceTest {

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    AccountService accountService;
    
    @Inject
    jakarta.persistence.EntityManager em;
    
    @Inject
    TestTransactionHelper transactionHelper;

    private String testAccountId;
    private static final String TEST_CLIENT_ID = "test_client_123";
    private static final String TEST_CLIENT_ID_2 = "test_client_456";

    @BeforeEach
    public void setup() throws Exception {
        transactionHelper.beginTransaction();
        
        // Ensure test clients exist
        for (String clientId : new String[]{TEST_CLIENT_ID, TEST_CLIENT_ID_2}) {
            var clientQuery = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", dev.abstratium.abstrauth.entity.OAuthClient.class);
            clientQuery.setParameter("clientId", clientId);
            if (clientQuery.getResultList().isEmpty()) {
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
        // Create a test account
        String uniqueEmail = "roletest_" + System.nanoTime() + "@example.com";
        String uniqueUsername = "roletest_" + System.nanoTime();
        
        Account account = accountService.createAccount(
            uniqueEmail,
            "Role Test User",
            uniqueUsername,
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");
        testAccountId = account.getId();
        
        transactionHelper.commitTransaction();
    }

    @Test
    public void testAddRole() {
        AccountRole role = accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        
        assertNotNull(role);
        assertNotNull(role.getId());
        assertEquals(testAccountId, role.getAccountId());
        assertEquals(TEST_CLIENT_ID, role.getClientId());
        assertEquals("admin", role.getRole());
        assertNotNull(role.getCreatedAt());
    }

    @Test
    public void testFindRolesByAccountIdAndClientId() {
        // Add multiple roles
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "user");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "editor");

        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        
        assertEquals(3, roles.size());
        assertTrue(roles.contains("user"));
        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("editor"));
    }

    @Test
    public void testFindRolesByAccountIdAndClientIdWithNoRoles() {
        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        
        assertTrue(roles.isEmpty());
    }

    @Test
    public void testFindRolesByAccountIdAndClientIdIsolation() {
        // Add roles for different clients
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID_2, "user");

        Set<String> rolesClient1 = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        Set<String> rolesClient2 = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID_2);
        
        assertEquals(1, rolesClient1.size());
        assertTrue(rolesClient1.contains("admin"));
        
        assertEquals(1, rolesClient2.size());
        assertTrue(rolesClient2.contains("user"));
    }

    @Test
    public void testFindRolesByAccountId() {
        // Add roles for multiple clients (note: account already has automatic roles:
        // "user", "manage_accounts", "manage_clients" because account is an owner)
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "editor");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID_2, "viewer");

        List<AccountRole> roles = accountRoleService.findRolesByAccountId(testAccountId);
        
        // Expect 6 roles: 3 automatic (user, manage_accounts, manage_clients) + 3 manually added
        assertEquals(6, roles.size());
    }

    @Test
    public void testRemoveRole() {
        // Add roles
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "user");

        // Verify roles exist
        Set<String> rolesBefore = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        assertEquals(2, rolesBefore.size());

        // Remove one role
        accountRoleService.removeRole(testAccountId, TEST_CLIENT_ID, "admin");

        // Verify role was removed
        Set<String> rolesAfter = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        assertEquals(1, rolesAfter.size());
        assertTrue(rolesAfter.contains("user"));
        assertFalse(rolesAfter.contains("admin"));
    }

    @Test
    public void testRemoveNonExistentRole() {
        // Should not throw exception when removing non-existent role
        assertDoesNotThrow(() -> {
            accountRoleService.removeRole(testAccountId, TEST_CLIENT_ID, "nonexistent");
        });
    }

    @Test
    public void testMultipleAccountsWithSameRole() {
        // Create another account
        String uniqueEmail2 = "roletest2_" + System.nanoTime() + "@example.com";
        String uniqueUsername2 = "roletest2_" + System.nanoTime();
        Account account2 = accountService.createAccount(
            uniqueEmail2,
            "Role Test User 2",
            uniqueUsername2,
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");

        // Add same role to both accounts for same client
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(account2.getId(), TEST_CLIENT_ID, "admin");

        // Verify both accounts have the role
        Set<String> roles1 = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        Set<String> roles2 = accountRoleService.findRolesByAccountIdAndClientId(account2.getId(), TEST_CLIENT_ID);
        
        assertTrue(roles1.contains("admin"));
        assertTrue(roles2.contains("admin"));
    }

    @Test
    public void testCannotDeleteLastAdminRoleForAbstrauthClient() throws Exception {
        // First, remove all existing admin roles for abstratium-abstrauth to ensure clean state
        transactionHelper.beginTransaction();
        var existingAdmins = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            dev.abstratium.abstrauth.entity.AccountRole.class
        );
        existingAdmins.setParameter("clientId", Roles.CLIENT_ID);
        existingAdmins.setParameter("role", Roles._ADMIN_PLAIN);
        for (var admin : existingAdmins.getResultList()) {
            em.remove(admin);
        }
        
        // Create a test account with admin role for abstratium-abstrauth
        String uniqueEmail = "admintest_" + System.nanoTime() + "@example.com";
        String uniqueUsername = "admintest_" + System.nanoTime();
        Account adminAccount = accountService.createAccount(
            uniqueEmail,
            "Admin Test User",
            uniqueUsername,
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");
        
        // Add admin role for abstratium-abstrauth
        accountRoleService.addRole(adminAccount.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        transactionHelper.commitTransaction();
        
        // Try to remove the admin role - should fail because it's the only one
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            accountRoleService.removeRole(adminAccount.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        });
        
        assertTrue(exception.getMessage().contains("Cannot delete the last admin role"));
        assertTrue(exception.getMessage().contains(Roles.CLIENT_ID));
    }

    @Test
    public void testCanDeleteAdminRoleWhenMultipleAdminsExist() throws Exception {
        // Create two accounts with admin role for abstratium-abstrauth
        transactionHelper.beginTransaction();
        String uniqueEmail1 = "admin1_" + System.nanoTime() + "@example.com";
        String uniqueUsername1 = "admin1_" + System.nanoTime();
        Account admin1 = accountService.createAccount(
            uniqueEmail1,
            "Admin 1",
            uniqueUsername1,
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");
        accountRoleService.addRole(admin1.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        String uniqueEmail2 = "admin2_" + System.nanoTime() + "@example.com";
        String uniqueUsername2 = "admin2_" + System.nanoTime();
        Account admin2 = accountService.createAccount(
            uniqueEmail2,
            "Admin 2",
            uniqueUsername2,
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");
        accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        transactionHelper.commitTransaction();
        
        // Should be able to remove one admin role since there are two
        assertDoesNotThrow(() -> {
            accountRoleService.removeRole(admin1.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        });
        
        // Verify the role was removed
        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(admin1.getId(), Roles.CLIENT_ID);
        assertFalse(roles.contains(Roles._ADMIN_PLAIN));
        
        // Verify the second admin still has the role
        Set<String> roles2 = accountRoleService.findRolesByAccountIdAndClientId(admin2.getId(), Roles.CLIENT_ID);
        assertTrue(roles2.contains(Roles._ADMIN_PLAIN));
    }

    @Test
    public void testCanDeleteAdminRoleForOtherClients() throws Exception {
        // Add admin role for a different client
        transactionHelper.beginTransaction();
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        transactionHelper.commitTransaction();
        
        // Should be able to remove admin role for other clients even if it's the only one
        assertDoesNotThrow(() -> {
            accountRoleService.removeRole(testAccountId, TEST_CLIENT_ID, "admin");
        });
        
        // Verify the role was removed
        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        assertFalse(roles.contains("admin"));
    }

    @Test
    public void testRoleInAllowlistCanBeAddedForPublicClient() throws Exception {
        // Create a public client with allowlist entries
        String publicClientId = "public_client_" + System.nanoTime();
        transactionHelper.beginTransaction();
        
        dev.abstratium.abstrauth.entity.OAuthClient publicClient = new dev.abstratium.abstrauth.entity.OAuthClient();
        publicClient.setClientId(publicClientId);
        publicClient.setClientName("Public Test Client");
        publicClient.setClientType("confidential");
        publicClient.setRedirectUris("[\"http://localhost:8080/callback\"]");
        publicClient.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
        publicClient.setRequirePkce(false);
        em.persist(publicClient);
        
        // Add allowed roles for this client
        dev.abstratium.abstrauth.entity.ClientAllowedRole allowedRole1 = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        allowedRole1.setClientId(publicClientId);
        allowedRole1.setRole("viewer");
        allowedRole1.setIsDefault(false);
        em.persist(allowedRole1);
        
        dev.abstratium.abstrauth.entity.ClientAllowedRole allowedRole2 = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        allowedRole2.setClientId(publicClientId);
        allowedRole2.setRole("editor");
        allowedRole2.setIsDefault(true);
        em.persist(allowedRole2);
        
        transactionHelper.commitTransaction();
        
        // Should be able to add a role that is in the allowlist
        assertDoesNotThrow(() -> {
            accountRoleService.addRole(testAccountId, publicClientId, "viewer");
        });
        
        // Verify the role was added
        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, publicClientId);
        assertTrue(roles.contains("viewer"));
    }

    @Test
    public void testRoleNotInAllowlistRejectedForPublicClient() throws Exception {
        // Create a public client with allowlist entries
        String publicClientId = "public_client_" + System.nanoTime();
        transactionHelper.beginTransaction();
        
        dev.abstratium.abstrauth.entity.OAuthClient publicClient = new dev.abstratium.abstrauth.entity.OAuthClient();
        publicClient.setClientId(publicClientId);
        publicClient.setClientName("Public Test Client");
        publicClient.setClientType("confidential");
        publicClient.setRedirectUris("[\"http://localhost:8080/callback\"]");
        publicClient.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
        publicClient.setRequirePkce(false);
        em.persist(publicClient);
        
        // Add only "viewer" to allowlist
        dev.abstratium.abstrauth.entity.ClientAllowedRole allowedRole = new dev.abstratium.abstrauth.entity.ClientAllowedRole();
        allowedRole.setClientId(publicClientId);
        allowedRole.setRole("viewer");
        allowedRole.setIsDefault(false);
        em.persist(allowedRole);
        
        transactionHelper.commitTransaction();
        
        // Should NOT be able to add "admin" since it's not in the allowlist
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            accountRoleService.addRole(testAccountId, publicClientId, "admin");
        });
        
        assertTrue(exception.getMessage().contains("not in the allowlist"));
        assertTrue(exception.getMessage().contains(publicClientId));
    }

    @Test
    public void testAnyRoleAllowedForPrivateClient() throws Exception {
        // Create a private client with NO allowlist entries
        String privateClientId = "private_client_" + System.nanoTime();
        transactionHelper.beginTransaction();
        
        dev.abstratium.abstrauth.entity.OAuthClient privateClient = new dev.abstratium.abstrauth.entity.OAuthClient();
        privateClient.setClientId(privateClientId);
        privateClient.setClientName("Private Test Client");
        privateClient.setClientType("confidential");
        privateClient.setRedirectUris("[\"http://localhost:8080/callback\"]");
        privateClient.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
        privateClient.setRequirePkce(false);
        em.persist(privateClient);
        
        transactionHelper.commitTransaction();
        
        // Should be able to add any role since there's no allowlist
        assertDoesNotThrow(() -> {
            accountRoleService.addRole(testAccountId, privateClientId, "custom-role");
            accountRoleService.addRole(testAccountId, privateClientId, "another-role");
        });
        
        // Verify the roles were added
        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, privateClientId);
        assertTrue(roles.contains("custom-role"));
        assertTrue(roles.contains("another-role"));
    }
}
