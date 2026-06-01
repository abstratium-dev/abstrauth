package dev.abstratium.abstrauth.boundary.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.SubscriptionService;
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
    SubscriptionService subscriptionService;

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

    private String getAccountOrgId(String accountId) throws Exception {
        transactionHelper.beginTransaction();
        java.util.List<Organisation> orgs = organisationService.listOrganisationsForAccount(accountId);
        String orgId = orgs.isEmpty() ? DEFAULT_ORG_ID : orgs.get(0).getId();
        transactionHelper.commitTransaction();
        return orgId;
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
    // POST /api/organisations
    // ─────────────────────────────────────────────────────────

    @Test
    public void testCreateOrganisation_success() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_create");
        String token = userToken(account.getId());

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
                .body("name", containsString("New Org"));
    }

    @Test
    public void testCreateOrganisation_blankName_returns400() throws Exception {
        Account account = createAccount(System.currentTimeMillis() + "_blankname");
        String token = userToken(account.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"\"}")
                .when()
                .post("/api/organisations")
                .then()
                .statusCode(400);
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

    // ─────────────────────────────────────────────────────────
    // POST /api/organisations/{orgId}/members
    // ─────────────────────────────────────────────────────────

    @Test
    public void testAddMember_success() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_owner");
        Account newMember = createAccount(ts + "_newmember");

        // owner's org was created during signup
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);
        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"accountId\":\"" + newMember.getId() + "\"}")
                .when()
                .post("/api/organisations/" + ownerOrg.getId() + "/members")
                .then()
                .statusCode(201);

        // verify via service
        assertTrue(organisationService.isMember(ownerOrg.getId(), newMember.getId()));
    }

    @Test
    public void testAddMember_nonOwner_returns403() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_o403");
        Account nonOwner = createAccount(ts + "_no403");
        Account target = createAccount(ts + "_t403");

        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);
        // nonOwner uses owner's org ID but is not an owner of it
        String token = userToken(nonOwner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"accountId\":\"" + target.getId() + "\"}")
                .when()
                .post("/api/organisations/" + ownerOrg.getId() + "/members")
                .then()
                .statusCode(403);
    }

    @Test
    public void testAddMember_unknownOrg_returns403() throws Exception {
        Account owner = createAccount(System.currentTimeMillis() + "_unknowno");
        String token = userToken(owner.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"accountId\":\"" + owner.getId() + "\"}")
                .when()
                .post("/api/organisations/unknown-org-id/members")
                .then()
                .statusCode(403);
    }

    @Test
    public void testAddMember_unknownAccount_returns404() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_owneracc");

        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);
        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body("{\"accountId\":\"nonexistent-account-id\"}")
                .when()
                .post("/api/organisations/" + ownerOrg.getId() + "/members")
                .then()
                .statusCode(404);
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
        createTestClient(clientId);
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
        createTestClient(clientId);
        subscriptionService.subscribe(ownerOrg.getId(), clientId);
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
        createTestClient(clientId);
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
        createTestClient(clientId);
        subscriptionService.subscribe(ownerOrg.getId(), clientId);
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/subscriptions/" + clientId)
                .then()
                .statusCode(204);

        assertFalse(subscriptionService.subscriptionExists(ownerOrg.getId(), clientId));
    }

    @Test
    public void testRemoveSubscription_nonOwner_returns403() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_unsubfown");
        Account nonOwner = createAccount(ts + "_unsubfnon");
        Organisation ownerOrg = organisationService.listOrganisationsForAccount(owner.getId()).get(0);

        String clientId = "unsub-f-client-" + ts;
        transactionHelper.beginTransaction();
        createTestClient(clientId);
        subscriptionService.subscribe(ownerOrg.getId(), clientId);
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
        createTestClient(clientId);
        transactionHelper.commitTransaction();

        String token = userToken(owner.getId(), ownerOrg.getId());

        given()
                .auth().oauth2(token)
                .when()
                .delete("/api/organisations/" + ownerOrg.getId() + "/subscriptions/" + clientId)
                .then()
                .statusCode(400);
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/clients/{clientId}/allowed-roles
    // ─────────────────────────────────────────────────────────

    @Test
    public void testListAllowedRoles_empty() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_roleemp");
        String clientId = "roles-empty-client-" + ts;

        transactionHelper.beginTransaction();
        createTestClient(clientId);
        transactionHelper.commitTransaction();

        String orgId = getAccountOrgId(account.getId());
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

        transactionHelper.beginTransaction();
        createTestClient(clientId);
        insertAllowedRole(clientId, "viewer", false);
        insertAllowedRole(clientId, "editor", true);
        transactionHelper.commitTransaction();

        String orgId = getAccountOrgId(account.getId());
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

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private void createTestClient(String clientId) {
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Test " + clientId);
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:8080/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);
    }

    private void insertAllowedRole(String clientId, String role, boolean isDefault) {
        ClientAllowedRole r = new ClientAllowedRole();
        r.setId(new ClientAllowedRole.Id(clientId, role));
        r.setIsDefault(isDefault);
        em.persist(r);
    }
}
