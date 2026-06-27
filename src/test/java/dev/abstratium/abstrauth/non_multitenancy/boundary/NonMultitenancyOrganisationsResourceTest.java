package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOrganisationService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tests for NonMultitenancyOrganisationsResource.
 *
 * These tests verify the cross-tenant organisation creation endpoint that uses
 * non-multitenancy services to assign initial roles before the orgId is known to Hibernate.
 */
@QuarkusTest
public class NonMultitenancyOrganisationsResourceTest {

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    NonMultitenancyOrganisationService nonMultitenancyOrganisationService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String adminToken(String accountId, String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
                .subject(accountId)
                .upn("admin@example.com")
                .groups(Set.of(Roles.USER, Roles.ADMIN))
                .claim("orgId", orgId)
                .sign();
    }

    private String userToken(String accountId, String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
                .subject(accountId)
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", orgId)
                .sign();
    }

    private Account createAccount(String suffix) throws Exception {
        transactionHelper.beginTransaction();
        Account account = accountService.createAccount(
                "nmorgtest_" + suffix + "@example.com",
                "NMOrgTest " + suffix,
                "nmorgtest_" + suffix,
                "Pass123!",
                AccountService.NATIVE,
                "Test Org " + suffix);
        transactionHelper.commitTransaction();
        return account;
    }

    private String accountOrgId(Account account) {
        return organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();
    }

    private Organisation createAdditionalOrganisationForAccount(String accountId, String name) throws Exception {
        transactionHelper.beginTransaction();
        Organisation org = organisationService.createOrganisation(name);
        organisationService.addOwner(org.getId(), accountId);
        organisationService.addMember(org.getId(), accountId);
        transactionHelper.commitTransaction();
        return org;
    }

    private Account createAccountForOrg(String orgId) throws Exception {
        transactionHelper.beginTransaction();
        Account account = accountService.createAccountForOrg(
                "nmorgtest_" + System.currentTimeMillis() + "@example.com",
                "NMOrgTest Member",
                "nmorgtest_" + System.currentTimeMillis(),
                "Pass123!",
                AccountService.NATIVE,
                orgId);
        transactionHelper.commitTransaction();
        return account;
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/organisations (cross-tenant organisation creation)
    // ─────────────────────────────────────────────────────────

    @Test
    public void testCreateOrganisation_success() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_create");
        String token = userToken(account.getId(), accountOrgId(account));

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"New Org " + System.currentTimeMillis() + "\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("name", notNullValue());
    }

    @Test
    public void testCreateOrganisation_assignsManagementRoles() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_roles");
        String token = userToken(account.getId(), accountOrgId(account));

        // Create organisation
        String orgId = given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Role Test Org " + System.currentTimeMillis() + "\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Verify management roles were assigned using non-multitenancy service
        var roles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
                account.getId(), Roles.CLIENT_ID, orgId);

        assertTrue(roles.contains(Roles._USER_PLAIN),
                "Account should have user role in new org");
        assertTrue(roles.contains(Roles._MANAGE_ACCOUNTS_PLAIN),
                "Account should have manage-accounts role in new org");
        assertTrue(roles.contains(Roles._MANAGE_CLIENTS_PLAIN),
                "Account should have manage-clients role in new org");
    }

    @Test
    public void testCreateOrganisation_blankName_returns400() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_blankname");
        String token = userToken(account.getId(), accountOrgId(account));

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(400);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/organisations/{orgId}
    // ─────────────────────────────────────────────────────────

    @Test
    public void testDeleteOrganisation_success() throws Exception {
        Account admin = createAccount(System.currentTimeMillis() + "_deladmin");
        String firstOrgId = accountOrgId(admin);
        Organisation secondOrg = createAdditionalOrganisationForAccount(admin.getId(),
                "Second Org " + System.currentTimeMillis());
        String orgId = secondOrg.getId();
        String token = adminToken(admin.getId(), orgId);

        // Seed a role for the admin in this org so the cascade delete has something to remove
        transactionHelper.beginTransaction();
        nonMultitenancyAccountRoleService.addRole(orgId, admin.getId(), Roles.CLIENT_ID, Roles._USER_PLAIN);
        transactionHelper.commitTransaction();

        // Create a client owned by this org
        transactionHelper.beginTransaction();
        String clientId = "del-test-client-" + System.currentTimeMillis();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', :redirectUris, :allowedScopes, true, false, :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("clientId", clientId)
            .setParameter("name", "Delete Test Client")
            .setParameter("redirectUris", "[\"http://localhost/cb\"]")
            .setParameter("allowedScopes", "[\"openid\"]")
            .setParameter("orgId", orgId)
            .executeUpdate();
        transactionHelper.commitTransaction();

        // Verify account roles and client exist before deletion
        transactionHelper.beginTransaction();
        long rolesBefore = em.createQuery(
            "SELECT COUNT(ar) FROM NonMultitenancyAccountRole ar WHERE ar.orgId = :orgId", Long.class)
            .setParameter("orgId", orgId).getSingleResult();
        assertTrue(rolesBefore > 0, "Account roles should exist before deletion");

        long clientsBefore = em.createQuery(
            "SELECT COUNT(c) FROM NonMultitenancyOAuthClient c WHERE c.orgId = :orgId", Long.class)
            .setParameter("orgId", orgId).getSingleResult();
        assertEquals(1, clientsBefore, "Client should exist before deletion");

        long orgAccountsBefore = em.createQuery(
            "SELECT COUNT(oa) FROM NonMultitenancyOrganisationAccount oa WHERE oa.id.orgId = :orgId", Long.class)
            .setParameter("orgId", orgId).getSingleResult();
        assertTrue(orgAccountsBefore > 0, "Organisation accounts should exist before deletion");
        transactionHelper.commitTransaction();

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + orgId)
                .then()
                .statusCode(204);

        assertTrue(nonMultitenancyOrganisationService.findById(orgId).isEmpty(),
                "Organisation should be deleted");

        // Verify all children are gone
        transactionHelper.beginTransaction();
        long rolesAfter = em.createQuery(
            "SELECT COUNT(ar) FROM NonMultitenancyAccountRole ar WHERE ar.orgId = :orgId", Long.class)
            .setParameter("orgId", orgId).getSingleResult();
        assertEquals(0, rolesAfter, "Account roles should be cascade-deleted");

        long clientsAfter = em.createQuery(
            "SELECT COUNT(c) FROM NonMultitenancyOAuthClient c WHERE c.orgId = :orgId", Long.class)
            .setParameter("orgId", orgId).getSingleResult();
        assertEquals(0, clientsAfter, "Clients should be cascade-deleted");

        long orgAccountsAfter = em.createQuery(
            "SELECT COUNT(oa) FROM NonMultitenancyOrganisationAccount oa WHERE oa.id.orgId = :orgId", Long.class)
            .setParameter("orgId", orgId).getSingleResult();
        assertEquals(0, orgAccountsAfter, "Organisation accounts should be cascade-deleted");
        transactionHelper.commitTransaction();

        // Account itself must remain because the DB cascade on account_id was removed
        assertTrue(accountService.findById(admin.getId()).isPresent(),
                "Account should not be deleted when organisation is deleted");

        // The first organisation must remain since only the second was deleted
        assertTrue(nonMultitenancyOrganisationService.findById(firstOrgId).isPresent(),
                "The caller's other organisation should not be affected");
    }

    @Test
    public void testDeleteOrganisation_nonAdmin_returns403() throws Exception {
        Account user = createAccount(System.currentTimeMillis() + "_delnoadmin");
        String orgId = accountOrgId(user);
        String token = userToken(user.getId(), orgId);

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + orgId)
                .then()
                .statusCode(403);

        assertTrue(nonMultitenancyOrganisationService.findById(orgId).isPresent(),
                "Organisation should not be deleted");
    }

    @Test
    public void testDeleteOrganisation_notFound_returns404() throws Exception {
        Account admin = createAccount(System.currentTimeMillis() + "_del404");
        String token = adminToken(admin.getId(), accountOrgId(admin));

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/00000000-0000-0000-0000-000000009999")
                .then()
                .statusCode(404)
                .body("error", containsString("Organisation not found"));
    }

    @Test
    public void testDeleteOrganisation_unauthenticated_returns401() {
        given()
                .when()
                .delete("/api/organisations/00000000-0000-0000-0000-000000009999")
                .then()
                .statusCode(401);
    }

    @Test
    public void testDeleteOrganisation_defaultOrg_returns400() throws Exception {
        Account account = createAccountForOrg(defaultOrgId);
        String token = adminToken(account.getId(), defaultOrgId);

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + defaultOrgId)
                .then()
                .statusCode(400)
                .body("error", containsString("default organisation"));

        assertTrue(nonMultitenancyOrganisationService.findById(defaultOrgId).isPresent(),
                "Default organisation should not be deleted");
    }

    @Test
    public void testDeleteOrganisation_lastOrg_returns400() throws Exception {
        transactionHelper.beginTransaction();
        Organisation soleOrg = organisationService.createOrganisation("Sole Org " + System.currentTimeMillis());
        String orgId = soleOrg.getId();
        transactionHelper.commitTransaction();

        Account account = createAccountForOrg(orgId);
        String token = adminToken(account.getId(), orgId);

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + orgId)
                .then()
                .statusCode(400)
                .body("error", containsString("last organisation"));

        assertTrue(nonMultitenancyOrganisationService.findById(orgId).isPresent(),
                "Last organisation should not be deleted");
    }

    @Test
    public void testDeleteOrganisation_otherMembers_returns400() throws Exception {
        Account admin = createAccount(System.currentTimeMillis() + "_othermembers");
        Organisation targetOrg = createAdditionalOrganisationForAccount(admin.getId(),
                "Target Org " + System.currentTimeMillis());
        String targetOrgId = targetOrg.getId();

        // Add another member to the target organisation
        createAccountForOrg(targetOrgId);

        String token = adminToken(admin.getId(), targetOrgId);

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + targetOrgId)
                .then()
                .statusCode(400)
                .body("error", containsString("other members"));

        assertTrue(nonMultitenancyOrganisationService.findById(targetOrgId).isPresent(),
                "Organisation with other members should not be deleted");
    }

    @Test
    public void testCreateOrganisation_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Org\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(401);
    }
}
