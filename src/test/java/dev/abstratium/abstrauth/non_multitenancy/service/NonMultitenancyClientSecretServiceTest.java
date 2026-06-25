package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientSecret;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Tests for NonMultitenancyClientSecretService notification queries.
 */
@QuarkusTest
public class NonMultitenancyClientSecretServiceTest {

    @Inject
    NonMultitenancyClientSecretService clientSecretService;

    @Inject
    EntityManager em;

    private String clientId;

    @BeforeEach
    @Transactional
    public void setup() {
        clientId = "test-client-secret-service-" + System.currentTimeMillis();

        // Clean up any leftover test data across all tenants
        em.createQuery("DELETE FROM NonMultitenancyClientSecret WHERE clientId = :clientId").setParameter("clientId", clientId).executeUpdate();
        em.createQuery("DELETE FROM NonMultitenancyOAuthClient WHERE clientId = :clientId").setParameter("clientId", clientId).executeUpdate();

        NonMultitenancyOAuthClient client = new NonMultitenancyOAuthClient();
        client.setClientId(clientId);
        client.setClientName("Test Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);
    }

    @Test
    @Transactional
    public void testFindSecretsNeedingFirstWarning() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(15, ChronoUnit.DAYS);
        createSecret(expiry, null, null, null);

        List<NonMultitenancyClientSecretNotificationInfo> result = clientSecretService.findSecretsNeedingFirstWarning(now);
        assertTrue(result.stream().anyMatch(s -> s.clientId().equals(clientId)));
    }

    @Test
    @Transactional
    public void testFindSecretsNeedingFirstWarningExcludesWithinThreeDays() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(2, ChronoUnit.DAYS);
        createSecret(expiry, null, null, null);

        List<NonMultitenancyClientSecretNotificationInfo> result = clientSecretService.findSecretsNeedingFirstWarning(now);
        assertTrue(result.stream().noneMatch(s -> s.clientId().equals(clientId)));
    }

    @Test
    @Transactional
    public void testFindSecretsNeedingFirstWarningAlreadySent() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(15, ChronoUnit.DAYS);
        createSecret(expiry, now.minus(1, ChronoUnit.DAYS), null, null);

        List<NonMultitenancyClientSecretNotificationInfo> result = clientSecretService.findSecretsNeedingFirstWarning(now);
        assertTrue(result.stream().noneMatch(s -> s.clientId().equals(clientId)));
    }

    @Test
    @Transactional
    public void testFindSecretsNeedingFinalWarning() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(2, ChronoUnit.DAYS);
        createSecret(expiry, null, null, null);

        List<NonMultitenancyClientSecretNotificationInfo> result = clientSecretService.findSecretsNeedingFinalWarning(now);
        assertTrue(result.stream().anyMatch(s -> s.clientId().equals(clientId)));
    }

    @Test
    @Transactional
    public void testFindSecretsNeedingExpiredNotice() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.minus(1, ChronoUnit.DAYS);
        createSecret(expiry, null, null, null);

        List<NonMultitenancyClientSecretNotificationInfo> result = clientSecretService.findSecretsNeedingExpiredNotice(now);
        assertTrue(result.stream().anyMatch(s -> s.clientId().equals(clientId)));
    }

    @Test
    @Transactional
    public void testMarkNotificationSent() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(15, ChronoUnit.DAYS);
        NonMultitenancyClientSecret secret = createSecret(expiry, null, null, null);

        clientSecretService.markNotificationSent(secret.getId(), "first_warning_sent_at", now);
        em.clear();

        NonMultitenancyClientSecret updated = em.find(NonMultitenancyClientSecret.class, secret.getId());
        assertEquals(now, updated.getFirstWarningSentAt());
    }

    private NonMultitenancyClientSecret createSecret(Instant expiresAt, Instant firstWarningSentAt,
                                      Instant finalWarningSentAt, Instant expiredNoticeSentAt) {
        NonMultitenancyClientSecret secret = new NonMultitenancyClientSecret();
        secret.setClientId(clientId);
        secret.setSecretHash("$2a$10$dummyhash");
        secret.setDescription("Test secret");
        secret.setActive(true);
        secret.setExpiresAt(expiresAt);
        secret.setFirstWarningSentAt(firstWarningSentAt);
        secret.setFinalWarningSentAt(finalWarningSentAt);
        secret.setExpiredNoticeSentAt(expiredNoticeSentAt);
        em.persist(secret);
        em.flush();
        return secret;
    }
}
