package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Subscription;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class SubscriptionService {

    @Inject
    EntityManager em;

    @Transactional
    public Subscription subscribe(String orgId, String clientId) {
        if (subscriptionExists(orgId, clientId)) {
            throw new IllegalArgumentException("Organisation is already subscribed to this client");
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

    public Optional<Subscription> findSubscription(String orgId, String clientId) {
        return em.createQuery(
                "SELECT s FROM Subscription s WHERE s.orgId = :orgId AND s.clientId = :clientId",
                Subscription.class)
                .setParameter("orgId", orgId)
                .setParameter("clientId", clientId)
                .getResultStream()
                .findFirst();
    }

    /**
     * Ensures the org is subscribed to the client.
     * If not subscribed and autoSubscribe is true, creates the subscription automatically.
     * If not subscribed and autoSubscribe is false, throws {@link NoSubscriptionException}.
     */
    @Transactional
    public void ensureSubscribed(String orgId, String clientId, boolean autoSubscribe) {
        if (subscriptionExists(orgId, clientId)) {
            return;
        }
        if (autoSubscribe) {
            Subscription subscription = new Subscription();
            subscription.setOrgId(orgId);
            subscription.setClientId(clientId);
            em.persist(subscription);
        } else {
            throw new NoSubscriptionException(orgId, clientId);
        }
    }
}
