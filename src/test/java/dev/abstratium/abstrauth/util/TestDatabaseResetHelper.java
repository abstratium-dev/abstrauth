package dev.abstratium.abstrauth.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.CurrentOrgContext;

/**
 * Helper class to reset the database state before tests.
 * Deletes all non-seed data in the correct order to avoid FK constraint violations.
 * Uses native SQL to bypass Hibernate's @TenantId filter and clean up across ALL orgs.
 * Also resets the request-scoped tenant context to the default organisation.
 */
@ApplicationScoped
public class TestDatabaseResetHelper {

    /** Default org used by R__01__test_default_org_and_clients.sql for test clients. */
    public static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";
    private static final String PROTECTED_CLIENT_ID = "abstratium-abstrauth";

    /** Client IDs that are seeded by the repeatable migration R__01__test_default_org_and_clients.sql. */
    private static final String[] SEEDED_CLIENT_IDS = {
        PROTECTED_CLIENT_ID,
        "client-a",
        "client-b",
        "test-client",
        "test_client",
        "client-unique",
        "client-different",
        "client-x",
        "client-y",
        "client-z"
    };

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @ConfigProperty(name = "default.org.uuid")
    String configuredDefaultOrgId;

    /**
     * Resets the database by deleting all data that is not part of the initial seed.
     * Deletes children before parents to avoid FK constraint violations.
     * Uses native SQL to bypass tenant filtering.
     * Resets the current tenant context to the default organisation.
     */
    public void resetDatabase() {
        currentOrgContext.setOrgId(configuredDefaultOrgId);
        accountService.resetAccountExistenceCache();

        final String configuredDefaultOrg = "'" + configuredDefaultOrgId + "'";
        final String testOrg = "'" + DEFAULT_ORG_ID + "'";
        final String protectedOrgs = "(" + configuredDefaultOrg + ", " + testOrg + ")";
        final String seededClients = buildInClause(SEEDED_CLIENT_IDS);
        final String protectedClient = "'" + PROTECTED_CLIENT_ID + "'";

        // Delete in reverse order of dependencies to avoid FK violations.
        // Most ON DELETE CASCADE constraints were removed in V01.035, so order matters.

        // 1. Transient tables with no seed data — delete everything.
        em.createNativeQuery("DELETE FROM T_revoked_tokens").executeUpdate();
        em.createNativeQuery("DELETE FROM T_authorization_codes").executeUpdate();
        em.createNativeQuery("DELETE FROM T_authorization_requests").executeUpdate();

        // 2. Account children (no seeded data in these tables).
        em.createNativeQuery("DELETE FROM T_credentials").executeUpdate();
        em.createNativeQuery("DELETE FROM T_federated_identities").executeUpdate();
        em.createNativeQuery("DELETE FROM T_account_roles").executeUpdate();
        em.createNativeQuery("DELETE FROM T_organisation_accounts").executeUpdate();

        // 3. Client children — keep only rows belonging to seeded clients in protected orgs.
        em.createNativeQuery("DELETE FROM T_oauth_client_secrets WHERE client_id NOT IN " + seededClients + " OR org_id NOT IN " + protectedOrgs)
            .executeUpdate();
        em.createNativeQuery("DELETE FROM T_client_roles").executeUpdate();
        em.createNativeQuery("DELETE FROM T_subscriptions WHERE client_id NOT IN " + seededClients + " OR org_id NOT IN " + protectedOrgs)
            .executeUpdate();
        em.createNativeQuery("DELETE FROM T_client_allowed_roles WHERE client_id != " + protectedClient)
            .executeUpdate();

        // 4. Accounts — no accounts are seeded; delete all.
        em.createNativeQuery("DELETE FROM T_accounts").executeUpdate();

        // 5. Clients — keep only seeded clients.
        em.createNativeQuery("DELETE FROM T_oauth_clients WHERE client_id NOT IN " + seededClients)
            .executeUpdate();

        // 6. Organisations — keep only the two orgs that contain seeded data.
        em.createNativeQuery("DELETE FROM T_organisations WHERE id NOT IN " + protectedOrgs)
            .executeUpdate();

        // 7. Safety-net: re-seed critical default data if a previous test removed it.
        reseedDefaultOrg(configuredDefaultOrg);
        reseedDefaultOrg(testOrg);
        reseedAbstrauthClient(configuredDefaultOrg, protectedClient);
        reseedTestClients(testOrg);
    }

    private static String buildInClause(String[] values) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < values.length; i++) {
            sb.append("'").append(values[i]).append("'");
            if (i < values.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private void reseedDefaultOrg(String defaultOrg) {
        em.createNativeQuery(
                "INSERT INTO T_organisations (id, name, created_at) " +
                "SELECT " + defaultOrg + ", 'Default Test Org', CURRENT_TIMESTAMP " +
                "WHERE NOT EXISTS (SELECT 1 FROM T_organisations WHERE id = " + defaultOrg + ")")
            .executeUpdate();
    }

    private void reseedAbstrauthClient(String org, String clientIdLiteral) {
        reseedClient(org, "abstratium-abstrauth-id", clientIdLiteral,
            "Abstratium Abstrauth", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
    }

    private void reseedTestClients(String org) {
        reseedClient(org, "client-a-id", "'client-a'",
            "Test Client A", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "client-b-id", "'client-b'",
            "Test Client B", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "test-client-id", "'test-client'",
            "Test Client", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "test_client-id", "'test_client'",
            "Test Client Underscore", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "client-unique-id", "'client-unique'",
            "Client Unique", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "client-different-id", "'client-different'",
            "Client Different", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "client-x-id", "'client-x'",
            "Client X", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "client-y-id", "'client-y'",
            "Client Y", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
        reseedClient(org, "client-z-id", "'client-z'",
            "Client Z", "confidential", "[\"http://localhost:8080/callback\"]",
            "[\"openid\",\"profile\",\"email\"]", false, false, false);
    }

    private void reseedClient(String org, String id, String clientId, String name,
                              String type, String redirectUris, String scopes,
                              boolean requirePkce, boolean autoSubscribe, boolean publik) {
        em.createNativeQuery(
                "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, publik, created_at, org_id) " +
                "SELECT '" + id + "', " + clientId + ", '" + name + "', '" + type + "', '" + redirectUris + "', '" + scopes + "', " + requirePkce + ", " + autoSubscribe + ", " + publik + ", CURRENT_TIMESTAMP, " + org + " " +
                "WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = " + clientId + ")")
            .executeUpdate();
    }

}
