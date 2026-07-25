package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyAccount;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.vertx.ext.mail.SMTPException;

/**
 * Unit tests for the logging behaviour of NonMultitenancySecretExpirationNotificationService.
 */
@ExtendWith(MockitoExtension.class)
public class NonMultitenancySecretExpirationNotificationServiceLogTest {

    private Mailer mailer;
    private NonMultitenancyClientSecretService clientSecretService;
    private NonMultitenancyAccountService accountService;
    private NonMultitenancySecretExpirationNotificationService notificationService;

    private Logger jbossLogger;
    private CapturingHandler logHandler;

    @BeforeEach
    public void setup() throws Exception {
        mailer = mock(Mailer.class);
        clientSecretService = mock(NonMultitenancyClientSecretService.class);
        accountService = mock(NonMultitenancyAccountService.class);

        notificationService = new NonMultitenancySecretExpirationNotificationService();
        setField(notificationService, "clientSecretService", clientSecretService);
        setField(notificationService, "accountService", accountService);
        setField(notificationService, "mailer", mailer);
        setField(notificationService, "emailEnabled", true);
        setField(notificationService, "fromAddress", "noreply@example.com");
        setField(notificationService, "baseUrl", "http://localhost:8080");
        setField(notificationService, "stage", "test");

        jbossLogger = Logger.getLogger(NonMultitenancySecretExpirationNotificationService.class.getName());
        logHandler = new CapturingHandler();
        jbossLogger.addHandler(logHandler);
    }

    @AfterEach
    public void teardown() {
        jbossLogger.removeHandler(logHandler);
    }

    @Test
    public void testRecipientRejectionIsLoggedAsWarning() {
        String rejectedEmail = "unknown@example.com";
        NonMultitenancyAccount owner = new NonMultitenancyAccount();
        owner.setId("owner-1");
        owner.setEmail(rejectedEmail);

        NonMultitenancyClientSecretNotificationInfo secret = new NonMultitenancyClientSecretNotificationInfo(
                47L, "client-id", "Client", "description", Instant.now(), "org-id");

        when(clientSecretService.findSecretsNeedingFinalWarning(any())).thenReturn(List.of(secret));
        when(accountService.findOwnersByOrgId("org-id")).thenReturn(List.of(owner));
        String smtpMessage = "recipient address not accepted: 550 5.1.1 <" + rejectedEmail + ">: User unknown";
        doThrow(new CompletionException(
                new SMTPException(smtpMessage, 550, List.of("550 5.1.1 <" + rejectedEmail + ">: User unknown"), false)))
                .when(mailer).send(any(Mail.class));

        notificationService.processFinalWarnings(Instant.now());

        List<ExtLogRecord> errorRecords = logHandler.records.stream()
                .filter(r -> isErrorLevel(r.getLevel().getName()))
                .filter(r -> r.getMessage().contains("Failed to send"))
                .toList();
        List<ExtLogRecord> warnRecords = logHandler.records.stream()
                .filter(r -> isInfoLevel(r.getLevel().getName()))
                .filter(r -> r.getMessage().contains("Failed to send"))
                .toList();

        assertTrue(errorRecords.isEmpty(), "Permanent recipient rejections must not be logged as errors");
        assertEquals(1, warnRecords.size(), "Permanent recipient rejection should be logged as a warning");
        assertTrue(warnRecords.get(0).getMessage().contains(rejectedEmail));
    }

    @Test
    public void testOtherMailFailureIsStillLoggedAsError() {
        String ownerEmail = "owner@example.com";
        NonMultitenancyAccount owner = new NonMultitenancyAccount();
        owner.setId("owner-1");
        owner.setEmail(ownerEmail);

        NonMultitenancyClientSecretNotificationInfo secret = new NonMultitenancyClientSecretNotificationInfo(
                48L, "client-id", "Client", "description", Instant.now(), "org-id");

        when(clientSecretService.findSecretsNeedingFinalWarning(any())).thenReturn(List.of(secret));
        when(accountService.findOwnersByOrgId("org-id")).thenReturn(List.of(owner));
        doThrow(new RuntimeException("connection refused")).when(mailer).send(any(Mail.class));

        notificationService.processFinalWarnings(Instant.now());

        List<ExtLogRecord> errorRecords = logHandler.records.stream()
                .filter(r -> isErrorLevel(r.getLevel().getName()))
                .filter(r -> r.getMessage().contains("Failed to send"))
                .toList();

        assertEquals(1, errorRecords.size(), "Unexpected mail failures should still be logged as errors");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = NonMultitenancySecretExpirationNotificationService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean isErrorLevel(String levelName) {
        return "ERROR".equals(levelName) || "SEVERE".equals(levelName) || "FATAL".equals(levelName);
    }

    private static boolean isInfoLevel(String levelName) {
        return "INFO".equals(levelName);
    }

    private static class CapturingHandler extends ExtHandler {
        final java.util.List<ExtLogRecord> records = new java.util.ArrayList<>();

        @Override
        protected void doPublish(ExtLogRecord record) {
            records.add(record);
        }
    }
}
