package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tests for NonMultitenancyAccountsResource
 * Tests cross-tenant (cross-organisation) account deletion operations
 */
@QuarkusTest
public class NonMultitenancyAccountsResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    OrganisationService organisationService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    // ─────────────────────────────────────────────────────────
    // DELETE /api/accounts/{accountId}
    // ─────────────────────────────────────────────────────────

    @Test
    public void testCannotDeleteAccountWithOnlyAdminRole() throws Exception {
        // First, remove all existing admin roles for abstratium-abstrauth to ensure clean state
        String testOrgId = defaultOrgId;
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
        if (!accountRoleService.findRolesByAccountIdAndClientId(adminAccountId, Roles.CLIENT_ID).contains(Roles._ADMIN_PLAIN)) {
            accountRoleService.addRole(adminAccountId, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        }

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
        String testOrgId = defaultOrgId;
        transactionHelper.beginTransaction();
        String email1 = "admin1_delacct_" + System.currentTimeMillis() + "@example.com";
        Account admin1 = accountService.createAccountForOrg(email1, "Admin 1", "admin1_delacct_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, testOrgId);
        String admin1Id = admin1.getId();
        if (!accountRoleService.findRolesByAccountIdAndClientId(admin1Id, Roles.CLIENT_ID).contains(Roles._ADMIN_PLAIN)) {
            accountRoleService.addRole(admin1Id, Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        }

        String email2 = "admin2_delacct_" + System.currentTimeMillis() + "@example.com";
        Account admin2 = accountService.createAccountForOrg(email2, "Admin 2", "admin2_delacct_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, testOrgId);
        if (!accountRoleService.findRolesByAccountIdAndClientId(admin2.getId(), Roles.CLIENT_ID).contains(Roles._ADMIN_PLAIN)) {
            accountRoleService.addRole(admin2.getId(), Roles.CLIENT_ID, Roles._ADMIN_PLAIN);
        }

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
    public void testDeleteAccountCascadesAllChildRecords() throws Exception {
        // Create a manager account in default org
        transactionHelper.beginTransaction();
        Account manager = new Account();
        manager.setEmail("manager-cascade@example.com");
        manager.setName("Manager Cascade");
        manager.setAuthProvider(AccountService.NATIVE);
        manager.setEmailVerified(true);
        em.persist(manager);
        em.flush();
        // Add manager to default org
        organisationService.addMember(this.defaultOrgId, manager.getId());

        // Create an account with all types of child records in default org
        Account account = accountService.createAccountForOrg(
            "cascade-test@example.com",
            "Cascade Test",
            "cascadeuser",
            "password123",
            AccountService.NATIVE,
            this.defaultOrgId);
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

        var orgAccountQuery = em.createQuery(
                "SELECT oa FROM NonMultitenancyOrganisationAccount oa WHERE oa.id.accountId = :accountId",
                dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOrganisationAccount.class);
        orgAccountQuery.setParameter("accountId", accountId);
        assertEquals(1, orgAccountQuery.getResultList().size(), "Account should be a member of the default org");
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

        var orgAccountQueryAfter = em.createQuery(
                "SELECT oa FROM NonMultitenancyOrganisationAccount oa WHERE oa.id.accountId = :accountId",
                dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOrganisationAccount.class);
        orgAccountQueryAfter.setParameter("accountId", accountId);
        assertTrue(orgAccountQueryAfter.getResultList().isEmpty(), "Organisation accounts should be deleted via JPA cascade");
        transactionHelper.commitTransaction();
    }

    @Test
    public void testDeleteAccountSoleOwnerOfMultiMemberOrgReturns400() throws Exception {
        transactionHelper.beginTransaction();

        Organisation org = organisationService.createOrganisation("Sole Owner Org " + System.currentTimeMillis());
        String orgId = org.getId();

        Account owner = accountService.createAccountForOrg(
                "owner_block_" + System.currentTimeMillis() + "@example.com",
                "Owner To Delete",
                "owner_block_" + System.currentTimeMillis(),
                "Pass123",
                AccountService.NATIVE,
                orgId);
        String ownerId = owner.getId();
        organisationService.addOwner(orgId, ownerId);

        Account member = accountService.createAccountForOrg(
                "member_block_" + System.currentTimeMillis() + "@example.com",
                "Member",
                "member_block_" + System.currentTimeMillis(),
                "Pass123",
                AccountService.NATIVE,
                orgId);
        String memberId = member.getId();

        Account manager = accountService.createAccountForOrg(
                "manager_block_" + System.currentTimeMillis() + "@example.com",
                "Manager",
                "manager_block_" + System.currentTimeMillis(),
                "Pass123",
                AccountService.NATIVE,
                orgId);
        String managerId = manager.getId();
        accountRoleService.addRole(managerId, Roles.CLIENT_ID, Roles._MANAGE_ACCOUNTS_PLAIN);

        transactionHelper.commitTransaction();

        given()
                .auth().oauth2(generateManageAccountsTokenForOrg(managerId, orgId))
                .when()
                .delete("/api/accounts/" + ownerId)
                .then()
                .statusCode(400)
                .body("error", containsString("sole owner"));

        Account stillExistingAccount = em.find(Account.class, ownerId);
        assertNotNull(stillExistingAccount, "Owner account should not be deleted");

        Organisation keptOrg = em.find(Organisation.class, orgId);
        assertNotNull(keptOrg, "Organisation should still exist");

        assertTrue(organisationService.isOwner(orgId, ownerId), "Owner should remain the owner");
        assertFalse(organisationService.isOwner(orgId, memberId), "Member should not be automatically promoted to owner");
    }

    @Test
    public void testDeleteAccountWithoutTokenReturns401() {
        given()
            .when()
            .delete("/api/accounts/some-id")
            .then()
            .statusCode(401);
    }

    @Test
    public void testDeleteAccountWithoutRoleReturns403() throws Exception {
        // Create a manager account in default org without MANAGE_ACCOUNTS role
        String testOrgId = defaultOrgId;
        transactionHelper.beginTransaction();
        String managerEmail = "manager_norole_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager No Role", "manager_norole_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, testOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(managerId))
            .when()
            .delete("/api/accounts/some-id")
            .then()
            .statusCode(403);
    }

    @Test
    public void testDeleteAccountWithNonExistentIdReturns404() throws Exception {
        // Create a manager account in default org
        transactionHelper.beginTransaction();
        String managerEmail = "manager_404_" + System.currentTimeMillis() + "@example.com";
        Account manager = accountService.createAccountForOrg(managerEmail, "Manager 404", "manager_404_" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String managerId = manager.getId();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateManageAccountsToken(managerId))
            .when()
            .delete("/api/accounts/non-existent-id")
            .then()
            .statusCode(404)
            .body("error", equalTo("Account not found"));
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private String generateManageAccountsToken(String accountId) {
        return generateManageAccountsTokenForOrg(accountId, defaultOrgId);
    }

    private String generateManageAccountsTokenForOrg(String accountId, String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("test@example.com")
            .groups(Set.of("abstratium-abstrauth_user", "abstratium-abstrauth_admin", "abstratium-abstrauth_manage-accounts"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .claim("orgId", orgId)
            .sign();
    }

    private String generateUserToken(String accountId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("test@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", "test@example.com")
            .claim("name", "Test User")
            .claim("orgId", defaultOrgId)
            .sign();
    }
}
