package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import io.quarkus.oidc.IdToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class ClientAllowedRoleService {

    @Inject
    EntityManager em;

    @Inject
    @IdToken
    JsonWebToken token;

    @Inject
    OAuthClientService oauthClientService;

    public List<ClientAllowedRole> findByClientId(String clientId) {
        return em.createQuery(
                "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId",
                ClientAllowedRole.class)
                .setParameter("clientId", clientId)
                .getResultList();
    }

    /**
     * Find all default roles for a client (where is_default = true).
     * These roles are seeded to new accounts during sign-in.
     */
    public List<ClientAllowedRole> findDefaultRolesByClientId(String clientId) {
        return em.createQuery(
                "SELECT r FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.isDefault = true",
                ClientAllowedRole.class)
                .setParameter("clientId", clientId)
                .getResultList();
    }

    /**
     * Check if a role is in the allowlist for a client.
     * Returns true if the client has no allowlist entries (private client) or if the role is in the allowlist.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to check
     * @return true if the role is allowed for this client
     */
    public boolean isRoleAllowed(String clientId, String role) {
        // Check if client has any allowlist entries
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ClientAllowedRole r WHERE r.id.clientId = :clientId",
                Long.class)
                .setParameter("clientId", clientId)
                .getSingleResult();

        // If no allowlist entries exist, client is private - any role is allowed
        if (count == 0) {
            return true;
        }

        // Check if the specific role is in the allowlist
        Long roleCount = em.createQuery(
                "SELECT COUNT(r) FROM ClientAllowedRole r WHERE r.id.clientId = :clientId AND r.id.role = :role",
                Long.class)
                .setParameter("clientId", clientId)
                .setParameter("role", role)
                .getSingleResult();

        return roleCount > 0;
    }

    /**
     * Check if a client has any allowlist entries (i.e., is a public client).
     *
     * @param clientId The OAuth client ID
     * @return true if the client has allowlist entries
     */
    public boolean hasAllowlist(String clientId) {
        Long count = em.createQuery(
                "SELECT COUNT(r) FROM ClientAllowedRole r WHERE r.id.clientId = :clientId",
                Long.class)
                .setParameter("clientId", clientId)
                .getSingleResult();
        return count > 0;
    }

    /**
     * Add a role to the allowlist for a client.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to add
     * @param isDefault Whether this role is assigned by default to new users
     * @throws ConflictException if the role already exists in the allowlist
     */
    @Transactional
    public void addAllowedRole(String clientId, String role, boolean isDefault) {
        verifyClientOwnership(clientId);

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
        em.persist(allowedRole);
    }

    /**
     * Remove a role from the allowlist for a client.
     * Uses per-row removal (no bulk DELETE) for Envers compatibility.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to remove
     * @throws IllegalArgumentException if the role is not found in the allowlist
     */
    @Transactional
    public void removeAllowedRole(String clientId, String role) {
        verifyClientOwnership(clientId);

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
    }

    /**
     * Update the is_default flag for a role in the allowlist.
     * Uses per-row update (no bulk UPDATE) for Envers compatibility.
     *
     * @param clientId The OAuth client ID
     * @param role The role name to update
     * @param isDefault The new default value
     * @throws IllegalArgumentException if the role is not found in the allowlist
     */
    @Transactional
    public void updateAllowedRole(String clientId, String role, boolean isDefault) {
        verifyClientOwnership(clientId);

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
        allowedRole.setIsDefault(isDefault);
    }

    /**
     * Verify that the client belongs to the organisation in the caller's JWT token.
     * This is defense-in-depth: the Hibernate tenant filter already scopes
     * OAuthClient queries, but ClientAllowedRole has no @TenantId so we
     * explicitly check ownership before mutating allowlist entries.
     *
     * @param clientId The OAuth client ID to verify
     * @throws IllegalArgumentException if the client is not found or does not belong to the caller's org
     */
    private void verifyClientOwnership(String clientId) {
        if (token == null) {
            // No JWT context (e.g. internal calls or some test paths);
            // rely on the Hibernate tenant filter for OAuthClient queries.
            return;
        }
        Object orgIdClaim = token.getClaim("orgId");
        if (orgIdClaim == null) {
            throw new IllegalArgumentException("Client not found");
        }
        String orgId = orgIdClaim.toString();

        var clientOpt = oauthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found");
        }
        if (!orgId.equals(clientOpt.get().getOrgId())) {
            throw new IllegalArgumentException("Client not found");
        }
    }
}
