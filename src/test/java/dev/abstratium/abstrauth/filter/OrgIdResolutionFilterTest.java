package dev.abstratium.abstrauth.filter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Tests that {@link OrgIdResolutionFilter} correctly resolves the {@code orgId}
 * from the JWT and stores it in {@link dev.abstratium.abstrauth.service.CurrentOrgContext},
 * so that {@link dev.abstratium.abstrauth.service.JwtOrgResolver} can scope database
 * queries to the right organisation.
 *
 * <p>Scenario: an account is a member of two organisations (O1 and O2).
 * O1 has a client; O2 has none. When the account authenticates with an
 * {@code orgId} claim pointing to O2, the GET /api/clients endpoint must
 * return an empty list.</p>
 */
@QuarkusTest
public class OrgIdResolutionFilterTest {

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    private String tokenForOrg(String accountId, String orgId) {
        return Jwt.issuer("https://dev.abstrauth.abstratium.dev").audience("abstratium-abstrauth")
            .subject(accountId)
            .upn("test_" + accountId + "@example.com")
            .groups(Set.of(
                "abstratium-abstrauth_user",
                "abstratium-abstrauth_manage-clients"
            ))
            .claim("orgId", orgId)
            .sign();
    }

    @Test
    public void testOrgIdFromJwtScopesQueriesToCorrectOrganisation() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create an account (auto-creates org O1 and makes the account owner/member)
        String email = "multiorg_user_" + ts + "@example.com";
        Account account = accountService.createAccount(
            email, "Multi Org User", email, "Pass123!",
            AccountService.NATIVE, "Org One " + ts);
        String orgO1Id = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        // Create a second organisation O2 and add the same account as a member
        var orgO2 = organisationService.createOrganisation("Org Two " + ts, account.getId());
        String orgO2Id = orgO2.getId();
        organisationService.addMember(orgO2Id, account.getId());

        // Insert a client in O1 using native SQL, bypassing the tenant resolver
        String clientId = "filter-test-client-" + ts;
        String clientUuid = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuid)
            .setParameter("clientId", clientId)
            .setParameter("name", "Filter Test Client")
            .setParameter("orgId", orgO1Id)
            .executeUpdate();
        em.createNativeQuery(
            "INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, org_id) " +
            "VALUES (:clientId, '$2a$10$dummyhashAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', true, 'Test', :orgId)")
            .setParameter("clientId", clientId)
            .setParameter("orgId", orgO1Id)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Authenticate with orgId = O2 — O2 has no clients
        given()
            .auth().oauth2(tokenForOrg(account.getId(), orgO2Id))
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("", hasSize(0));
    }
}
