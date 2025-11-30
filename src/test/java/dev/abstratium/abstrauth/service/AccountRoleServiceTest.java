package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    private String testAccountId;
    private static final String TEST_CLIENT_ID = "test_client_123";
    private static final String TEST_CLIENT_ID_2 = "test_client_456";

    @BeforeEach
    @Transactional
    public void setup() {
        // Create a test account
        String uniqueEmail = "roletest_" + System.nanoTime() + "@example.com";
        String uniqueUsername = "roletest_" + System.nanoTime();
        
        Account account = accountService.createAccount(
            uniqueEmail,
            "Role Test User",
            uniqueUsername,
            "TestPassword123"
        );
        testAccountId = account.getId();
    }

    @Test
    @Transactional
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
    @Transactional
    public void testGetRolesForAccountAndClient() {
        // Add multiple roles
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "user");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "editor");

        Set<String> roles = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID);
        
        assertEquals(3, roles.size());
        assertTrue(roles.contains("user"));
        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("editor"));
    }

    @Test
    @Transactional
    public void testGetRolesForAccountAndClientWithNoRoles() {
        Set<String> roles = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID);
        
        assertTrue(roles.isEmpty());
    }

    @Test
    @Transactional
    public void testGetRolesForAccountAndClientIsolation() {
        // Add roles for different clients
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID_2, "user");

        Set<String> rolesClient1 = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID);
        Set<String> rolesClient2 = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID_2);
        
        assertEquals(1, rolesClient1.size());
        assertTrue(rolesClient1.contains("admin"));
        
        assertEquals(1, rolesClient2.size());
        assertTrue(rolesClient2.contains("user"));
    }

    @Test
    @Transactional
    public void testGetRolesForAccount() {
        // Add roles for multiple clients
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "user");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID_2, "viewer");

        List<AccountRole> roles = accountRoleService.getRolesForAccount(testAccountId);
        
        assertEquals(3, roles.size());
    }

    @Test
    @Transactional
    public void testRemoveRole() {
        // Add roles
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "user");

        // Verify roles exist
        Set<String> rolesBefore = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID);
        assertEquals(2, rolesBefore.size());

        // Remove one role
        accountRoleService.removeRole(testAccountId, TEST_CLIENT_ID, "admin");

        // Verify role was removed
        Set<String> rolesAfter = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID);
        assertEquals(1, rolesAfter.size());
        assertTrue(rolesAfter.contains("user"));
        assertFalse(rolesAfter.contains("admin"));
    }

    @Test
    @Transactional
    public void testRemoveNonExistentRole() {
        // Should not throw exception when removing non-existent role
        assertDoesNotThrow(() -> {
            accountRoleService.removeRole(testAccountId, TEST_CLIENT_ID, "nonexistent");
        });
    }

    @Test
    @Transactional
    public void testMultipleAccountsWithSameRole() {
        // Create another account
        String uniqueEmail2 = "roletest2_" + System.nanoTime() + "@example.com";
        String uniqueUsername2 = "roletest2_" + System.nanoTime();
        Account account2 = accountService.createAccount(
            uniqueEmail2,
            "Role Test User 2",
            uniqueUsername2,
            "TestPassword123"
        );

        // Add same role to both accounts for same client
        accountRoleService.addRole(testAccountId, TEST_CLIENT_ID, "admin");
        accountRoleService.addRole(account2.getId(), TEST_CLIENT_ID, "admin");

        // Verify both accounts have the role
        Set<String> roles1 = accountRoleService.getRolesForAccountAndClient(testAccountId, TEST_CLIENT_ID);
        Set<String> roles2 = accountRoleService.getRolesForAccountAndClient(account2.getId(), TEST_CLIENT_ID);
        
        assertTrue(roles1.contains("admin"));
        assertTrue(roles2.contains("admin"));
    }
}
