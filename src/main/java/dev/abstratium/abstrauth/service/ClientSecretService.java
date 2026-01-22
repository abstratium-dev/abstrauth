package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.ClientSecret;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing client secrets.
 * Supports multiple active secrets per client for zero-downtime rotation.
 */
@ApplicationScoped
public class ClientSecretService {
    
    @Inject
    EntityManager em;
    
    /**
     * Find all active secrets for a client.
     * Used during authentication to check against all valid secrets.
     */
    public List<ClientSecret> findActiveSecrets(String clientId) {
        var query = em.createQuery(
            "SELECT cs FROM ClientSecret cs WHERE cs.clientId = :clientId AND cs.active = true", 
            ClientSecret.class);
        query.setParameter("clientId", clientId);
        return query.getResultList();
    }
    
    /**
     * Find all secrets (active and inactive) for a client.
     */
    public List<ClientSecret> findByClientId(String clientId) {
        var query = em.createQuery(
            "SELECT cs FROM ClientSecret cs WHERE cs.clientId = :clientId", 
            ClientSecret.class);
        query.setParameter("clientId", clientId);
        return query.getResultList();
    }
    
    /**
     * Count active secrets for a client.
     */
    public long countActiveSecrets(String clientId) {
        var query = em.createQuery(
            "SELECT COUNT(cs) FROM ClientSecret cs WHERE cs.clientId = :clientId AND cs.active = true", 
            Long.class);
        query.setParameter("clientId", clientId);
        return query.getSingleResult();
    }
    
    /**
     * Find all expired secrets that are still marked as active.
     * Used for cleanup jobs.
     */
    public List<ClientSecret> findExpiredSecrets(Instant now) {
        var query = em.createQuery(
            "SELECT cs FROM ClientSecret cs WHERE cs.expiresAt IS NOT NULL AND cs.expiresAt < :now AND cs.active = true", 
            ClientSecret.class);
        query.setParameter("now", now);
        return query.getResultList();
    }
    
    /**
     * Persist a new client secret.
     */
    @Transactional
    public void persist(ClientSecret clientSecret) {
        em.persist(clientSecret);
    }
    
    /**
     * Find a secret by ID.
     */
    public ClientSecret findById(Long id) {
        return em.find(ClientSecret.class, id);
    }
    
    /**
     * Deactivate a secret.
     */
    @Transactional
    public void deactivate(Long secretId) {
        ClientSecret secret = findById(secretId);
        if (secret != null) {
            secret.setActive(false);
            em.merge(secret);
        }
    }
}
