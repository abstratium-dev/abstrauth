package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;

import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class NonMultitenancyOAuthClientService {

    private static final Logger log = Logger.getLogger(NonMultitenancyOAuthClientService.class);

    @Inject
    EntityManager em;

    /**
     * Returns clients matching the given clientIds, across all organisations.
     * Uses NonMultitenancyOAuthClient to bypass the @TenantId discriminator so that
     * clients owned by other orgs (e.g. subscribed public clients) are included.
     */
    public List<NonMultitenancyOAuthClient> findAllByClientIds(java.util.Set<String> clientIds) {
        if (clientIds.isEmpty()) {
            return java.util.List.of();
        }
        return em.createQuery(
            "SELECT c FROM NonMultitenancyOAuthClient c WHERE c.clientId IN :clientIds",
            NonMultitenancyOAuthClient.class)
            .setParameter("clientIds", clientIds)
            .getResultList();
    }

}
