package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientSecret;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NonMultitenancyOAuthClientService {

    private static final String DEFAULT_SECRET = "dev-secret-CHANGE-IN-PROD";

    @Inject
    EntityManager em;

    @Inject
    OAuthClientService multitenantClientService;

    @Inject
    NonMultitenancyClientSecretService clientSecretService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Returns clients matching the given clientIds, across all organisations.
     * Uses NonMultitenancyOAuthClient to bypass the @TenantId discriminator so that
     * clients owned by other orgs (e.g. subscribed public clients) are included.
     */
    public List<NonMultitenancyOAuthClient> findAllByClientIds(Set<String> clientIds) {
        return findByClientIds(clientIds);
    }

    /**
     * Find a single client by clientId across all organisations.
     * Uses NonMultitenancyOAuthClient to bypass the @TenantId discriminator.
     */
    public Optional<NonMultitenancyOAuthClient> findByClientId(String clientId) {
        return findByClientIds(Set.of(clientId)).stream().findFirst();
    }

    private List<NonMultitenancyOAuthClient> findByClientIds(Set<String> clientIds) {
        if (clientIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
            "SELECT c FROM NonMultitenancyOAuthClient c WHERE c.clientId IN :clientIds",
            NonMultitenancyOAuthClient.class)
            .setParameter("clientIds", clientIds)
            .getResultList();
    }

    /**
     * Delete an OAuth client and all its related entities (account roles, client secrets,
     * client allowed roles, client roles, and subscriptions) across ALL organisations
     * using JPA cascade.
     *
     * This uses NonMultitenancyOAuthClient which has CascadeType.REMOVE on all
     * related collections, ensuring complete deletion regardless of org_id.
     *
     * @param clientId The client ID (not the internal UUID) to delete
     * @return true if client was found and deleted, false if not found
     */
    @Transactional
    public boolean deleteClientWithCascade(String clientId) {
        // Prevent deletion of the abstratium-abstrauth client
        if (Roles.CLIENT_ID.equals(clientId)) {
            throw new IllegalArgumentException("Cannot delete the " + Roles.CLIENT_ID + " client");
        }

        Optional<NonMultitenancyOAuthClient> clientOpt = findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return false;
        }

        NonMultitenancyOAuthClient client = clientOpt.get();

        em.remove(client);
        return true;
    }

    /**
     * Count the total number of OAuth clients in the database
     * @return The number of clients
     */
    public long countClients() {
        var query = em.createQuery("SELECT COUNT(c) FROM NonMultitenancyOAuthClient c", Long.class);
        return query.getSingleResult();
    }

    /**
     * Checks if a client's secret hash matches the given plain secret.
     * Returns false if client not found or hash doesn't match.
     * Checks against all active secrets.
     */
    public boolean abstrauthClientSecretMatches() {
        Optional<NonMultitenancyOAuthClient> clientOpt = findByClientId(Roles.CLIENT_ID);
        if (clientOpt.isEmpty()) {
            return false;
        }
        
        // Check against all active secrets
        List<NonMultitenancyClientSecret> activeSecrets = clientSecretService.findActiveSecrets(Roles.CLIENT_ID);
        if (activeSecrets.isEmpty()) {
            return false;
        }
        
        // Return true if any active secret matches
        return activeSecrets.stream()
            .anyMatch(secret -> multitenantClientService.verifyClientSecret(DEFAULT_SECRET, secret.getSecretHash()));
    }

    public boolean isRedirectUriAllowed(NonMultitenancyOAuthClient client, String redirectUri) {
        try {
            String[] allowedUris = objectMapper.readValue(client.getRedirectUris(), String[].class);
            return Arrays.asList(allowedUris).contains(redirectUri);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public boolean isScopeAllowed(NonMultitenancyOAuthClient client, String requestedScope) {
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


}
