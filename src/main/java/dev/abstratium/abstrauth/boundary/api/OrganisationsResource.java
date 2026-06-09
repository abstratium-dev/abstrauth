package dev.abstratium.abstrauth.boundary.api;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.entity.Subscription;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import dev.abstratium.abstrauth.service.SubscriptionService;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

@Path("/api/organisations")
@Tag(name = "Organisations", description = "Organisation management endpoints")
public class OrganisationsResource {

    @Inject
    OrganisationService organisationService;

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    AccountService accountService;

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @IdToken
    JsonWebToken token;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List organisations", description = "Returns all organisations the current user is a member of")
    @RolesAllowed(Roles.USER)
    public List<OrganisationResponse> listOrganisations() {
        String accountId = token.getSubject();
        return organisationService.listOrganisationsForAccount(accountId).stream()
                .map(this::toOrganisationResponse)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/current")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get current organisation", description = "Returns the organisation currently selected in the JWT token (based on orgId claim)")
    @RolesAllowed(Roles.USER)
    public Response getCurrentOrganisation() {
        String orgId = token.getClaim("orgId");
        if (orgId == null || orgId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("No organisation selected in current session"))
                    .build();
        }

        return organisationService.findById(orgId)
                .map(org -> Response.ok(toOrganisationResponse(org)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Organisation not found"))
                        .build());
    }

    @GET
    @Path("/{orgId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get organisation", description = "Returns an organisation by ID. The caller must be a member.")
    @RolesAllowed(Roles.USER)
    public Response getOrganisation(@PathParam("orgId") String orgId) {
        String accountId = token.getSubject();
        return organisationService.findById(orgId)
                .filter(org -> organisationService.isMember(orgId, accountId))
                .map(org -> Response.ok(toOrganisationResponse(org)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Organisation not found"))
                        .build());
    }

    @PUT
    @Path("/{orgId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update organisation", description = "Updates an organisation's name. Caller must be an owner.")
    @RolesAllowed(Roles.USER)
    public Response updateOrganisation(@PathParam("orgId") String orgId, @Valid UpdateOrganisationRequest request) {
        String callerId = token.getSubject();

        if (!isOwnerOfOrg(callerId, orgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You must be an owner of this organisation"))
                    .build();
        }

        return organisationService.findById(orgId)
                .map(org -> {
                    org = organisationService.updateName(orgId, request.name);
                    return Response.ok(toOrganisationResponse(org)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Organisation not found"))
                        .build());
    }

    @DELETE
    @Path("/{orgId}/members/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove member", description = "Removes an account from the organisation. Caller must be owner of that organisation.")
    @RolesAllowed(Roles.USER)
    public Response removeMember(@PathParam("orgId") String orgId, @PathParam("accountId") String accountId) {
        String callerId = token.getSubject();

        if (!isOwnerOfOrg(callerId, orgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You must be an owner of this organisation"))
                    .build();
        }

        if (organisationService.findById(orgId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Organisation not found"))
                    .build();
        }

        try {
            organisationService.removeMember(orgId, accountId);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{orgId}/subscriptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Subscribe to client", description = "Subscribes the organisation to a client. Caller must be owner of that organisation.")
    @RolesAllowed(Roles.USER)
    public Response addSubscription(@PathParam("orgId") String orgId, @Valid AddSubscriptionRequest request) {
        String callerId = token.getSubject();

        if (!isOwnerOfOrg(callerId, orgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You must be an owner of this organisation"))
                    .build();
        }

        if (organisationService.findById(orgId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Organisation not found"))
                    .build();
        }

        Subscription subscription = subscriptionService.subscribe(orgId, request.clientId);
        return Response.status(Response.Status.CREATED).entity(toSubscriptionResponse(subscription)).build();
    }

    @DELETE
    @Path("/{orgId}/subscriptions/{clientId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Unsubscribe from client", description = "Removes the organisation's subscription to a client. Caller must be owner of that organisation.")
    @RolesAllowed(Roles.USER)
    public Response removeSubscription(@PathParam("orgId") String orgId, @PathParam("clientId") String clientId) {
        String callerId = token.getSubject();

        if (!isOwnerOfOrg(callerId, orgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You must be an owner of this organisation"))
                    .build();
        }

        if (organisationService.findById(orgId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Organisation not found"))
                    .build();
        }

        subscriptionService.unsubscribe(orgId, clientId);
        return Response.noContent().build();
    }

    private boolean isOwnerOfOrg(String accountId, String orgId) {
        return organisationService.isOwner(orgId, accountId);
    }

    private OrganisationResponse toOrganisationResponse(Organisation org) {
        String accountId = token.getSubject();
        List<String> roles = organisationService.getRolesForAccount(org.getId(), accountId);
        return new OrganisationResponse(org.getId(), org.getName(), org.getCreatedByAccountId(),
                org.getCreatedAt() != null ? org.getCreatedAt().toString() : null, roles);
    }

    private SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return new SubscriptionResponse(subscription.getId(), subscription.getOrgId(),
                subscription.getClientId(),
                subscription.getCreatedAt() != null ? subscription.getCreatedAt().toString() : null);
    }

    @RegisterForReflection
    public static class OrganisationResponse {
        public String id;
        public String name;
        public String createdByAccountId;
        public String createdAt;
        public List<String> roles;

        public OrganisationResponse(String id, String name, String createdByAccountId, String createdAt, List<String> roles) {
            this.id = id;
            this.name = name;
            this.createdByAccountId = createdByAccountId;
            this.createdAt = createdAt;
            this.roles = roles;
        }
    }

    @RegisterForReflection
    public static class SubscriptionResponse {
        public String id;
        public String orgId;
        public String clientId;
        public String createdAt;

        public SubscriptionResponse(String id, String orgId, String clientId, String createdAt) {
            this.id = id;
            this.orgId = orgId;
            this.clientId = clientId;
            this.createdAt = createdAt;
        }
    }

    @RegisterForReflection
    public static class UpdateOrganisationRequest {
        @NotBlank(message = "Organisation name is required")
        public String name;
    }

    @RegisterForReflection
    public static class AddSubscriptionRequest {
        @NotBlank(message = "Client ID is required")
        public String clientId;
    }
}
