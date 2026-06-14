package dev.abstratium.abstrauth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.AccountRole;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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
     * Validate that the role is in the client's allowlist.
     * All clients now require roles to be declared in the catalog.
     * Skipped during bootstrap (first account) and when there is no security context.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to validate
     * @throws IllegalArgumentException if the role is not in the allowlist
     */
    public void checkRoleAgainstAllowlist(String clientId, String role) {
        String assigningOrgId = currentOrgContext.getOrgId();
        checkRoleAgainstAllowlist(clientId, role, assigningOrgId);
    }

    /**
     * Validate that the role is in the client's allowlist for a specific assigning org.
     * All clients now require roles to be declared in the catalog.
     * Skipped during bootstrap (first account) and when there is no security context.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to validate
     * @param assigningOrgId The organisation attempting to assign the role
     * @throws IllegalArgumentException if the role is not in the allowlist for the assigning org
     */
    public void checkRoleAgainstAllowlist(String clientId, String role, String assigningOrgId) {
        long accountCount = accountService.countAccounts();
        if (accountCount <= 1 || securityIdentity.isAnonymous()) {
            return;
        }
        if (!clientAllowedRoleService.isRoleAllowed(clientId, role, assigningOrgId)) {
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
     * Seed default roles from ClientAllowedRoles to an account for a specific client and org.
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param defaultRoles List of default roles to seed
     */
    @Transactional
    public void seedDefaultRoles(String accountId, String clientId, List<ClientAllowedRole> defaultRoles) {
        var query = em.createQuery(
            "SELECT ar.role FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId",
            String.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        Set<String> existingRoles = query.getResultStream().collect(Collectors.toSet());

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
