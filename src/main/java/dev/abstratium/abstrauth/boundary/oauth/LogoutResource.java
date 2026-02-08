package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.service.MetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * OpenID Connect RP-Initiated Logout Endpoint
 * https://openid.net/specs/openid-connect-rpinitiated-1_0.html
 */
@Path("/api/auth/logout")
@Tag(name = "OAuth 2.0 Logout", description = "OpenID Connect logout endpoint")
public class LogoutResource {

    private static final Logger log = Logger.getLogger(LogoutResource.class);

    @Inject
    MetricsService metricsService;

    @GET
    @Operation(
        summary = "RP-Initiated Logout (GET)",
        description = "Logout endpoint for OpenID Connect RP-Initiated Logout. " +
                     "Terminates the user's session and optionally redirects to a post-logout URI."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to post-logout URI or default page"
        )
    })
    public Response logoutGet(
        @Parameter(description = "ID Token hint for the session to terminate")
        @QueryParam("id_token_hint") String idTokenHint,
        
        @Parameter(description = "URI to redirect to after logout")
        @QueryParam("post_logout_redirect_uri") String postLogoutRedirectUri,
        
        @Parameter(description = "State to maintain between logout request and callback")
        @QueryParam("state") String state
    ) {
        return performLogout(idTokenHint, postLogoutRedirectUri, state);
    }

    @POST
    @Operation(
        summary = "RP-Initiated Logout (POST)",
        description = "Logout endpoint for OpenID Connect RP-Initiated Logout. " +
                     "Terminates the user's session and optionally redirects to a post-logout URI."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to post-logout URI or default page"
        )
    })
    public Response logoutPost(
        @Parameter(description = "ID Token hint for the session to terminate")
        @QueryParam("id_token_hint") String idTokenHint,
        
        @Parameter(description = "URI to redirect to after logout")
        @QueryParam("post_logout_redirect_uri") String postLogoutRedirectUri,
        
        @Parameter(description = "State to maintain between logout request and callback")
        @QueryParam("state") String state
    ) {
        return performLogout(idTokenHint, postLogoutRedirectUri, state);
    }

    private Response performLogout(String idTokenHint, String postLogoutRedirectUri, String state) {
        log.infof("Logout request received - id_token_hint: %s, post_logout_redirect_uri: %s, state: %s",
                  idTokenHint != null ? "present" : "null",
                  postLogoutRedirectUri,
                  state);

        // Record explicit logout metric
        // Note: This counter is independent of the login counter and does NOT track automatic session expirations
        metricsService.recordExplicitLogout();

        // In a real implementation, you would:
        // 1. Validate the id_token_hint
        // 2. Revoke any refresh tokens
        // 3. Clear any server-side session state

        // Build redirect URI
        String redirectUri = postLogoutRedirectUri != null ? postLogoutRedirectUri : "/";
        
        if (state != null) {
            redirectUri += (redirectUri.contains("?") ? "&" : "?") + "state=" + state;
        }

        log.infof("Redirecting to: %s", redirectUri);
        
        return Response.seeOther(URI.create(redirectUri)).build();
    }
}
