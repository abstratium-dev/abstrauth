package dev.abstratium.abstrauth.boundary.api;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.util.ClientIpUtil;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * API endpoints for OAuth authorization approval.
 * These endpoints are under /api to ensure OIDC BFF authentication applies.
 */
@Path("/api/oauth")
@Tag(name = "OAuth Approval API", description = "API endpoints for OAuth authorization approval")
public class OAuthApprovalResource {

    private static final Logger log = Logger.getLogger(OAuthApprovalResource.class);

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    AccountService accountService;

    @POST
    @Path("/approve-authenticated")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(
        summary = "Approve authorization for already-authenticated user",
        description = "Approves an authorization request for a user who is already authenticated via OIDC session. " +
                     "This endpoint is under /api to ensure OIDC BFF authentication applies."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Authorization request approved",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AuthenticationResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request"
        ),
        @APIResponse(
            responseCode = "401",
            description = "User not authenticated"
        ),
        @APIResponse(
            responseCode = "403",
            description = "User has no roles for this client"
        )
    })
    public Response approveAuthenticated(
            @Parameter(description = "Authorization request identifier", required = true)
            @QueryParam("request_id") String requestId,
            @Context ContainerRequestContext requestContext) {

        // Verify user is authenticated via OIDC
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("User not authenticated")
                    .build();
        }

        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(requestId);
        if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid request")
                    .build();
        }

        // Get account from authenticated user's email
        String email = securityIdentity.getPrincipal().getName();
        Optional<Account> accountOpt = accountService.findByEmail(email);
        if (accountOpt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Account not found")
                    .build();
        }

        Account account = accountOpt.get();
        AuthorizationRequest authRequest = requestOpt.get();

        // Check if user has at least one role for this client
        boolean hasRoleForClient = account.getRoles().stream()
                .anyMatch(role -> role.getClientId().equals(authRequest.getClientId()));

        if (!hasRoleForClient) {
            String clientIp = ClientIpUtil.getClientIp(requestContext);
            log.warn("User " + account.getEmail() + " attempted to authorize for client " + authRequest.getClientId() + 
                    " but has no roles for this client - IP " + clientIp);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("You do not have any roles for this application. Please contact your administrator.")
                    .build();
        }

        // Approve the authorization request with the authenticated user's account
        authorizationService.approveAuthorizationRequest(requestId, account.getId(), account.getAuthProvider());

        String clientIp = ClientIpUtil.getClientIp(requestContext);
        log.info("User " + account.getEmail() + " approved authorization request " + requestId + " for client " + authRequest.getClientId() + " (already authenticated) from IP " + clientIp);

        // Return user info for consent page
        return Response.ok(new AuthenticationResponse(account.getName())).build();
    }

    public static final class AuthenticationResponse {
        private final String name;

        public AuthenticationResponse(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
