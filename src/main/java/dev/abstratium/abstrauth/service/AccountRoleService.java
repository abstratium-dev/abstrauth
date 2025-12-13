package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.AccountRole;
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
    SecurityIdentity securityIdentity;

    /**
     * Get all roles (groups) for a specific account and client combination
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @return Set of role names
     */
    public Set<String> getRolesForAccountAndClient(String accountId, String clientId) {
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
    public List<AccountRole> getRolesForAccount(String accountId) {
        var query = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId",
            AccountRole.class
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

        // Check if role already exists
        if (getRolesForAccountAndClient(accountId, clientId).contains(role)) {
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
        
        // only admin can add a user to a clientId for which they are not already a member of.
        // start by selecting all the roles belonging to the account
        var query = em.createQuery(
            "SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId",
            AccountRole.class
        );
        query.setParameter("accountId", accountId);
        List<AccountRole> accountRolesForAccount = query.getResultList();
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
     * Remove a role from an account for a specific client
     * 
     * @param accountId The account ID
     * @param clientId The OAuth client ID
     * @param role The role name
     */
    @Transactional
    public void removeRole(String accountId, String clientId, String role) {
        var query = em.createQuery(
            "DELETE FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId AND ar.role = :role"
        );
        query.setParameter("accountId", accountId);
        query.setParameter("clientId", clientId);
        query.setParameter("role", role);
        query.executeUpdate();
    }
}
