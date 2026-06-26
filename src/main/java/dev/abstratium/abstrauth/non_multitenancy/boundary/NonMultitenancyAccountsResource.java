package dev.abstratium.abstrauth.non_multitenancy.boundary;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.interceptor.VerifyOrgMembership;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.CurrentOrgContext;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.Roles;
import io.quarkus.oidc.IdToken;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints for cross-tenant account operations.
 *
 * These endpoints bypass Hibernate's tenant discriminator to perform
 * cross-tenant cascade deletion of accounts and associated data.
 *
 * This resource is intentionally located in the non_multitenancy package to make
 * cross-tenant ("dangerous") endpoints easily identifiable during security audits.
 *
 * IMPORTANT: All methods in this resource require explicit orgId verification and
 * membership checks to ensure the caller only deletes accounts in their own org.
 */
@Path("/api/accounts")
@Tag(name = "Cross-Tenant Accounts", description = "Endpoints that perform cross-tenant account operations for cascade deletion")
@VerifyOrgMembership
public class NonMultitenancyAccountsResource {

    @Inject
    NonMultitenancyAccountService nonMultitenancyAccountService;

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    @IdToken
    JsonWebToken token;

    /**
     * Deletes the authenticated user's own account and all associated data across ALL organisations.
     * Organisations where the user is the sole member are also deleted.
     * This uses non-multitenancy entities to perform cross-tenant cascade deletion.
     *
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete own account", description = "Deletes the authenticated user's own account and all associated data across all organisations. Organisations where the user is the sole member are also deleted.")
    @RolesAllowed(Roles.USER)
    public Response deleteOwnAccount() {
        String accountId = token.getSubject();
        if (accountId == null || accountId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Account ID is required in token"))
                    .build();
        }

        try {
            boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade(accountId);
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Account not found"))
                        .build();
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse(e.getMessage()))
                    .build();
        }

        return Response.noContent().build();
    }

    /**
     * Deletes an account and all associated data across ALL organisations.
     * This uses non-multitenancy entities to perform cross-tenant cascade deletion.
     * Only users with MANAGE_ACCOUNTS role can delete accounts.
     *
     * @param accountId The account ID to delete
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete an account", description = "Deletes an account and all associated data (roles, credentials, federated identities) across ALL organisations. Caller must be a member of the account's organisation.")
    @RolesAllowed(Roles.MANAGE_ACCOUNTS)
    public Response deleteAccount(@PathParam("accountId") String accountId) {
        String callerOrgId = currentOrgContext.getOrgId();

        // Verify account exists
        if (accountService.findById(accountId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Account not found"))
                    .build();
        }

        // Verify account belongs to caller's organization
        if (!organisationService.isMember(callerOrgId, accountId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Account not found in your organization"))
                    .build();
        }

        // Delete the account using non-multitenancy service for cross-tenant cascade deletion
        try {
            boolean deleted = nonMultitenancyAccountService.deleteAccountWithCascade(accountId);
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse("Account not found"))
                        .build();
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new dev.abstratium.abstrauth.boundary.ErrorResponse(e.getMessage()))
                    .build();
        }

        return Response.noContent().build();
    }
}
