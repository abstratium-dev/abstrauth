package dev.abstratium.abstrauth.non_multitenancy.boundary;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.interceptor.VerifyOrgMembership;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOrganisationService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.oidc.IdToken;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints for cross-tenant organisation operations.
 *
 * These endpoints bypass Hibernate's tenant discriminator to assign initial roles
 * when creating a new organisation, before the orgId is established in the Hibernate session.
 *
 * This resource is intentionally located in the non_multitenancy package to make
 * cross-tenant ("dangerous") endpoints easily identifiable during security audits.
 *
 * IMPORTANT: All methods in this resource require explicit orgId handling since
 * the tenant context is not yet established during organisation creation.
 */
@Path("/api/organisations")
@Tag(name = "Cross-Tenant Organisations", description = "Endpoints that perform cross-tenant operations during organisation creation")
@VerifyOrgMembership
public class NonMultitenancyOrganisationsResource {

    @Inject
    OrganisationService organisationService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    NonMultitenancyOrganisationService nonMultitenancyOrganisationService;

    @Inject
    @IdToken
    JsonWebToken token;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create organisation", description = "Creates a new organisation and links the current user as owner and member with manage-accounts and manage-clients roles. This endpoint uses non-multitenancy services to assign initial roles before the orgId is known to Hibernate.")
    @RolesAllowed(Roles.USER)
    public Response createOrganisation(@Valid CreateOrganisationRequest request) {
        String accountId = token.getSubject();
        Organisation org = organisationService.createOrganisation(request.name, accountId);
        organisationService.addOwner(org.getId(), accountId);
        organisationService.addMember(org.getId(), accountId);

        // Assign manage-accounts and manage-clients roles so the creator can manage their org
        // Uses non-multitenancy service because the orgId is not yet in the Hibernate session
        // All orgs share the same abstratium-abstrauth client (created in migrations)
        String clientId = Roles.CLIENT_ID;
        nonMultitenancyAccountRoleService.addRole(org.getId(), accountId, clientId, Roles._USER_PLAIN);
        nonMultitenancyAccountRoleService.addRole(org.getId(), accountId, clientId, Roles._MANAGE_ACCOUNTS_PLAIN);
        nonMultitenancyAccountRoleService.addRole(org.getId(), accountId, clientId, Roles._MANAGE_CLIENTS_PLAIN);

        return Response.status(Response.Status.CREATED).entity(toOrganisationResponse(org)).build();
    }

    @DELETE
    @Path("/{orgId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete organisation", description = "Deletes an organisation and all its associated data (account roles, subscriptions, memberships) via JPA cascade. Only admin users can delete organisations.")
    @RolesAllowed(Roles.ADMIN)
    public Response deleteOrganisation(@PathParam("orgId") String orgId) {
        if (nonMultitenancyOrganisationService.findById(orgId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Organisation not found"))
                    .build();
        }
        nonMultitenancyOrganisationService.deleteOrganisationWithCascade(orgId);
        return Response.noContent().build();
    }

    private OrganisationResponse toOrganisationResponse(Organisation org) {
        return new OrganisationResponse(org.getId(), org.getName());
    }

    @RegisterForReflection
    public static class CreateOrganisationRequest {
        @NotBlank(message = "Organisation name is required")
        public String name;
    }

    @RegisterForReflection
    public static class OrganisationResponse {
        public String id;
        public String name;

        public OrganisationResponse(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
