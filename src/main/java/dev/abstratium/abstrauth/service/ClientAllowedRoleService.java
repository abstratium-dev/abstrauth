package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class ClientAllowedRoleService {

    @Inject
    EntityManager em;

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
}
