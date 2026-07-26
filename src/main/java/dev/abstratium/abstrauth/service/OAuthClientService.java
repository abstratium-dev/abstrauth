package dev.abstratium.abstrauth.service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.util.PasswordEncoder;
import dev.abstratium.abstrauth.util.SecureRandomProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OAuthClientService {

    private static final Logger log = Logger.getLogger(OAuthClientService.class);

    @Inject
    SecureRandomProvider secureRandomProvider;

    @Inject
    PasswordEncoder passwordEncoder;

    @Inject
    EntityManager em;

    @Inject
    ClientSecretService clientSecretService;

    public List<OAuthClient> findByClientIds(Set<String> clientIds) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId IN :clientIds", OAuthClient.class);
        query.setParameter("clientIds", clientIds);
        return query.getResultList();
    }

    public Optional<OAuthClient> findById(String id) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.id = :id", OAuthClient.class);
        query.setParameter("id", id);
        return query.getResultList().stream().findFirst();
    }

    public Optional<OAuthClient> findByClientId(String clientId) {
        return findByClientIds(Set.of(clientId)).stream().findFirst();
    }

    public List<OAuthClient> findAll() {
        var query = em.createQuery("SELECT c FROM OAuthClient c ORDER BY c.createdAt DESC", OAuthClient.class);
        return query.getResultList();
    }

    @Transactional
    public OAuthClient create(OAuthClient client) {
        em.persist(client);
        return client;
    }

    @Transactional
    public OAuthClient update(OAuthClient client) {
        // Prevent changing the clientId - it's immutable after creation
        OAuthClient existing = em.find(OAuthClient.class, client.getId());
        if (existing != null && !existing.getClientId().equals(client.getClientId())) {
            throw new IllegalArgumentException("Client ID cannot be changed");
        }
        return em.merge(client);
    }

    /**
     * Generates a cryptographically secure client secret.
     * Returns a base64-encoded random string (32 bytes = 43 characters in base64).
     */
    public String generateClientSecret() {
        byte[] randomBytes = new byte[32];
        secureRandomProvider.getSecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hashes a client secret using BCrypt.
     */
    public String hashClientSecret(String plainSecret) {
        return passwordEncoder.hashClientSecret(plainSecret);
    }

    /**
     * Verifies if a plain secret matches a hashed secret.
     */
    public boolean verifyClientSecret(String plainSecret, String hashedSecret) {
        return passwordEncoder.matches(plainSecret, hashedSecret);
    }

    /**
     * Updates the client secret hash for a given client.
     */
    @Transactional
    public void updateClientSecretHash(String plainSecret) {
        log.infov("Checking if client secret hash for {0} needs updating", Roles.CLIENT_ID);

        Optional<OAuthClient> clientOpt = findByClientId(Roles.CLIENT_ID);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + Roles.CLIENT_ID 
                + ". Is the database empty or was that client deleted by accident? It must be present for abstrauth to run!");
        }

        // Check against all active secrets
        List<ClientSecret> activeSecrets = clientSecretService.findActiveSecrets(Roles.CLIENT_ID);

        var matchingSecrets = activeSecrets.stream()
            .filter(secret -> verifyClientSecret(plainSecret, secret.getSecretHash()))
            .toList();

        // only create a new secret, if an existing one does not match
        boolean matchesExisting = !matchingSecrets.isEmpty();

        if(!matchesExisting) {
            log.warnv("The client secret for client id {0}, that is set using the environment, has changed and so the database is being updated so that it matches.", Roles.CLIENT_ID);

            // Create new secret in ClientSecret table
            String hashedSecret = hashClientSecret(plainSecret);
            ClientSecret clientSecret = new ClientSecret();
            clientSecret.setClientId(Roles.CLIENT_ID);
            clientSecret.setSecretHash(hashedSecret);
            clientSecret.setDescription("Updated secret");
            clientSecret.setActive(true);

            // by default, there is just one non-expiring secret for abstrauth.
            // the user might have replaced it with others that expire - even though that makes no sense, since only abstrauth should be using the secret.
            // let's add a new non-expiring secret. so don't set expiresAt

            clientSecretService.persist(clientSecret);
        } else {
            log.debugv("Client secret for client id {0} already matches given plain secret", Roles.CLIENT_ID);
        }
    }

    /**
     * Checks if a client's secret hash matches the given plain secret.
     * Returns false if client not found or hash doesn't match.
     * Checks against all active secrets.
     */
    public boolean clientSecretMatches(String clientId, String plainSecret) {
        Optional<OAuthClient> clientOpt = findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return false;
        }
        
        // Check against all active secrets
        List<ClientSecret> activeSecrets = clientSecretService.findActiveSecrets(clientId);
        if (activeSecrets.isEmpty()) {
            return false;
        }
        
        // Return true if any active secret matches
        return activeSecrets.stream()
            .anyMatch(secret -> verifyClientSecret(plainSecret, secret.getSecretHash()));
    }

    /**
     * Creates a client with a generated secret.
     * Returns both the persisted client and the plain-text secret (which should only be shown once).
     */
    @Transactional
    public ClientWithSecret createWithSecret(OAuthClient client) {
        String plainSecret = generateClientSecret();
        String hashedSecret = hashClientSecret(plainSecret);
        
        // Persist client first
        em.persist(client);
        
        // Create initial secret in ClientSecret table
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setClientId(client.getClientId());
        clientSecret.setSecretHash(hashedSecret);
        clientSecret.setDescription("Initial secret");
        clientSecret.setActive(true);
        clientSecretService.persist(clientSecret);
        
        return new ClientWithSecret(client, plainSecret);
    }

    /**
     * DTO to hold a client and its plain-text secret (for one-time display).
     */
    public static class ClientWithSecret {
        private final OAuthClient client;
        private final String plainSecret;

        public ClientWithSecret(OAuthClient client, String plainSecret) {
            this.client = client;
            this.plainSecret = plainSecret;
        }

        public OAuthClient getClient() {
            return client;
        }

        public String getPlainSecret() {
            return plainSecret;
        }
    }
}
