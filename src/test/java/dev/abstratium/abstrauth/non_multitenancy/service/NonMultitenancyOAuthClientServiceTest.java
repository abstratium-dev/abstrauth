package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Tests for NonMultitenancyOAuthClientService
 */
@QuarkusTest
public class NonMultitenancyOAuthClientServiceTest {

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Inject
    OAuthClientService oauthClientService;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @Inject
    TestTransactionHelper transactionHelper;

    @BeforeEach
    public void resetDatabaseBeforeTest() throws Exception {
        transactionHelper.beginTransaction();
        dbResetHelper.resetDatabase();
        transactionHelper.commitTransaction();
    }

    @Test
    @Transactional
    public void testDeleteClientWithCascade() {
        // Create a client
        OAuthClient client = new OAuthClient();
        client.setClientId("test-delete-cascade-" + System.currentTimeMillis());
        client.setClientName("Test Delete Cascade Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);

        OAuthClient created = oauthClientService.create(client);
        String clientId = created.getClientId();

        // Verify it exists using non-multitenancy service
        Optional<NonMultitenancyOAuthClient> found = nonMultitenancyOAuthClientService.findByClientId(clientId);
        assertTrue(found.isPresent(), "Client should exist before deletion");

        // Delete using non-multitenancy service
        boolean deleted = nonMultitenancyOAuthClientService.deleteClientWithCascade(clientId);
        assertTrue(deleted, "Delete should return true");

        // Verify it's deleted using non-multitenancy service
        Optional<NonMultitenancyOAuthClient> notFound = nonMultitenancyOAuthClientService.findByClientId(clientId);
        assertFalse(notFound.isPresent(), "Client should be deleted");
    }

    @Test
    @Transactional
    public void testDeleteClientWithCascadeReturnsFalseWhenNotFound() {
        // Try to delete a non-existent client
        boolean deleted = nonMultitenancyOAuthClientService.deleteClientWithCascade("non-existent-client-" + System.currentTimeMillis());
        assertFalse(deleted, "Delete should return false when client not found");
    }

    @Test
    public void testCannotDeleteAbstrauthClient() {
        // Try to delete the abstratium-abstrauth client - should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            nonMultitenancyOAuthClientService.deleteClientWithCascade(Roles.CLIENT_ID);
        });

        assertTrue(exception.getMessage().contains("Cannot delete the " + Roles.CLIENT_ID + " client"),
            "Exception should mention the protected client");

        // Verify it still exists
        Optional<NonMultitenancyOAuthClient> stillExists = nonMultitenancyOAuthClientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(stillExists.isPresent(), "abstratium-abstrauth client should still exist");
    }

    @Test
    @Transactional
    public void testCanDeleteOtherClients() {
        // Create a different client
        OAuthClient client = new OAuthClient();
        client.setClientId("deletable-cascade-client-" + System.currentTimeMillis());
        client.setClientName("Deletable Cascade Client");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);

        OAuthClient created = oauthClientService.create(client);
        String clientId = created.getClientId();

        // Should be able to delete other clients
        assertDoesNotThrow(() -> {
            boolean deleted = nonMultitenancyOAuthClientService.deleteClientWithCascade(clientId);
            assertTrue(deleted, "Delete should return true");
        });

        // Verify it's deleted
        Optional<NonMultitenancyOAuthClient> deleted = nonMultitenancyOAuthClientService.findByClientId(clientId);
        assertFalse(deleted.isPresent(), "Client should be deleted");
    }
}
