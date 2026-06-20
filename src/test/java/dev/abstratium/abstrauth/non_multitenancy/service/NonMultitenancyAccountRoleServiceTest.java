package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccountRole;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class NonMultitenancyAccountRoleServiceTest {

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    AccountService accountService;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String testAccountId;
    private String testOrgId;
    private static final String TEST_CLIENT_ID = "test_client_non_multi_123";
    private static final String TEST_CLIENT_ID_2 = "test_client_non_multi_456";

    @BeforeEach
    public void setup() throws Exception {
        transactionHelper.beginTransaction();

        // Reset tenant context to the default org before querying OAuth clients
        dbResetHelper.resetDatabase();

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

                // Create client secret
                dev.abstratium.abstrauth.entity.ClientSecret secret = new dev.abstratium.abstrauth.entity.ClientSecret();
                secret.setClientId(clientId);
                secret.setSecretHash("$2a$10$dummyhash");
                secret.setDescription("Test secret");
                secret.setActive(true);
                em.persist(secret);
            }
        }

        // Create a test account
        String uniqueEmail = "nonmultitest_" + System.nanoTime() + "@example.com";
        String uniqueUsername = "nonmultitest_" + System.nanoTime();

        Account account = accountService.createAccount(
            uniqueEmail,
            "Non-Multitenancy Test User",
            uniqueUsername,
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");
        testAccountId = account.getId();
        testOrgId = defaultOrgId;
        transactionHelper.commitTransaction();
    }

    @Test
    public void testAddRoleWithExplicitOrgId() {
        NonMultitenancyAccountRole role = nonMultitenancyAccountRoleService.addRole(
            testOrgId, testAccountId, TEST_CLIENT_ID, "admin");

        assertNotNull(role);
        assertNotNull(role.getId());
        assertEquals(testAccountId, role.getAccountId());
        assertEquals(TEST_CLIENT_ID, role.getClientId());
        assertEquals("admin", role.getRole());
        assertEquals(testOrgId, role.getOrgId());
        assertNotNull(role.getCreatedAt());
    }

    @Test
    public void testAddRoleThrowsConflictWhenRoleExists() {
        // Add role first time
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "editor");

        // Try to add same role again - should throw ConflictException
        assertThrows(ConflictException.class, () -> {
            nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "editor");
        });
    }

    @Test
    public void testAddMultipleRolesToSameAccount() {
        NonMultitenancyAccountRole role1 = nonMultitenancyAccountRoleService.addRole(
            testOrgId, testAccountId, TEST_CLIENT_ID, "admin");
        NonMultitenancyAccountRole role2 = nonMultitenancyAccountRoleService.addRole(
            testOrgId, testAccountId, TEST_CLIENT_ID, "editor");
        NonMultitenancyAccountRole role3 = nonMultitenancyAccountRoleService.addRole(
            testOrgId, testAccountId, TEST_CLIENT_ID, "viewer");

        assertNotNull(role1);
        assertNotNull(role2);
        assertNotNull(role3);
        assertNotEquals(role1.getId(), role2.getId());
        assertNotEquals(role2.getId(), role3.getId());

        // Verify all roles have correct orgId
        assertEquals(testOrgId, role1.getOrgId());
        assertEquals(testOrgId, role2.getOrgId());
        assertEquals(testOrgId, role3.getOrgId());
    }

    @Test
    public void testFindRolesByAccountIdAndClientIdAndOrgId() {
        // Add roles with specific orgId
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "admin");
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "editor");

        // Add role with same orgId but different role
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "viewer");

        // Find roles for testOrgId - should get all three
        Set<String> roles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
            testAccountId, TEST_CLIENT_ID, testOrgId);

        assertEquals(3, roles.size());
        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("editor"));
        assertTrue(roles.contains("viewer"));
    }

    @Test
    public void testFindRolesByAccountIdAndClientIdAndOrgIdWithNoRoles() {
        Set<String> roles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
            testAccountId, TEST_CLIENT_ID, testOrgId);

        assertTrue(roles.isEmpty());
    }

    @Test
    public void testFindRolesByAccountIdAndClientIdAndOrgIdIsolation() {
        // Add roles for different clients with same org
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "admin");
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID_2, "user");

        Set<String> rolesClient1 = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
            testAccountId, TEST_CLIENT_ID, testOrgId);
        Set<String> rolesClient2 = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
            testAccountId, TEST_CLIENT_ID_2, testOrgId);

        assertEquals(1, rolesClient1.size());
        assertTrue(rolesClient1.contains("admin"));

        assertEquals(1, rolesClient2.size());
        assertTrue(rolesClient2.contains("user"));
    }

    @Test
    public void testHasAnyRoleForClientWhenRolesExist() {
        // Add a role
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "admin");

        // Should return true
        assertTrue(nonMultitenancyAccountRoleService.hasAnyRoleForClient(
            testAccountId, TEST_CLIENT_ID, testOrgId));
    }

    @Test
    public void testHasAnyRoleForClientWhenNoRoles() {
        // Should return false when no roles exist
        assertFalse(nonMultitenancyAccountRoleService.hasAnyRoleForClient(
            testAccountId, TEST_CLIENT_ID, testOrgId));
    }

    @Test
    public void testHasAnyRoleForClientWithDifferentAccount() throws Exception {
        // Create another account
        transactionHelper.beginTransaction();
        String uniqueEmail2 = "otheraccount_" + System.nanoTime() + "@example.com";
        Account otherAccount = accountService.createAccount(
            uniqueEmail2,
            "Other Test User",
            "otheraccount_" + System.nanoTime(),
            "TestPassword123",
            AccountService.NATIVE,
            "Other Test Org");
        String otherAccountId = otherAccount.getId();
        transactionHelper.commitTransaction();

        // Add role for first account
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "admin");

        // Check with different account - should return false
        assertFalse(nonMultitenancyAccountRoleService.hasAnyRoleForClient(
            otherAccountId, TEST_CLIENT_ID, testOrgId));
    }

    @Test
    public void testSeedDefaultRoles() {
        // Create default roles (as would be returned by ClientAllowedRoleService.findDefaultRolesByClientId)
        ClientAllowedRole role1 = new ClientAllowedRole();
        role1.setClientId(TEST_CLIENT_ID);
        role1.setRole("viewer");
        role1.setIsDefault(true);

        ClientAllowedRole role2 = new ClientAllowedRole();
        role2.setClientId(TEST_CLIENT_ID);
        role2.setRole("admin");
        role2.setIsDefault(true);

        // Only pass the default roles (as the caller would do)
        List<ClientAllowedRole> defaultRoles = List.of(role1, role2);

        // Seed roles
        nonMultitenancyAccountRoleService.seedDefaultRoles(
            testAccountId, TEST_CLIENT_ID, testOrgId, defaultRoles);

        // Verify default roles were seeded
        Set<String> roles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
            testAccountId, TEST_CLIENT_ID, testOrgId);

        assertEquals(2, roles.size());
        assertTrue(roles.contains("viewer"));
        assertTrue(roles.contains("admin"));
    }

    @Test
    public void testSeedDefaultRolesSkipsExistingRoles() {
        // First, manually add a role that will also be in the default list
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "viewer");

        // Verify it exists
        Set<String> rolesBefore = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
            testAccountId, TEST_CLIENT_ID, testOrgId);
        assertEquals(1, rolesBefore.size());
        assertTrue(rolesBefore.contains("viewer"));

        // Create default roles including the existing one
        ClientAllowedRole role1 = new ClientAllowedRole();
        role1.setClientId(TEST_CLIENT_ID);
        role1.setRole("viewer");
        role1.setIsDefault(true);

        ClientAllowedRole role2 = new ClientAllowedRole();
        role2.setClientId(TEST_CLIENT_ID);
        role2.setRole("admin");
        role2.setIsDefault(true);

        List<ClientAllowedRole> defaultRoles = List.of(role1, role2);

        // Seed roles - should not duplicate viewer
        nonMultitenancyAccountRoleService.seedDefaultRoles(
            testAccountId, TEST_CLIENT_ID, testOrgId, defaultRoles);

        // Verify no duplicates and admin was added
        List<NonMultitenancyAccountRole> allRoles = em.createQuery(
            "SELECT ar FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.orgId = :orgId",
            NonMultitenancyAccountRole.class)
            .setParameter("accountId", testAccountId)
            .setParameter("clientId", TEST_CLIENT_ID)
            .setParameter("orgId", testOrgId)
            .getResultList();

        assertEquals(2, allRoles.size());

        Set<String> roleNames = Set.of(allRoles.get(0).getRole(), allRoles.get(1).getRole());
        assertTrue(roleNames.contains("viewer"));
        assertTrue(roleNames.contains("admin"));
    }

    @Test
    public void testSeedDefaultRolesWithEmptyList() {
        // Should not throw exception with empty list
        assertDoesNotThrow(() -> {
            nonMultitenancyAccountRoleService.seedDefaultRoles(
                testAccountId, TEST_CLIENT_ID, testOrgId, List.of());
        });

        // Verify no roles were created
        assertFalse(nonMultitenancyAccountRoleService.hasAnyRoleForClient(
            testAccountId, TEST_CLIENT_ID, testOrgId));
    }

    @Test
    public void testSeedDefaultRolesSkipsExistingWithSameRole() {
        // Add role in testOrgId
        nonMultitenancyAccountRoleService.addRole(testOrgId, testAccountId, TEST_CLIENT_ID, "viewer");

        // Verify role exists
        assertTrue(nonMultitenancyAccountRoleService.hasAnyRoleForClient(
            testAccountId, TEST_CLIENT_ID, testOrgId));

        // Try to seed the same role again
        ClientAllowedRole role = new ClientAllowedRole();
        role.setClientId(TEST_CLIENT_ID);
        role.setRole("viewer");
        role.setIsDefault(true);

        // Should not create duplicate
        nonMultitenancyAccountRoleService.seedDefaultRoles(
            testAccountId, TEST_CLIENT_ID, testOrgId, List.of(role));

        // Verify still only one role
        List<NonMultitenancyAccountRole> allRoles = em.createQuery(
            "SELECT ar FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.orgId = :orgId",
            NonMultitenancyAccountRole.class)
            .setParameter("accountId", testAccountId)
            .setParameter("clientId", TEST_CLIENT_ID)
            .setParameter("orgId", testOrgId)
            .getResultList();

        assertEquals(1, allRoles.size());
        assertEquals("viewer", allRoles.get(0).getRole());
    }

    @Test
    public void testAddRoleWithAbstrauthClientAndAdminRole() throws Exception {
        // Create a second account (so we're not in bootstrap mode)
        transactionHelper.beginTransaction();
        String uniqueEmail2 = "adminsecond_" + System.nanoTime() + "@example.com";
        Account account2 = accountService.createAccount(
            uniqueEmail2,
            "Admin Second Account",
            "adminsecond_" + System.nanoTime(),
            "TestPassword123",
            AccountService.NATIVE,
            "Test Org");
        transactionHelper.commitTransaction();

        // First account (testAccountId) is now account #2 in the system
        // Account2 was created to ensure we're not in bootstrap mode (accountCount > 1)
        assertNotNull(account2);
        assertNotNull(account2.getId());

        // Adding admin role via nonMultitenancyAccountRoleService should still work
        // because checkNonAdminCannotAddAdminRole uses securityIdentity which is anonymous in unit tests

        NonMultitenancyAccountRole role = nonMultitenancyAccountRoleService.addRole(
            testOrgId, testAccountId, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);

        assertNotNull(role);
        assertEquals(Roles._ADMIN_PLAIN, role.getRole());
        assertEquals(Roles.CLIENT_ID, role.getClientId());
    }
}
