package dev.abstratium.abstrauth.non_multitenancy.service;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientSecret;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;

/**
 * Non-multitenancy service for client secret operations.
 *
 * This service bypasses Hibernate's @TenantId discriminator to query client
 * secrets across all organizations. This is required for:
 * 1. Token endpoint client authentication - client secrets are owned by the
 *    client-owning org, not the user's org from the JWT
 * 2. Any cross-tenant client secret lookups
 *
 * Client secrets are tied to the organization that created the OAuth client,
 * and should be accessible for authentication regardless of which org the
 * current request's JWT resolves to.
 */
@ApplicationScoped
public class NonMultitenancyClientSecretService {

    @Inject
    EntityManager em;

    /**
     * Find all active AND non-expired secrets for a client.
     * Used during token endpoint authentication to check against all valid secrets.
     * A secret is valid if:
     * 1. It is active (not revoked)
     * 2. It has not expired (expiresAt is null OR expiresAt is in the future)
     *
     * This method bypasses the Hibernate tenant discriminator to find secrets
     * across all organizations, as client secrets are owned by the client-owning
     * org, not the user's org from the JWT context.
     *
     * @param clientId the client ID to find secrets for
     * @return list of active, non-expired secrets for the client
     */
    public List<NonMultitenancyClientSecret> findActiveSecrets(String clientId) {
        var query = em.createQuery(
            "SELECT cs FROM NonMultitenancyClientSecret cs " +
            "WHERE cs.clientId = :clientId " +
            "AND cs.active = true " +
            "AND (cs.expiresAt IS NULL OR cs.expiresAt > :now)",
            NonMultitenancyClientSecret.class);
        query.setParameter("clientId", clientId);
        query.setParameter("now", Instant.now());
        return query.getResultList();
    }
}
