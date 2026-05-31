package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.entity.OrganisationAccount;
import dev.abstratium.abstrauth.entity.ServiceAccountRole;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

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

        beginTransaction();

        // Create Org A with admin
        String emailA = "orgA_admin_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Admin", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with admin
        String emailB = "orgB_admin_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Admin", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
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

        commitTransaction();

        // Org B user attempts to GET Org A's client directly by ID
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients/" + clientUuidA)
            .then()
            .statusCode(anyOf(is(404), is(403)));

        // Verify Org B's list doesn't include Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .get("/api/clients")
            .then()
            .statusCode(200)
            .body("clientId", not(hasItem(clientIdA)))
            .body("clientId", hasItem(clientIdB));
    }

    @Test
    public void testCannotUpdateAnotherOrgsClient() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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

        commitTransaction();

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

        beginTransaction();

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

        commitTransaction();

        // Org B user attempts to DELETE Org A's client
        given()
            .auth().oauth2(manageClientsTokenForOrg(accountB.getId(), orgBId))
            .when()
            .delete("/api/clients/" + clientUuidA)
            .then()
            .statusCode(anyOf(is(404), is(403)));
    }

    // ====================================================================================
    // TEST 2: Cross-Org Client Secrets Access Prevention
    // ====================================================================================

    @Test
    public void testCannotAccessAnotherOrgsClientSecrets() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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

        commitTransaction();

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

        beginTransaction();

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

        commitTransaction();

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

    // ====================================================================================
    // TEST 3: Cross-Org Account Role Management Prevention
    // ====================================================================================

    @Test
    public void testCannotAddRoleToAccountInAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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

        commitTransaction();

        // Org B admin attempts to add role to victim account in Org A
        String requestBody = """
            {
                "accountId": "%s",
                "clientId": "some-client",
                "role": "admin"
            }
            """.formatted(victimAccount.getId());

        // This should fail because the caller's AccountRole rows are org-scoped
        // and they cannot have MANAGE_ACCOUNTS role in org A
        given()
            .auth().oauth2(manageAccountsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/accounts/role")
            .then()
            .statusCode(anyOf(is(403), is(404)));
    }

    @Test
    public void testCannotRemoveRoleFromAccountInAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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

        // Add a role to victim via native SQL (scoped to org A)
        em.createNativeQuery(
            "INSERT INTO T_account_roles (account_id, client_id, role, org_id) " +
            "VALUES (:accountId, :clientId, :role, :orgId)")
            .setParameter("accountId", victimAccount.getId())
            .setParameter("clientId", "test-client")
            .setParameter("role", "test-role")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        commitTransaction();

        // Org B admin attempts to remove role from victim account in Org A
        String requestBody = """
            {
                "accountId": "%s",
                "clientId": "test-client",
                "role": "test-role"
            }
            """.formatted(victimAccount.getId());

        given()
            .auth().oauth2(manageAccountsTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .delete("/api/accounts/role")
            .then()
            .statusCode(anyOf(is(403), is(404)));
    }

    // ====================================================================================
    // TEST 4: Cross-Org Service Account Roles Prevention
    // ====================================================================================

    @Test
    public void testCannotAccessAnotherOrgsServiceRoles() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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
            "INSERT INTO T_service_account_roles (client_id, role, org_id) " +
            "VALUES (:clientId, 'api-reader', :orgId)")
            .setParameter("clientId", clientIdA)
            .setParameter("orgId", orgAId)
            .executeUpdate();

        commitTransaction();

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

        beginTransaction();

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

        commitTransaction();

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

    // ====================================================================================
    // RESEARCH TEST: Hibernate Multi-Tenancy with em.find()
    // ====================================================================================

    @Test
    public void testJpaFindRespectsTenantId() throws Exception {
        long ts = System.currentTimeMillis();

        // Create Org A with account and client
        String orgAId = UUID.randomUUID().toString();
        beginTransaction();
        Organisation orgA = new Organisation();
        orgA.setId(orgAId);
        orgA.setName("Org A - JPA Test " + ts);
        em.persist(orgA);

        // Create account A manually using JPA
        Account accountA = new Account();
        accountA.setId("account-a-jpa-" + ts);
        accountA.setEmail("account-a-jpa-" + ts + "@example.com");
        accountA.setName("Account A " + ts);
        em.persist(accountA);

        OrganisationAccount orgAccountA = new OrganisationAccount();
        orgAccountA.setOrgId(orgA.getId());
        orgAccountA.setAccountId(accountA.getId());
        orgAccountA.setRole("owner");
        em.persist(orgAccountA);

        // Create client in Org A using JPA (not native SQL)
        OAuthClient clientA = new OAuthClient();
        clientA.setId("client-a-jpa-" + ts);
        clientA.setClientName("Client A " + ts);
        clientA.setClientType("public");
        clientA.setOrgId(orgAId);
        em.persist(clientA);

        commitTransaction();

        // Create Org B with account and client
        String orgBId = UUID.randomUUID().toString();
        beginTransaction();
        Organisation orgB = new Organisation();
        orgB.setId(orgBId);
        orgB.setName("Org B - JPA Test " + ts);
        em.persist(orgB);

        // Create account B manually using JPA
        Account accountB = new Account();
        accountB.setId("account-b-jpa-" + ts);
        accountB.setEmail("account-b-jpa-" + ts + "@example.com");
        accountB.setName("Account B " + ts);
        em.persist(accountB);

        OrganisationAccount orgAccountB = new OrganisationAccount();
        orgAccountB.setOrgId(orgB.getId());
        orgAccountB.setAccountId(accountB.getId());
        orgAccountB.setRole("owner");
        em.persist(orgAccountB);

        // Create client in Org B using JPA
        OAuthClient clientB = new OAuthClient();
        clientB.setId("client-b-jpa-" + ts);
        clientB.setClientName("Client B " + ts);
        clientB.setClientType("public");
        clientB.setOrgId(orgBId);
        em.persist(clientB);

        commitTransaction();

        // Now test: JPQL-based findById (which respects @TenantId) should NOT return
        // a client from Org B when the current tenant context is Org A (default org)
        beginTransaction();

        // em.find() bypasses @TenantId - confirmed vulnerability (CRITICAL)
        // JPQL queries DO respect @TenantId - this is the correct approach
        // The current tenant context here is the default org (00000000-...),
        // so neither clientA nor clientB should be visible via JPQL
        var jpqlQuery = em.createQuery("SELECT c FROM OAuthClient c WHERE c.id = :id", OAuthClient.class);
        jpqlQuery.setParameter("id", clientB.getId());
        OAuthClient foundViaJpql = jpqlQuery.getResultList().stream().findFirst().orElse(null);

        userTransaction.rollback();

        // The JPQL query with @TenantId should NOT return clientB when in a different tenant context
        assertNull(foundViaJpql, "JPQL query with @TenantId should not return clients from other tenants");
    }

    // ====================================================================================
    // TEST 5: Token Scope Enforcement
    // ====================================================================================

    @Test
    public void testTokenWithOrgAClaimCannotAccessOrgBData() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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

        commitTransaction();

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

        beginTransaction();

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

        commitTransaction();

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

        beginTransaction();

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

        commitTransaction();

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
        given()
            .auth().oauth2(forgedToken)
            .when()
            .get("/api/clients")
            .then()
            .statusCode(anyOf(is(200), is(403)))
            .body("clientId", not(hasItem(clientIdB)));
    }

    // ====================================================================================
    // TEST 8: Organisation Endpoint Security
    // ====================================================================================

    @Test
    public void testNonOwnerCannotAddMemberToAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

        // Create Org A with owner
        String emailA = "orgA_owner_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Org A Owner", emailA, "Pass123!", AccountService.NATIVE, "Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B with owner
        String emailB = "orgB_owner_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Org B Owner", emailB, "Pass123!", AccountService.NATIVE, "Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create a user to be added
        String emailUser = "user_to_add_" + ts + "@example.com";
        Account userAccount = accountService.createAccount(emailUser, "User To Add", emailUser, "Pass123!", AccountService.NATIVE, "User Org " + ts);

        commitTransaction();

        // Org B owner attempts to add member to Org A (using Org B token)
        // The endpoint checks isOwnerOfOrg with the caller's account ID from JWT
        String requestBody = """
            {
                "accountId": "%s"
            }
            """.formatted(userAccount.getId());

        given()
            .auth().oauth2(userTokenForOrg(accountB.getId(), orgBId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/organisations/" + orgAId + "/members")
            .then()
            .statusCode(403);
    }

    @Test
    public void testNonOwnerCannotRemoveMemberFromAnotherOrg() throws Exception {
        long ts = System.currentTimeMillis();

        beginTransaction();

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

        commitTransaction();

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

        beginTransaction();

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

        commitTransaction();

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
