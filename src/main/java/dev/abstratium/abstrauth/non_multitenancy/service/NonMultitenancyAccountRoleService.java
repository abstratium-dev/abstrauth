package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.boundary.ConflictException;
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

        // accountRoleService.checkOnlyAddingToClientWhichTheyAlreadyHave(accountId, clientId, accountCount);

        // TODO no point since orgId used within the following call is always the default: accountRoleService.checkClientBelongsToCallerOrg(clientId, accountCount);

        // Validate role against allowlist for public clients
        accountRoleService.checkRoleAgainstAllowlist(clientId, role);

        // Check if role already exists
        if (accountRoleService.findRolesByAccountIdAndClientId(accountId, clientId).contains(role)) {
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
}
