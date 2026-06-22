package dev.abstratium.abstrauth.entity;

import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class EnversAuditingTest {

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @BeforeEach
    public void setup() throws Exception {
        dbResetHelper.resetDatabase();
    }

    @Test
    public void testAccountCreationCreatesAuditEntry() throws Exception {
        String email = "envers_test_" + System.currentTimeMillis() + "@example.com";
        String username = "envers_test_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Envers Test", username, "Password123", AccountService.NATIVE, "Test Org");
        assertNotNull(account);

        // Query the audit table for the created account
        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> auditRows = em.createNativeQuery(
                    "SELECT a.id, a.REVTYPE, a.email, r.username FROM T_accounts_AUD a " +
                    "JOIN REVINFO r ON a.REV = r.REV WHERE a.id = :id")
                .setParameter("id", account.getId())
                .getResultList();

            assertFalse(auditRows.isEmpty(), "Expected at least one audit entry for the created account");

            Object[] row = auditRows.get(0);
            assertEquals(account.getId(), row[0]);
            // REVTYPE 0 = INSERT
            Number revType = (Number) row[1];
            assertEquals(0, revType.intValue(), "REVTYPE should be 0 (INSERT)");
            assertEquals(email, row[2]);
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }

    @Test
    public void testAccountUpdateCreatesAuditEntry() throws Exception {
        String email = "envers_update_" + System.currentTimeMillis() + "@example.com";
        String username = "envers_update_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Before Update", username, "Password123", AccountService.NATIVE, "Test Org");
        assertNotNull(account);

        // Update the account name
        transactionHelper.beginTransaction();
        Account managed = em.find(Account.class, account.getId());
        managed.setName("After Update");
        em.merge(managed);
        transactionHelper.commitTransaction();

        // Query audit entries — should have INSERT (0) and UPDATE (1)
        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> auditRows = em.createNativeQuery(
                    "SELECT a.REVTYPE, a.name FROM T_accounts_AUD a WHERE a.id = :id ORDER BY a.REV")
                .setParameter("id", account.getId())
                .getResultList();

            assertTrue(auditRows.size() >= 2, "Expected at least 2 audit entries (INSERT + UPDATE), got " + auditRows.size());

            Number insertType = (Number) auditRows.get(0)[1 - 1];
            assertEquals(0, insertType.intValue(), "First entry should be INSERT (0)");

            Object[] updateRow = auditRows.get(auditRows.size() - 1);
            Number updateType = (Number) updateRow[0];
            assertEquals(1, updateType.intValue(), "Last entry should be UPDATE (1)");
            assertEquals("After Update", updateRow[1]);
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }

    @Test
    public void testRevisionInfoHasUsernameAndTimestamp() throws Exception {
        String email = "envers_revinfo_" + System.currentTimeMillis() + "@example.com";
        String username = "envers_revinfo_" + System.currentTimeMillis();

        accountService.createAccount(email, "RevInfo Test", username, "Password123", AccountService.NATIVE, "Test Org");

        // Check that REVINFO has entries with timestamp
        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> revRows = em.createNativeQuery(
                    "SELECT r.REV, r.REVTSTMP, r.username FROM REVINFO r ORDER BY r.REV DESC")
                .getResultList();

            assertFalse(revRows.isEmpty(), "Expected at least one REVINFO entry");

            Object[] latest = revRows.get(0);
            assertNotNull(latest[0], "REV should not be null");
            assertNotNull(latest[1], "REVTSTMP should not be null");
            // username defaults to "system" when no security context
            assertNotNull(latest[2], "username should not be null");
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }

    @Test
    public void testChangeNoteIsNullByDefault() throws Exception {
        String email = "envers_changenote_" + System.currentTimeMillis() + "@example.com";
        String username = "envers_changenote_" + System.currentTimeMillis();

        accountService.createAccount(email, "ChangeNote Test", username, "Password123", AccountService.NATIVE, "Test Org");

        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object> changeNotes = em.createNativeQuery(
                    "SELECT r.change_note FROM REVINFO r ORDER BY r.REV DESC")
                .getResultList();

            assertFalse(changeNotes.isEmpty());
            // Change note should be null since none was set via ChangeNoteContext
            assertNull(changeNotes.get(0), "change_note should be null when no change note is set");
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }

    @Test
    public void testCredentialCreationIsAudited() throws Exception {
        String email = "envers_cred_" + System.currentTimeMillis() + "@example.com";
        String username = "envers_cred_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Cred Test", username, "Password123", AccountService.NATIVE, "Test Org");

        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> auditRows = em.createNativeQuery(
                    "SELECT c.id, c.REVTYPE, c.account_id FROM T_credentials_AUD c " +
                    "WHERE c.account_id = :accountId")
                .setParameter("accountId", account.getId())
                .getResultList();

            assertFalse(auditRows.isEmpty(), "Expected at least one credential audit entry");
            Number revType = (Number) auditRows.get(0)[1];
            assertEquals(0, revType.intValue(), "REVTYPE should be 0 (INSERT)");
            assertEquals(account.getId(), auditRows.get(0)[2]);
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }

    @Test
    public void testOAuthClientCreationIsAudited() throws Exception {
        transactionHelper.beginTransaction();
        OAuthClient client = new OAuthClient();
        client.setClientId("envers-test-client-" + System.currentTimeMillis());
        client.setClientName("Envers Test Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:8080/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> auditRows = em.createNativeQuery(
                    "SELECT o.id, o.REVTYPE, o.client_id FROM T_oauth_clients_AUD o " +
                    "WHERE o.id = :id")
                .setParameter("id", client.getId())
                .getResultList();

            assertFalse(auditRows.isEmpty(), "Expected at least one oauth_clients audit entry");
            Number revType = (Number) auditRows.get(0)[1];
            assertEquals(0, revType.intValue(), "REVTYPE should be 0 (INSERT)");
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }

    @Test
    public void testDeleteCreatesAuditEntry() throws Exception {
        String email = "envers_delete_" + System.currentTimeMillis() + "@example.com";
        String username = "envers_delete_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Delete Test", username, "Password123", AccountService.NATIVE, "Test Org");
        String accountId = account.getId();

        // Delete the account's credentials first, then the account
        transactionHelper.beginTransaction();
        em.createNativeQuery("DELETE FROM T_account_roles WHERE account_id = :aid")
            .setParameter("aid", accountId).executeUpdate();
        em.createNativeQuery("DELETE FROM T_organisation_accounts WHERE account_id = :aid")
            .setParameter("aid", accountId).executeUpdate();
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        // Delete credentials via entity manager so Envers fires
        Credential cred = em.createQuery("SELECT c FROM Credential c WHERE c.accountId = :aid", Credential.class)
            .setParameter("aid", accountId)
            .getResultList().stream().findFirst().orElse(null);
        if (cred != null) {
            em.remove(cred);
        }
        transactionHelper.commitTransaction();

        transactionHelper.beginTransaction();
        Account toDelete = em.find(Account.class, accountId);
        if (toDelete != null) {
            em.remove(toDelete);
        }
        transactionHelper.commitTransaction();

        // Now check for DELETE audit entry (REVTYPE = 2)
        transactionHelper.beginTransaction();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> auditRows = em.createNativeQuery(
                    "SELECT a.REVTYPE FROM T_accounts_AUD a WHERE a.id = :id ORDER BY a.REV")
                .setParameter("id", accountId)
                .getResultList();

            assertTrue(auditRows.size() >= 2, "Expected at least INSERT + DELETE audit entries, got " + auditRows.size());

            Object lastRow = auditRows.get(auditRows.size() - 1);
            Number deleteType;
            if (lastRow instanceof Object[]) {
                deleteType = (Number) ((Object[]) lastRow)[0];
            } else {
                deleteType = (Number) lastRow;
            }
            assertEquals(2, deleteType.intValue(), "Last entry should be DELETE (2)");
        } catch (Exception e) {
            transactionHelper.rollback();
            throw e;
        }
        transactionHelper.commitTransaction();
    }
}
