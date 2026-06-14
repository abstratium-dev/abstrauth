package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOAuthClientService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ClientAllowedRoleService {

    @Inject
    EntityManager em;

    @Inject
    ClientOwnershipVerifier ownershipVerifier;

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    /** 
     * Returns ALL ClientAllowedRoles, for the given clientId. Note that 
     * not all should be visible by other organisations!! This interface is
     * designed for use by users who are in the same org as the client.
     * ClientAllowedRole is not paritioned by @TenantId annotations and 
     * only implicitly belongs to the organisation of the clientId for which
     * the role exists. NEVER USE THIS METHOD FOR USERS OUTSIDE OF THE ORG
     * THAT OWNS THE CLIENT. 
     */
    public List<ClientAllowedRole> findAllAllowedRolesByClientId(String clientId) {
        return em.createQuery(
                "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId",
                ClientAllowedRole.class)
                .setParameter("clientId", clientId)
                .getResultList();
    }

    /**
     * Find ALL default roles for a client (where is_default = true).
     * These roles are seeded to new accounts during sign-in.
     * Does NOT filter by org — callers that need org-aware filtering should use.
     * Note that 
     * not all should be visible by other organisations!! This interface is
     * designed for use by users who are in the same org as the client.
     * ClientAllowedRole is not paritioned by @TenantId annotations and 
     * only implicitly belongs to the organisation of the clientId for which
     * the role exists. NEVER USE THIS METHOD FOR USERS OUTSIDE OF THE ORG
     * THAT OWNS THE CLIENT. 

     * {@link #findDefaultRolesByClientIdForOrg(String, String)}.
     */
    public List<ClientAllowedRole> findDefaultRolesByClientId(String clientId) {
        return em.createQuery(
                "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.isDefault = true",
                ClientAllowedRole.class)
                .setParameter("clientId", clientId)
                .getResultList();
    }

    /**
     * Find default roles for a client that are applicable to the given target organisation.
     * If the target org is the client owner, all default roles are returned.
     * If the target org is foreign, only default roles with availableToForeignOrgs=true are returned.
     *
     * @param clientId The OAuth client ID
     * @param targetOrgId The organisation that the account belongs to
     * @return List of applicable default roles
     */
    public List<ClientAllowedRole> findDefaultRolesByClientIdForOrg(String clientId, String targetOrgId) {
        var clientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
        boolean isOwnOrg = clientOpt.isPresent() && targetOrgId.equals(clientOpt.get().getOrgId());

        if (isOwnOrg) {
            return em.createQuery(
                    "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.isDefault = true",
                    ClientAllowedRole.class)
                    .setParameter("clientId", clientId)
                    .getResultList();
        } else {
            return em.createQuery(
                    "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.isDefault = true AND r.availableToForeignOrgs = true",
                    ClientAllowedRole.class)
                    .setParameter("clientId", clientId)
                    .getResultList();
        }
    }

    /**
     * Find all allowed roles for a client that are visible to the given calling organisation.
     * If the caller's org is the client owner, all roles are returned.
     * If the caller's org is foreign (subscribed), only roles with availableToForeignOrgs=true are returned.
     *
     * @param clientId The OAuth client ID
     * @param callerOrgId The organisation attempting to view/assign roles
     * @return List of roles visible to the caller's organisation
     */
    public List<ClientAllowedRole> findAllowedRolesByClientIdForOrg(String clientId, String callerOrgId) {
        var clientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
        boolean isOwnOrg = clientOpt.isPresent() && callerOrgId.equals(clientOpt.get().getOrgId());

        if (isOwnOrg) {
            // Client owner can see all roles in their catalog
            return em.createQuery(
                    "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId",
                    ClientAllowedRole.class)
                    .setParameter("clientId", clientId)
                    .getResultList();
        } else {
            // Foreign orgs can only see roles marked as available to them
            return em.createQuery(
                    "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.availableToForeignOrgs = true",
                    ClientAllowedRole.class)
                    .setParameter("clientId", clientId)
                    .getResultList();
        }
    }

    /**
     * Check if a role is allowed for a client when being assigned by a specific organisation.
     * The role must exist in the client's catalog. If the assigning org is the client owner,
     * any catalog role is allowed. If the assigning org is foreign, the role must have
     * availableToForeignOrgs = true.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to check
     * @param assigningOrgId The organisation attempting to assign the role
     * @return true if the role is allowed for this client and assigning org
     */
    public boolean isRoleAllowed(String clientId, String role, String assigningOrgId) {
        // Load the role from the catalog (single-row PK lookup; may hit second-level cache)
        ClientAllowedRole allowedRole = em.find(
                ClientAllowedRole.class,
                new ClientAllowedRole.Id(clientId, role));
        if (allowedRole == null) {
            return false;
        }

        // Client owner may assign any role in its own catalog
        var clientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
        if (clientOpt.isPresent() && assigningOrgId.equals(clientOpt.get().getOrgId())) {
            return true;
        }

        // Foreign orgs may only assign roles marked as available to them
        return Boolean.TRUE.equals(allowedRole.getAvailableToForeignOrgs());
    }

    /**
     * Add a role to the allowlist for a client.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to add
     * @param isDefault Whether this role is assigned by default to new users
     * @param availableToForeignOrgs Whether foreign organisations may assign this role
     * @throws ConflictException if the role already exists in the allowlist
     */
    @Transactional
    public void addAllowedRole(String clientId, String role, boolean isDefault, boolean availableToForeignOrgs) {
        ownershipVerifier.verifyClientOwnership(clientId);

        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.id.role = :role",
                Long.class)
                .setParameter("clientId", clientId)
                .setParameter("role", role)
                .getSingleResult();

        if (count > 0) {
            throw new ConflictException("Role already exists in allowlist");
        }

        ClientAllowedRole allowedRole = new ClientAllowedRole();
        allowedRole.setClientId(clientId);
        allowedRole.setRole(role);
        allowedRole.setIsDefault(isDefault);
        allowedRole.setAvailableToForeignOrgs(availableToForeignOrgs);
        em.persist(allowedRole);
    }

    /**
     * Remove a role from the allowlist for a client.
     * Also removes the role from ALL users across ALL organisations.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to remove
     * @throws IllegalArgumentException if the role is not found in the allowlist
     */
    @Transactional
    public void removeAllowedRole(String clientId, String role) {
        ownershipVerifier.verifyClientOwnership(clientId);

        List<ClientAllowedRole> roles = em.createQuery(
                "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.id.role = :role",
                ClientAllowedRole.class)
                .setParameter("clientId", clientId)
                .setParameter("role", role)
                .getResultList();

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Role not found in allowlist");
        }

        em.remove(roles.get(0));

        // Cascade: remove this role from all users in all organisations
        nonMultitenancyAccountRoleService.removeRolesForClientAndRole(clientId, role);
    }

    /**
     * Update a role in the allowlist.
     * If availableToForeignOrgs is changed from true to false, the role is
     * automatically removed from all users in foreign organisations.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to update
     * @param isDefault The new default value
     * @param availableToForeignOrgs The new foreign-availability value
     * @throws IllegalArgumentException if the role is not found in the allowlist
     */
    @Transactional
    public void updateAllowedRole(String clientId, String role, boolean isDefault, boolean availableToForeignOrgs) {
        ownershipVerifier.verifyClientOwnership(clientId);

        List<ClientAllowedRole> roles = em.createQuery(
                "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.id.role = :role",
                ClientAllowedRole.class)
                .setParameter("clientId", clientId)
                .setParameter("role", role)
                .getResultList();

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Role not found in allowlist");
        }

        ClientAllowedRole allowedRole = roles.get(0);

        boolean wasAvailable = Boolean.TRUE.equals(allowedRole.getAvailableToForeignOrgs());
        if (wasAvailable && !availableToForeignOrgs) {
            // Role is being retracted from foreign orgs — remove assignments outside the owning org
            var clientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
            if (clientOpt.isPresent()) {
                String owningOrgId = clientOpt.get().getOrgId();
                nonMultitenancyAccountRoleService.removeRolesForClientAndRoleOutsideOrg(clientId, role, owningOrgId);
            }
        }

        allowedRole.setIsDefault(isDefault);
        allowedRole.setAvailableToForeignOrgs(availableToForeignOrgs);
    }

}
