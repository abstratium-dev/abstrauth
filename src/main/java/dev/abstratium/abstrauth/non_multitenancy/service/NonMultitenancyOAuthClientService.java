package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import dev.abstratium.abstrauth.service.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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

    /**
     * Delete an OAuth client and all its related entities (account roles, client secrets,
     * client allowed roles, client roles, and subscriptions) across ALL organisations
     * using JPA cascade.
     *
     * This uses NonMultitenancyOAuthClient which has CascadeType.REMOVE on all
     * related collections, ensuring complete deletion regardless of org_id.
     *
     * @param clientId The client ID (not the internal UUID) to delete
     * @return true if client was found and deleted, false if not found
     */
    @Transactional
    public boolean deleteClientWithCascade(String clientId) {
        // Prevent deletion of the abstratium-abstrauth client
        if (Roles.CLIENT_ID.equals(clientId)) {
            throw new IllegalArgumentException("Cannot delete the " + Roles.CLIENT_ID + " client");
        }

        Optional<NonMultitenancyOAuthClient> clientOpt = findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return false;
        }

        NonMultitenancyOAuthClient client = clientOpt.get();

        // Load all cascade collections to ensure proper deletion order
        // Hibernate will handle the cascade deletion of related entities
        client.getAccountRoles().size();
        client.getClientSecrets().size();
        client.getClientAllowedRoles().size();
        client.getSrcClientRoles().size();
        client.getTargetClientRoles().size();
        client.getSubscriptions().size();

        em.remove(client);
        return true;
    }

}
