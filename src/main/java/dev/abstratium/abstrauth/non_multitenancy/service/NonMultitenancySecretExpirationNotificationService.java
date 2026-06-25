package dev.abstratium.abstrauth.non_multitenancy.service;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccount;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduled service that sends email notifications when client secrets are
 * about to expire or have just expired.
 *
 * Three notifications are sent:
 * <ul>
 *   <li>First warning: secret is within 30 days of expiry but more than 3 days away.</li>
 *   <li>Final warning: secret is within 3 days of expiry but not yet expired.</li>
 *   <li>Expired notice: secret has expired.</li>
 * </ul>
 *
 * Native SQL is used by the supporting queries so the scheduled job can run
 * without a request-scoped tenant context.
 */
@ApplicationScoped
public class NonMultitenancySecretExpirationNotificationService {

    private static final Logger log = Logger.getLogger(NonMultitenancySecretExpirationNotificationService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Inject
    NonMultitenancyClientSecretService clientSecretService;

    @Inject
    NonMultitenancyAccountService accountService;

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "abstrauth.email.enabled", defaultValue = "false")
    boolean emailEnabled;

    @ConfigProperty(name = "abstrauth.email.from")
    String fromAddress;

    @ConfigProperty(name = "abstrauth.base-url")
    String baseUrl;

    @ConfigProperty(name = "abstratium.stage", defaultValue = "dev")
    String stage;

    /**
     * Daily check for secrets that need expiration notifications.
     * Cron expression is configurable via the {@code abstrauth.secret-expiration.notification-cron}
     * property (default: 06:00 UTC every day).
     */
    @Scheduled(cron = "${abstrauth.secret-expiration.notification-cron}")
    public void checkAndNotify() {
        if (!emailEnabled) {
            log.debug("Email notifications are disabled; skipping secret expiration checks");
            return;
        }

        Instant now = Instant.now();
        log.debug("Checking for client secrets needing expiration notifications");

        processFirstWarnings(now);
        processFinalWarnings(now);
        processExpiredNotices(now);
    }

    void processFirstWarnings(Instant now) {
        List<NonMultitenancyClientSecretNotificationInfo> secrets = clientSecretService.findSecretsNeedingFirstWarning(now);
        for (NonMultitenancyClientSecretNotificationInfo secret : secrets) {
            try {
                sendNotification(secret, "first warning", "expires in 30 days", "first_warning_sent_at");
            } catch (Exception e) {
                log.error("Failed to send first warning for secret " + secret.id(), e);
            }
        }
    }

    void processFinalWarnings(Instant now) {
        List<NonMultitenancyClientSecretNotificationInfo> secrets = clientSecretService.findSecretsNeedingFinalWarning(now);
        for (NonMultitenancyClientSecretNotificationInfo secret : secrets) {
            try {
                sendNotification(secret, "final warning", "expires in 3 days", "final_warning_sent_at");
            } catch (Exception e) {
                log.error("Failed to send final warning for secret " + secret.id(), e);
            }
        }
    }

    void processExpiredNotices(Instant now) {
        List<NonMultitenancyClientSecretNotificationInfo> secrets = clientSecretService.findSecretsNeedingExpiredNotice(now);
        for (NonMultitenancyClientSecretNotificationInfo secret : secrets) {
            try {
                sendNotification(secret, "expired", "has expired", "expired_notice_sent_at");
            } catch (Exception e) {
                log.error("Failed to send expired notice for secret " + secret.id(), e);
            }
        }
    }

    private void sendNotification(NonMultitenancyClientSecretNotificationInfo secret,
                                  String notificationType,
                                  String bodyPhrase,
                                  String sentAtColumn) {
        List<NonMultitenancyAccount> owners = accountService.findOwnersByOrgId(secret.orgId());
        if (owners.isEmpty()) {
            log.warn("No organisation owners found for secret " + secret.id() + " (orgId=" + secret.orgId() + "); skipping notification");
            return;
        }

        String subject = "[" + stage + "] Client secret " + bodyPhrase + " - " + secret.clientName();
        String body = buildEmailBody(secret, notificationType);

        boolean anySent = false;
        for (NonMultitenancyAccount owner : owners) {
            String email = owner.getEmail();
            if (email == null || email.isBlank()) {
                log.warn("Owner account " + owner.getId() + " has no email; skipping notification for secret " + secret.id());
                continue;
            }

            try {
                mailer.send(Mail.withText(email, subject, body).setFrom(fromAddress));
                anySent = true;
                log.info("Sent " + notificationType + " email for secret " + secret.id() + " to owner " + email);
            } catch (Exception e) {
                log.error("Failed to send " + notificationType + " email for secret " + secret.id() + " to owner " + email, e);
            }
        }

        if (anySent) {
            clientSecretService.markNotificationSent(secret.id(), sentAtColumn, Instant.now());
        } else {
            log.warn("No owner emails were sent for secret " + secret.id() + "; notification timestamp not updated");
        }
    }

    private String buildEmailBody(NonMultitenancyClientSecretNotificationInfo secret, String notificationType) {
        String clientName = secret.clientName() != null ? secret.clientName() : secret.clientId();
        String description = secret.description() != null ? secret.description() : "Untitled secret";
        String link = baseUrl + "/clients?viewSecrets=" + secret.clientId() + "&highlightSecret=" + secret.id();
        String expiryDate = DATE_FORMATTER.format(secret.expiresAt());

        return "Hello,\n\n"
                + "This is a " + notificationType + " about a client secret in the " + stage + " environment.\n\n"
                + "Client: " + clientName + "\n"
                + "Secret: " + description + "\n"
                + "Expiration: " + expiryDate + "\n\n"
                + "You can manage this secret here:\n" + link + "\n\n"
                + "If you no longer need this secret, you can revoke it and create a new one.\n\n"
                + "Best regards,\n"
                + stage + " Abstrauth";
    }
}
