package dev.abstratium.abstrauth.boundary.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.ServiceAccountRoleService;
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
 * REST API for managing service account roles.
 * Service account roles are used for @RolesAllowed authorization in microservices.
 */
@Path("/api/clients/{clientId}/roles")
@Tag(name = "Service Account Roles", description = "Manage roles for service clients used in @RolesAllowed authorization")
public class ServiceAccountRolesResource {

    @Inject
    ServiceAccountRoleService roleService;

    @Inject
    OAuthClientService clientService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "List service account roles",
        description = "Returns all roles assigned to a service client. These roles are included in the JWT 'groups' claim for @RolesAllowed authorization."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "List of roles",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = RolesResponse.class)
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
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response listRoles(
        @Parameter(description = "Client ID", required = true)
        @PathParam("clientId") String clientId
    ) {
        // Verify client exists
        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        Set<String> roles = roleService.findRolesByClientId(clientId);
        return Response.ok(new RolesResponse(clientId, roles.stream().sorted().collect(Collectors.toList())))
                .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Add service account role",
        description = "Assigns a new role to a service client. The role will be included in the JWT 'groups' claim as '{clientId}_{role}'."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Role added successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = RoleResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid role name or role already exists",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
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
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response addRole(
        @Parameter(description = "Client ID", required = true)
        @PathParam("clientId") String clientId,
        @Valid AddRoleRequest request
    ) {
        // Verify client exists
        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        // Check if role already exists
        Set<String> existingRoles = roleService.findRolesByClientId(clientId);
        if (existingRoles.contains(request.role)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Role already exists for this client"))
                    .build();
        }

        // Add the role
        roleService.addRole(clientId, request.role);

        return Response.status(Response.Status.CREATED)
                .entity(new RoleResponse(clientId, request.role, clientId + "_" + request.role))
                .build();
    }

    @DELETE
    @Path("/{role}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Remove service account role",
        description = "Removes a role from a service client. The role will no longer be included in JWT tokens."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "204",
            description = "Role removed successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Client or role not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Response removeRole(
        @Parameter(description = "Client ID", required = true)
        @PathParam("clientId") String clientId,
        @Parameter(description = "Role name", required = true)
        @PathParam("role") String role
    ) {
        // Verify client exists
        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Client not found"))
                    .build();
        }

        // Check if role exists
        Set<String> existingRoles = roleService.findRolesByClientId(clientId);
        if (!existingRoles.contains(role)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Role not found for this client"))
                    .build();
        }

        // Remove the role
        roleService.removeRole(clientId, role);

        return Response.noContent().build();
    }

    // DTOs

    @RegisterForReflection
    @Schema(description = "Request to add a role to a service client")
    public static class AddRoleRequest {
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Role must contain only lowercase letters, numbers, and hyphens")
        @Schema(
            description = "Role name (lowercase, alphanumeric with hyphens)",
            examples = "api-reader",
            required = true
        )
        public String role;
    }

    @RegisterForReflection
    @Schema(description = "Response containing a single role")
    public static class RoleResponse {
        @Schema(description = "Client ID", examples = "my-service")
        public String clientId;

        @Schema(description = "Role name", examples = "api-reader")
        public String role;

        @Schema(description = "Full group name as it appears in JWT", examples = "my-service_api-reader")
        public String groupName;

        public RoleResponse(String clientId, String role, String groupName) {
            this.clientId = clientId;
            this.role = role;
            this.groupName = groupName;
        }
    }

    @RegisterForReflection
    @Schema(description = "Response containing all roles for a client")
    public static class RolesResponse {
        @Schema(description = "Client ID", examples = "my-service")
        public String clientId;

        @Schema(description = "List of role names", examples = "[\"api-reader\", \"api-writer\"]")
        public List<String> roles;

        public RolesResponse(String clientId, List<String> roles) {
            this.clientId = clientId;
            this.roles = roles;
        }
    }
}
