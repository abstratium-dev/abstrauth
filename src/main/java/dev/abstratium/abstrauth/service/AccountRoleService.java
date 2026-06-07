package dev.abstratium.abstrauth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccountRole;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;

@ApplicationScoped
public class AccountRoleService {

    private static final Logger log = Logger.getLogger(AccountRoleService.class); 

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    SecurityProblemLogger securityProblemLogger;

    /**
     * Get all roles (groups) for a specific account and client combination
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @return Set of role names
     */
    public Set<String> findRolesByAccountIdAndClientId(String accountId, String clientId) {
        var query = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId",
            AccountRole.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        
        var roles = query.getResultStream()
            .map(AccountRole::getRole)
            .collect(Collectors.toSet());
        log.debugf("Found roles for accountId %s and clientId %s: %s", accountId, clientId, roles);
        return roles;
    }

    /**
     * Get all roles for an account across all clients
     * 
     * @param accountId The account ID
     * @return List of AccountRole entities
     */
    public List<AccountRole> findRolesByAccountId(String accountId) {
        var query = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId",
            AccountRole.class
        );
        query.setParameter("accountId", accountId);
        return query.getResultList();
    }

    /**
     * Get all clients for an account
     * 
     * @param accountId The account ID
     * @return List of OAuth client IDs
     */
    public List<String> findClientsByAccountId(String accountId) {
        var query = em.createQuery(
            "SELECT ar.clientId FROM AccountRole ar WHERE ar.accountId = :accountId",
            String.class
        );
        query.setParameter("accountId", accountId);
        return query.getResultList();
    }

    /**
     * Add a role to an account for a specific client
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param role The role name
     * @return The created AccountRole entity
     */
    @Transactional
    public AccountRole addRole(String accountId, String clientId, String role) {
        long accountCount = accountService.countAccounts();

        checkNonAdminCannotAddAdminRole(role, accountCount);

        // checkOnlyAddingToClientWhichTheyAlreadyHave(accountId, clientId, accountCount);

        // not right since user should be able to add to abstrauth: checkClientBelongsToCallerOrg(clientId, accountCount);

        // Validate role against allowlist for public clients
        checkRoleAgainstAllowlist(clientId, role);

        // Check if role already exists
        if (findRolesByAccountIdAndClientId(accountId, clientId).contains(role)) {
            throw new ConflictException("Role already exists");
        }

        AccountRole accountRole = new AccountRole();
        accountRole.setAccountId(accountId);
        accountRole.setClientId(clientId);
        accountRole.setRole(role);
        em.persist(accountRole);
        return accountRole;
    }

    /**
     * Verifies that the target OAuthClient belongs to the same organisation as the
     * caller's current org context (JWT orgId claim).  Skipped for the first account
     * (bootstrap) and when there is no active security context (plain unit tests).
     */
    public void checkClientBelongsToCallerOrg(String clientId, long accountCount) {
        if (accountCount == 1 || securityIdentity.isAnonymous()) {
            return;
        }
        String callerOrgId;
        try {
            callerOrgId = currentOrgContext.getOrgId();
        } catch (Exception e) {
            return;
        }
        if (callerOrgId == null || callerOrgId.isBlank()) {
            return;
        }
        // Use NonMultitenancyOAuthClient to bypass Hibernate's automatic tenant discriminator filter,
        // which would otherwise double-apply the org_id predicate and produce wrong results.
        var count = (Number) em.createQuery(
                "SELECT COUNT(*) FROM NonMultitenancyOAuthClient c WHERE c.clientId = ? AND c.orgId = ?", Long.class)
            .setParameter(1, clientId)
            .setParameter(2, callerOrgId)
            .getSingleResult();
        if (count.longValue() == 0) {
            securityProblemLogger.warnf("attempt to add role to an oauth-client that does not belong to the users organization");
            throw new ForbiddenException("Client not found in your organization");
        }
    }

    // TODO check if actually used and if not, then delete
    public void checkOnlyAddingToClientWhichTheyAlreadyHave(String accountId, String clientId, long accountCount) {
        // Allow if this is the first account (system initialization) or no security context (tests)
        if (accountCount == 1 || securityIdentity.isAnonymous()) {
            return;
        }
        
        // Get all existing roles for the account
        var query = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId",
            AccountRole.class
        );
        query.setParameter("accountId", accountId);
        List<AccountRole> accountRolesForAccount = query.getResultList();
        
        // Allow adding the first role to a new account (no existing roles)
        if (accountRolesForAccount.isEmpty()) {
            return;
        }
        
        // Only admin can add a user to a clientId for which they are not already a member of
        var uniqueClientIdsForAccount = accountRolesForAccount.stream()
            .map(AccountRole::getClientId)
            .collect(Collectors.toSet());
        if(!uniqueClientIdsForAccount.contains(clientId)) {
            if(!securityIdentity.hasRole(Roles.ADMIN)) {
                throw new IllegalArgumentException("Only admin can add roles to accounts that are not members of the client");
            }
        }
    }

    /**
     * only admin can add the admin role
     */
    public void checkNonAdminCannotAddAdminRole(String role, long accountCount) {
        if (role.equals(Roles._ADMIN_PLAIN)) {
            // Allow if this is the first account (system initialization) or no security context (tests)
            if (accountCount == 1 || securityIdentity.isAnonymous()) {
                return;
            }
            if (!securityIdentity.hasRole(Roles.ADMIN)) {
                throw new IllegalArgumentException("Only admin can add the admin role");
            }
        }
    }

    /**
     * Validate that the role is in the client's allowlist for public clients.
     * For private clients (no allowlist entries), any role is allowed.
     * Skipped during bootstrap (first account) and when there is no security context.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to validate
     * @throws IllegalArgumentException if the role is not in the allowlist for a public client
     */
    public void checkRoleAgainstAllowlist(String clientId, String role) {
        long accountCount = accountService.countAccounts();
        if (accountCount <= 1 || securityIdentity.isAnonymous()) {
            return;
        }
        if (!clientAllowedRoleService.isRoleAllowed(clientId, role)) {
            throw new IllegalArgumentException(
                "Role '" + role + "' is not in the allowlist for client '" + clientId + "'");
        }
    }

    /**
     * Remove a role from an account for a specific client
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param role The role name
     * @throws IllegalArgumentException if attempting to delete the last admin role for abstratium-abstrauth
     */
    @Transactional
    public void removeRole(String accountId, String clientId, String role) {
        // Prevent deletion of the last admin role for abstratium-abstrauth
        if (Roles.CLIENT_ID.equals(clientId) && Roles._ADMIN_PLAIN.equals(role)) {
            long adminCount = countAdminRolesForClient(Roles.CLIENT_ID);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last admin role for " + Roles.CLIENT_ID);
            }
        }

        em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role",
            AccountRole.class)
            .setParameter("accountId", accountId)
            .setParameter("clientId", clientId)
            .setParameter("role", role)
            .getResultStream()
            .forEach(ar -> em.remove(ar));
    }
    
    /**
     * Count the number of admin roles for a specific client
     * 
     * @param clientId The OAuth client ID
     * @return The count of admin roles
     */
    private long countAdminRolesForClient(String clientId) {
        var query = em.createQuery(
            "SELECT COUNT(ar) FROM AccountRole ar WHERE ar.clientId = :clientId AND ar.role = :role",
            Long.class
        );
        query.setParameter("clientId", clientId);
        query.setParameter("role", Roles._ADMIN_PLAIN);
        return query.getSingleResult();
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
}
