package dev.abstratium.abstrauth.boundary.api;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.interceptor.VerifyOrgMembership;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.ClientAllowedRoleService;
import dev.abstratium.abstrauth.service.MetricsService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.SubscriptionService;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@VerifyOrgMembership
public class ClientsResource {

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    OAuthClientService oauthClientService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    MetricsService metricsService;

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    @IdToken
    JsonWebToken token;    

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get an OAuth client by ID", description = "Returns a single OAuth client by its ID")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response getClient(@PathParam("id") String id) {
        return oauthClientService.findById(id)
                .map(client -> Response.ok(toClientResponse(client)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Client not found"))
                        .build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a new OAuth client", description = "Creates a new OAuth client with the provided details")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response createClient(@Valid CreateClientRequest request) {

        // Validate client ID format: only letters, numbers, and underscores allowed
        if (!request.clientId.matches("^[a-zA-Z0-9_]+$")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Client ID must contain only letters, numbers, and underscores"))
                    .build();
        }

        // ensure clientId is unique by prepending the orgId
        var orgId = token.getClaim("orgId");
        request.clientId = orgId + "__" + request.clientId;

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

        // Validate redirect URIs and scopes: both must be present or both must be absent
        boolean hasRedirectUris = request.redirectUris != null && !request.redirectUris.isBlank() && !"[]".equals(request.redirectUris.trim());
        boolean hasScopes = request.allowedScopes != null && !request.allowedScopes.isBlank() && !"[]".equals(request.allowedScopes.trim());
        
        if (hasScopes && !hasRedirectUris) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Redirect URIs are required when scopes are configured"))
                    .build();
        }
        
        if (hasRedirectUris && !hasScopes) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Scopes are required when redirect URIs are configured"))
                    .build();
        }

        // Create new client with generated secret
        OAuthClient client = new OAuthClient();
        client.setClientId(request.clientId);
        client.setClientName(request.clientName);
        client.setClientType("confidential");  // Always confidential
        // Default null/blank to empty arrays for M2M clients
        client.setRedirectUris(hasRedirectUris ? request.redirectUris : "[]");
        client.setAllowedScopes(hasScopes ? request.allowedScopes : "[]");
        client.setRequirePkce(true);  // Always require PKCE
        boolean publik = request.publik != null ? request.publik : false;
        boolean autoSubscribe = publik && (request.autoSubscribe != null ? request.autoSubscribe : false);
        client.setPublik(publik);
        client.setAutoSubscribe(autoSubscribe);

        OAuthClientService.ClientWithSecret result = oauthClientService.createWithSecret(client);
        subscriptionService.subscribe(orgId.toString(), client.getClientId());
        metricsService.recordClientCreation();
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

        // Validate redirect URIs and scopes: both must be present or both must be absent
        boolean hasRedirectUris = request.redirectUris != null && !request.redirectUris.isBlank() && !"[]".equals(request.redirectUris.trim());
        boolean hasScopes = request.allowedScopes != null && !request.allowedScopes.isBlank() && !"[]".equals(request.allowedScopes.trim());
        
        if (hasScopes && !hasRedirectUris) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Redirect URIs are required when scopes are configured"))
                    .build();
        }
        
        if (hasRedirectUris && !hasScopes) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Scopes are required when redirect URIs are configured"))
                    .build();
        }

        // Update client fields
        existing.setClientName(request.clientName);
        existing.setClientType("confidential");  // Always confidential
        // Default null/blank to empty arrays for M2M clients
        existing.setRedirectUris(hasRedirectUris ? request.redirectUris : "[]");
        existing.setAllowedScopes(hasScopes ? request.allowedScopes : "[]");
        existing.setRequirePkce(true);  // Always require PKCE
        boolean updatedPublik = request.publik != null ? request.publik : Boolean.TRUE.equals(existing.getPublik());
        boolean updatedAutoSubscribe = updatedPublik && (request.autoSubscribe != null ? request.autoSubscribe : Boolean.TRUE.equals(existing.getAutoSubscribe()));
        existing.setPublik(updatedPublik);
        existing.setAutoSubscribe(updatedAutoSubscribe);

        OAuthClient updated = oauthClientService.update(existing);
        return Response.ok(toClientResponse(updated)).build();
    }

    @GET
    @Path("/{clientId}/allowed-roles-for-users-in-clients-org")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List allowed roles for a client", description = "Returns the roles that users may have for this client")
    @RolesAllowed(Roles.USER)
    public Response listAllowedRoles(@PathParam("clientId") String clientId) {
        if (oauthClientService.findByClientId(clientId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        var roles = clientAllowedRoleService.findAllAllowedRolesByClientId(clientId);
        List<AllowedRoleResponse> response = roles.stream()
                .map(r -> new AllowedRoleResponse(r.getClientId(), r.getRole(), r.getIsDefault(), r.getAvailableToForeignOrgs()))
                .collect(Collectors.toList());
        return Response.ok(response).build();
    }

    @POST
    @Path("/{clientId}/allowed-roles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add an allowed role to a client", description = "Adds a role to the allowlist for this client. Only the client owner can do this.")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response addAllowedRole(@PathParam("clientId") String clientId, @Valid AddAllowedRoleRequest request) {
        try {
            clientAllowedRoleService.addAllowedRole(clientId, request.role, Boolean.TRUE.equals(request.isDefault), Boolean.TRUE.equals(request.availableToForeignOrgs));
            return Response.status(Response.Status.CREATED)
                    .entity(new AllowedRoleResponse(clientId, request.role, Boolean.TRUE.equals(request.isDefault), Boolean.TRUE.equals(request.availableToForeignOrgs)))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{clientId}/allowed-roles/{role}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update an allowed role", description = "Updates the default and foreign-availability flags for a role in the client's allowlist")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response updateAllowedRole(@PathParam("clientId") String clientId, @PathParam("role") String role,
                                      @Valid UpdateAllowedRoleRequest request) {
        try {
            clientAllowedRoleService.updateAllowedRole(clientId, role, Boolean.TRUE.equals(request.isDefault), Boolean.TRUE.equals(request.availableToForeignOrgs));
            return Response.ok(new AllowedRoleResponse(clientId, role, Boolean.TRUE.equals(request.isDefault), Boolean.TRUE.equals(request.availableToForeignOrgs))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{clientId}/allowed-roles/{role}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove an allowed role", description = "Removes a role from the client's allowlist")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response removeAllowedRole(@PathParam("clientId") String clientId, @PathParam("role") String role) {
        try {
            clientAllowedRoleService.removeAllowedRole(clientId, role);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    private ClientResponse toClientResponse(OAuthClient client) {
        return new ClientResponse(
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
                null  // No secret in normal responses
        );
    }

    private ClientResponse toClientResponseWithSecret(OAuthClient client, String plainSecret) {
        return new ClientResponse(
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
                plainSecret  // Include plain secret for one-time display
        );
    }

    @RegisterForReflection
    public static class ClientResponse {
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
        public String clientSecret;  // Only populated on creation, null otherwise

        public ClientResponse(String id, String orgId, String clientId, String clientName, String clientType,
                            String redirectUris, String allowedScopes, Boolean requirePkce,
                            Boolean autoSubscribe, Boolean publik, String createdAt, String clientSecret) {
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
            this.clientSecret = clientSecret;
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

    @RegisterForReflection
    public static class CreateClientRequest {
        @NotBlank(message = "Client ID is required")
        public String clientId;
        
        @NotBlank(message = "Client name is required")
        public String clientName;
        
        @NotBlank(message = "Client type is required")
        public String clientType;
        
        // Redirect URIs are optional for M2M clients (validated in createClient)
        public String redirectUris;
        
        // Allowed scopes are optional - empty for role-based M2M clients
        public String allowedScopes;
        
        public Boolean requirePkce;

        // Whether any organisation may subscribe to this client automatically on first sign-in.
        // If false, an org owner must explicitly subscribe before users can sign in.
        public Boolean autoSubscribe;

        // Whether third-party organisations may subscribe to this client at all.
        // If false, only the owning organisation may use it.
        public Boolean publik;
    }

    @RegisterForReflection
    public static class UpdateClientRequest {
        @NotBlank(message = "Client name is required")
        public String clientName;

        @NotBlank(message = "Client type is required")
        public String clientType;

        // Redirect URIs are optional for M2M clients (validated in updateClient)
        public String redirectUris;

        // Allowed scopes are optional - empty for role-based M2M clients
        public String allowedScopes;

        public Boolean requirePkce;

        // Whether any organisation may subscribe to this client automatically on first sign-in.
        // Coerced to false if publik is false.
        public Boolean autoSubscribe;

        // Whether third-party organisations may subscribe to this client at all.
        // If false, only the owning organisation may use it.
        public Boolean publik;
    }

    @RegisterForReflection
    public static class AddAllowedRoleRequest {
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "[a-zA-Z0-9\\-]+", message = "Role must contain only alphanumeric characters and hyphens")
        public String role;
        public Boolean isDefault;
        public Boolean availableToForeignOrgs;
    }

    @RegisterForReflection
    public static class UpdateAllowedRoleRequest {
        public Boolean isDefault;
        public Boolean availableToForeignOrgs;
    }

}
