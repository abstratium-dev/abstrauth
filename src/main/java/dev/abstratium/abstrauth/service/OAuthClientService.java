package dev.abstratium.abstrauth.service;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.ClientSecretService;
import dev.abstratium.abstrauth.util.SecureRandomProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OAuthClientService {

    @Inject
    SecureRandomProvider secureRandomProvider;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;
    
    @Inject
    ClientSecretService clientSecretService;

    public List<OAuthClient> findByClientIds(Set<String> clientIds) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId IN :clientIds", OAuthClient.class);
        query.setParameter("clientIds", clientIds);
        return query.getResultList();
    }

    public Optional<OAuthClient> findByClientId(String clientId) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", OAuthClient.class);
        query.setParameter("clientId", clientId);
        return query.getResultList().stream().findFirst();
    }

    public List<OAuthClient> findAll() {
        var query = em.createQuery("SELECT c FROM OAuthClient c ORDER BY c.createdAt DESC", OAuthClient.class);
        return query.getResultList();
    }

    public boolean isRedirectUriAllowed(OAuthClient client, String redirectUri) {
        try {
            String[] allowedUris = objectMapper.readValue(client.getRedirectUris(), String[].class);
            return Arrays.asList(allowedUris).contains(redirectUri);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public boolean isScopeAllowed(OAuthClient client, String requestedScope) {
        // Empty/null requested scope is always allowed (role-based auth only)
        if (requestedScope == null || requestedScope.isBlank()) {
            return true;
        }

        // If no allowed scopes are configured, reject any scope request
        // (client should use role-based authorization only)
        if (client.getAllowedScopes() == null || client.getAllowedScopes().isBlank()) {
            return false;
        }

        try {
            String[] allowedScopes = objectMapper.readValue(client.getAllowedScopes(), String[].class);
            
            // Empty array means no scopes allowed (role-based auth only)
            if (allowedScopes.length == 0) {
                return false;
            }
            
            List<String> allowedScopeList = Arrays.asList(allowedScopes);
            
            String[] requestedScopes = requestedScope.split(" ");
            for (String scope : requestedScopes) {
                if (!allowedScopeList.contains(scope)) {
                    return false;
                }
            }
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    @Transactional
    public OAuthClient create(OAuthClient client) {
        em.persist(client);
        return client;
    }

    @Transactional
    public OAuthClient update(OAuthClient client) {
        return em.merge(client);
    }

    @Transactional
    public void delete(OAuthClient client) {
        // Prevent deletion of the abstratium-abstrauth client
        if (Roles.CLIENT_ID.equals(client.getClientId())) {
            throw new IllegalArgumentException("Cannot delete the " + Roles.CLIENT_ID + " client");
        }
        em.remove(em.contains(client) ? client : em.merge(client));
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
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(plainSecret);
    }

    /**
     * Verifies if a plain secret matches a hashed secret.
     */
    public boolean verifyClientSecret(String plainSecret, String hashedSecret) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(plainSecret, hashedSecret);
    }

    /**
     * Updates the client secret hash for a given client.
     */
    @Transactional
    public void updateClientSecretHash(String clientId, String plainSecret) {
        Optional<OAuthClient> clientOpt = findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        
        // Create new secret in ClientSecret table
        String hashedSecret = hashClientSecret(plainSecret);
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setClientId(clientId);
        clientSecret.setSecretHash(hashedSecret);
        clientSecret.setDescription("Updated secret");
        clientSecret.setActive(true);
        clientSecretService.persist(clientSecret);
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
