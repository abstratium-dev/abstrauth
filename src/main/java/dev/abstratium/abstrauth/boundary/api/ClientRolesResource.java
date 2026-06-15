package dev.abstratium.abstrauth.boundary.api;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.ClientRole;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.interceptor.VerifyOrgMembership;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOAuthClientService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancySubscriptionService;
import dev.abstratium.abstrauth.service.ClientRoleService;
import dev.abstratium.abstrauth.service.CurrentOrgContext;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API for managing client-to-client role assignments.
 * These roles allow a source client to authenticate and call a target client
 * with specific permissions. The role must exist in the target client's allowed roles catalog.
 */
@Path("/api/clients/{clientId}/client-roles")
@Tag(name = "Client Roles", description = "Manage role assignments that allow one client to call another client")
@VerifyOrgMembership
public class ClientRolesResource {

    @Inject
    ClientRoleService clientRoleService;

    @Inject
    OAuthClientService clientService;

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Inject
    NonMultitenancySubscriptionService nonMultitenancySubscriptionService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "List client role assignments",
        description = "Returns all role assignments where the specified client is the source client. " +
                      "These roles allow the source client to call target clients with specific permissions."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "List of client role assignments",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ClientRolesResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Client not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @RolesAllowed({Roles.MANAGE_CLIENTS, Roles.USER})
    public Response listRoles(
        @Parameter(description = "Source client ID", required = true)
        @PathParam("clientId") String clientId
    ) {
        // Verify client exists
        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        List<ClientRole> roles = clientRoleService.findBySrcClientId(clientId);
        List<ClientRoleDto> dtos = roles.stream()
                .map(r -> new ClientRoleDto(r.getTargetClientId(), r.getRole(), r.getCreatedAt().toString()))
                .collect(Collectors.toList());

        return Response.ok(new ClientRolesResponse(clientId, dtos)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Add client role assignment",
        description = "Assigns a role to a source client for calling a target client. " +
                      "The role must exist in the target client's allowed roles catalog. " +
                      "The caller must own the source client. Target client may be in the same org " +
                      "or a public client from another org that the caller's org subscribes to."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Role assignment created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ClientRoleDto.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid role name, role not in target catalog, or other validation error",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Client not found or caller does not own source client",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "409",
            description = "Role assignment already exists",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response addRole(
        @Parameter(description = "Source client ID", required = true)
        @PathParam("clientId") String clientId,
        @Valid AddClientRoleRequest request
    ) {
        // Verify source client exists
        Optional<OAuthClient> srcClientOpt = clientService.findByClientId(clientId);
        if (srcClientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Source client not found"))
                    .build();
        }

        // Verify target client exists (same org or cross-org public client with subscription)
        boolean targetExists = clientService.findByClientId(request.targetClientId).isPresent();
        if (!targetExists) {
            // Check if it's a public client from another org that caller's org subscribes to
            var crossOrgClients = nonMultitenancyOAuthClientService.findAllByClientIds(java.util.Set.of(request.targetClientId));
            if (crossOrgClients.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Target client not found"))
                        .build();
            }
            // Verify caller's org has a subscription to this cross-org client
            String callerOrgId = currentOrgContext.getOrgId();
            if (nonMultitenancySubscriptionService.findNonMultitenancySubscription(callerOrgId, request.targetClientId).isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Target client not found"))
                        .build();
            }
        }

        try {
            String assigningOrgId = currentOrgContext.getOrgId();
            clientRoleService.addRole(clientId, request.targetClientId, request.role, assigningOrgId);

            return Response.status(Response.Status.CREATED)
                    .entity(new ClientRoleDto(request.targetClientId, request.role, null))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (dev.abstratium.abstrauth.boundary.ConflictException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{targetClientId}/{role}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Remove client role assignment",
        description = "Removes a role assignment from a source client. The caller must own the source client."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "204",
            description = "Role assignment removed successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Role assignment, source client, or target client not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response removeRole(
        @Parameter(description = "Source client ID", required = true)
        @PathParam("clientId") String clientId,
        @Parameter(description = "Target client ID", required = true)
        @PathParam("targetClientId") String targetClientId,
        @Parameter(description = "Role name", required = true)
        @PathParam("role") String role
    ) {
        // Verify clients exist
        Optional<OAuthClient> srcClientOpt = clientService.findByClientId(clientId);
        if (srcClientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Source client not found"))
                    .build();
        }

        Optional<OAuthClient> targetClientOpt = clientService.findByClientId(targetClientId);
        if (targetClientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Target client not found"))
                    .build();
        }

        try {
            clientRoleService.removeRole(clientId, targetClientId, role);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    // DTOs

    @RegisterForReflection
    @Schema(description = "Request to add a role assignment to a client")
    public static class AddClientRoleRequest {
        @NotBlank(message = "Target client ID is required")
        @Schema(
            description = "Target client ID that the source client will call",
            examples = "target-api-client",
            required = true
        )
        public String targetClientId;

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Role must contain only lowercase letters, numbers, and hyphens")
        @Schema(
            description = "Role name (must exist in target client's allowed roles)",
            examples = "api-reader",
            required = true
        )
        public String role;
    }

    @RegisterForReflection
    @Schema(description = "Client role assignment data")
    public static class ClientRoleDto {
        @Schema(description = "Target client ID", examples = "target-api-client")
        public String targetClientId;

        @Schema(description = "Role name", examples = "api-reader")
        public String role;

        @Schema(description = "When the assignment was created", examples = "2026-06-14T10:30:00")
        public String createdAt;

        public ClientRoleDto(String targetClientId, String role, String createdAt) {
            this.targetClientId = targetClientId;
            this.role = role;
            this.createdAt = createdAt;
        }
    }

    @RegisterForReflection
    @Schema(description = "Response containing all client role assignments for a source client")
    public static class ClientRolesResponse {
        @Schema(description = "Source client ID", examples = "my-service-client")
        public String srcClientId;

        @Schema(description = "List of role assignments")
        public List<ClientRoleDto> roles;

        public ClientRolesResponse(String srcClientId, List<ClientRoleDto> roles) {
            this.srcClientId = srcClientId;
            this.roles = roles;
        }
    }
}
