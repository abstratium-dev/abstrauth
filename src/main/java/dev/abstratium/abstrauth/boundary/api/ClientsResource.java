package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.OAuthClientService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/clients")
@Tag(name = "Clients", description = "OAuth client management endpoints")
@RolesAllowed("abstratium-abstrauth_manage-clients")
public class ClientsResource {

    @Inject
    OAuthClientService oauthClientService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all OAuth clients", description = "Returns a list of all registered OAuth clients")
    public List<ClientResponse> listClients() {
        return oauthClientService.findAll().stream()
                .map(this::toClientResponse)
                .collect(Collectors.toList());
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
}
