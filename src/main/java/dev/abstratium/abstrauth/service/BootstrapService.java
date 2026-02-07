package dev.abstratium.abstrauth.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service responsible for synchronizing the client secret hash on application startup.
 * This ensures that the database always contains the hash of the current ABSTRAUTH_CLIENT_SECRET
 * environment variable.
 */
@ApplicationScoped
public class BootstrapService {
    
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String DEFAULT_SECRET = "dev-secret-CHANGE-IN-PROD";
    
    @Inject
    OAuthClientService clientService;
    
    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;
    
    /**
     * Synchronize the client secret hash on application startup.
     * This method is called automatically when the application starts.
     */
    void onStart(@Observes StartupEvent ev) {
        syncClientSecretHash();
    }
    
    /**
     * Synchronizes the client secret hash in the database with the current
     * environment variable value. This operation is idempotent and safe to
     * run on every startup.
     */
    void syncClientSecretHash() {
        try {
            // Validate secret length
            if (clientSecret.length() < MIN_SECRET_LENGTH) {
                Log.warn("Client secret is too short (" + clientSecret.length() + " < " + MIN_SECRET_LENGTH + " chars). Please use a stronger secret.");
            }
            
            // Check if using default secret
            if (DEFAULT_SECRET.equals(clientSecret)) {
                Log.warn("Using default client secret! Please set ABSTRAUTH_CLIENT_SECRET environment variable to a secure value.");
            }
            
            // Update the client secret hash using the service
            clientService.updateClientSecretHash(clientSecret);
            
            Log.info("Client secret hash synchronized for '" + Roles.CLIENT_ID + "'");
        } catch (IllegalArgumentException e) {
            Log.error("Failed to synchronize client secret", e);
        }
    }
}
