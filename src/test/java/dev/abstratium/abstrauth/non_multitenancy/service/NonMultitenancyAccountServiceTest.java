package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccount;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Tests for NonMultitenancyAccountService
 */
@QuarkusTest
public class NonMultitenancyAccountServiceTest {

    @Inject
    NonMultitenancyAccountService nonMultitenancyAccountService;

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @BeforeEach
    public void setup() throws Exception {
        transactionHelper.beginTransaction();

        // Reset tenant context to the default org before querying OAuth clients
        dbResetHelper.resetDatabase();

        // Clean up all existing admin roles for abstratium-abstrauth to ensure test isolation
        em.createQuery("DELETE FROM NonMultitenancyAccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role")
            .setParameter("clientId", Roles.CLIENT_ID)
            .setParameter("role", Roles._ADMIN_PLAIN)
            .executeUpdate();

        // Ensure test client exists
        ensureClientExists("client-test");

        transactionHelper.commitTransaction();
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
            em.persist(client);

            dev.abstratium.abstrauth.entity.ClientSecret secret = new dev.abstratium.abstrauth.entity.ClientSecret();
            secret.setClientId(clientId);
            secret.setSecretHash("$2a$10$dummyhash");
            secret.setDescription("Test secret");
            secret.setActive(true);
            em.persist(secret);
        }
    }

    @Test
    public void testDeleteAccountWithCascade() throws Exception {
        transactionHelper.beginTransaction();

        // Create a dummy admin first (so subsequent accounts don't get auto-assigned admin role)
        String dummyEmail = "dummyadmin_" + System.currentTimeMillis() + "@example.com";
        String dummyUsername = "dummyadmin_" + System.currentTimeMillis();
        accountService.createAccount(dummyEmail, "Dummy Admin", dummyUsername, "Password123", AccountService.NATIVE, "Test Org");

        // Create the account to be deleted (this one won't get admin role since dummy already has it)
        String email = "deletecascade_" + System.currentTimeMillis() + "@example.com";
        String username = "deletecascade_" + System.currentTimeMillis();
        Account account = accountService.createAccount(email, "Delete Cascade Test", username, "Password123", AccountService.NATIVE, "Test Org");
        String accountId = account.getId();

        // Add a role for client-test
        accountRoleService.addRole(accountId, "client-test", "user");

        transactionHelper.commitTransaction();

        // Verify account exists using non-multitenancy service
        Optional<NonMultitenancyAccount> found = nonMultitenancyAccountService.findById(accountId);
        assertTrue(found.isPresent(), "Account should exist before deletion");

        // Delete using non-multitenancy service (runs in its own transaction)
        boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade(accountId);
        assertTrue(deleted, "Delete should return true");

        // Verify it's deleted using regular service
        Optional<Account> notFound = accountService.findById(accountId);
        assertFalse(notFound.isPresent(), "Account should be deleted");

        // Verify associated organisation accounts are deleted via JPA cascade
        transactionHelper.beginTransaction();
        long orgAccountCount = em.createQuery(
                "SELECT COUNT(oa) FROM NonMultitenancyOrganisationAccount oa WHERE oa.id.accountId = :accountId", Long.class)
                .setParameter("accountId", accountId)
                .getSingleResult();
        transactionHelper.commitTransaction();
        assertEquals(0, orgAccountCount, "Organisation accounts should be deleted via JPA cascade");
    }

    @Test
    @Transactional
    public void testDeleteAccountWithCascadeReturnsFalseWhenNotFound() {
        // Try to delete a non-existent account
        boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade("non-existent-account-id");
        assertFalse(deleted, "Delete should return false when account not found");
    }

    @Test
    public void testCannotDeleteAccountWithOnlyAdminRole() throws Exception {
        transactionHelper.beginTransaction();

        // Create first admin account (will automatically get admin role if first account in test)
        String email1 = "admin1_nm_" + System.currentTimeMillis() + "@example.com";
        String username1 = "admin1_nm_" + System.currentTimeMillis();
        Account admin1 = accountService.createAccount(email1, "Admin 1 NM", username1, "Password123", AccountService.NATIVE, "Test Org");

        // Ensure first account has admin role
        var adminRoleQuery = em.createQuery(
            "SELECT ar FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role",
            dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccountRole.class
        );
        adminRoleQuery.setParameter("accountId", admin1.getId());
        adminRoleQuery.setParameter("clientId", Roles.CLIENT_ID);
        adminRoleQuery.setParameter("role", Roles._ADMIN_PLAIN);
        if (adminRoleQuery.getResultList().isEmpty()) {
            accountRoleService.addRole(admin1.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        }

        // Create second account (non-admin for abstrauth)
        String email2 = "admin2_nm_" + System.currentTimeMillis() + "@example.com";
        String username2 = "admin2_nm_" + System.currentTimeMillis();
        Account admin2 = accountService.createAccount(email2, "Admin 2 NM", username2, "Password123", AccountService.NATIVE, "Test Org");

        // Remove any abstrauth admin role from second account to ensure it's not an admin
        var adminRoleQuery2 = em.createQuery(
            "SELECT ar FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role",
            dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccountRole.class
        );
        adminRoleQuery2.setParameter("accountId", admin2.getId());
        adminRoleQuery2.setParameter("clientId", Roles.CLIENT_ID);
        adminRoleQuery2.setParameter("role", Roles._ADMIN_PLAIN);
        var adminRoles2 = adminRoleQuery2.getResultList();
        for (var role : adminRoles2) {
            em.remove(role);
        }

        transactionHelper.commitTransaction();

        // Now admin1 should be the ONLY admin for abstratium-abstrauth
        // Try to delete admin1 - should fail because it has the only admin role
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            nonMultitenancyAccountService.deleteAccountWithCascade(admin1.getId());
        });

        assertTrue(exception.getMessage().contains("Cannot delete the account with the only admin role"));
        assertTrue(exception.getMessage().contains(Roles.CLIENT_ID));
    }

    @Test
    public void testCanDeleteAccountWhenMultipleAdminsExist() throws Exception {
        // Create two accounts with admin role for abstratium-abstrauth
        // admin1 is first after reset, so it already has admin automatically
        transactionHelper.beginTransaction();
        String email1 = "admin1_nm_" + System.currentTimeMillis() + "@example.com";
        String username1 = "admin1_nm_" + System.currentTimeMillis();
        Account admin1 = accountService.createAccount(email1, "Admin 1 NM", username1, "Password123", AccountService.NATIVE, "Test Org");

        String email2 = "admin2_nm_" + System.currentTimeMillis() + "@example.com";
        String username2 = "admin2_nm_" + System.currentTimeMillis();
        Account admin2 = accountService.createAccount(email2, "Admin 2 NM", username2, "Password123", AccountService.NATIVE, "Test Org");
        accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        transactionHelper.commitTransaction();

        // Should be able to delete one admin account since there are two
        assertDoesNotThrow(() -> {
            boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade(admin1.getId());
            assertTrue(deleted, "Delete should return true");
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
        transactionHelper.beginTransaction();

        // Create an admin account first to ensure there's at least one admin
        // First account after reset already has admin automatically
        String email1 = "admin_seed_" + System.currentTimeMillis() + "@example.com";
        String username1 = "admin_seed_" + System.currentTimeMillis();
        accountService.createAccount(email1, "Admin Seed", username1, "Password123", AccountService.NATIVE, "Test Org");

        // Create a second account (non-admin for abstrauth)
        String email2 = "nonadmin_nm_" + System.currentTimeMillis() + "@example.com";
        String username2 = "nonadmin_nm_" + System.currentTimeMillis();
        Account account = accountService.createAccount(email2, "Non Admin NM", username2, "Password123", AccountService.NATIVE, "Test Org");

        // Remove any abstrauth admin role from second account to ensure it's not an admin
        var adminRoleQuery = em.createQuery(
            "SELECT ar FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role",
            dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccountRole.class
        );
        adminRoleQuery.setParameter("accountId", account.getId());
        adminRoleQuery.setParameter("clientId", Roles.CLIENT_ID);
        adminRoleQuery.setParameter("role", Roles._ADMIN_PLAIN);
        var adminRoles = adminRoleQuery.getResultList();
        for (var role : adminRoles) {
            em.remove(role);
        }

        transactionHelper.commitTransaction();

        // Should be able to delete this account (it's not the only admin)
        assertDoesNotThrow(() -> {
            boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade(account.getId());
            assertTrue(deleted, "Delete should return true");
        });

        // Verify the account was deleted
        Optional<Account> deleted = accountService.findById(account.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testCanDeleteAccountWithAdminRoleForOtherClients() throws Exception {
        // Create a dummy admin first (so the test account doesn't get auto-assigned abstrauth admin role)
        transactionHelper.beginTransaction();
        String dummyEmail = "dummyadmin2_" + System.currentTimeMillis() + "@example.com";
        String dummyUsername = "dummyadmin2_" + System.currentTimeMillis();
        Account dummyAccount = accountService.createAccount(dummyEmail, "Dummy Admin 2", dummyUsername, "Password123", AccountService.NATIVE, "Test Org");
        // Note: createAccount() auto-assigns admin role to first account

        // Create the test account with admin role for a different client (not abstratium-abstrauth)
        String email = "otheradmin_nm_" + System.currentTimeMillis() + "@example.com";
        String username = "otheradmin_nm_" + System.currentTimeMillis();
        Account account = accountService.createAccount(email, "Other Admin NM", username, "Password123", AccountService.NATIVE, "Test Org");
        accountRoleService.addRole(account.getId(), "client-test", "admin");
        transactionHelper.commitTransaction();

        // Should be able to delete this account (admin role is for different client)
        assertDoesNotThrow(() -> {
            boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade(account.getId());
            assertTrue(deleted, "Delete should return true");
        });

        // Verify the account was deleted
        Optional<Account> deleted = accountService.findById(account.getId());
        assertFalse(deleted.isPresent());
    }
}
