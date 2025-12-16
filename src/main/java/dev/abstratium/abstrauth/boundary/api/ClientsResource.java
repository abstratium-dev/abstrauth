package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    JsonWebToken accessToken;    

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all OAuth clients", description = "Returns a list of all registered OAuth clients")
    @RolesAllowed(Roles.USER)
    public List<ClientResponse> listClients() {
        // Get the current user's ID from the JWT token (sub claim)
        String accountId = accessToken.getSubject();

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

        // Create new client
        OAuthClient client = new OAuthClient();
        client.setClientId(request.clientId);
        client.setClientName(request.clientName);
        client.setClientType(request.clientType);
        client.setRedirectUris(request.redirectUris);
        client.setAllowedScopes(request.allowedScopes);
        client.setRequirePkce(request.requirePkce != null ? request.requirePkce : true);

        OAuthClient created = oauthClientService.create(client);
        return Response.status(Response.Status.CREATED)
                .entity(toClientResponse(created))
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

        // Update client fields
        existing.setClientName(request.clientName);
        existing.setClientType(request.clientType);
        existing.setRedirectUris(request.redirectUris);
        existing.setAllowedScopes(request.allowedScopes);
        existing.setRequirePkce(request.requirePkce != null ? request.requirePkce : true);

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
                client.getCreatedAt() != null ? client.getCreatedAt().toString() : null
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

        public ClientResponse(String id, String clientId, String clientName, String clientType,
                            String redirectUris, String allowedScopes, Boolean requirePkce, String createdAt) {
            this.id = id;
            this.clientId = clientId;
            this.clientName = clientName;
            this.clientType = clientType;
            this.redirectUris = redirectUris;
            this.allowedScopes = allowedScopes;
            this.requirePkce = requirePkce;
            this.createdAt = createdAt;
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

    @RegisterForReflection
    public static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
