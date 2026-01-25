package dev.abstratium.abstrauth.boundary.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.ClientSecretService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for managing client secrets.
 * Supports secret rotation with multiple active secrets per client.
 */
@Path("/api/clients/{clientId}/secrets")
@Tag(name = "Client Secrets", description = "OAuth client secret management endpoints")
public class ClientSecretsResource {

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    OAuthClientService oauthClientService;
    
    @Inject
    ClientSecretService clientSecretService;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @IdToken
    JsonWebToken token;

    /**
     * List all secrets for a client (metadata only, no secret values).
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    @Operation(summary = "List client secrets", description = "Returns metadata for all secrets of a client")
    public Response listSecrets(@PathParam("clientId") String clientId) {
        // Verify client exists
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Not found", "Client not found"))
                    .build();
        }

        // Get all secrets for this client
        List<ClientSecret> secrets = clientSecretService.findByClientId(clientId);
        List<SecretInfo> secretInfos = secrets.stream()
                .map(s -> new SecretInfo(
                        s.getId(),
                        s.getDescription(),
                        s.getCreatedAt(),
                        s.getExpiresAt(),
                        s.isActive()
                ))
                .collect(Collectors.toList());

        return Response.ok(secretInfos).build();
    }

    /**
     * Generate a new secret for a client.
     * Returns the plain secret value (only shown once).
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    @Transactional
    @Operation(summary = "Create new client secret", description = "Generates a new secret for the client")
    public Response createSecret(
            @PathParam("clientId") String clientId,
            @Valid CreateSecretRequest request) {
        
        // Verify client exists
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Not found", "Client not found"))
                    .build();
        }

        // Generate new secret
        String plainSecret = oauthClientService.generateClientSecret();
        String hashedSecret = oauthClientService.hashClientSecret(plainSecret);

        // Calculate expiration if specified
        Instant expiresAt = null;
        if (request.expiresInDays != null && request.expiresInDays > 0) {
            expiresAt = Instant.now().plus(request.expiresInDays, ChronoUnit.DAYS);
        }

        // Create and persist the secret
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setClientId(clientId);
        clientSecret.setSecretHash(hashedSecret);
        clientSecret.setDescription(request.description != null ? request.description : "Generated secret");
        clientSecret.setActive(true);
        clientSecret.setExpiresAt(expiresAt);
        clientSecret.setAccountId(token.getSubject()); // Track who created it (account ID from JWT sub claim)
        clientSecretService.persist(clientSecret);

        // Return response with plain secret (only shown once)
        CreateSecretResponse response = new CreateSecretResponse(
                clientSecret.getId(),
                plainSecret,
                clientSecret.getDescription(),
                clientSecret.getCreatedAt(),
                clientSecret.getExpiresAt()
        );

        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Revoke (deactivate) a client secret.
     */
    @DELETE
    @Path("/{secretId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    @Transactional
    @Operation(summary = "Revoke client secret", description = "Deactivates a client secret")
    public Response revokeSecret(
            @PathParam("clientId") String clientId,
            @PathParam("secretId") Long secretId) {
        
        // Verify client exists
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Not found", "Client not found"))
                    .build();
        }

        // Verify secret exists and belongs to this client
        ClientSecret secret = clientSecretService.findById(secretId);
        if (secret == null || !secret.getClientId().equals(clientId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Not found", "Secret not found"))
                    .build();
        }

        // Check that at least one other active secret exists
        long activeCount = clientSecretService.countActiveSecrets(clientId);
        if (activeCount <= 1 && secret.isActive()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Bad request", "Cannot revoke the last active secret"))
                    .build();
        }

        // Deactivate the secret
        clientSecretService.deactivate(secretId);

        return Response.noContent().build();
    }

    /**
     * Permanently delete a revoked client secret.
     * Can only delete secrets that are already inactive.
     */
    @DELETE
    @Path("/{secretId}/permanent")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    @Transactional
    @Operation(summary = "Delete revoked client secret", description = "Permanently deletes an inactive client secret")
    public Response deleteSecret(
            @PathParam("clientId") String clientId,
            @PathParam("secretId") Long secretId) {
        
        // Verify client exists
        Optional<OAuthClient> clientOpt = oauthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Not found", "Client not found"))
                    .build();
        }

        // Verify secret exists and belongs to this client
        ClientSecret secret = clientSecretService.findById(secretId);
        if (secret == null || !secret.getClientId().equals(clientId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Not found", "Secret not found"))
                    .build();
        }

        // Can only delete inactive secrets
        if (secret.isActive()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Bad request", "Cannot delete an active secret. Revoke it first."))
                    .build();
        }

        // Permanently delete the secret
        clientSecretService.delete(secretId);

        return Response.noContent().build();
    }

    // ==================== DTOs ====================

    @RegisterForReflection
    public static class CreateSecretRequest {
        @Size(max = 255, message = "Description must not exceed 255 characters")
        public String description;
        
        public Integer expiresInDays;

        public CreateSecretRequest() {
        }

        public CreateSecretRequest(String description, Integer expiresInDays) {
            this.description = description;
            this.expiresInDays = expiresInDays;
        }
    }

    @RegisterForReflection
    public static class CreateSecretResponse {
        public Long id;
        public String secret;  // Plain secret - only shown once!
        public String description;
        public Instant createdAt;
        public Instant expiresAt;

        public CreateSecretResponse(Long id, String secret, String description, 
                                   Instant createdAt, Instant expiresAt) {
            this.id = id;
            this.secret = secret;
            this.description = description;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    @RegisterForReflection
    public static class SecretInfo {
        public Long id;
        public String description;
        public Instant createdAt;
        public Instant expiresAt;
        public boolean active;

        public SecretInfo(Long id, String description, Instant createdAt, 
                         Instant expiresAt, boolean active) {
            this.id = id;
            this.description = description;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.active = active;
        }
    }
}
