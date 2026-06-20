package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientRole;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@QuarkusTest
public class NonMultitenancyClientRoleServiceTest {

    @Inject
    NonMultitenancyClientRoleService nonMultitenancyClientRoleService;

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private static final String TEST_SRC_CLIENT_ID = "test_src_client_123";
    private static final String TEST_SRC_CLIENT_ID_2 = "test_src_client_456";
    private static final String TEST_TARGET_CLIENT_ID = "test_target_client_789";

    @BeforeEach
    public void setup() throws Exception {
        transactionHelper.beginTransaction();

        // Clean up any existing test data
        em.createQuery("DELETE FROM NonMultitenancyClientRole cr WHERE cr.srcClientId LIKE 'test_src_client_%'")
            .executeUpdate();
        
        // Clean up test clients using cascade delete (to properly delete child records)
        for (String clientId : new String[]{TEST_SRC_CLIENT_ID, TEST_SRC_CLIENT_ID_2, TEST_TARGET_CLIENT_ID}) {
            var clientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
            if (clientOpt.isPresent()) {
                nonMultitenancyOAuthClientService.deleteClientWithCascade(clientOpt.get().getId());
            }
        }

        // Create test OAuth clients
        for (String clientId : new String[]{TEST_SRC_CLIENT_ID, TEST_SRC_CLIENT_ID_2, TEST_TARGET_CLIENT_ID}) {
            var clientQuery = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", dev.abstratium.abstrauth.entity.OAuthClient.class);
            clientQuery.setParameter("clientId", clientId);
            if (clientQuery.getResultList().isEmpty()) {
                dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
                client.setClientId(clientId);
                client.setClientName("Test " + clientId);
                client.setClientType("confidential");
                client.setRedirectUris("[\"http://localhost:8080/callback\"]");
                client.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
                client.setRequirePkce(false);
                client.setOrgId(defaultOrgId);
                em.persist(client);

                // Create client secret
                dev.abstratium.abstrauth.entity.ClientSecret secret = new dev.abstratium.abstrauth.entity.ClientSecret();
                secret.setClientId(clientId);
                secret.setSecretHash("$2a$10$dummyhash");
                secret.setDescription("Test secret");
                secret.setActive(true);
                em.persist(secret);
            }
        }

        transactionHelper.commitTransaction();
    }

    @Test
    public void testFindBySrcClientIdReturnsRoles() throws Exception {
        transactionHelper.beginTransaction();

        // Create client roles for src client
        NonMultitenancyClientRole role1 = new NonMultitenancyClientRole();
        role1.setRole("admin");
        role1.setOrgId(defaultOrgId);
        role1.setSrcClientId(TEST_SRC_CLIENT_ID);
        role1.setTargetClientId(TEST_TARGET_CLIENT_ID);
        em.persist(role1);

        NonMultitenancyClientRole role2 = new NonMultitenancyClientRole();
        role2.setRole("reader");
        role2.setOrgId(defaultOrgId);
        role2.setSrcClientId(TEST_SRC_CLIENT_ID);
        role2.setTargetClientId(TEST_TARGET_CLIENT_ID);
        em.persist(role2);

        transactionHelper.commitTransaction();

        List<NonMultitenancyClientRole> results = nonMultitenancyClientRoleService.findBySrcClientId(TEST_SRC_CLIENT_ID);

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.getRole().equals("admin")));
        assertTrue(results.stream().anyMatch(r -> r.getRole().equals("reader")));
        assertTrue(results.stream().allMatch(r -> r.getSrcClientId().equals(TEST_SRC_CLIENT_ID)));
    }

    @Test
    public void testFindBySrcClientIdReturnsEmptyListWhenNoRoles() throws Exception {
        List<NonMultitenancyClientRole> results = nonMultitenancyClientRoleService.findBySrcClientId("nonexistent_client");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testFindBySrcClientIdIsolationBetweenClients() throws Exception {
        transactionHelper.beginTransaction();

        // Create role for first src client
        NonMultitenancyClientRole role1 = new NonMultitenancyClientRole();
        role1.setRole("admin");
        role1.setOrgId(defaultOrgId);
        role1.setSrcClientId(TEST_SRC_CLIENT_ID);
        role1.setTargetClientId(TEST_TARGET_CLIENT_ID);
        em.persist(role1);

        // Create role for second src client
        NonMultitenancyClientRole role2 = new NonMultitenancyClientRole();
        role2.setRole("editor");
        role2.setOrgId(defaultOrgId);
        role2.setSrcClientId(TEST_SRC_CLIENT_ID_2);
        role2.setTargetClientId(TEST_TARGET_CLIENT_ID);
        em.persist(role2);

        transactionHelper.commitTransaction();

        List<NonMultitenancyClientRole> resultsClient1 = nonMultitenancyClientRoleService.findBySrcClientId(TEST_SRC_CLIENT_ID);
        List<NonMultitenancyClientRole> resultsClient2 = nonMultitenancyClientRoleService.findBySrcClientId(TEST_SRC_CLIENT_ID_2);

        assertEquals(1, resultsClient1.size());
        assertEquals("admin", resultsClient1.get(0).getRole());
        assertEquals(TEST_SRC_CLIENT_ID, resultsClient1.get(0).getSrcClientId());

        assertEquals(1, resultsClient2.size());
        assertEquals("editor", resultsClient2.get(0).getRole());
        assertEquals(TEST_SRC_CLIENT_ID_2, resultsClient2.get(0).getSrcClientId());
    }

    @Test
    public void testFindBySrcClientIdPreservesEntityFields() throws Exception {
        transactionHelper.beginTransaction();

        NonMultitenancyClientRole role = new NonMultitenancyClientRole();
        role.setRole("viewer");
        role.setOrgId(defaultOrgId);
        role.setSrcClientId(TEST_SRC_CLIENT_ID);
        role.setTargetClientId(TEST_TARGET_CLIENT_ID);
        em.persist(role);

        transactionHelper.commitTransaction();

        List<NonMultitenancyClientRole> results = nonMultitenancyClientRoleService.findBySrcClientId(TEST_SRC_CLIENT_ID);

        assertEquals(1, results.size());
        NonMultitenancyClientRole found = results.get(0);
        assertNotNull(found.getId());
        assertEquals("viewer", found.getRole());
        assertEquals(defaultOrgId, found.getOrgId());
        assertEquals(TEST_SRC_CLIENT_ID, found.getSrcClientId());
        assertEquals(TEST_TARGET_CLIENT_ID, found.getTargetClientId());
        assertNotNull(found.getCreatedAt());
    }
}
