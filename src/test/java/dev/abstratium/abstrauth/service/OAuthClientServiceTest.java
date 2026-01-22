package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.ClientSecretService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OAuthClientService
 */
@QuarkusTest
public class OAuthClientServiceTest {

    @Inject
    OAuthClientService oauthClientService;
    
    @Inject
    ClientSecretService clientSecretService;

    @Test
    public void testFindAll() {
        List<OAuthClient> clients = oauthClientService.findAll();
        assertNotNull(clients);
        assertTrue(clients.size() >= 1, "Should have at least the default client");
    }

    @Test
    public void testFindByClientId() {
        Optional<OAuthClient> client = oauthClientService.findByClientId("abstratium-abstrauth");
        assertTrue(client.isPresent(), "Default client should exist");
        assertEquals("abstratium-abstrauth", client.get().getClientId());
        assertEquals("abstratium abstrauth", client.get().getClientName());
    }

    @Test
    public void testFindByClientIdNotFound() {
        Optional<OAuthClient> client = oauthClientService.findByClientId("non-existent-client");
        assertFalse(client.isPresent(), "Non-existent client should not be found");
    }

    @Test
    @Transactional
    public void testCreate() {
        OAuthClient client = new OAuthClient();
        client.setClientId("test-client-" + System.currentTimeMillis());
        client.setClientName("Test Client");
        client.setClientType("public");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\", \"profile\"]");
        client.setRequirePkce(true);

        OAuthClient created = oauthClientService.create(client);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(client.getClientId(), created.getClientId());
        assertEquals(client.getClientName(), created.getClientName());
        assertEquals(client.getClientType(), created.getClientType());
        assertNotNull(created.getCreatedAt());

        // Verify it can be found
        Optional<OAuthClient> found = oauthClientService.findByClientId(created.getClientId());
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    public void testIsRedirectUriAllowed() {
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId("abstratium-abstrauth");
        assertTrue(clientOpt.isPresent());
        
        OAuthClient client = clientOpt.get();
        assertTrue(oauthClientService.isRedirectUriAllowed(client, "http://localhost:8080/api/auth/callback"));
        assertFalse(oauthClientService.isRedirectUriAllowed(client, "http://evil.com/callback"));
    }

    @Test
    public void testIsRedirectUriAllowedWithInvalidJson() {
        OAuthClient client = new OAuthClient();
        client.setRedirectUris("invalid-json");
        
        assertFalse(oauthClientService.isRedirectUriAllowed(client, "http://localhost:3000/callback"));
    }

    @Test
    public void testIsScopeAllowed() {
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId("abstratium-abstrauth");
        assertTrue(clientOpt.isPresent());
        
        OAuthClient client = clientOpt.get();
        assertTrue(oauthClientService.isScopeAllowed(client, "openid"));
        assertTrue(oauthClientService.isScopeAllowed(client, "openid profile"));
        assertFalse(oauthClientService.isScopeAllowed(client, "invalid-scope"));
    }

    @Test
    public void testIsScopeAllowedWithNullOrBlank() {
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId("abstratium-abstrauth");
        assertTrue(clientOpt.isPresent());
        
        OAuthClient client = clientOpt.get();
        assertTrue(oauthClientService.isScopeAllowed(client, null));
        assertTrue(oauthClientService.isScopeAllowed(client, ""));
        assertTrue(oauthClientService.isScopeAllowed(client, "   "));
    }

    @Test
    public void testIsScopeAllowedWithInvalidJson() {
        OAuthClient client = new OAuthClient();
        client.setAllowedScopes("invalid-json");
        
        assertFalse(oauthClientService.isScopeAllowed(client, "openid"));
    }

    @Test
    public void testFindAllOrderedByCreatedAtDesc() {
        List<OAuthClient> clients = oauthClientService.findAll();
        assertNotNull(clients);
        
        // Verify ordering (newer clients should come first)
        if (clients.size() > 1) {
            for (int i = 0; i < clients.size() - 1; i++) {
                assertTrue(
                    clients.get(i).getCreatedAt().isAfter(clients.get(i + 1).getCreatedAt()) ||
                    clients.get(i).getCreatedAt().isEqual(clients.get(i + 1).getCreatedAt()),
                    "Clients should be ordered by createdAt descending"
                );
            }
        }
    }

    @Test
    @Transactional
    public void testUpdate() {
        // First create a client
        OAuthClient client = new OAuthClient();
        client.setClientId("test-update-client-" + System.currentTimeMillis());
        client.setClientName("Original Name");
        client.setClientType("public");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);

        OAuthClient created = oauthClientService.create(client);
        String originalId = created.getId();

        // Update the client
        created.setClientName("Updated Name");
        created.setClientType("confidential");
        created.setRedirectUris("[\"http://localhost:4000/callback\"]");
        created.setAllowedScopes("[\"openid\", \"profile\", \"email\"]");
        created.setRequirePkce(false);

        OAuthClient updated = oauthClientService.update(created);

        assertNotNull(updated);
        assertEquals(originalId, updated.getId());
        assertEquals("Updated Name", updated.getClientName());
        assertEquals("confidential", updated.getClientType());
        assertEquals("[\"http://localhost:4000/callback\"]", updated.getRedirectUris());
        assertEquals("[\"openid\", \"profile\", \"email\"]", updated.getAllowedScopes());
        assertFalse(updated.getRequirePkce());

        // Verify the update persisted
        Optional<OAuthClient> found = oauthClientService.findByClientId(created.getClientId());
        assertTrue(found.isPresent());
        assertEquals("Updated Name", found.get().getClientName());
    }

    @Test
    @Transactional
    public void testDelete() {
        // First create a client
        OAuthClient client = new OAuthClient();
        client.setClientId("test-delete-client-" + System.currentTimeMillis());
        client.setClientName("Client to Delete");
        client.setClientType("public");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);

        OAuthClient created = oauthClientService.create(client);
        String clientIdToDelete = created.getClientId();

        // Verify it exists
        Optional<OAuthClient> found = oauthClientService.findByClientId(clientIdToDelete);
        assertTrue(found.isPresent());

        // Delete the client
        oauthClientService.delete(created);

        // Verify it's deleted
        Optional<OAuthClient> notFound = oauthClientService.findByClientId(clientIdToDelete);
        assertFalse(notFound.isPresent());
    }

    @Test
    public void testCannotDeleteAbstrauthClient() {
        // Find the abstratium-abstrauth client
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(clientOpt.isPresent(), "abstratium-abstrauth client should exist");
        
        OAuthClient client = clientOpt.get();
        
        // Try to delete it - should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            oauthClientService.delete(client);
        });
        
        assertTrue(exception.getMessage().contains("Cannot delete the " + Roles.CLIENT_ID + " client"));
        
        // Verify it still exists
        Optional<OAuthClient> stillExists = oauthClientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(stillExists.isPresent(), "abstratium-abstrauth client should still exist");
    }

    @Test
    @Transactional
    public void testCanDeleteOtherClients() {
        // Create a different client
        OAuthClient client = new OAuthClient();
        client.setClientId("deletable-client-" + System.currentTimeMillis());
        client.setClientName("Deletable Client");
        client.setClientType("public");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);

        OAuthClient created = oauthClientService.create(client);
        
        // Should be able to delete other clients
        assertDoesNotThrow(() -> {
            oauthClientService.delete(created);
        });
        
        // Verify it's deleted
        Optional<OAuthClient> deleted = oauthClientService.findByClientId(created.getClientId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testHashClientSecret() {
        String plainSecret = "my-secret-password";
        String hash = oauthClientService.hashClientSecret(plainSecret);
        
        assertNotNull(hash);
        assertNotEquals(plainSecret, hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"), "Should be a BCrypt hash");
    }

    @Test
    public void testVerifyClientSecret() {
        String plainSecret = "my-secret-password";
        String hash = oauthClientService.hashClientSecret(plainSecret);
        
        assertTrue(oauthClientService.verifyClientSecret(plainSecret, hash));
        assertFalse(oauthClientService.verifyClientSecret("wrong-password", hash));
    }

    @Test
    @Transactional
    public void testUpdateClientSecretHash() {
        // Create a test client
        OAuthClient client = new OAuthClient();
        client.setClientId("test-secret-update-" + System.currentTimeMillis());
        client.setClientName("Test Secret Update");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        
        OAuthClient created = oauthClientService.create(client);
        String clientId = created.getClientId();
        
        // Update the secret
        String newSecret = "new-secret-value";
        oauthClientService.updateClientSecretHash(clientId, newSecret);
        
        // Verify the new secret was added
        List<ClientSecret> secrets = clientSecretService.findActiveSecrets(clientId);
        assertFalse(secrets.isEmpty());
        
        // Verify the new secret matches
        assertTrue(oauthClientService.clientSecretMatches(clientId, newSecret));
    }

    @Test
    public void testUpdateClientSecretHashThrowsForNonExistentClient() {
        assertThrows(IllegalArgumentException.class, () -> {
            oauthClientService.updateClientSecretHash("non-existent-client", "some-secret");
        });
    }

    @Test
    @Transactional
    public void testClientSecretMatches() {
        // Create a test client with a secret
        OAuthClient client = new OAuthClient();
        client.setClientId("test-secret-match-" + System.currentTimeMillis());
        client.setClientName("Test Secret Match");
        client.setClientType("confidential");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        
        String secret = "test-secret-123";
        
        OAuthClient created = oauthClientService.create(client);
        
        // Create a secret for this client
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setClientId(created.getClientId());
        clientSecret.setSecretHash(oauthClientService.hashClientSecret(secret));
        clientSecret.setDescription("Test secret");
        clientSecret.setActive(true);
        clientSecretService.persist(clientSecret);
        
        // Test matching
        assertTrue(oauthClientService.clientSecretMatches(created.getClientId(), secret));
        assertFalse(oauthClientService.clientSecretMatches(created.getClientId(), "wrong-secret"));
    }

    @Test
    public void testClientSecretMatchesReturnsFalseForNonExistentClient() {
        assertFalse(oauthClientService.clientSecretMatches("non-existent-client", "any-secret"));
    }

    @Test
    @Transactional
    public void testClientSecretMatchesReturnsFalseForNullHash() {
        // Create a client without a secret hash
        OAuthClient client = new OAuthClient();
        client.setClientId("test-no-hash-" + System.currentTimeMillis());
        client.setClientName("Test No Hash");
        client.setClientType("public");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\"]");
        client.setRequirePkce(true);
        
        OAuthClient created = oauthClientService.create(client);
        
        assertFalse(oauthClientService.clientSecretMatches(created.getClientId(), "any-secret"));
    }
}
