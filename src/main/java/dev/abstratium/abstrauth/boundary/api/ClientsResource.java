package dev.abstratium.abstrauth.boundary.api;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/clients")
@Tag(name = "Clients", description = "OAuth client management endpoints")
public class ClientsResource {

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    OAuthClientService oauthClientService;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @IdToken
    JsonWebToken token;    

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all OAuth clients", description = "Returns a list of all registered OAuth clients")
    @RolesAllowed(Roles.USER)
    public List<ClientResponse> listClients() {
        // Get the current user's ID from the JWT token (sub claim)
        String accountId = token.getSubject();

        if (securityIdentity.hasRole(Roles.MANAGE_CLIENTS)) {
            return oauthClientService.findAll().stream()
                    .map(this::toClientResponse)
                    .collect(Collectors.toList());
        }
        List<String> clientIds = accountRoleService.findClientsByAccountId(accountId);
        
        return oauthClientService.findByClientIds(new HashSet<>(clientIds)).stream()
                .map(this::toClientResponse)
                .collect(Collectors.toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a new OAuth client", description = "Creates a new OAuth client with the provided details")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response createClient(@Valid CreateClientRequest request) {
        // Check if client ID already exists
        if (oauthClientService.findByClientId(request.clientId).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Client ID already exists"))
                    .build();
        }

        // Enforce confidential clients only
        if (!"confidential".equals(request.clientType)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Only confidential clients are allowed"))
                    .build();
        }

        // Enforce PKCE requirement
        if (request.requirePkce != null && !request.requirePkce) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("PKCE is required for all clients"))
                    .build();
        }

        // Create new client with generated secret
        OAuthClient client = new OAuthClient();
        client.setClientId(request.clientId);
        client.setClientName(request.clientName);
        client.setClientType("confidential");  // Always confidential
        client.setRedirectUris(request.redirectUris);
        client.setAllowedScopes(request.allowedScopes);
        client.setRequirePkce(true);  // Always require PKCE

        OAuthClientService.ClientWithSecret result = oauthClientService.createWithSecret(client);
        return Response.status(Response.Status.CREATED)
                .entity(toClientResponseWithSecret(result.getClient(), result.getPlainSecret()))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update an OAuth client", description = "Updates an existing OAuth client with the provided details")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response updateClient(@PathParam("id") String id, @Valid UpdateClientRequest request) {
        // Find existing client
        OAuthClient existing = oauthClientService.findAll().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        // Enforce confidential clients only
        if (!"confidential".equals(request.clientType)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Only confidential clients are allowed"))
                    .build();
        }

        // Enforce PKCE requirement
        if (request.requirePkce != null && !request.requirePkce) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("PKCE is required for all clients"))
                    .build();
        }

        // Update client fields
        existing.setClientName(request.clientName);
        existing.setClientType("confidential");  // Always confidential
        existing.setRedirectUris(request.redirectUris);
        existing.setAllowedScopes(request.allowedScopes);
        existing.setRequirePkce(true);  // Always require PKCE

        OAuthClient updated = oauthClientService.update(existing);
        return Response.ok(toClientResponse(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete an OAuth client", description = "Deletes an existing OAuth client")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response deleteClient(@PathParam("id") String id) {
        // Find existing client
        OAuthClient existing = oauthClientService.findAll().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        oauthClientService.delete(existing);
        return Response.noContent().build();
    }

    private ClientResponse toClientResponse(OAuthClient client) {
        return new ClientResponse(
                client.getId(),
                client.getClientId(),
                client.getClientName(),
                client.getClientType(),
                client.getRedirectUris(),
                client.getAllowedScopes(),
                client.getRequirePkce(),
                client.getCreatedAt() != null ? client.getCreatedAt().toString() : null,
                null  // No secret in normal responses
        );
    }

    private ClientResponse toClientResponseWithSecret(OAuthClient client, String plainSecret) {
        return new ClientResponse(
                client.getId(),
                client.getClientId(),
                client.getClientName(),
                client.getClientType(),
                client.getRedirectUris(),
                client.getAllowedScopes(),
                client.getRequirePkce(),
                client.getCreatedAt() != null ? client.getCreatedAt().toString() : null,
                plainSecret  // Include plain secret for one-time display
        );
    }

    @RegisterForReflection
    public static class ClientResponse {
        public String id;
        public String clientId;
        public String clientName;
        public String clientType;
        public String redirectUris;
        public String allowedScopes;
        public Boolean requirePkce;
        public String createdAt;
        public String clientSecret;  // Only populated on creation, null otherwise

        public ClientResponse(String id, String clientId, String clientName, String clientType,
                            String redirectUris, String allowedScopes, Boolean requirePkce, String createdAt,
                            String clientSecret) {
            this.id = id;
            this.clientId = clientId;
            this.clientName = clientName;
            this.clientType = clientType;
            this.redirectUris = redirectUris;
            this.allowedScopes = allowedScopes;
            this.requirePkce = requirePkce;
            this.createdAt = createdAt;
            this.clientSecret = clientSecret;
        }
    }

    @RegisterForReflection
    public static class CreateClientRequest {
        @NotBlank(message = "Client ID is required")
        public String clientId;
        
        @NotBlank(message = "Client name is required")
        public String clientName;
        
        @NotBlank(message = "Client type is required")
        public String clientType;
        
        @NotBlank(message = "Redirect URIs are required")
        public String redirectUris;
        
        @NotBlank(message = "Allowed scopes are required")
        public String allowedScopes;
        
        public Boolean requirePkce;
    }

    @RegisterForReflection
    public static class UpdateClientRequest {
        @NotBlank(message = "Client name is required")
        public String clientName;
        
        @NotBlank(message = "Client type is required")
        public String clientType;
        
        @NotBlank(message = "Redirect URIs are required")
        public String redirectUris;
        
        @NotBlank(message = "Allowed scopes are required")
        public String allowedScopes;
        
        public Boolean requirePkce;
    }

}
