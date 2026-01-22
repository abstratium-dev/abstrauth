package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.ClientSecretService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BootstrapService
 * Tests the client secret synchronization on startup
 */
@QuarkusTest
public class BootstrapServiceTest {

    @Inject
    BootstrapService bootstrapService;

    @Inject
    OAuthClientService clientService;
    
    @Inject
    ClientSecretService clientSecretService;

    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;

    @Test
    public void testBootstrapServiceExists() {
        assertNotNull(bootstrapService);
    }

    @Test
    public void testSyncClientSecretHashUpdatesHash() {
        // Get the current secrets
        List<ClientSecret> secretsBefore = clientSecretService.findActiveSecrets(Roles.CLIENT_ID);
        assertFalse(secretsBefore.isEmpty(), "Default client should have secrets after bootstrap");
        String hashBefore = secretsBefore.get(0).getSecretHash();
        assertNotNull(hashBefore, "Client should have a secret hash after bootstrap");

        // Sync again (should be idempotent or add new secret)
        bootstrapService.syncClientSecretHash();

        // Get the secrets after sync
        List<ClientSecret> secretsAfter = clientSecretService.findActiveSecrets(Roles.CLIENT_ID);
        assertFalse(secretsAfter.isEmpty());
        
        // All active secrets should verify against the configured secret
        for (ClientSecret secret : secretsAfter) {
            assertTrue(clientService.verifyClientSecret(clientSecret, secret.getSecretHash()));
        }
    }

    @Test
    public void testSyncClientSecretHashVerifiesAgainstConfiguredSecret() {
        // After bootstrap, the secrets should verify against the configured secret
        Optional<OAuthClient> client = clientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(client.isPresent());
        
        List<ClientSecret> secrets = clientSecretService.findActiveSecrets(Roles.CLIENT_ID);
        assertFalse(secrets.isEmpty(), "Client should have at least one active secret");

        // Verify all active secrets match the configured secret
        for (ClientSecret secret : secrets) {
            assertTrue(clientService.verifyClientSecret(clientSecret, secret.getSecretHash()));
        }
    }

    @Test
    public void testDefaultClientExists() {
        // The default client should exist (created by migration)
        Optional<OAuthClient> client = clientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(client.isPresent(), "Default client should exist");
        assertEquals(Roles.CLIENT_ID, client.get().getClientId());
    }

    @Test
    public void testClientSecretMatchesAfterBootstrap() {
        // After bootstrap, the client secret should match
        assertTrue(clientService.clientSecretMatches(Roles.CLIENT_ID, clientSecret));
    }
}
