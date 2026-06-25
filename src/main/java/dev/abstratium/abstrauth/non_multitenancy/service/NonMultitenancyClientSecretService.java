package dev.abstratium.abstrauth.non_multitenancy.service;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientSecret;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * Find active secrets that need a first expiration warning (within 30 days,
     * more than 3 days away, and first warning not yet sent).
     */
    public List<NonMultitenancyClientSecretNotificationInfo> findSecretsNeedingFirstWarning(Instant now) {
        Instant thirtyDays = now.plus(30, ChronoUnit.DAYS);
        Instant threeDays = now.plus(3, ChronoUnit.DAYS);
        String jpql = notificationInfoJpql("s.firstWarningSentAt IS NULL");
        var query = em.createQuery(jpql, NonMultitenancyClientSecretNotificationInfo.class);
        query.setParameter("upperBound", thirtyDays);
        query.setParameter("lowerBound", threeDays);
        return query.getResultList();
    }

    /**
     * Find active secrets that need a final expiration warning (within 3 days,
     * not yet expired, and final warning not yet sent).
     */
    public List<NonMultitenancyClientSecretNotificationInfo> findSecretsNeedingFinalWarning(Instant now) {
        Instant threeDays = now.plus(3, ChronoUnit.DAYS);
        String jpql = notificationInfoJpql("s.finalWarningSentAt IS NULL");
        var query = em.createQuery(jpql, NonMultitenancyClientSecretNotificationInfo.class);
        query.setParameter("upperBound", threeDays);
        query.setParameter("lowerBound", now);
        return query.getResultList();
    }

    /**
     * Find active secrets that need an expired notice (already expired and
     * expired notice not yet sent).
     */
    public List<NonMultitenancyClientSecretNotificationInfo> findSecretsNeedingExpiredNotice(Instant now) {
        String jpql = notificationInfoJpql("s.expiredNoticeSentAt IS NULL");
        var query = em.createQuery(jpql, NonMultitenancyClientSecretNotificationInfo.class);
        query.setParameter("upperBound", now);
        query.setParameter("lowerBound", Instant.EPOCH);
        return query.getResultList();
    }

    private String notificationInfoJpql(String notificationPredicate) {
        return "SELECT NEW " + NonMultitenancyClientSecretNotificationInfo.class.getName() +
               "(s.id, s.clientId, c.clientName, s.description, s.expiresAt, s.accountId, s.orgId) " +
               "FROM " + NonMultitenancyClientSecret.class.getName() + " s " +
               "JOIN " + NonMultitenancyOAuthClient.class.getName() + " c ON s.clientId = c.clientId " +
               "WHERE s.active = true " +
               "AND s.expiresAt IS NOT NULL " +
               "AND s.expiresAt <= :upperBound " +
               "AND s.expiresAt > :lowerBound " +
               "AND " + notificationPredicate;
    }

    /**
     * Mark a notification as sent for a secret.
     *
     * Loads the entity so the change is picked up by Hibernate Envers.
     */
    @Transactional
    public void markNotificationSent(Long secretId, String column, Instant sentAt) {
        NonMultitenancyClientSecret secret = em.find(NonMultitenancyClientSecret.class, secretId);
        if (secret == null) {
            throw new IllegalArgumentException("Client secret not found: " + secretId);
        }
        switch (column) {
            case "first_warning_sent_at" -> secret.setFirstWarningSentAt(sentAt);
            case "final_warning_sent_at" -> secret.setFinalWarningSentAt(sentAt);
            case "expired_notice_sent_at" -> secret.setExpiredNoticeSentAt(sentAt);
            default -> throw new IllegalArgumentException("Unknown notification column: " + column);
        }
        em.flush();
    }
}
