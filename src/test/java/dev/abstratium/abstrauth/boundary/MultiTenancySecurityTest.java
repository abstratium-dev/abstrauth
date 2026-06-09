package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Comprehensive security tests for multi-tenancy isolation.
 * Tests that users cannot access or modify data belonging to other organisations.
 * All tests are performed via REST API only (black-box testing).
 */
@QuarkusTest
public class MultiTenancySecurityTest {

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    /**
     * Generate a JWT token with specific orgId and roles.
     */
    private String generateToken(String accountId, String orgId, Set<String> groups) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("test_" + accountId + "@example.com")
            .groups(groups)
            .claim("orgId", orgId)
            .sign();
    }

    private String adminTokenForOrg(String accountId, String orgId) {
        return generateToken(accountId, orgId, Set.of(
            "abstratium-abstrauth_user",
            "abstratium-abstrauth_admin",
            "abstratium-abstrauth_manage-accounts",
            "abstratium-abstrauth_manage-clients"
        ));
    }

    private String manageAccountsTokenForOrg(String accountId, String orgId) {
        return generateToken(accountId, orgId, Set.of(
            "abstratium-abstrauth_user",
            "abstratium-abstrauth_manage-accounts"
        ));
    }

    private String manageClientsTokenForOrg(String accountId, String orgId) {
        return generateToken(accountId, orgId, Set.of(
            "abstratium-abstrauth_user",
            "abstratium-abstrauth_manage-clients"
        ));
    }

    private String userTokenForOrg(String accountId, String orgId) {
        return generateToken(accountId, orgId, Set.of(
            "abstratium-abstrauth_user"
        ));
    }

    // ====================================================================================
    // TEST 1: Cross-Org Client Access Prevention
    // ====================================================================================

    @Test
    public void testCannotReadAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        em.flush();
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        em.flush();
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A via native SQL to bypass Hibernate's @TenantId
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
        em.flush();

        // Create client in Org B
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
        em.flush();

        transactionHelper.commitTransaction();
        System.out.println("[DEBUG] Transaction status after commit: " + transactionHelper.getStatus());

        // Debug: verify membership is established by reading directly from DB
        em.clear();
        System.out.println("[DEBUG] testCannotReadAnotherOrgsClient: accountB.id=" + accountB.getId() + ", orgBId=" + orgBId);
        long memberCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM T_organisation_accounts WHERE org_id = :orgId AND account_id = :accountId AND role = 'member'")
            .setParameter("orgId", orgBId)
            .setParameter("accountId", accountB.getId())
            .getSingleResult()).longValue();
        assertTrue(memberCount == 1, "accountB should be member of orgB in DB, found " + memberCount);

        // Org B user attempts to GET Org A's client directly by ID
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients/" + clientUuidA)
            .then()
            .statusCode(anyOf(is(404), is(403)));

        // Verify Org B's list doesn't include Org A's client
        var response = given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients")
            .then()
            .extract().response();
        System.out.println("[DEBUG] GET /api/clients status=" + response.statusCode() + " body=" + response.body().asString());
        assertEquals(200, response.statusCode());
        assertThat(response.jsonPath().getList("clientId"), not(hasItem(clientIdA)));
        assertThat(response.jsonPath().getList("clientId"), hasItem(clientIdB));
    }

    @Test
    public void testCannotUpdateAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "update-test-client-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Update Test Client A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B user attempts to PUT Org A's client
        String updateBody = """
            {
                "clientName": "Hacked Name",
                "clientType": "confidential",
                "redirectUris": "[]",
                "allowedScopes": "[]",
                "requirePkce": true
            }
            """;

        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when()
            .put("/api/clients/" + clientUuidA)
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotDeleteAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "delete-test-client-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Delete Test Client A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B user attempts to DELETE Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/clients/" + clientUuidA)
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotListAllowedRolesForAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "allowed-roles-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Allowed Roles Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Add allowed role for Org A's client
        em.createNativeQuery(
            "INSERT INTO T_client_allowed_roles (client_id, role, is_default) " +
            "VALUES (:clientId, 'user', true)")
            .setParameter("clientId", clientIdA)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B user attempts to list allowed roles of Org A's client
        given()
            .auth().oauth2(userTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients/" + clientIdA + "/allowed-roles")
            .then()
            .statusCode(404);
    }

    // ====================================================================================
    // TEST 2: Cross-Org Client Secrets Access Prevention
    // ====================================================================================

    @Test
    public void testCannotAccessAnotherOrgsClientSecrets() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "secrets-test-client-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Secrets Test Client A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Create secret for client in Org A
        em.createNativeQuery(
            "INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, org_id) " +
            "VALUES (:clientId, '$2a$10$dummyhash', true, 'Test Secret', :orgId)")
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B user attempts to list secrets of Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients/" + clientIdA + "/secrets")
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotCreateSecretForAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A (without secrets)
        String clientIdA = "create-secret-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Create Secret Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B user attempts to create secret for Org A's client
        String requestBody = """
            {
                "description": "Malicious Secret",
                "expiresInDays": 30
            }
            """;

        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/clients/" + clientIdA + "/secrets")
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotRevokeSecretForAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "revoke-secret-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Revoke Secret Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Create secret for client in Org A
        em.createNativeQuery(
            "INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, org_id) " +
            "VALUES (:clientId, '$2a$10$dummyhash', true, 'Test Secret', :orgId)")
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Get the generated secret ID
        Number secretId = (Number) em.createNativeQuery(
            "SELECT id FROM T_oauth_client_secrets WHERE client_id = :clientId")
            .setParameter("clientId", clientIdA)
            .getSingleResult();

        transactionHelper.commitTransaction();

        // Org B admin attempts to revoke secret of Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/clients/" + clientIdA + "/secrets/" + secretId.longValue())
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotDeleteSecretPermanentlyForAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "perm-delete-secret-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Perm Delete Secret Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Create an already-inactive (revoked) secret for client in Org A
        em.createNativeQuery(
            "INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, org_id) " +
            "VALUES (:clientId, '$2a$10$dummyhash', false, 'Revoked Secret', :orgId)")
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Get the generated secret ID
        Number secretId = (Number) em.createNativeQuery(
            "SELECT id FROM T_oauth_client_secrets WHERE client_id = :clientId")
            .setParameter("clientId", clientIdA)
            .getSingleResult();

        transactionHelper.commitTransaction();

        // Org B admin attempts to permanently delete secret of Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/clients/" + clientIdA + "/secrets/" + secretId.longValue() + "/permanent")
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    // ====================================================================================
    // TEST 3: Cross-Org Account Role Management Prevention
    // ====================================================================================

    @Test
    public void testCannotAddRoleToAccountInAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create a third account in Org A (victim)
        String emailVictim = "victim_" + ts + "@example.com";
        Account victimAccount = accountService.createAccountForOrg(emailVictim, "Victim User", emailVictim, "Pass123!", AccountService.NATIVE, orgAId);

        // Create a client so the FK constraint on account_roles is satisfied
        String someClientUuid = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", someClientUuid)
            .setParameter("clientId", "some-client")
            .setParameter("name", "Some Client")
            .setParameter("orgId", orgBId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B admin attempts to add role to victim account in Org A
        // Should be rejected because the target account is not in the caller's org
        String requestBody = """
            {
                "accountId": "%s",
                "clientId": "some-client",
                "role": "user"
            }
            """.formatted(victimAccount.getId());

        given()
            .auth().oauth2(manageAccountsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(403);
    }

    @Test
    public void testCannotRemoveRoleFromAccountInAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create a victim account in Org A with a role
        String emailVictim = "victim_role_" + ts + "@example.com";
        Account victimAccount = accountService.createAccountForOrg(emailVictim, "Victim User", emailVictim, "Pass123!", AccountService.NATIVE, orgAId);

        // Create a client in Org A so the FK constraint on account_roles is satisfied
        String uniqueClientId = "remove-role-test-" + ts;
        String roleClientUuid = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", roleClientUuid)
            .setParameter("clientId", uniqueClientId)
            .setParameter("name", "Test Client")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Add a role to victim via native SQL (scoped to org A)
        em.createNativeQuery(
            "INSERT INTO T_account_roles (id, account_id, client_id, role, org_id) " +
            "VALUES (:id, :accountId, :clientId, :role, :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("accountId", victimAccount.getId())
            .setParameter("clientId", uniqueClientId)
            .setParameter("role", "test-role")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B admin attempts to remove role from victim account in Org A
        // Should be rejected because the target account is not in the caller's org
        String requestBody = """
            {
                "accountId": "%s",
                "clientId": "%s",
                "role": "test-role"
            }
            """.formatted(victimAccount.getId(), uniqueClientId);

        given()
            .auth().oauth2(manageAccountsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(403);
    }

    // ====================================================================================
    // TEST 4: Cross-Org Service Account Roles Prevention
    // ====================================================================================

    @Test
    public void testCannotAccessAnotherOrgsServiceRoles() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "svc-roles-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Svc Roles Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Add service role for Org A's client
        em.createNativeQuery(
            "INSERT INTO T_service_account_roles (id, client_id, role, org_id) " +
            "VALUES (:id, :clientId, 'api-reader', :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B admin attempts to list service roles of Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients/" + clientIdA + "/roles")
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotAddServiceRoleToAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "svc-add-role-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Svc Add Role Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B admin attempts to add service role to Org A's client
        String requestBody = """
            {
                "role": "api-writer"
            }
            """;

        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/clients/" + clientIdA + "/roles")
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    @Test
    public void testCannotRemoveServiceRoleFromAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org A
        String clientIdA = "svc-remove-role-test-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Svc Remove Role Test A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        // Add service role for Org A's client
        em.createNativeQuery(
            "INSERT INTO T_service_account_roles (id, client_id, role, org_id) " +
            "VALUES (:id, :clientId, 'api-reader', :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B admin attempts to remove service role from Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/clients/" + clientIdA + "/roles/api-reader")
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    // ====================================================================================
    // RESEARCH TEST: Hibernate Multi-Tenancy with em.find()
    // ====================================================================================

    @Test
    public void testJpaFindRespectsTenantId() throws Exception {
        long ts = System.currentTimeMillis();
        String orgAId = UUID.randomUUID().toString();
        String orgBId = UUID.randomUUID().toString();
        String clientIdA = "client-a-jpa-" + ts;
        String clientIdB = "client-b-jpa-" + ts;

        // Insert organisations first to satisfy FK constraints
        transactionHelper.beginTransaction();
        em.createNativeQuery(
            "INSERT INTO T_organisations (id, name, created_at) VALUES (:id, :name, CURRENT_TIMESTAMP)")
            .setParameter("id", orgAId)
            .setParameter("name", "Org A - JPA Test " + ts)
            .executeUpdate();
        em.createNativeQuery(
            "INSERT INTO T_organisations (id, name, created_at) VALUES (:id, :name, CURRENT_TIMESTAMP)")
            .setParameter("id", orgBId)
            .setParameter("name", "Org B - JPA Test " + ts)
            .executeUpdate();

        // Insert clients via native SQL with explicit org_id (bypasses @TenantId enforcement)
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'public', '[]', '[]', true, true, :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Client A " + ts)
            .setParameter("orgId", orgAId)
            .executeUpdate();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'public', '[]', '[]', true, true, :orgId)")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("clientId", clientIdB)
            .setParameter("name", "Client B " + ts)
            .setParameter("orgId", orgBId)
            .executeUpdate();
        transactionHelper.commitTransaction();

        // Query via JPQL in the default tenant context
        // @TenantId filter should exclude clients from orgA and orgB
        transactionHelper.beginTransaction();
        var jpqlQuery = em.createQuery("SELECT c FROM OAuthClient c WHERE c.id = :id", OAuthClient.class);
        jpqlQuery.setParameter("id", clientIdB);
        OAuthClient foundViaJpql = jpqlQuery.getResultList().stream().findFirst().orElse(null);
        transactionHelper.rollback();

        assertNull(foundViaJpql, "JPQL query with @TenantId should not return clients from other tenants");
    }

    // ====================================================================================
    // TEST 5: Token Scope Enforcement
    // ====================================================================================

    @Test
    public void testTokenWithOrgAClaimCannotAccessOrgBData() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org B
        String clientIdB = "token-scope-test-b-" + ts;
        String clientUuidB = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidB)
            .setParameter("clientId", clientIdB)
            .setParameter("name", "Token Scope Test B")
            .setParameter("orgId", orgBId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Create token for accountA with orgA claim
        String orgAToken = adminTokenForOrg(accountA.getId(), orgAId);

        // Attempt to access Org B's client using Org A's token
        given()
            .auth().oauth2(orgAToken)
            .when()
            .get("/api/clients/" + clientUuidB)
            .then()
            .statusCode(anyOf(is(404), is(403)));

        // List clients - should only see Org A's clients (none created), not Org B's
        given()
            .auth().oauth2(orgAToken)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("clientId", not(hasItem(clientIdB)));
    }

    // ====================================================================================
    // TEST 6: Account Deletion Cross-Org Prevention
    // ====================================================================================

    @Test
    public void testCannotDeleteAccountFromAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create victim account in Org A
        String emailVictim = "victim_delete_" + ts + "@example.com";
        Account victimAccount = accountService.createAccountForOrg(emailVictim, "Victim User", emailVictim, "Pass123!", AccountService.NATIVE, orgAId);

        transactionHelper.commitTransaction();

        // Org B admin attempts to delete victim account from Org A
        // This should fail because while Account is global, the caller needs
        // MANAGE_ACCOUNTS role which is org-scoped
        given()
            .auth().oauth2(manageAccountsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/accounts/" + victimAccount.getId())
            .then()
            .statusCode(anyOf(is(403), is(404)));
    }

    // ====================================================================================
    // TEST 7: JWT orgId Claim Forgery Attempts
    // ====================================================================================

    @Test
    public void testForgedOrgIdClaimCannotAccessOtherOrgData() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create client in Org B
        String clientIdB = "forged-orgid-test-b-" + ts;
        String clientUuidB = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidB)
            .setParameter("clientId", clientIdB)
            .setParameter("name", "Forged OrgId Test B")
            .setParameter("orgId", orgBId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Create token for accountA but with FORGED orgBId claim
        // This simulates an attacker who somehow obtained a token for org A
        // but is trying to use it to access org B by forging the orgId
        String forgedToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountA.getId())
            .upn("forged_" + accountA.getId() + "@example.com")
            .groups(Set.of(
                "abstratium-abstrauth_user",
                "abstratium-abstrauth_admin",
                "abstratium-abstrauth_manage-accounts",
                "abstratium-abstrauth_manage-clients"
            ))
            .claim("orgId", orgBId)  // FORGED: claiming to be from Org B
            .sign();

        // Try to access Org B's client with forged token
        // Since accountA is not a member of Org B, and the token contains accountA's ID
        // but claims orgB, the system should not allow access to orgB's data
        given()
            .auth().oauth2(forgedToken)
            .when()
            .get("/api/clients/" + clientUuidB)
            .then()
            .statusCode(anyOf(is(404), is(403)));

        // List clients - should not see Org B's clients because
        // the @TenantId filter applies the orgId from the JWT
        // BUT the account (accountA) doesn't have roles in orgB context
        var listResponse = given()
            .auth().oauth2(forgedToken)
            .when()
            .get("/api/clients")
            .then()
            .extract().response();
        int listStatus = listResponse.statusCode();
        assertTrue(listStatus == 200 || listStatus == 403, "Expected 200 or 403 but got " + listStatus);
        if (listStatus == 200) {
            assertThat(listResponse.jsonPath().getList("clientId"), not(hasItem(clientIdB)));
        }
    }

    // ====================================================================================
    // TEST 8: Organisation Endpoint Security
    // ====================================================================================

    @Test
    public void testNonOwnerCannotRemoveMemberFromAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A with owner and member
        String emailA = "orgA_owner_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Owner", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Add a member to Org A
        String emailMember = "orgA_member_" + ts + "@example.com";
        Account memberAccount = accountService.createAccount(emailMember, "Org A Member", emailMember, "Pass123!", AccountService.NATIVE, "Member Org " + ts);
        organisationService.addMember(orgAId, memberAccount.getId());

        // Create Org B with owner
        String emailB = "orgB_owner_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Owner", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        transactionHelper.commitTransaction();

        // Org B owner attempts to remove member from Org A
        given()
            .auth().oauth2(userTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/organisations/" + orgAId + "/members/" + memberAccount.getId())
            .then()
            .statusCode(403);
    }

    @Test
    public void testNonOwnerCannotSubscribeAnotherOrgToClient() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A
        String emailA = "orgA_owner_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Owner", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B
        String emailB = "orgB_owner_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Owner", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create a client in Org A
        String clientIdA = "sub-test-client-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Sub Test Client A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Org B owner attempts to subscribe Org A to a client
        String requestBody = """
            {
                "clientId": "%s"
            }
            """.formatted(clientIdA);

        given()
            .auth().oauth2(userTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/organisations/" + orgAId + "/subscriptions")
            .then()
            .statusCode(403);
    }
}
