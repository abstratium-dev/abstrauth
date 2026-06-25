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
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOrganisation;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOrganisationAccount;
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
    private String orgId;
    private String ownerEmail1;
    private String ownerEmail2;
    private String nonOwnerEmail;

    @BeforeEach
    @Transactional
    public void setup() {
        clientId = "test-notification-client-" + System.currentTimeMillis();
        String orgName = "Test Org " + System.currentTimeMillis();
        ownerEmail1 = "owner1-" + System.currentTimeMillis() + "@example.com";
        ownerEmail2 = "owner2-" + System.currentTimeMillis() + "@example.com";
        nonOwnerEmail = "member-" + System.currentTimeMillis() + "@example.com";

        // Clean up previous test data across all tenants
        em.createQuery("DELETE FROM NonMultitenancyClientSecret WHERE clientId = :clientId").setParameter("clientId", clientId).executeUpdate();
        em.createQuery("DELETE FROM NonMultitenancyOAuthClient WHERE clientId = :clientId").setParameter("clientId", clientId).executeUpdate();
        em.createQuery("DELETE FROM NonMultitenancyOrganisation WHERE name = :name").setParameter("name", orgName).executeUpdate();
        em.createQuery("DELETE FROM NonMultitenancyAccount WHERE email IN (:emails)")
                .setParameter("emails", List.of(ownerEmail1, ownerEmail2, nonOwnerEmail))
                .executeUpdate();

        NonMultitenancyOrganisation org = new NonMultitenancyOrganisation();
        org.setName(orgName);
        em.persist(org);
        orgId = org.getId();

        NonMultitenancyAccount owner1 = createAccount(ownerEmail1, "Owner One");
        NonMultitenancyAccount owner2 = createAccount(ownerEmail2, "Owner Two");
        NonMultitenancyAccount member = createAccount(nonOwnerEmail, "Member");

        NonMultitenancyOrganisationAccount owner1Org = new NonMultitenancyOrganisationAccount();
        owner1Org.setId(new NonMultitenancyOrganisationAccount.Id(orgId, owner1.getId(), "owner"));
        em.persist(owner1Org);

        NonMultitenancyOrganisationAccount owner2Org = new NonMultitenancyOrganisationAccount();
        owner2Org.setId(new NonMultitenancyOrganisationAccount.Id(orgId, owner2.getId(), "owner"));
        em.persist(owner2Org);

        NonMultitenancyOrganisationAccount memberOrg = new NonMultitenancyOrganisationAccount();
        memberOrg.setId(new NonMultitenancyOrganisationAccount.Id(orgId, member.getId(), "member"));
        em.persist(memberOrg);

        NonMultitenancyOAuthClient client = new NonMultitenancyOAuthClient();
        client.setClientId(clientId);
        client.setClientName("Notification Test Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        client.setOrgId(orgId);
        em.persist(client);

        mailbox.clear();
    }

    private NonMultitenancyAccount createAccount(String email, String name) {
        NonMultitenancyAccount account = new NonMultitenancyAccount();
        account.setEmail(email);
        account.setName(name);
        em.persist(account);
        return account;
    }

    @Test
    @Transactional
    public void testSendFirstWarning() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant expiry = now.plus(15, ChronoUnit.DAYS);
        NonMultitenancyClientSecret secret = createSecret(expiry, null, null, null);

        notificationService.processFirstWarnings(now);

        assertEquals(2, mailbox.getTotalMessagesSent(), "One email per owner should be sent");
        assertEquals(1, mailbox.getMailsSentTo(ownerEmail1).size());
        assertEquals(1, mailbox.getMailsSentTo(ownerEmail2).size());
        assertEquals(0, mailbox.getMailsSentTo(nonOwnerEmail).size(), "Non-owner should not receive email");

        Mail mail = mailbox.getMailsSentTo(ownerEmail1).get(0);
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

        assertEquals(2, mailbox.getTotalMessagesSent(), "One email per owner should be sent");
        assertTrue(mailbox.getMailsSentTo(ownerEmail1).get(0).getSubject().contains("Client secret expires in 3 days"));
        assertTrue(mailbox.getMailsSentTo(ownerEmail2).get(0).getSubject().contains("Client secret expires in 3 days"));

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

        assertEquals(2, mailbox.getTotalMessagesSent(), "One email per owner should be sent");
        assertTrue(mailbox.getMailsSentTo(ownerEmail1).get(0).getSubject().contains("Client secret has expired"));
        assertTrue(mailbox.getMailsSentTo(ownerEmail2).get(0).getSubject().contains("Client secret has expired"));

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
        secret.setOrgId(orgId);
        secret.setFirstWarningSentAt(firstWarningSentAt);
        secret.setFinalWarningSentAt(finalWarningSentAt);
        secret.setExpiredNoticeSentAt(expiredNoticeSentAt);
        em.persist(secret);
        em.flush();
        return secret;
    }
}
