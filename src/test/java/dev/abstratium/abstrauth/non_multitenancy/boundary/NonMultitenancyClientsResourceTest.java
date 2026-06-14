package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancySubscriptionService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Tests for NonMultitenancyClientsResource
 * Tests cross-tenant (cross-organisation) client operations
 */
@QuarkusTest
public class NonMultitenancyClientsResourceTest {

    private static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    NonMultitenancySubscriptionService subscriptionService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    // ─────────────────────────────────────────────────────────
    // GET /api/clients/{clientId}/allowed-roles
    // ─────────────────────────────────────────────────────────

    @Test
    public void testListAllowedRoles_empty() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_roleemp");
        String clientId = "roles-empty-client-" + ts;
        String orgId = getAccountOrgId(account.getId());

        transactionHelper.beginTransaction();
        createTestClient(clientId, orgId);
        transactionHelper.commitTransaction();
        String token = userToken(account.getId(), orgId);

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/clients/" + clientId + "/allowed-roles")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", empty());
    }

    @Test
    public void testListAllowedRoles_withRoles() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_rolewith");
        String clientId = "roles-with-client-" + ts;
        String orgId = getAccountOrgId(account.getId());

        transactionHelper.beginTransaction();
        createTestClient(clientId, orgId);
        insertAllowedRole(clientId, "viewer", false);
        insertAllowedRole(clientId, "editor", true);
        transactionHelper.commitTransaction();

        String token = userToken(account.getId(), orgId);

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/clients/" + clientId + "/allowed-roles")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("role", hasItems("viewer", "editor"))
                .body("isDefault", hasItems(false, true));
    }

    @Test
    public void testListAllowedRoles_unauthenticated_returns401() {
        given()
                .when()
                .get("/api/clients/some-client/allowed-roles")
                .then()
                .statusCode(401);
    }

    @Test
    public void testListAllowedRoles_crossOrg_withSubscription_returnsRoles() throws Exception {
        long ts = System.currentTimeMillis();
        Account ownerAccount = createAccount(ts + "_crossowner");
        String ownerOrgId = getAccountOrgId(ownerAccount.getId());
        String clientId = "cross-org-client-" + ts;

        Account callerAccount = createAccount(ts + "_crosscaller");
        String callerOrgId = getAccountOrgId(callerAccount.getId());

        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrgId);
        // Roles must be available to foreign orgs to be visible to them
        insertAllowedRole(clientId, "cross-viewer", false, true);
        insertAllowedRole(clientId, "cross-editor", true, true);
        subscriptionService.ensureSubscribed(callerOrgId, clientId, true);
        transactionHelper.commitTransaction();

        String token = userToken(callerAccount.getId(), callerOrgId);

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/clients/" + clientId + "/allowed-roles")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("role", hasItems("cross-viewer", "cross-editor"))
                .body("isDefault", hasItems(false, true));
    }

    @Test
    public void testListAllowedRoles_crossOrg_withoutSubscription_returns404() throws Exception {
        long ts = System.currentTimeMillis();
        Account ownerAccount = createAccount(ts + "_nosubowner");
        String ownerOrgId = getAccountOrgId(ownerAccount.getId());
        String clientId = "no-sub-cross-client-" + ts;

        Account callerAccount = createAccount(ts + "_nosubcaller");
        String callerOrgId = getAccountOrgId(callerAccount.getId());

        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrgId);
        insertAllowedRole(clientId, "viewer", false);
        transactionHelper.commitTransaction();

        String token = userToken(callerAccount.getId(), callerOrgId);

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/clients/" + clientId + "/allowed-roles")
                .then()
                .statusCode(404);
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private String userToken(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
                .subject(accountId)
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", orgId)
                .sign();
    }

    private Account createAccount(String suffix) throws Exception {
        transactionHelper.beginTransaction();
        Account account = accountService.createAccount(
                "crt_" + suffix + "@example.com",
                "CRTest " + suffix,
                "crt_" + suffix,
                "Pass123!",
                AccountService.NATIVE,
                "CRT Org " + suffix);
        transactionHelper.commitTransaction();
        return account;
    }

    private String getAccountOrgId(String accountId) throws Exception {
        transactionHelper.beginTransaction();
        java.util.List<Organisation> orgs = organisationService.listOrganisationsForAccount(accountId);
        String orgId = orgs.isEmpty() ? DEFAULT_ORG_ID : orgs.get(0).getId();
        transactionHelper.commitTransaction();
        return orgId;
    }

    private void createTestClient(String clientId, String orgId) {
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', :redirectUris, :allowedScopes, true, true, :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("clientId", clientId)
            .setParameter("name", "Test " + clientId)
            .setParameter("redirectUris", "[\"http://localhost:8080/callback\"]")
            .setParameter("allowedScopes", "[\"openid\"]")
            .setParameter("orgId", orgId)
            .executeUpdate();
    }

    private void insertAllowedRole(String clientId, String role, boolean isDefault) {
        ClientAllowedRole r = new ClientAllowedRole();
        r.setId(new ClientAllowedRole.Id(clientId, role));
        r.setIsDefault(isDefault);
        r.setAvailableToForeignOrgs(false);
        em.persist(r);
    }

    private void insertAllowedRole(String clientId, String role, boolean isDefault, boolean availableToForeignOrgs) {
        ClientAllowedRole r = new ClientAllowedRole();
        r.setId(new ClientAllowedRole.Id(clientId, role));
        r.setIsDefault(isDefault);
        r.setAvailableToForeignOrgs(availableToForeignOrgs);
        em.persist(r);
    }
}
