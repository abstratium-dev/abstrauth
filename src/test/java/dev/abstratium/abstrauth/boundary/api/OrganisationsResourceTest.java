package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
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

@QuarkusTest
public class OrganisationsResourceTest {

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

    private String userToken(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
                .subject(accountId)
                .upn("test@example.com")
                .groups(Set.of(Roles.USER))
                .claim("orgId", orgId)
                .sign();
    }

    private String userToken(String accountId) {
        return userToken(accountId, DEFAULT_ORG_ID);
    }

    private Account createAccount(String suffix) throws Exception {
        transactionHelper.beginTransaction();
        Account account = accountService.createAccount(
                "orgtest_" + suffix + "@example.com",
                "OrgTest " + suffix,
                "orgtest_" + suffix,
                "Pass123!",
                AccountService.NATIVE,
                "Test Org " + suffix);
        transactionHelper.commitTransaction();
        return account;
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/organisations
    // ─────────────────────────────────────────────────────────

    @Test
    public void testListOrganisations_returnsMemberships() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_list");
        String token = userToken(account.getId());

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/organisations")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", not(empty()))
                .body("name", hasItem("Test Org " + ts + "_list"));
    }

    @Test
    public void testListOrganisations_unauthenticated_returns401() {
        given()
                .when()
                .get("/api/organisations")
                .then()
                .statusCode(401);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/organisations/{orgId}/members/{accountId}
    // ─────────────────────────────────────────────────────────

    @Test
    public void testRemoveMember_success() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_ownerrem");
        Account member = createAccount(ts + "_memberrem");

        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        transactionHelper.beginTransaction();
        organisationService.addMember(ownerOrg.getId(), member.getId());
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/members/" + member.getId())
                .then()
                .statusCode(204);

        assertFalse(organisationService.isMember(ownerOrg.getId(), member.getId()));
    }

    @Test
    public void testRemoveMember_nonOwner_returns403() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_ownerf");
        Account nonOwner = createAccount(ts + "_nonownf");
        Account victim = createAccount(ts + "_victimf");

        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        transactionHelper.beginTransaction();
        organisationService.addMember(ownerOrg.getId(), victim.getId());
        transactionHelper.commitTransaction();

        String token = userToken(nonOwner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/members/" + victim.getId())
                .then()
                .statusCode(403);
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/organisations/{orgId}/subscriptions
    // ─────────────────────────────────────────────────────────

    @Test
    public void testAddSubscription_success() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_subowner");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        // create a client to subscribe to
        String clientId = "sub-test-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrg.getId());
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"clientId\":\"" + clientId + "\"}")
                .when()
                .post("/api/organisations/" + ownerOrg.getId() + "/subscriptions")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("orgId", equalTo(ownerOrg.getId()))
                .body("clientId", equalTo(clientId));
    }

    @Test
    public void testAddSubscription_duplicate_returns400() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_dupsubowner");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        String clientId = "dup-sub-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrg.getId());
        subscriptionService.ensureSubscribed(ownerOrg.getId(), clientId, true);
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"clientId\":\"" + clientId + "\"}")
                .when()
                .post("/api/organisations/" + ownerOrg.getId() + "/subscriptions")
                .then()
                .statusCode(400);
    }

    @Test
    public void testAddSubscription_nonOwner_returns403() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_subfowner");
        Account nonOwner = createAccount(ts + "_subfnon");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        String clientId = "sub-f-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrg.getId());
        transactionHelper.commitTransaction();

        String token = userToken(nonOwner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"clientId\":\"" + clientId + "\"}")
                .when()
                .post("/api/organisations/" + ownerOrg.getId() + "/subscriptions")
                .then()
                .statusCode(403);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/organisations/{orgId}/subscriptions/{clientId}
    // ─────────────────────────────────────────────────────────

    @Test
    public void testRemoveSubscription_success() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_unsubowner");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        String clientId = "unsub-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrg.getId());
        subscriptionService.ensureSubscribed(ownerOrg.getId(), clientId, true);
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/subscriptions/" + clientId)
                .then()
                .statusCode(204);

        assertFalse(subscriptionService.findNonMultitenancySubscription(ownerOrg.getId(), clientId).isPresent());
    }

    @Test
    public void testRemoveSubscription_nonOwner_returns403() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_unsubfown");
        Account nonOwner = createAccount(ts + "_unsubfnon");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        String clientId = "unsub-f-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrg.getId());
        subscriptionService.ensureSubscribed(ownerOrg.getId(), clientId, true);
        transactionHelper.commitTransaction();

        String token = userToken(nonOwner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/subscriptions/" + clientId)
                .then()
                .statusCode(403);
    }

    @Test
    public void testRemoveSubscription_notSubscribed_returns400() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_unsubmiss");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        String clientId = "unsub-miss-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId, ownerOrg.getId());
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/subscriptions/" + clientId)
                .then()
                .statusCode(400);
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

}
