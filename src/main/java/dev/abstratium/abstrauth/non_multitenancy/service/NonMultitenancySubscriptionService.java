package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.Optional;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancySubscription;
import dev.abstratium.abstrauth.service.NoSubscriptionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NonMultitenancySubscriptionService {

    @Inject
    EntityManager em;

    public Optional<NonMultitenancySubscription> findNonMultitenancySubscription(String orgId, String clientId) {
        return em.createQuery(
                "SELECT s FROM NonMultitenancySubscription s WHERE s.orgId = :orgId AND s.clientId = :clientId",
                NonMultitenancySubscription.class)
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
        if (findNonMultitenancySubscription(orgId, clientId).isPresent()) {
            return;
        }
        if (autoSubscribe) {
            NonMultitenancySubscription subscription = new NonMultitenancySubscription();
            subscription.setOrgId(orgId);
            subscription.setClientId(clientId);
            em.persist(subscription);
        } else {
            throw new NoSubscriptionException(orgId, clientId);
        }
    }
}
