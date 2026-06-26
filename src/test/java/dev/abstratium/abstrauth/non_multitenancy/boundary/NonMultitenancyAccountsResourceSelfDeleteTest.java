package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tests for self-service account deletion via DELETE /api/accounts/me.
 */
@QuarkusTest
public class NonMultitenancyAccountsResourceSelfDeleteTest {

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

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    public void resetDatabaseBeforeTest() throws Exception {
        transactionHelper.beginTransaction();
        dbResetHelper.resetDatabase();
        transactionHelper.commitTransaction();
    }

    private String generateUserToken(String accountId, String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("user@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("email", "user@example.com")
            .claim("name", "Regular User")
            .claim("orgId", orgId)
            .sign();
    }

    @Test
    public void testSelfDeleteOwnAccount() throws Exception {
        transactionHelper.beginTransaction();
        // Seed an admin first so the account under test is not the only admin
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        String email = "selfdelete_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Self Delete", "selfdelete" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, defaultOrgId))
            .when()
            .delete("/api/accounts/me")
            .then()
            .statusCode(204);

        Account deletedAccount = em.find(Account.class, accountId);
        assertNull(deletedAccount, "Account should be deleted");

        transactionHelper.beginTransaction();
        var roles = accountRoleService.findRolesByAccountId(accountId);
        assertTrue(roles.isEmpty(), "Account roles should be deleted");
        transactionHelper.commitTransaction();
    }

    @Test
    public void testSelfDeleteRemovesSingleMemberOrganisation() throws Exception {
        transactionHelper.beginTransaction();
        // Seed an admin in the default org so the account under test is not the only admin
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        // Create a new organisation with only one member
        Organisation singleMemberOrg = organisationService.createOrganisation("Single Member Org");
        String singleMemberOrgId = singleMemberOrg.getId();

        String email = "singleowner_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Single Owner", "singleowner" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, singleMemberOrgId);
        String accountId = account.getId();
        organisationService.addOwner(singleMemberOrgId, accountId);
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, singleMemberOrgId))
            .when()
            .delete("/api/accounts/me")
            .then()
            .statusCode(204);

        Account deletedAccount = em.find(Account.class, accountId);
        assertNull(deletedAccount, "Account should be deleted");

        Organisation deletedOrg = em.find(Organisation.class, singleMemberOrgId);
        assertNull(deletedOrg, "Single-member organisation should be deleted");

        Long auditDeleteRows = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM T_organisations_AUD WHERE id = :id AND REVTYPE = 2")
                .setParameter("id", singleMemberOrgId)
                .getSingleResult();
        assertTrue(auditDeleteRows > 0, "Organisation deletion should be recorded in audit table");
    }

    @Test
    public void testSelfDeleteSoleOwnerOfMultiMemberOrgReturns400() throws Exception {
        transactionHelper.beginTransaction();
        // Seed an admin in the default org so the account under test is not the only admin
        String adminEmail = "admin_" + System.currentTimeMillis() + "@example.com";
        accountService.createAccountForOrg(adminEmail, "Admin", "admin" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        // Create a new organisation with two members where account1 is the sole owner
        Organisation multiMemberOrg = organisationService.createOrganisation("Multi Member Org");
        String multiMemberOrgId = multiMemberOrg.getId();

        String email1 = "member1_" + System.currentTimeMillis() + "@example.com";
        Account account1 = accountService.createAccountForOrg(email1, "Member 1", "member1" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, multiMemberOrgId);
        String account1Id = account1.getId();
        organisationService.addOwner(multiMemberOrgId, account1Id);

        String email2 = "member2_" + System.currentTimeMillis() + "@example.com";
        Account account2 = accountService.createAccountForOrg(email2, "Member 2", "member2" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, multiMemberOrgId);
        String account2Id = account2.getId();
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(account1Id, multiMemberOrgId))
            .when()
            .delete("/api/accounts/me")
            .then()
            .statusCode(400)
            .body("error", containsString("sole owner"));

        Account stillExistingAccount = em.find(Account.class, account1Id);
        assertNotNull(stillExistingAccount, "Account should not be deleted when it is the sole owner of a multi-member organisation");

        Organisation keptOrg = em.find(Organisation.class, multiMemberOrgId);
        assertNotNull(keptOrg, "Multi-member organisation should still exist");

        // Ownership should not have changed automatically
        assertTrue(organisationService.isOwner(multiMemberOrgId, account1Id), "Deleting account should still be the owner");
        assertFalse(organisationService.isOwner(multiMemberOrgId, account2Id), "Other member should not be automatically promoted to owner");
    }

    @Test
    public void testSelfDeleteCannotDeleteOnlyAdmin() throws Exception {
        transactionHelper.beginTransaction();
        // Remove any existing admin roles to ensure clean state
        var existingAdmins = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            dev.abstratium.abstrauth.entity.AccountRole.class
        );
        existingAdmins.setParameter("clientId", Roles.CLIENT_ID);
        existingAdmins.setParameter("role", Roles._ADMIN_PLAIN);
        for (var admin : existingAdmins.getResultList()) {
            em.remove(admin);
        }

        String email = "onlyadmin_self_" + System.currentTimeMillis() + "@example.com";
        Account account = accountService.createAccountForOrg(email, "Only Admin", "onlyadmin_self" + System.currentTimeMillis(), "Pass123", AccountService.NATIVE, defaultOrgId);
        String accountId = account.getId();
        // First account created in a fresh database is automatically assigned the admin role
        transactionHelper.commitTransaction();

        given()
            .auth().oauth2(generateUserToken(accountId, defaultOrgId))
            .when()
            .delete("/api/accounts/me")
            .then()
            .statusCode(400)
            .body("error", containsString("Cannot delete the account with the only admin role"));

        Account stillExists = em.find(Account.class, accountId);
        assertNotNull(stillExists, "Account should not be deleted when it is the only admin");
    }

    @Test
    public void testSelfDeleteWithoutTokenReturns401() {
        given()
            .when()
            .delete("/api/accounts/me")
            .then()
            .statusCode(401);
    }
}
