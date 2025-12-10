package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.GoogleOAuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
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

/**
 * Google OAuth 2.0 Callback Endpoint
 */
@Path("/oauth2/callback/google")
@Tag(name = "OAuth 2.0 Federated Login", description = "Federated login with external identity providers")
public class GoogleCallbackResource {

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(
        summary = "Google OAuth Callback",
        description = "Handles the callback from Google after user authentication. " +
                     "Exchanges the authorization code for user information and creates/links account."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to client callback URI with authorization code"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request or error from Google"
        )
    })
    public Response handleGoogleCallback(
        @Parameter(
            description = "Authorization code from Google",
            required = true
        )
        @QueryParam("code") String code,

        @Parameter(
            description = "State parameter containing the authorization request ID",
            required = true
        )
        @QueryParam("state") String state,

        @Parameter(
            description = "Error code if Google authentication failed",
            required = false
        )
        @QueryParam("error") String error
    ) {
        // Handle error from Google
        if (error != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Google authentication failed: " + error + "</p></body></html>")
                    .build();
        }

        // Validate required parameters
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Missing authorization code</p></body></html>")
                    .build();
        }

        if (state == null || state.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Missing state parameter</p></body></html>")
                    .build();
        }

        // Find the original authorization request using the state parameter
        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(state);
        if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Invalid or expired authorization request</p></body></html>")
                    .build();
        }

        AuthorizationRequest authRequest = requestOpt.get();

        try {
            // Exchange Google code for user info and create/link account
            Account account = googleOAuthService.handleGoogleCallback(code);

            // Approve the authorization request with the account and Google auth method
            authorizationService.approveAuthorizationRequest(authRequest.getId(), account.getId(), "google");

            // Generate authorization code for the client
            AuthorizationCode authCode = authorizationService.generateAuthorizationCode(authRequest.getId());

            // Redirect back to client with authorization code
            String redirectUrl = authRequest.getRedirectUri() +
                    (authRequest.getRedirectUri().contains("?") ? "&" : "?") +
                    "code=" + URLEncoder.encode(authCode.getCode(), StandardCharsets.UTF_8);

            if (authRequest.getState() != null && !authRequest.getState().isBlank()) {
                redirectUrl += "&state=" + URLEncoder.encode(authRequest.getState(), StandardCharsets.UTF_8);
            }

            return Response.seeOther(URI.create(redirectUrl)).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body><h1>Error</h1><p>Failed to process Google authentication: " + 
                            e.getMessage() + "</p><a href='/'>Try again</a></body></html>")
                    .build();
        }
    }
}
