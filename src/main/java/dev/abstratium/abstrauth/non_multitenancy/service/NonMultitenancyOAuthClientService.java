package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class NonMultitenancyOAuthClientService {

    @Inject
    EntityManager em;

    /**
     * Returns clients matching the given clientIds, across all organisations.
     * Uses NonMultitenancyOAuthClient to bypass the @TenantId discriminator so that
     * clients owned by other orgs (e.g. subscribed public clients) are included.
     */
    public List<NonMultitenancyOAuthClient> findAllByClientIds(Set<String> clientIds) {
        return findByClientIds(clientIds);
    }

    /**
     * Find a single client by clientId across all organisations.
     * Uses NonMultitenancyOAuthClient to bypass the @TenantId discriminator.
     */
    public Optional<NonMultitenancyOAuthClient> findByClientId(String clientId) {
        return findByClientIds(Set.of(clientId)).stream().findFirst();
    }

    private List<NonMultitenancyOAuthClient> findByClientIds(Set<String> clientIds) {
        if (clientIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
            "SELECT c FROM NonMultitenancyOAuthClient c WHERE c.clientId IN :clientIds",
            NonMultitenancyOAuthClient.class)
            .setParameter("clientIds", clientIds)
            .getResultList();
    }

}
