package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

/**
 * Tests that AccountsResource and ClientsResource are scoped to the signed-in org.
 * Cross-org isolation: accounts/clients in org B must not be visible to a user signed in as org A.
 */
@QuarkusTest
public class CrossOrgIsolationTest {

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction userTransaction;

    private void beginTransaction() throws Exception {
        if (userTransaction.getStatus() == jakarta.transaction.Status.STATUS_NO_TRANSACTION) {
            userTransaction.begin();
        }
    }

    private void commitTransaction() throws Exception {
        if (userTransaction.getStatus() == jakarta.transaction.Status.STATUS_ACTIVE) {
            userTransaction.commit();
        }
    }

    private String adminTokenForOrg(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("admin_" + accountId + "@example.com")
            .groups(Set.of(
                "abstratium-abstrauth_user",
                "abstratium-abstrauth_admin",
                "abstratium-abstrauth_manage-accounts"
            ))
            .claim("orgId", orgId)
            .sign();
    }

    private String manageClientsTokenForOrg(String accountId, String orgId) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("mgr_" + accountId + "@example.com")
            .groups(Set.of(
                "abstratium-abstrauth_user",
                "abstratium-abstrauth_manage-clients"
            ))
            .claim("orgId", orgId)
            .sign();
    }

    @Test
    public void testAdminCannotSeeAccountsFromAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

        // Org A: create account and org
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Org B: create account and org
        String emailB = "orgB_member_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Member", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        commitTransaction();

        // User authenticated as org A must see org A account but NOT org B account
        given()
            .auth().oauth2(adminTokenForOrg(accountA.getId(), orgAId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("email", hasItem(emailA))
            .body("email", not(hasItem(emailB)));

        // User authenticated as org B must see org B account but NOT org A account
        given()
            .auth().oauth2(adminTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("email", hasItem(emailB))
            .body("email", not(hasItem(emailA)));
    }

    @Test
    public void testManageClientsCannotSeeClientsFromAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

        // Org A: create account and org
        String emailA = "orgA_mgr_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Manager", emailA, "Pass123!", AccountService.NATIVE, "Org A Clients " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Org B: create account and org
        String emailB = "orgB_mgr_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Manager", emailB, "Pass123!", AccountService.NATIVE, "Org B Clients " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Use native SQL to insert clients with explicit org_id, bypassing Hibernate's @TenantId resolver
        String clientIdA = "cross-org-client-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Cross Org Client A")
            .setParameter("orgId", orgAId)
            .executeUpdate();
        em.createNativeQuery(
            "INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, org_id) " +
            "VALUES (:clientId, '$2a$10$dummyhashAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', true, 'Test', :orgId)")
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        String clientIdB = "cross-org-client-b-" + ts;
        String clientUuidB = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidB)
            .setParameter("clientId", clientIdB)
            .setParameter("name", "Cross Org Client B")
            .setParameter("orgId", orgBId)
            .executeUpdate();
        em.createNativeQuery(
            "INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, org_id) " +
            "VALUES (:clientId, '$2a$10$dummyhashBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB', true, 'Test', :orgId)")
            .setParameter("clientId", clientIdB)
            .setParameter("orgId", orgBId)
            .executeUpdate();

        commitTransaction();

        // Org A user should see their client but not org B's
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountA.getId(), orgAId))
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("clientId", hasItem(clientIdA))
            .body("clientId", not(hasItem(clientIdB)));

        // Org B user should see their client but not org A's
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("clientId", hasItem(clientIdB))
            .body("clientId", not(hasItem(clientIdA)));
    }
}
