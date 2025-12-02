package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.FederatedIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class FederatedIdentityService {

    private final EntityManager entityManager;

    public FederatedIdentityService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Find a federated identity by provider and provider user ID
     */
    public Optional<FederatedIdentity> findByProviderAndUserId(String provider, String providerUserId) {
        try {
            FederatedIdentity identity = entityManager.createQuery(
                    "SELECT f FROM FederatedIdentity f WHERE f.provider = :provider AND f.providerUserId = :providerUserId",
                    FederatedIdentity.class)
                    .setParameter("provider", provider)
                    .setParameter("providerUserId", providerUserId)
                    .getSingleResult();
            return Optional.of(identity);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Create a new federated identity link
     */
    @Transactional
    public FederatedIdentity createFederatedIdentity(String accountId, String provider, 
                                                     String providerUserId, String email) {
        FederatedIdentity identity = new FederatedIdentity();
        identity.setAccountId(accountId);
        identity.setProvider(provider);
        identity.setProviderUserId(providerUserId);
        identity.setEmail(email);
        entityManager.persist(identity);
        return identity;
    }
}
