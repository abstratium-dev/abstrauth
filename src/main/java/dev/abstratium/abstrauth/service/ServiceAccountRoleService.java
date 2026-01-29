package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.boundary.ConflictException;
import dev.abstratium.abstrauth.entity.ServiceAccountRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ServiceAccountRoleService {

    @Inject
    EntityManager em;

    /**
     * Get all roles for a specific service client
     * 
     * @param clientId The OAuth client ID
     * @return Set of role names
     */
    public Set<String> findRolesByClientId(String clientId) {
        var query = em.createQuery(
            "SELECT sar FROM ServiceAccountRole sar WHERE sar.clientId = :clientId",
            ServiceAccountRole.class
        );
        query.setParameter("clientId", clientId);
        
        return query.getResultStream()
            .map(ServiceAccountRole::getRole)
            .collect(Collectors.toSet());
    }

    /**
     * Add a role to a service client
     * 
     * @param clientId The OAuth client ID
     * @param role The role name (without client_id prefix)
     */
    @Transactional
    public void addRole(String clientId, String role) {
        // Check if role already exists
        if (findRolesByClientId(clientId).contains(role)) {
            throw new ConflictException("Role already exists");
        }

        ServiceAccountRole serviceAccountRole = new ServiceAccountRole();
        serviceAccountRole.setClientId(clientId);
        serviceAccountRole.setRole(role);
        em.persist(serviceAccountRole);
    }

    /**
     * Remove a role from a service client
     * 
     * @param clientId The OAuth client ID
     * @param role The role name (without client_id prefix)
     */
    @Transactional
    public void removeRole(String clientId, String role) {
        em.createQuery(
            "DELETE FROM ServiceAccountRole sar WHERE sar.clientId = :clientId AND sar.role = :role")
            .setParameter("clientId", clientId)
            .setParameter("role", role)
            .executeUpdate();
    }

    /**
     * Remove all roles for a service client
     * 
     * @param clientId The OAuth client ID
     */
    @Transactional
    public void removeAllRoles(String clientId) {
        em.createQuery(
            "DELETE FROM ServiceAccountRole sar WHERE sar.clientId = :clientId")
            .setParameter("clientId", clientId)
            .executeUpdate();
    }
}
