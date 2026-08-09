package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Subscription;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOAuthClientService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class SubscriptionService {

    @Inject
    EntityManager em;

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Transactional
    public Subscription subscribe(String orgId, String clientId) {
        if (subscriptionExists(orgId, clientId)) {
            throw new IllegalArgumentException("Organisation is already subscribed to this client");
        }

        // Enforce publik isolation: a private client (publik=false) can only be
        // subscribed by its owning organisation. This mirrors the check in
        // NonMultitenancyAuthorizationService.checkSubscription and prevents a
        // non-owning org from self-subscribing to another org's private client.
        var client = nonMultitenancyOAuthClientService.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        boolean isPublik = Boolean.TRUE.equals(client.getPublik());
        if (!isPublik && !orgId.equals(client.getOrgId())) {
            throw new IllegalArgumentException(
                    "Organisation " + orgId + " cannot subscribe to private client " + clientId
                            + " owned by organisation " + client.getOrgId());
        }

        Subscription subscription = new Subscription();
        subscription.setOrgId(orgId);
        subscription.setClientId(clientId);
        em.persist(subscription);
        return subscription;
    }

    @Transactional
    public void unsubscribe(String orgId, String clientId) {
        Subscription subscription = findSubscription(orgId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation is not subscribed to this client"));
        em.remove(subscription);
    }

    public boolean subscriptionExists(String orgId, String clientId) {
        return findSubscription(orgId, clientId).isPresent();
    }

    public java.util.List<String> findClientIdsByOrgId(String orgId) {
        return em.createQuery(
                "SELECT s.clientId FROM Subscription s WHERE s.orgId = :orgId",
                String.class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    public Optional<Subscription> findSubscription(String orgId, String clientId) {
        return em.createQuery(
                "SELECT s FROM Subscription s WHERE s.orgId = :orgId AND s.clientId = :clientId",
                Subscription.class)
                .setParameter("orgId", orgId)
                .setParameter("clientId", clientId)
                .getResultStream()
                .findFirst();
    }
}
