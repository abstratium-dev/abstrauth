package dev.abstratium.abstrauth.boundary.oauth;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import dev.abstratium.abstrauth.boundary.ErrorResponse;
import static dev.abstratium.abstrauth.entity.AuthorizationRequest.SESSION_COOKIE_NAME;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAuthorizationService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.NoSubscriptionException;
import dev.abstratium.abstrauth.service.SecurityProblemLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * Handles the organisation selection step during the OAuth sign-in flow.
 * This resource is used when an account belongs to more than one organisation.
 *
 * Security: session fixation is prevented by verifying that the account_id
 * in the session cookie matches the account_id stored on the AuthorizationRequest.
 * The cookie is set during authentication when multiple orgs are detected.
 */
@Path("/api/org-selection")
@Tag(name = "Org Selection", description = "Organisation selection during OAuth sign-in")
public class OrgSelectionResource {

    @Inject
    SecurityProblemLogger securityProblemLogger;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    NonMultitenancyAuthorizationService nonMultitenancyAuthorizationService;

    @Inject
    OrganisationService organisationService;

    /**
     * Returns the list of organisations available to the authenticated user for
     * the given authorization request. The request must be in status
     * {@code org_selection_pending}.
     */
    @GET
    @Path("/{requestId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "List organisations for selection",
        description = "Returns the organisations the user may choose from during sign-in"
    )
    public Response listOrgsForSelection(@PathParam("requestId") String requestId) {
        AuthorizationRequest authRequest = authorizationService.findAuthorizationRequest(requestId)
                .orElse(null);

        if (authRequest == null || !"org_selection_pending".equals(authRequest.getStatus())) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Authorization request not found or not awaiting org selection"))
                    .build();
        }

        List<Organisation> orgs = organisationService.listOrganisationsForAccount(authRequest.getAccountId());
        List<OrgResponse> body = orgs.stream()
                .map(o -> new OrgResponse(o.getId(), o.getName()))
                .collect(Collectors.toList());

        return Response.ok(body).build();
    }

    /**
     * Accepts the user's chosen organisation, verifies session fixation guard,
     * stores the orgId on the request and marks it approved.
     *
     * Returns JSON so the Angular UI can proceed to the consent step.
     * The accountId is extracted from the session cookie for security.
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Submit org selection",
        description = "Stores the chosen org on the authorization request and approves it"
    )
    public Response selectOrg(
            @FormParam("request_id") String requestId,
            @FormParam("org_id") String orgId,
            @CookieParam(SESSION_COOKIE_NAME) String sessionAccountId,
            @Context ContainerRequestContext requestContext) {

        if (requestId == null || requestId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("request_id is required"))
                    .build();
        }
        if (orgId == null || orgId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("org_id is required"))
                    .build();
        }

        AuthorizationRequest authRequest = authorizationService.findAuthorizationRequest(requestId)
                .orElse(null);

        if (authRequest == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Authorization request not found"))
                    .build();
        }

        // Verify session cookie exists
        if (sessionAccountId == null || sessionAccountId.isBlank()) {
            securityProblemLogger.warnfNoAuth(requestContext, "Org selection attempted without session cookie for request %s", requestId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Session cookie required"))
                    .build();
        }

        // Get accountId from the authorization request (stored during authentication)
        String accountId = authRequest.getAccountId();
        if (accountId == null || accountId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Authorization request not authenticated"))
                    .build();
        }

        // Verify the caller is a member of the chosen org (prevents choosing arbitrary orgs)
        if (!organisationService.isMember(orgId, accountId)) {
            securityProblemLogger.warnfNoAuth(requestContext, "Account %s attempted to select org %s but is not a member", accountId, orgId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You are not a member of the selected organisation"))
                    .build();
        }

        try {
            nonMultitenancyAuthorizationService.checkSubscription(orgId, authRequest.getClientId());
        } catch (NoSubscriptionException e) {
            securityProblemLogger.warnfNoAuth(requestContext, "Organisation %s has no subscription to client %s", orgId, authRequest.getClientId());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Your organisation is not subscribed to this application. Please contact your administrator."))
                    .build();
        }

        try {
            authorizationService.selectOrg(requestId, orgId, sessionAccountId);
        } catch (IllegalArgumentException e) {
            securityProblemLogger.warnfNoAuth(requestContext, "Session fixation guard rejected for request %s: %s", requestId, e.getMessage());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Session mismatch: authentication required"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }

        // Clear the session cookie after successful org selection
        NewCookie clearedSessionCookie = new NewCookie.Builder(SESSION_COOKIE_NAME)
            .value("")
            .path("/")
            .maxAge(0) // Delete the cookie
            .httpOnly(true)
            .secure(true)
            .sameSite(NewCookie.SameSite.STRICT)
            .build();

        return Response.ok(new OrgSelectedResponse(true))
            .cookie(clearedSessionCookie)
            .build();
    }

    @RegisterForReflection
    public static class OrgResponse {
        public String id;
        public String name;

        public OrgResponse(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @RegisterForReflection
    public static class OrgSelectedResponse {
        public boolean consentRequired;

        public OrgSelectedResponse(boolean consentRequired) {
            this.consentRequired = consentRequired;
        }
    }
}
