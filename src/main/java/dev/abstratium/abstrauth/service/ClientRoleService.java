package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.entity.ClientRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing client-to-client role assignments.
 * These roles allow a source client to call a target client with specific permissions.
 * The role must exist in the target client's allowed roles catalog.
 */
@ApplicationScoped
public class ClientRoleService {

    @Inject
    EntityManager em;

    @Inject
    ClientOwnershipVerifier ownershipVerifier;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    /**
     * Find all client roles for a given source client.
     * Results are scoped to the caller's organisation via Hibernate tenant filter.
     *
     * @param srcClientId The source client ID
     * @return List of client roles
     */
    public List<ClientRole> findBySrcClientId(String srcClientId) {
        return em.createQuery(
                "SELECT r FROM ClientRole r WHERE r.srcClientId = :srcClientId",
                ClientRole.class)
                .setParameter("srcClientId", srcClientId)
                .getResultList();
    }

    /**
     * Find all client roles where the given client is the target.
     * Results are scoped to the caller's organisation via Hibernate tenant filter.
     *
     * @param targetClientId The target client ID
     * @return List of client roles
     */
    public List<ClientRole> findByTargetClientId(String targetClientId) {
        return em.createQuery(
                "SELECT r FROM ClientRole r WHERE r.targetClientId = :targetClientId",
                ClientRole.class)
                .setParameter("targetClientId", targetClientId)
                .getResultList();
    }

    /**
     * Find a specific client role by source client, target client, and role name.
     *
     * @param srcClientId The source client ID
     * @param targetClientId The target client ID
     * @param role The role name
     * @return Optional containing the role if found
     */
    public Optional<ClientRole> findBySrcTargetAndRole(String srcClientId, String targetClientId, String role) {
        List<ClientRole> roles = em.createQuery(
                "SELECT r FROM ClientRole r WHERE r.srcClientId = :srcClientId AND r.targetClientId = :targetClientId AND r.role = :role",
                ClientRole.class)
                .setParameter("srcClientId", srcClientId)
                .setParameter("targetClientId", targetClientId)
                .setParameter("role", role)
                .getResultList();
        return roles.isEmpty() ? Optional.empty() : Optional.of(roles.get(0));
    }

    /**
     * Get all distinct target client IDs for a source client.
     *
     * @param srcClientId The source client ID
     * @return Set of target client IDs
     */
    public Set<String> findTargetClientIds(String srcClientId) {
        return findBySrcClientId(srcClientId).stream()
                .map(ClientRole::getTargetClientId)
                .collect(Collectors.toSet());
    }

    /**
     * Get all roles that a source client has for a specific target client.
     *
     * @param srcClientId The source client ID
     * @param targetClientId The target client ID
     * @return Set of role names
     */
    public Set<String> findRolesForTarget(String srcClientId, String targetClientId) {
        return em.createQuery(
                "SELECT r FROM ClientRole r WHERE r.srcClientId = :srcClientId AND r.targetClientId = :targetClientId",
                ClientRole.class)
                .setParameter("srcClientId", srcClientId)
                .setParameter("targetClientId", targetClientId)
                .getResultStream()
                .map(ClientRole::getRole)
                .collect(Collectors.toSet());
    }

    /**
     * Add a role to a source client for calling a target client.
     * Verifies that the caller owns the source client and that the role
     * exists in the target client's allowed roles catalog.
     *
     * @param srcClientId The source client ID (must be owned by caller's org)
     * @param targetClientId The target client ID
     * @param role The role name (must exist in target client's allowed roles)
     * @throws IllegalArgumentException if the source client is not owned by caller
     * @throws ConflictException if the role already exists or is not in target's catalog
     */
    @Transactional
    public void addRole(String srcClientId, String targetClientId, String role) {
        addRole(srcClientId, targetClientId, role, null);
    }

    /**
     * Add a role to a source client for calling a target client.
     * Verifies that the caller owns the source client and that the role
     * exists in the target client's allowed roles catalog and is available to the assigning org.
     *
     * @param srcClientId The source client ID (must be owned by caller's org)
     * @param targetClientId The target client ID
     * @param role The role name (must exist in target client's allowed roles)
     * @param assigningOrgId The org ID of the caller (for validating foreign org access, null means skip foreign org validation)
     * @throws IllegalArgumentException if the source client is not owned by caller or role is not available
     * @throws ConflictException if the role already exists or is not in target's catalog
     */
    @Transactional
    public void addRole(String srcClientId, String targetClientId, String role, String assigningOrgId) {
        // Verify the caller owns the source client
        ownershipVerifier.verifyClientOwnership(srcClientId);

        // Check if role already exists
        Optional<ClientRole> existing = findBySrcTargetAndRole(srcClientId, targetClientId, role);
        if (existing.isPresent()) {
            throw new ConflictException("Role already assigned to this client for the target");
        }

        // Verify the role exists in the target client's allowed roles and is available to the assigning org
        if (!clientAllowedRoleService.isRoleAllowed(targetClientId, role, assigningOrgId)) {
            throw new IllegalArgumentException("Role '" + role + "' is not in the target client's allowed roles catalog");
        }

        // Create the role assignment
        ClientRole clientRole = new ClientRole();
        clientRole.setSrcClientId(srcClientId);
        clientRole.setTargetClientId(targetClientId);
        clientRole.setRole(role);
        // orgId is set automatically by Hibernate tenant filter

        em.persist(clientRole);
    }

    /**
     * Remove a role from a source client.
     * Verifies that the caller owns the source client.
     *
     * @param srcClientId The source client ID
     * @param targetClientId The target client ID
     * @param role The role name
     * @throws IllegalArgumentException if the role is not found or caller doesn't own source client
     */
    @Transactional
    public void removeRole(String srcClientId, String targetClientId, String role) {
        // Verify the caller owns the source client
        ownershipVerifier.verifyClientOwnership(srcClientId);

        Optional<ClientRole> existing = findBySrcTargetAndRole(srcClientId, targetClientId, role);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Role not found for this client and target");
        }

        em.remove(existing.get());
    }
}
