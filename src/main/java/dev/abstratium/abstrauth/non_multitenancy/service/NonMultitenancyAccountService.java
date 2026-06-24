package dev.abstratium.abstrauth.non_multitenancy.service;

import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccount;
import dev.abstratium.abstrauth.service.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Non-multitenancy service for account operations.
 *
 * This service bypasses Hibernate's @TenantId discriminator to perform
 * cross-tenant operations. Used for cascade deletions that must remove
 * related entities across all organizations.
 */
@ApplicationScoped
public class NonMultitenancyAccountService {

    @Inject
    EntityManager em;

    /**
     * Find an account by ID across all organisations.
     * Uses NonMultitenancyAccount to bypass the @TenantId discriminator.
     *
     * @param accountId The account ID
     * @return Optional containing the account if found
     */
    public Optional<NonMultitenancyAccount> findById(String accountId) {
        return Optional.ofNullable(em.find(NonMultitenancyAccount.class, accountId));
    }

    /**
     * Delete an account and all its related entities (credentials, federated identities,
     * and account roles) across ALL organisations using JPA cascade.
     *
     * This uses NonMultitenancyAccount which has CascadeType.REMOVE on all
     * related collections, ensuring complete deletion regardless of org_id.
     *
     * Note: Authorization requests are deleted manually since they have no foreign key constraint
     * to the account table.
     *
     * @param accountId The account ID to delete
     * @return true if account was found and deleted, false if not found
     * @throws IllegalArgumentException if attempting to delete the account with the only admin role for abstratium-abstrauth
     */
    @Transactional
    public boolean deleteAccountWithCascade(String accountId) {
        Optional<NonMultitenancyAccount> accountOpt = findById(accountId);
        if (accountOpt.isEmpty()) {
            return false;
        }

        // Prevent deletion if this account has the only admin role for abstratium-abstrauth
        if (hasTheOnlyAdminRoleForAbstrauthClient(accountId)) {
            throw new IllegalArgumentException("Cannot delete the account with the only admin role for " + Roles.CLIENT_ID);
        }

        // Delete authorization requests (no foreign key constraint to account)
        em.createQuery("SELECT ar FROM AuthorizationRequest ar WHERE ar.accountId = :accountId", AuthorizationRequest.class)
            .setParameter("accountId", accountId)
            .getResultList()
            .forEach(em::remove);

        NonMultitenancyAccount account = accountOpt.get();

        em.remove(account);
        return true;
    }

    /**
     * Check if this account has the only admin role for abstratium-abstrauth
     *
     * @param accountId The account ID to check
     * @return true if this account has the only admin role for abstratium-abstrauth
     */
    private boolean hasTheOnlyAdminRoleForAbstrauthClient(String accountId) {
        // Check if this account has the admin role for abstratium-abstrauth
        var accountRoleQuery = em.createQuery(
            "SELECT COUNT(ar) FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role",
            Long.class
        );
        accountRoleQuery.setParameter("accountId", accountId);
        accountRoleQuery.setParameter("clientId", Roles.CLIENT_ID);
        accountRoleQuery.setParameter("role", Roles._ADMIN_PLAIN);
        long accountHasAdminRole = accountRoleQuery.getSingleResult();

        if (accountHasAdminRole == 0) {
            return false; // This account doesn't have the admin role
        }

        // Count total admin roles for abstratium-abstrauth
        var totalAdminQuery = em.createQuery(
            "SELECT COUNT(ar) FROM NonMultitenancyAccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            Long.class
        );
        totalAdminQuery.setParameter("clientId", Roles.CLIENT_ID);
        totalAdminQuery.setParameter("role", Roles._ADMIN_PLAIN);
        long totalAdminRoles = totalAdminQuery.getSingleResult();

        return totalAdminRoles <= 1; // This is the only admin
    }
}
