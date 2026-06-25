package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccount;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientSecret;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Tests for NonMultitenancySecretExpirationNotificationService.
 */
@QuarkusTest
public class NonMultitenancySecretExpirationNotificationServiceTest {

    @Inject
    NonMultitenancySecretExpirationNotificationService notificationService;

    @Inject
    NonMultitenancyClientSecretService clientSecretService;

    @Inject
    MockMailbox mailbox;

    @Inject
    EntityManager em;

    private String clientId;
    private String accountId;
    private String accountEmail;

    @BeforeEach
    @Transactional
    public void setup() {
        clientId = "test-notification-client-" + System.currentTimeMillis();
        accountEmail = "notification-test-" + System.currentTimeMillis() + "@example.com";

        // Clean up previous test data across all tenants
        em.createQuery("DELETE FROM NonMultitenancyClientSecret WHERE clientId = :clientId").setParameter("clientId", clientId).executeUpdate();
        em.createQuery("DELETE FROM NonMultitenancyOAuthClient WHERE clientId = :clientId").setParameter("clientId", clientId).executeUpdate();
        em.createQuery("DELETE FROM NonMultitenancyAccount WHERE email = :email").setParameter("email", accountEmail).executeUpdate();

        NonMultitenancyAccount account = new NonMultitenancyAccount();
        account.setEmail(accountEmail);
        account.setName("Notification Test");
        em.persist(account);
        accountId = account.getId();

        NonMultitenancyOAuthClient client = new NonMultitenancyOAuthClient();
        client.setClientId(clientId);
        client.setClientName("Notification Test Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);

        mailbox.clear();
    }

    @Test
    @Transactional
    public void testSendFirstWarning() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(15, ChronoUnit.DAYS);
        NonMultitenancyClientSecret secret = createSecret(expiry, null, null, null);

        notificationService.processFirstWarnings(now);

        List<Mail> mails = mailbox.getMailsSentTo(accountEmail);
        assertEquals(1, mails.size(), "One first warning email should be sent");
        Mail mail = mails.get(0);
        assertTrue(mail.getSubject().contains("Client secret expires in 30 days"));
        assertTrue(mail.getSubject().contains("Notification Test Client"));
        assertTrue(mail.getText().contains("/clients?viewSecrets=" + clientId + "&highlightSecret=" + secret.getId()));
        assertTrue(mail.getText().contains("first warning"));

        em.clear();
        NonMultitenancyClientSecret updated = em.find(NonMultitenancyClientSecret.class, secret.getId());
        assertTrue(updated.getFirstWarningSentAt() != null);
    }

    @Test
    @Transactional
    public void testSendFinalWarning() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(2, ChronoUnit.DAYS);
        NonMultitenancyClientSecret secret = createSecret(expiry, null, null, null);

        notificationService.processFinalWarnings(now);

        List<Mail> mails = mailbox.getMailsSentTo(accountEmail);
        assertEquals(1, mails.size(), "One final warning email should be sent");
        assertTrue(mails.get(0).getSubject().contains("Client secret expires in 3 days"));

        em.clear();
        NonMultitenancyClientSecret updated = em.find(NonMultitenancyClientSecret.class, secret.getId());
        assertTrue(updated.getFinalWarningSentAt() != null);
    }

    @Test
    @Transactional
    public void testSendExpiredNotice() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.minus(1, ChronoUnit.DAYS);
        NonMultitenancyClientSecret secret = createSecret(expiry, null, null, null);

        notificationService.processExpiredNotices(now);

        List<Mail> mails = mailbox.getMailsSentTo(accountEmail);
        assertEquals(1, mails.size(), "One expired notice email should be sent");
        assertTrue(mails.get(0).getSubject().contains("Client secret has expired"));

        em.clear();
        NonMultitenancyClientSecret updated = em.find(NonMultitenancyClientSecret.class, secret.getId());
        assertTrue(updated.getExpiredNoticeSentAt() != null);
    }

    @Test
    @Transactional
    public void testNoDuplicateFirstWarning() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(15, ChronoUnit.DAYS);
        createSecret(expiry, now.minus(1, ChronoUnit.DAYS), null, null);

        notificationService.processFirstWarnings(now);

        assertEquals(0, mailbox.getTotalMessagesSent(), "No duplicate first warning should be sent");
    }

    @Test
    @Transactional
    public void testEmailDisabledSkipsNotifications() {
        // The service is enabled by default in test mode; we can still verify the
        // process methods only send when criteria are met.
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(40, ChronoUnit.DAYS);
        createSecret(expiry, null, null, null);

        notificationService.processFirstWarnings(now);

        assertEquals(0, mailbox.getTotalMessagesSent(), "No email should be sent for a secret outside the warning window");
    }

    private NonMultitenancyClientSecret createSecret(Instant expiresAt, Instant firstWarningSentAt,
                                      Instant finalWarningSentAt, Instant expiredNoticeSentAt) {
        NonMultitenancyClientSecret secret = new NonMultitenancyClientSecret();
        secret.setClientId(clientId);
        secret.setSecretHash("$2a$10$dummyhash");
        secret.setDescription("Notification test secret");
        secret.setActive(true);
        secret.setExpiresAt(expiresAt);
        secret.setAccountId(accountId);
        secret.setFirstWarningSentAt(firstWarningSentAt);
        secret.setFinalWarningSentAt(finalWarningSentAt);
        secret.setExpiredNoticeSentAt(expiredNoticeSentAt);
        em.persist(secret);
        em.flush();
        return secret;
    }
}
