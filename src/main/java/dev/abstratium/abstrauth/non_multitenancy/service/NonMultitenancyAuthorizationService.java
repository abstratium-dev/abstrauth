package dev.abstratium.abstrauth.non_multitenancy.service;

import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.NoSubscriptionException;
import dev.abstratium.abstrauth.service.OAuthClientService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * Authorization service that uses non-multitenancy entities for cross-tenant operations.
 * 
 * This service is intentionally placed in the non_multitenancy package because it uses
 * {@link NonMultitenancySubscriptionService} to check and create subscriptions across
 * organization boundaries during the OAuth authorization flow.
 * 
 * These operations require bypassing Hibernate's @TenantId discriminator because:
 * 1. During authorization, the orgId is being determined/selected (not yet in JWT)
 * 2. Subscriptions must be queried/created before the tenant context is fully established
 */
@ApplicationScoped
public class NonMultitenancyAuthorizationService {

    @Inject
    EntityManager em;

    @Inject
    OAuthClientService oAuthClientService;

    @Inject
    NonMultitenancySubscriptionService nonMultitenancySubscriptionService;

    @Inject
    dev.abstratium.abstrauth.service.MetricsService metricsService;

    @Inject
    dev.abstratium.abstrauth.service.AuthorizationService authorizationService;

    /**
     * Checks the subscription gate for the given org and client.
     * Auto-creates a subscription if auto_subscribe = true AND publik = true;
     * throws {@link NoSubscriptionException} otherwise.
     * A private client (publik = false) can never be auto-subscribed by a non-owning org.
     * Used by the org-selection path where approval is handled separately.
     * 
     * This method uses non-multitenancy entities because the subscription check happens
     * during the authorization flow before the tenant context is fully established.
     *
     * @param orgId The organization ID to check subscription for
     * @param clientId The client ID to check subscription for
     * @throws NoSubscriptionException if the org is not subscribed and auto-subscribe is disabled
     */
    @Transactional
    public void checkSubscription(String orgId, String clientId) {
        OAuthClient client = oAuthClientService.findByClientId(clientId).orElse(null);
        boolean isPublik = client == null || Boolean.TRUE.equals(client.getPublik());
        boolean autoSubscribe = isPublik && (client == null || Boolean.TRUE.equals(client.getAutoSubscribe()));
        nonMultitenancySubscriptionService.ensureSubscribed(orgId, clientId, autoSubscribe);
    }

    /**
     * Approves the authorization request for the given account, checks the subscription
     * gate for the chosen org, and records the orgId — all in one call.
     * Throws {@link NoSubscriptionException} if the org is not subscribed and
     * the client has auto_subscribe = false.
     * 
     * This method uses non-multitenancy entities for the subscription check.
     *
     * @param requestId The authorization request ID
     * @param accountId The account ID to approve
     * @param authMethod The authentication method used (e.g., "native", "google")
     * @param orgId The organization ID to set on the request
     * @throws NotFoundException if the authorization request is not found
     * @throws NoSubscriptionException if the org is not subscribed to the client
     */
    @Transactional
    public void approveWithSubscriptionCheck(String requestId, String accountId, String authMethod, String orgId) {
        AuthorizationRequest request = em.find(AuthorizationRequest.class, requestId);
        if (request == null) {
            throw new NotFoundException("Authorization request not found");
        }
        checkSubscription(orgId, request.getClientId());
        authorizationService.approveAuthorizationRequest(requestId, accountId, authMethod);
        authorizationService.setOrgId(requestId, orgId);
    }
}
