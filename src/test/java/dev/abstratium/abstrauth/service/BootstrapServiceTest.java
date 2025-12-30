package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.OAuthClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

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

    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;

    @Test
    public void testBootstrapServiceExists() {
        assertNotNull(bootstrapService);
    }

    @Test
    public void testSyncClientSecretHashUpdatesHash() {
        // Get the current hash
        Optional<OAuthClient> clientBefore = clientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(clientBefore.isPresent(), "Default client should exist");
        String hashBefore = clientBefore.get().getClientSecretHash();
        assertNotNull(hashBefore, "Client should have a secret hash after bootstrap");

        // Sync again (should be idempotent)
        bootstrapService.syncClientSecretHash();

        // Get the hash after sync
        Optional<OAuthClient> clientAfter = clientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(clientAfter.isPresent());
        String hashAfter = clientAfter.get().getClientSecretHash();
        assertNotNull(hashAfter);

        // The hash should be different (BCrypt generates new salt each time)
        // but both should verify against the same secret
        assertTrue(clientService.verifyClientSecret(clientSecret, hashBefore));
        assertTrue(clientService.verifyClientSecret(clientSecret, hashAfter));
    }

    @Test
    public void testSyncClientSecretHashVerifiesAgainstConfiguredSecret() {
        // After bootstrap, the hash should verify against the configured secret
        Optional<OAuthClient> client = clientService.findByClientId(Roles.CLIENT_ID);
        assertTrue(client.isPresent());
        assertNotNull(client.get().getClientSecretHash());

        // Verify the hash matches the configured secret
        assertTrue(clientService.verifyClientSecret(clientSecret, client.get().getClientSecretHash()));
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
