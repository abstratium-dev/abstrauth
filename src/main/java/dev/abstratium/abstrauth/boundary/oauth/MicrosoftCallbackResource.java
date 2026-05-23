package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.MicrosoftOAuthService;
import dev.abstratium.abstrauth.util.ClientIpUtil;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.jboss.logging.Logger;

/**
 * Microsoft OAuth 2.0 Callback Endpoint
 */
@Path("/oauth2/callback/microsoft")
@Tag(name = "OAuth 2.0 Federated Login", description = "Federated login with external identity providers")
public class MicrosoftCallbackResource {

    private static final Logger log = Logger.getLogger(MicrosoftCallbackResource.class);

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(
        summary = "Microsoft OAuth Callback",
        description = "Handles the callback from Microsoft Entra after user authentication. " +
                     "Exchanges the authorization code for user information and creates/links account."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to client callback URI with authorization code"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request or error from Microsoft"
        )
    })
    public Response handleMicrosoftCallback(
        @Parameter(
            description = "Authorization code from Microsoft",
            required = true
        )
        @QueryParam("code") String code,

        @Parameter(
            description = "State parameter containing the authorization request ID",
            required = true
        )
        @QueryParam("state") String state,

        @Parameter(
            description = "Error code if Microsoft authentication failed",
            required = false
        )
        @QueryParam("error") String error,

        @Context ContainerRequestContext requestContext
    ) {
        // Handle error from Microsoft
        if (error != null) {
            log.info("Microsoft callback received with error: " + error);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Microsoft authentication failed: " + error + "</p></body></html>")
                    .build();
        }

        // Validate required parameters
        if (code == null || code.isBlank()) {
            log.info("Microsoft callback received with missing authorization code");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Missing authorization code</p></body></html>")
                    .build();
        }

        if (state == null || state.isBlank()) {
            log.info("Microsoft callback received with missing state parameter");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Missing state parameter</p></body></html>")
                    .build();
        }

        // Find the original authorization request using the state parameter
        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(state);
        if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
            log.info("Microsoft callback received with invalid or expired authorization request");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Invalid or expired authorization request</p></body></html>")
                    .build();
        }

        log.info("Microsoft callback received with valid authorization request");

        AuthorizationRequest authRequest = requestOpt.get();

        try {
            // Exchange Microsoft code for user info and create/link account
            Account account = microsoftOAuthService.handleMicrosoftCallback(code);

            // Approve the authorization request with the account and Microsoft auth method
            authorizationService.approveAuthorizationRequest(authRequest.getId(), account.getId(), AccountService.MICROSOFT);

            // Generate authorization code for the client
            AuthorizationCode authCode = authorizationService.generateAuthorizationCode(authRequest.getId());

            // Redirect back to client with authorization code
            String redirectUrl = authRequest.getRedirectUri() +
                    (authRequest.getRedirectUri().contains("?") ? "&" : "?") +
                    "code=" + URLEncoder.encode(authCode.getCode(), StandardCharsets.UTF_8);

            if (authRequest.getState() != null && !authRequest.getState().isBlank()) {
                redirectUrl += "&state=" + URLEncoder.encode(authRequest.getState(), StandardCharsets.UTF_8);
            }

            String clientIp = ClientIpUtil.getClientIp(requestContext);
            log.info("User " + account.getEmail() + " has been approved by Microsoft for authorization request " + authRequest.getId() + " for client " + authRequest.getClientId() + " from IP " + clientIp);

            return Response.seeOther(URI.create(redirectUrl)).build();

        } catch (Exception e) {
            String clientInfo = authRequest != null ? " for client " + authRequest.getClientId() : "";
            log.error("Microsoft callback had an exception" + clientInfo + ": " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body><h1>Error</h1><p>Failed to process Microsoft authentication: " +
                            e.getMessage() + "</p><a href='/'>Try again</a></body></html>")
                    .build();
        }
    }
}
