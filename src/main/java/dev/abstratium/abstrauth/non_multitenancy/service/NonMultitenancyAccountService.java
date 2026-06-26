package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccount;
import dev.abstratium.abstrauth.service.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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

    @Inject
    NonMultitenancyOrganisationService nonMultitenancyOrganisationService;

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
     * Find all owner accounts for a given organisation ID across all tenants.
     * Uses NonMultitenancyOrganisationAccount to bypass the @TenantId discriminator.
     *
     * @param orgId The organisation ID
     * @return List of owner accounts (never null)
     */
    public List<NonMultitenancyAccount> findOwnersByOrgId(String orgId) {
        return em.createQuery(
                "SELECT a FROM NonMultitenancyAccount a " +
                "WHERE a.id IN (" +
                "  SELECT oa.id.accountId FROM NonMultitenancyOrganisationAccount oa " +
                "  WHERE oa.id.orgId = :orgId AND oa.id.role = :role" +
                ")",
                NonMultitenancyAccount.class)
                .setParameter("orgId", orgId)
                .setParameter("role", "owner")
                .getResultList();
    }

    /**
     * Delete an account and all its related entities (credentials, federated identities,
     * account roles, organisation accounts, and single-member organisations) across ALL
     * organisations using JPA cascade.
     *
     * This uses NonMultitenancyAccount which has CascadeType.REMOVE on all
     * related collections, ensuring complete deletion regardless of org_id.
     *
     * Single-member organisations are identified before the account is deleted and removed
     * afterwards, because the organisation membership rows are deleted by the account cascade.
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

        // Identify organisations where this account is the only member before deleting the account,
        // because the membership rows are removed by the account cascade.
        Set<String> singleMemberOrgIds = findSingleMemberOrganisations(accountId);

        // Prevent deletion if the account is the sole owner of any multi-member organisation.
        // The user must promote another member to owner first.
        ensureAccountIsNotSoleOwnerOfMultiMemberOrganisation(accountId);

        // Delete authorization requests (no foreign key constraint to account)
        em.createQuery("SELECT ar FROM AuthorizationRequest ar WHERE ar.accountId = :accountId", AuthorizationRequest.class)
            .setParameter("accountId", accountId)
            .getResultList()
            .forEach(em::remove);

        NonMultitenancyAccount account = accountOpt.get();

        em.remove(account);
        em.flush();

        // Delete organisations that only contained this account
        for (String orgId : singleMemberOrgIds) {
            nonMultitenancyOrganisationService.deleteOrganisationWithCascade(orgId);
        }

        return true;
    }

    /**
     * Prevent account deletion if the account is the sole owner of any organisation that
     * still has other members. In that case the user must promote another member to owner
     * before deleting the account. Single-member organisations are allowed to be deleted.
     *
     * @param accountId The account ID that is about to be deleted
     * @throws IllegalArgumentException if the account is the sole owner of a multi-member organisation
     */
    private void ensureAccountIsNotSoleOwnerOfMultiMemberOrganisation(String accountId) {
        List<String> soleOwnerMultiMemberOrgIds = em.createQuery(
                "SELECT oa.id.orgId FROM NonMultitenancyOrganisationAccount oa " +
                "WHERE oa.id.accountId = :accountId AND oa.id.role = :ownerRole " +
                "AND oa.id.orgId IN (" +
                "  SELECT oa2.id.orgId FROM NonMultitenancyOrganisationAccount oa2 " +
                "  WHERE oa2.id.role = :ownerRole " +
                "  GROUP BY oa2.id.orgId HAVING COUNT(DISTINCT oa2.id.accountId) = 1" +
                ") " +
                "AND oa.id.orgId IN (" +
                "  SELECT oa3.id.orgId FROM NonMultitenancyOrganisationAccount oa3 " +
                "  WHERE oa3.id.accountId <> :accountId " +
                "  GROUP BY oa3.id.orgId HAVING COUNT(DISTINCT oa3.id.accountId) >= 1" +
                ")",
                String.class)
                .setParameter("accountId", accountId)
                .setParameter("ownerRole", "owner")
                .getResultList();

        if (!soleOwnerMultiMemberOrgIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete the account because it is the sole owner of one or more organisations. " +
                    "Promote another member to owner before deleting the account.");
        }
    }

    /**
     * Find all organisation IDs where the given account is a member and the organisation
     * has no other members.
     *
     * @param accountId The account ID
     * @return Set of organisation IDs where the account is the sole member
     */
    private Set<String> findSingleMemberOrganisations(String accountId) {
        List<String> orgIds = em.createQuery(
                "SELECT oa.id.orgId FROM NonMultitenancyOrganisationAccount oa " +
                "WHERE oa.id.orgId IN (" +
                "  SELECT oa2.id.orgId FROM NonMultitenancyOrganisationAccount oa2 " +
                "  WHERE oa2.id.accountId = :accountId" +
                ") " +
                "GROUP BY oa.id.orgId " +
                "HAVING COUNT(DISTINCT oa.id.accountId) = 1",
                String.class)
            .setParameter("accountId", accountId)
            .getResultList();
        return orgIds.stream().collect(Collectors.toSet());
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
