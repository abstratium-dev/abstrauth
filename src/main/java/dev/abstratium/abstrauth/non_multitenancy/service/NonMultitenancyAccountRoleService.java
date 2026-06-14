package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccountRole;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NonMultitenancyAccountRoleService {
    private static final Logger log = Logger.getLogger(NonMultitenancyAccountRoleService.class); 

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    /**
     * Add a role to an account for a specific client AND GIVEN ORG ID.
     * WARNING: this method DOES NOT automatically add the AccountRole to the
     * organisation found in JwtOrgResolver, rather it stores it in the GIVEN ORGANISATION.
     * 
     * TO BE USED IN circumstances like initial account creation where the orgId is unknown
     * at the time when Hibernate creates the session and adds the tenant id.
     * 
     * @param orgId The organisation Id to use
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param role The role name
     * @return The created AccountRole entity
     */
    @Transactional
    public NonMultitenancyAccountRole addRole(String orgId, String accountId, String clientId, String role) {
        long accountCount = accountService.countAccounts();
        accountRoleService.checkNonAdminCannotAddAdminRole(role, accountCount);

        // Validate role against allowlist for the assigning organisation
        accountRoleService.checkRoleAgainstAllowlist(clientId, role, orgId);

        // Check if role already exists
        if (nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(accountId, clientId, orgId).contains(role)) {
            throw new ConflictException("Role already exists");
        }

        NonMultitenancyAccountRole accountRole = new NonMultitenancyAccountRole();
        accountRole.setAccountId(accountId);
        accountRole.setClientId(clientId);
        accountRole.setRole(role);
        accountRole.setOrgId(orgId);
        em.persist(accountRole);
        return accountRole;
    }

    /**
     * Get all roles (groups) for a specific account and client and orgId combination.
     * IMPORTANT: ONLY TO BE USED IN CONTEXTS WHERE THE ORG ID IS NOT KNOWN AT THE TIME
     * WHEN HIBERNATE ADDS THE TENTANT ID TO THE SESSION. e.g. during signing in where 
     * the JWT has not yet been created.
     * 
     * @param orgId The org ID
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @return Set of role names
     */
    public Set<String> findRolesByAccountIdAndClientIdAndOrgId(String accountId, String clientId, String orgId) {
        var query = em.createQuery(
            "SELECT ar FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.orgId = :orgId",
            NonMultitenancyAccountRole.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        query.setParameter("orgId", orgId);
        
        var roles = query.getResultStream()
            .map(NonMultitenancyAccountRole::getRole)
            .collect(Collectors.toSet());
        log.debugf("Found roles for accountId %s and clientId %s: %s", accountId, clientId, roles);
        return roles;
    }

    /**
     * Check if account has any roles for the given client within a specific org.
     * Uses NonMultitenancyAccountRole to bypass the @TenantId discriminator and query
     * by explicit orgId, preventing false negatives when the Hibernate tenant context
     * does not match the orgId of existing rows.
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param orgId The organisation ID
     * @return true if account has at least one role for this client in this org
     */
    public boolean hasAnyRoleForClient(String accountId, String clientId, String orgId) {
        var query = em.createQuery(
            "SELECT COUNT(ar) FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.orgId = :orgId",
            Long.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        query.setParameter("orgId", orgId);
        return query.getSingleResult() > 0;
    }

    /**
     * Seed default roles from ClientAllowedRoles to an account for a specific client and org.
     * Uses NonMultitenancyAccountRole to bypass the @TenantId discriminator so that both
     * the existence check and the insert use the explicit orgId, preventing duplicate inserts.
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param orgId The organisation ID
     * @param defaultRoles List of default roles to seed
     */
    @Transactional
    public void seedDefaultRoles(String accountId, String clientId, String orgId, List<ClientAllowedRole> defaultRoles) {
        var query = em.createQuery(
            "SELECT ar.role FROM NonMultitenancyAccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.orgId = :orgId",
            String.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        query.setParameter("orgId", orgId);
        Set<String> existingRoles = query.getResultStream().collect(Collectors.toSet());

        for (ClientAllowedRole allowedRole : defaultRoles) {
            String roleName = allowedRole.getRole();
            if (!existingRoles.contains(roleName)) {
                NonMultitenancyAccountRole accountRole = new NonMultitenancyAccountRole();
                accountRole.setAccountId(accountId);
                accountRole.setClientId(clientId);
                accountRole.setRole(roleName);
                accountRole.setOrgId(orgId);
                em.persist(accountRole);
            }
        }
    }

    /**
     * Remove all AccountRole rows for a given client and role across ALL organisations.
     * Uses NonMultitenancyAccountRole to bypass the @TenantId discriminator.
     *
     * @param clientId The OAuth client ID
     * @param role The role name
     */
    @Transactional
    public void removeRolesForClientAndRole(String clientId, String role) {
        em.createQuery(
            "DELETE FROM NonMultitenancyAccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role")
            .setParameter("clientId", clientId)
            .setParameter("role", role)
            .executeUpdate();
    }

    /**
     * Remove all AccountRole rows for a given client and role from organisations
     * OTHER THAN the specified owning organisation.
     * Uses NonMultitenancyAccountRole to bypass the @TenantId discriminator.
     *
     * @param clientId The OAuth client ID
     * @param role The role name
     * @param owningOrgId The organisation ID to preserve roles in
     */
    @Transactional
    public void removeRolesForClientAndRoleOutsideOrg(String clientId, String role, String owningOrgId) {
        em.createQuery(
            "DELETE FROM NonMultitenancyAccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role AND ar.orgId != :owningOrgId")
            .setParameter("clientId", clientId)
            .setParameter("role", role)
            .setParameter("owningOrgId", owningOrgId)
            .executeUpdate();
    }
}
