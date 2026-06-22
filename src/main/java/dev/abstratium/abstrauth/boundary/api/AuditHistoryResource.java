package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.interceptor.VerifyOrgMembership;
import dev.abstratium.abstrauth.service.AuditHistoryService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/audit")
@Tag(name = "Audit History", description = "Query Envers audit history for entities within the caller's organisation")
@VerifyOrgMembership
public class AuditHistoryResource {

    @Inject
    AuditHistoryService auditHistoryService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/types")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "List auditable entity types",
        description = "Returns the list of entity type identifiers that can be used with the history endpoint."
    )
    @APIResponse(responseCode = "200", description = "List of auditable entity types")
    @RolesAllowed({Roles.MANAGE_ACCOUNTS, Roles.MANAGE_CLIENTS})
    public Response listTypes() {
        List<String> types = Arrays.stream(AuditHistoryService.AuditableEntity.values())
            .filter(e -> securityIdentity.hasRole(e.getRequiredRole()))
            .map(Enum::name)
            .collect(Collectors.toList());
        return Response.ok(types).build();
    }

    @GET
    @Path("/{entityType}/{primaryKey: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get audit history for an entity",
        description = "Returns the full revision history for the specified entity, " +
                      "filtered to only include data belonging to the caller's organisation. " +
                      "For composite-key entities, use slash-separated keys " +
                      "(e.g. orgId/accountId/role for organisation_account, or clientId/role for client_allowed_role)."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Audit history entries ordered by revision",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(type = SchemaType.ARRAY, implementation = AuditEntry.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Unknown entity type or invalid primary key format",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @RolesAllowed({Roles.MANAGE_ACCOUNTS, Roles.MANAGE_CLIENTS})
    public Response getHistory(
        @Parameter(
            description = "Entity type identifier (e.g. account, oauth_client, credential)",
            required = true
        )
        @PathParam("entityType") String entityType,
        @Parameter(
            description = "Primary key of the entity. For composite keys use slash-separated values.",
            required = true
        )
        @PathParam("primaryKey") String primaryKey
    ) {
        AuditHistoryService.AuditableEntity entity;
        try {
            entity = AuditHistoryService.AuditableEntity.valueOf(entityType);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Unknown entity type: " + entityType))
                    .build();
        }

        if (!securityIdentity.hasRole(entity.getRequiredRole())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions for entity type: " + entityType))
                    .build();
        }

        try {
            List<Map<String, Object>> history = auditHistoryService.getHistory(entityType, primaryKey);
            return Response.ok(history).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/related/{relatedEntityType}/by-{parentEntityType}/{parentKey}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get related entity audit history",
        description = "Returns audit history for a related entity type, filtered by a foreign key column " +
                      "matching the primary key of the parent entity. For example, fetching account_role " +
                      "history related to a specific account via /related/account_role/by-account/{accountId}."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Related audit history entries ordered by revision",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(type = SchemaType.ARRAY, implementation = AuditEntry.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Unknown entity type, related entity type, or missing foreign key column",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @RolesAllowed({Roles.MANAGE_ACCOUNTS, Roles.MANAGE_CLIENTS})
    public Response getRelatedHistory(
        @Parameter(description = "Related entity type identifier (e.g. account_role)", required = true)
        @PathParam("relatedEntityType") String relatedEntityType,
        @Parameter(description = "Parent entity type identifier (e.g. account)", required = true)
        @PathParam("parentEntityType") String parentEntityType,
        @Parameter(description = "Primary key of the parent entity", required = true)
        @PathParam("parentKey") String parentKey
    ) {
        // Validate parent entity type
        AuditHistoryService.AuditableEntity parentEntity;
        try {
            parentEntity = AuditHistoryService.AuditableEntity.valueOf(parentEntityType);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Unknown entity type: " + parentEntityType))
                    .build();
        }

        if (!securityIdentity.hasRole(parentEntity.getRequiredRole())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions for entity type: " + parentEntityType))
                    .build();
        }

        // Validate related entity type
        AuditHistoryService.AuditableEntity relatedEntity;
        try {
            relatedEntity = AuditHistoryService.AuditableEntity.valueOf(relatedEntityType);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Unknown related entity type: " + relatedEntityType))
                    .build();
        }

        if (!securityIdentity.hasRole(relatedEntity.getRequiredRole())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Insufficient permissions for related entity type: " + relatedEntityType))
                    .build();
        }

        // Determine the foreign key column: use the parent entity type name suffixed with _id
        // e.g. account -> account_id
        String fkColumn = parentEntityType + "_id";

        try {
            List<Map<String, Object>> history = auditHistoryService.getHistoryByColumn(
                relatedEntityType, fkColumn, parentKey
            );
            return Response.ok(history).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @RegisterForReflection
    public static class AuditEntry {
        @Schema(description = "Revision number")
        public Long rev;
        @Schema(description = "Revision type: 0=INSERT, 1=UPDATE, 2=DELETE")
        public Integer revType;
        @Schema(description = "Revision timestamp (epoch millis)")
        public Long revTimestamp;
        @Schema(description = "User who made the change")
        public String username;
        @Schema(description = "Correlation ID from the request")
        public String correlationId;
        @Schema(description = "Optional change note")
        public String changeNote;
    }
}
