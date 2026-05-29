package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AccountRoleService {

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    SecurityIdentity securityIdentity;

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
        
        return query.getResultStream()
            .map(AccountRole::getRole)
            .collect(Collectors.toSet());
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
        checkNonAdminCannotAddAdminRole(role);

        checkOnlyAddingToClientWhichTheyAlreadyHave(accountId, clientId);

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

    private void checkOnlyAddingToClientWhichTheyAlreadyHave(String accountId, String clientId) {
        // Allow if this is the first account (system initialization) or no security context (tests)
        long accountCount = accountService.countAccounts();
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
    private void checkNonAdminCannotAddAdminRole(String role) {
        if (role.equals(Roles._ADMIN_PLAIN)) {
            // Allow if this is the first account (system initialization) or no security context (tests)
            long accountCount = accountService.countAccounts();
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
     *
     * @param clientId The OAuth client ID
     * @param role The role name to validate
     * @throws IllegalArgumentException if the role is not in the allowlist for a public client
     */
    private void checkRoleAgainstAllowlist(String clientId, String role) {
        // Skip validation for internal abstrauth client - it manages its own roles
        if (Roles.CLIENT_ID.equals(clientId)) {
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
     * Check if account has any roles for the given client.
     * Used to determine if default roles should be seeded.
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @return true if account has at least one role for this client
     */
    public boolean hasAnyRoleForClient(String accountId, String clientId) {
        var query = em.createQuery(
            "SELECT COUNT(ar) FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId",
            Long.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        return query.getSingleResult() > 0;
    }

    /**
     * Seed default roles from ClientAllowedRoles to an account for a specific client.
     * Only adds roles that don't already exist for the account + client combination.
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param defaultRoles List of default roles to seed
     */
    @Transactional
    public void seedDefaultRoles(String accountId, String clientId, List<ClientAllowedRole> defaultRoles) {
        Set<String> existingRoles = findRolesByAccountIdAndClientId(accountId, clientId);
        
        for (ClientAllowedRole allowedRole : defaultRoles) {
            String roleName = allowedRole.getRole();
            if (!existingRoles.contains(roleName)) {
                AccountRole accountRole = new AccountRole();
                accountRole.setAccountId(accountId);
                accountRole.setClientId(clientId);
                accountRole.setRole(roleName);
                em.persist(accountRole);
            }
        }
    }
}
