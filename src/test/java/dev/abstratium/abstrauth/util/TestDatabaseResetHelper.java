package dev.abstratium.abstrauth.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

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

    private static final Logger log = Logger.getLogger(TestDatabaseResetHelper.class);

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
    @Transactional
    public void resetDatabase() {
        currentOrgContext.setOrgId(configuredDefaultOrgId);
        accountService.resetAccountExistenceCache();

        final String defaultOrg = "'" + configuredDefaultOrgId + "'";
        final String seededClients = buildInClause(SEEDED_CLIENT_IDS);
        final String protectedClient = "'" + PROTECTED_CLIENT_ID + "'";

        log.debugv("[resetDatabase] Before cleanup — abstrauth redirect_uris: {0}",
            queryRedirectUris(PROTECTED_CLIENT_ID));

        // H2-specific: disable FK checks so we can clean up without worrying about
        // leftover cross-org references from earlier tests.
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

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

        // 3. Client children — keep only rows belonging to seeded clients.
        em.createNativeQuery("DELETE FROM T_oauth_client_secrets WHERE client_id NOT IN " + seededClients)
            .executeUpdate();
        em.createNativeQuery("DELETE FROM T_client_roles").executeUpdate();
        em.createNativeQuery("DELETE FROM T_subscriptions WHERE client_id NOT IN " + seededClients)
            .executeUpdate();
        em.createNativeQuery("DELETE FROM T_client_allowed_roles WHERE client_id != " + protectedClient)
            .executeUpdate();

        // 4. Accounts — no accounts are seeded; delete all.
        em.createNativeQuery("DELETE FROM T_accounts").executeUpdate();

        // 5. Clients — keep only seeded clients.
        em.createNativeQuery("DELETE FROM T_oauth_clients WHERE client_id NOT IN " + seededClients)
            .executeUpdate();

        // 6. Organisations — keep only the default org.
        em.createNativeQuery("DELETE FROM T_organisations WHERE id != " + defaultOrg)
            .executeUpdate();

        // Re-enable FK checks before re-seeding so inserts are validated.
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();

        // 7. Safety-net: re-seed critical default data if a previous test removed it.
        reseedDefaultOrg(defaultOrg);
        restoreAbstrauthClient(defaultOrg, protectedClient);
        reseedTestClients(defaultOrg);

        log.debugv("[resetDatabase] After cleanup — abstrauth redirect_uris: {0}",
            queryRedirectUris(PROTECTED_CLIENT_ID));
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

    private void restoreAbstrauthClient(String org, String clientIdLiteral) {
        // Must match actual post-migration state (V01.006 + V01.010 + V01.024)
        final String redirectUris = "[\"http://localhost:8080/api/auth/callback\",\"https://auth.abstratium.dev/api/auth/callback\"]";
        final String scopes = "[\"openid\",\"profile\",\"email\"]";

        // Some tests mutate the abstratium-abstrauth client; restore it to correct values.
        int updated = em.createNativeQuery(
                "UPDATE T_oauth_clients SET " +
                "client_name = 'abstratium abstrauth', " +
                "client_type = 'confidential', " +
                "redirect_uris = '" + redirectUris + "', " +
                "allowed_scopes = '" + scopes + "', " +
                "require_pkce = true, " +
                "auto_subscribe = true, " +
                "publik = true, " +
                "org_id = " + org + " " +
                "WHERE client_id = " + clientIdLiteral)
            .executeUpdate();
        log.debugv("[restoreAbstrauthClient] Updated {0} row(s) for {1}", updated, PROTECTED_CLIENT_ID);

        // Fallback: insert if the row was somehow deleted.
        reseedClient(org, "abstratium-abstrauth-id", clientIdLiteral,
            "abstratium abstrauth", "confidential", redirectUris,
            scopes, true, true, true);
    }

    private String queryRedirectUris(String clientId) {
        try {
            var results = em.createNativeQuery(
                    "SELECT redirect_uris FROM T_oauth_clients WHERE client_id = '" + clientId + "'")
                .getResultList();
            return results.isEmpty() ? "<not found>" : String.valueOf(results.get(0));
        } catch (Exception e) {
            return "<error: " + e.getMessage() + ">";
        }
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
