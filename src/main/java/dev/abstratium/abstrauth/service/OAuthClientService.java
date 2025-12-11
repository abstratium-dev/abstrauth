package dev.abstratium.abstrauth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abstratium.abstrauth.entity.OAuthClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OAuthClientService {

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    public Optional<OAuthClient> findByClientId(String clientId) {
        var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", OAuthClient.class);
        query.setParameter("clientId", clientId);
        return query.getResultStream().findFirst();
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
        if (requestedScope == null || requestedScope.isBlank()) {
            return true;
        }

        try {
            String[] allowedScopes = objectMapper.readValue(client.getAllowedScopes(), String[].class);
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
        em.remove(em.contains(client) ? client : em.merge(client));
    }
}
