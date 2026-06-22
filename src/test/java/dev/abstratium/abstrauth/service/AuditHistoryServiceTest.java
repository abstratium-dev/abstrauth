package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AuditHistoryServiceTest {

    @Inject
    AuditHistoryService auditHistoryService;

    @Inject
    AccountService accountService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @Inject
    CurrentOrgContext currentOrgContext;

    @BeforeEach
    public void setup() throws Exception {
        dbResetHelper.resetDatabase();
    }

    @Test
    public void testGetAccountHistory() throws Exception {
        String email = "audit_hist_" + System.currentTimeMillis() + "@example.com";
        String username = "audit_hist_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "History Test", username, "Password123", AccountService.NATIVE, "Test Org");

        List<Map<String, Object>> history = auditHistoryService.getHistory("account", account.getId());

        assertFalse(history.isEmpty(), "Expected at least one audit entry");
        Map<String, Object> first = history.get(0);
        assertEquals(account.getId(), first.get("id"));
        assertEquals(email, first.get("email"));
        Number revType = (Number) first.get("revType");
        assertEquals(0, revType.intValue(), "First entry should be INSERT (0)");
        assertNotNull(first.get("rev"));
        assertNotNull(first.get("revTimestamp"));
    }

    @Test
    public void testGetAccountHistoryAfterUpdate() throws Exception {
        String email = "audit_upd_" + System.currentTimeMillis() + "@example.com";
        String username = "audit_upd_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Before", username, "Password123", AccountService.NATIVE, "Test Org");

        // Update
        transactionHelper.beginTransaction();
        Account managed = em.find(Account.class, account.getId());
        managed.setName("After");
        em.merge(managed);
        transactionHelper.commitTransaction();

        List<Map<String, Object>> history = auditHistoryService.getHistory("account", account.getId());

        assertTrue(history.size() >= 2, "Expected INSERT + UPDATE entries, got " + history.size());
        assertEquals("Before", history.get(0).get("name"));
        assertEquals("After", history.get(history.size() - 1).get("name"));
    }

    @Test
    public void testGetCredentialHistory() throws Exception {
        String email = "audit_cred_" + System.currentTimeMillis() + "@example.com";
        String username = "audit_cred_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Cred Hist", username, "Password123", AccountService.NATIVE, "Test Org");

        // Find the credential
        transactionHelper.beginTransaction();
        String credId = (String) em.createNativeQuery(
                "SELECT id FROM T_credentials WHERE account_id = :aid")
            .setParameter("aid", account.getId())
            .getSingleResult();
        transactionHelper.commitTransaction();

        List<Map<String, Object>> history = auditHistoryService.getHistory("credential", credId);

        assertFalse(history.isEmpty(), "Expected credential audit entry");
        assertEquals(account.getId(), history.get(0).get("account_id"));
    }

    @Test
    public void testGetOAuthClientHistory() throws Exception {
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId("audit-hist-client-" + System.currentTimeMillis());
        client.setClientName("Audit History Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost/cb\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        em.persist(client);
        transactionHelper.commitTransaction();

        List<Map<String, Object>> history = auditHistoryService.getHistory("oauth_client", client.getId());

        assertFalse(history.isEmpty(), "Expected oauth_client audit entry");
        assertEquals(client.getClientId(), history.get(0).get("client_id"));
    }

    @Test
    public void testHistoryFilteredByOrg() throws Exception {
        String email = "audit_org_" + System.currentTimeMillis() + "@example.com";
        String username = "audit_org_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Org Filter", username, "Password123", AccountService.NATIVE, "Test Org");

        // Should be visible with the current org
        List<Map<String, Object>> history = auditHistoryService.getHistory("account", account.getId());
        assertFalse(history.isEmpty(), "Expected to see audit entries for own org");

        // Switch to a different org — should return empty
        String originalOrg = currentOrgContext.getOrgId();
        try {
            currentOrgContext.setOrgId("non-existent-org-id-12345");
            List<Map<String, Object>> filtered = auditHistoryService.getHistory("account", account.getId());
            assertTrue(filtered.isEmpty(), "Expected no audit entries for a different org");
        } finally {
            currentOrgContext.setOrgId(originalOrg);
        }
    }

    @Test
    public void testUnknownEntityTypeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> auditHistoryService.getHistory("nonexistent_entity", "some-id"));
    }

    @Test
    public void testOrganisationHistory() throws Exception {
        String orgId = currentOrgContext.getOrgId();

        // The default org was created by resetDatabase, but that was via native SQL
        // so Envers didn't fire. We need to update it to create an audit trail.
        transactionHelper.beginTransaction();
        em.createNativeQuery(
                "INSERT INTO T_organisations_AUD (id, REV, REVTYPE, name, created_by_account_id, created_at) " +
                "SELECT :orgId, r.REV, 0, 'Default Test Org', NULL, CURRENT_TIMESTAMP " +
                "FROM REVINFO r ORDER BY r.REV DESC LIMIT 1")
            .setParameter("orgId", orgId)
            .executeUpdate();

        // If no REVINFO exists yet, create one
        if (em.createNativeQuery("SELECT REV FROM REVINFO").getResultList().isEmpty()) {
            em.createNativeQuery("INSERT INTO REVINFO (REVTSTMP, username) VALUES (:ts, 'test')")
                .setParameter("ts", System.currentTimeMillis())
                .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO T_organisations_AUD (id, REV, REVTYPE, name) " +
                    "SELECT :orgId, r.REV, 0, 'Default Test Org' FROM REVINFO r ORDER BY r.REV DESC LIMIT 1")
                .setParameter("orgId", orgId)
                .executeUpdate();
        }
        transactionHelper.commitTransaction();

        List<Map<String, Object>> history = auditHistoryService.getHistory("organisation", orgId);
        assertFalse(history.isEmpty(), "Expected organisation audit entry for own org");
    }

    @Test
    public void testAccountRoleHistory() throws Exception {
        String email = "audit_role_" + System.currentTimeMillis() + "@example.com";
        String username = "audit_role_" + System.currentTimeMillis();

        Account account = accountService.createAccount(email, "Role Hist", username, "Password123", AccountService.NATIVE, "Test Org");

        // Create an account role directly via the audited entity
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.AccountRole role = new dev.abstratium.abstrauth.entity.AccountRole();
        role.setAccountId(account.getId());
        role.setClientId("client-a");
        role.setRole("test-audit-role");
        em.persist(role);
        transactionHelper.commitTransaction();

        List<Map<String, Object>> history = auditHistoryService.getHistory("account_role", role.getId());
        assertFalse(history.isEmpty(), "Expected account_role audit entry");
        assertEquals("test-audit-role", history.get(0).get("role"));
    }
}
