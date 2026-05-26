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
}
