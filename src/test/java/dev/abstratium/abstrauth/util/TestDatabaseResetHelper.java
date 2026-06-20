package dev.abstratium.abstrauth.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Helper class to reset the database state before tests.
 * Deletes all test data in the correct order to avoid FK constraint violations.
 * Uses native SQL to bypass Hibernate's @TenantId filter and clean up across ALL orgs.
 */
@ApplicationScoped
public class TestDatabaseResetHelper {

    @Inject
    EntityManager em;

    /**
     * Resets the database by deleting all test-related data.
     * Deletes children before parents to avoid FK constraint violations.
     * Uses native SQL to bypass tenant filtering.
     */
    public void resetDatabase() {
        // Delete in reverse order of dependencies to avoid FK violations

        // 1. Authorization codes (depends on accounts, clients)
        em.createNativeQuery("DELETE FROM T_authorization_codes WHERE client_id LIKE '%test%' OR account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
            .executeUpdate();

        // 2. Authorization requests (depends on accounts, clients)
        em.createNativeQuery("DELETE FROM T_authorization_requests WHERE client_id LIKE '%test%' OR account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
            .executeUpdate();

        // 3. Client secrets (depends on clients)
        em.createNativeQuery("DELETE FROM T_oauth_client_secrets WHERE client_id LIKE '%test%' OR client_id LIKE '%client-%'")
            .executeUpdate();

        // 4. Client roles (depends on clients)
        em.createNativeQuery("DELETE FROM T_client_roles WHERE src_client_id LIKE '%test%' OR target_client_id LIKE '%test%' OR src_client_id LIKE '%client-%' OR target_client_id LIKE '%client-%'")
            .executeUpdate();

        // 5. Subscriptions (depends on orgs, clients)
        em.createNativeQuery("DELETE FROM T_subscriptions WHERE org_id IN (SELECT org_id FROM T_organisations WHERE name LIKE 'Test Org%' OR name LIKE 'Different Org%')")
            .executeUpdate();

        // 6. Client allowed roles (depends on clients)
        em.createNativeQuery("DELETE FROM T_client_allowed_roles WHERE client_id LIKE '%test%' OR client_id LIKE '%client-%'")
            .executeUpdate();

        // 7. Account roles (depends on accounts, clients)
        em.createNativeQuery("DELETE FROM T_account_roles WHERE account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
            .executeUpdate();

        // 8. Organisation accounts (depends on accounts, orgs)
        em.createNativeQuery("DELETE FROM T_organisation_accounts WHERE account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
            .executeUpdate();
        em.createNativeQuery("DELETE FROM T_organisation_accounts WHERE org_id IN (SELECT org_id FROM T_organisations WHERE name LIKE 'Test Org%' OR name LIKE 'Different Org%')")
            .executeUpdate();

        // 9. Test OAuth clients (not created by Flyway migration - dynamic test clients)
        em.createNativeQuery("DELETE FROM T_oauth_clients WHERE client_id LIKE 'new-client-%'")
            .executeUpdate();

        // 10. Credentials (depends on accounts) - covers both native and password-based auth
        em.createNativeQuery("DELETE FROM T_credentials WHERE account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
            .executeUpdate();

        // 11. Federated identities (depends on accounts)
        em.createNativeQuery("DELETE FROM T_federated_identities WHERE account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
            .executeUpdate();

        // 12. Revoked tokens (depends on authorization_codes)
        em.createNativeQuery("DELETE FROM T_revoked_tokens WHERE authorization_code_id IN (SELECT id FROM T_authorization_codes WHERE account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com'))")
            .executeUpdate();

        // 13. Test accounts
        em.createNativeQuery("DELETE FROM T_accounts WHERE email LIKE '%@example.com'")
            .executeUpdate();

        // 14. Test organisations (but NOT the default org)
        em.createNativeQuery("DELETE FROM T_organisations WHERE name LIKE 'Test Org%' OR name LIKE 'Different Org%'")
            .executeUpdate();
    }
}
