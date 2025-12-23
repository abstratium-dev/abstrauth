package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
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
    jakarta.transaction.UserTransaction userTransaction;

    private String testAccountId;
    private static final String TEST_CLIENT_ID = "test_client_123";
    private static final String TEST_CLIENT_ID_2 = "test_client_456";

    @BeforeEach
    public void setup() throws Exception {
        userTransaction.begin();
        
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
                client.setClientSecretHash("$2a$10$dummyhash");
                em.persist(client);
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
            AccountService.NATIVE
        );
        testAccountId = account.getId();
        
        userTransaction.commit();
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
        // Add roles for multiple clients (note: account already has automatic "user" role)
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "editor");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID_2, "viewer");

        List<AccountRole> roles = accountRoleService.findRolesByAccountId(testAccountId);
        
        // Expect 4 roles: automatic "user" + 3 manually added
        assertEquals(4, roles.size());
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
            AccountService.NATIVE
        );

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
        
        // Create a test account with admin role for abstratium-abstrauth
        String uniqueEmail = "admintest_" + System.nanoTime() + "@example.com";
        String uniqueUsername = "admintest_" + System.nanoTime();
        Account adminAccount = accountService.createAccount(
            uniqueEmail,
            "Admin Test User",
            uniqueUsername,
            "TestPassword123",
            AccountService.NATIVE
        );
        
        // Add admin role for abstratium-abstrauth
        accountRoleService.addRole(adminAccount.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        userTransaction.commit();
        
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
        userTransaction.begin();
        String uniqueEmail1 = "admin1_" + System.nanoTime() + "@example.com";
        String uniqueUsername1 = "admin1_" + System.nanoTime();
        Account admin1 = accountService.createAccount(
            uniqueEmail1,
            "Admin 1",
            uniqueUsername1,
            "TestPassword123",
            AccountService.NATIVE
        );
        accountRoleService.addRole(admin1.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        
        String uniqueEmail2 = "admin2_" + System.nanoTime() + "@example.com";
        String uniqueUsername2 = "admin2_" + System.nanoTime();
        Account admin2 = accountService.createAccount(
            uniqueEmail2,
            "Admin 2",
            uniqueUsername2,
            "TestPassword123",
            AccountService.NATIVE
        );
        accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        userTransaction.commit();
        
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
        userTransaction.begin();
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        userTransaction.commit();
        
        // Should be able to remove admin role for other clients even if it's the only one
        assertDoesNotThrow(() -> {
            accountRoleService.removeRole(testAccountId, TEST_CLIENT_ID, "admin");
        });
        
        // Verify the role was removed
        Set<String> roles = accountRoleService.findRolesByAccountIdAndClientId(testAccountId, TEST_CLIENT_ID);
        assertFalse(roles.contains("admin"));
    }
}
