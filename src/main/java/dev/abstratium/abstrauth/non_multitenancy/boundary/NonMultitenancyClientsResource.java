package dev.abstratium.abstrauth.non_multitenancy.boundary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.api.ClientsResource;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.interceptor.VerifyOrgMembership;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOAuthClientService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancySubscriptionService;
import dev.abstratium.abstrauth.service.ClientAllowedRoleService;
import dev.abstratium.abstrauth.service.CurrentOrgContext;
import dev.abstratium.abstrauth.service.MetricsService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.SubscriptionService;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints for cross-tenant client operations.
 * 
 * These endpoints bypass Hibernate's tenant discriminator to access public OAuth clients
 * owned by other organisations that the caller's organisation subscribes to.
 * 
 * This resource is intentionally located in the non_multitenancy package to make
 * cross-tenant ("dangerous") endpoints easily identifiable during security audits.
 * 
 * IMPORTANT: All methods in this resource require explicit orgId verification and
 * subscription checks to ensure the caller only accesses clients their org is subscribed to.
 */
@Path("/api/clients")
@Tag(name = "Cross-Tenant Clients", description = "Endpoints that access client data across tenant boundaries for subscription-based access")
@VerifyOrgMembership
public class NonMultitenancyClientsResource {

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Inject
    NonMultitenancySubscriptionService nonMultitenancySubscriptionService;

    @Inject
    OAuthClientService oauthClientService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    @IdToken
    JsonWebToken token;

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    MetricsService metricsService;

    /**
     * Lists all OAuth clients visible to the caller's organisation.
     * This includes:
     * 1. Cross-org public clients that the caller's org subscribes to (via non-multitenancy)
     * 2. All clients owned by the caller's own org (any user in the org may see these)
     * 
     * This endpoint uses non-multitenancy entities for cross-tenant access to subscribed public clients.
     * 
     * @return List of all visible clients
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all OAuth clients", description = "Returns a list of all registered OAuth clients visible to the caller's organisation, including cross-org subscribed clients")
    @RolesAllowed(Roles.USER)
    public List<ClientsResource.ClientResponse> listClients() {
        String orgId = currentOrgContext.getOrgId();

        // The set of clientIds this org is subscribed to drives visibility.
        // This includes own org's clients (subscribed by default) and cross-org public clients
        // that the org owner has explicitly subscribed to.
        Set<String> subscribedClientIds = new HashSet<>(subscriptionService.findClientIdsByOrgId(orgId));

        Map<String, ClientsResource.ClientResponse> merged = new LinkedHashMap<>();

        // Add subscribed clients that belong to other orgs (cross-org) via non-multitenancy entity
        nonMultitenancyOAuthClientService.findAllByClientIds(subscribedClientIds).stream()
                .map(this::toClientResponse)
                .forEach(r -> merged.put(r.clientId, r));

        // Also include all clients owned by the caller's org (tenant-scoped), regardless of subscription.
        // Any user in the org may see their own org's clients.
        oauthClientService.findAll().stream()
                .filter(c -> !merged.containsKey(c.getClientId()))
                .map(this::toClientResponse)
                .forEach(r -> merged.put(r.clientId, r));

        return new ArrayList<>(merged.values());
    }

    private ClientsResource.ClientResponse toClientResponse(OAuthClient client) {
        return new ClientsResource.ClientResponse(
                client.getId(),
                client.getOrgId(),
                client.getClientId(),
                client.getClientName(),
                client.getClientType(),
                client.getRedirectUris(),
                client.getAllowedScopes(),
                client.getRequirePkce(),
                client.getAutoSubscribe(),
                client.getPublik(),
                client.getCreatedAt() != null ? client.getCreatedAt().toString() : null,
                null // clientSecret is not included in list responses
        );
    }

    private ClientsResource.ClientResponse toClientResponse(NonMultitenancyOAuthClient client) {
        return new ClientsResource.ClientResponse(
                client.getId(),
                client.getOrgId(),
                client.getClientId(),
                client.getClientName(),
                client.getClientType(),
                client.getRedirectUris(),
                client.getAllowedScopes(),
                client.getRequirePkce(),
                client.getAutoSubscribe(),
                client.getPublik(),
                client.getCreatedAt() != null ? client.getCreatedAt().toString() : null,
                null // clientSecret is not included in list responses
        );
    }

    /**
     * Lists allowed roles for a client.
     * This endpoint uses non-multitenancy entities when checking cross-org subscriptions.
     * 
     * @param clientId The client ID to get allowed roles for
     * @return Response containing the list of allowed roles
     */
    @GET
    @Path("/{clientId}/allowed-roles")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List allowed roles for a client", description = "Returns the roles that subscribing organisations may assign to their users for this client")
    @RolesAllowed(Roles.USER)
    public Response listAllowedRoles(@PathParam("clientId") String clientId) {
        String callerOrgId = currentOrgContext.getOrgId();

        // Check own org first (tenant-scoped). If not found, check cross-org via subscription.
        // Note: This check uses non-multitenancy entities to verify cross-org subscriptions
        // because the subscription may be for a public client owned by another organization.
        boolean clientExists = oauthClientService.findByClientId(clientId).isPresent();
        if (!clientExists) {
            List<NonMultitenancyOAuthClient> crossOrgClients = nonMultitenancyOAuthClientService.findAllByClientIds(Set.of(clientId));
            if (crossOrgClients.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Client not found"))
                        .build();
            }
            // Caller's org must have a subscription to this cross-org client
            if (nonMultitenancySubscriptionService.findNonMultitenancySubscription(callerOrgId, clientId).isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Client not found"))
                        .build();
            }
        }

        List<ClientAllowedRole> roles = clientAllowedRoleService.findAllowedRolesByClientIdForOrg(clientId, callerOrgId);
        List<AllowedRoleResponse> response = roles.stream()
                .map(r -> new AllowedRoleResponse(r.getClientId(), r.getRole(), r.getIsDefault(), r.getAvailableToForeignOrgs()))
                .collect(Collectors.toList());
        return Response.ok(response).build();
    }

    /**
     * Deletes an OAuth client and all associated data across ALL organisations.
     * This uses non-multitenancy entities to perform cross-tenant cascade deletion.
     * Only users with MANAGE_CLIENTS role can delete clients.
     *
     * @param clientId The client ID to delete
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/{clientId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete an OAuth client", description = "Deletes an OAuth client and all associated data (roles, secrets, subscriptions) across ALL organisations. Requires ownership of the client.")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response deleteClient(@PathParam("clientId") String clientId) {
        String callerOrgId = currentOrgContext.getOrgId();

        // Find the client using non-multitenancy service
        var clientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Client not found"))
                    .build();
        }

        var client = clientOpt.get();

        // Verify the caller's org owns this client
        if (!client.getOrgId().equals(callerOrgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("You can only delete clients owned by your organisation"))
                    .build();
        }

        // Delete using non-multitenancy service for cross-tenant cascade deletion
        try {
            nonMultitenancyOAuthClientService.deleteClientWithCascade(clientId);
            metricsService.recordClientDeletion();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse(e.getMessage()))
                    .build();
        }

        return Response.noContent().build();
    }

    @RegisterForReflection
    public static class SubscribedClientResponse {
        public String id;
        public String orgId;
        public String clientId;
        public String clientName;
        public String clientType;
        public String redirectUris;
        public String allowedScopes;
        public Boolean requirePkce;
        public Boolean autoSubscribe;
        public Boolean publik;
        public String createdAt;

        public SubscribedClientResponse(String id, String orgId, String clientId, String clientName, 
                                        String clientType, String redirectUris, String allowedScopes,
                                        Boolean requirePkce, Boolean autoSubscribe, Boolean publik, 
                                        String createdAt) {
            this.id = id;
            this.orgId = orgId;
            this.clientId = clientId;
            this.clientName = clientName;
            this.clientType = clientType;
            this.redirectUris = redirectUris;
            this.allowedScopes = allowedScopes;
            this.requirePkce = requirePkce;
            this.autoSubscribe = autoSubscribe;
            this.publik = publik;
            this.createdAt = createdAt;
        }
    }

    @RegisterForReflection
    public static class AllowedRoleResponse {
        public String clientId;
        public String role;
        public Boolean isDefault;
        public Boolean availableToForeignOrgs;

        public AllowedRoleResponse(String clientId, String role, Boolean isDefault, Boolean availableToForeignOrgs) {
            this.clientId = clientId;
            this.role = role;
            this.isDefault = isDefault;
            this.availableToForeignOrgs = availableToForeignOrgs;
        }
    }
}
