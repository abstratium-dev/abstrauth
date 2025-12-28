package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * Backend For Frontend (BFF) Authentication Endpoints
 * 
 * These endpoints are protected by Quarkus OIDC. When an unauthenticated user
 * accesses them, Quarkus OIDC automatically:
 * 1. Redirects to /oauth2/authorize with all required OAuth parameters
 * 2. Handles the callback at /auth-callback
 * 3. Exchanges the authorization code for tokens
 * 4. Stores tokens in encrypted HTTP-only cookies
 * 5. Redirects back to the original page
 */
@Path("/api/auth")
@Tag(name = "BFF Authentication", description = "Backend For Frontend authentication endpoints")
public class AuthBffResource {

    private static final Logger LOG = Logger.getLogger(AuthBffResource.class);

    @GET
    @Path("/login")
    @Authenticated
    @Operation(
        summary = "Initiate Login (BFF Pattern)",
        description = "Triggers the OAuth 2.0 authorization code flow. " +
                     "Unauthenticated users are automatically redirected to the authorization endpoint. " +
                     "After successful authentication, users are redirected back to the application."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to authorization endpoint (if not authenticated) or back to app (if authenticated)"
        )
    })
    public Response login() {
        LOG.debug("Login endpoint accessed - user is authenticated, redirecting to app");
        // If we reach here, the user is already authenticated (Quarkus OIDC handled it)
        // Redirect to the main app
        return Response.seeOther(URI.create("/")).build();
    }

    @GET
    @Path("/check")
    @Authenticated
    @Operation(
        summary = "Check Authentication Status",
        description = "Returns 200 if authenticated, triggers login flow if not"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "User is authenticated"
        ),
        @APIResponse(
            responseCode = "302",
            description = "User not authenticated, redirect to login"
        )
    })
    public Response checkAuth() {
        LOG.debug("Auth check - user is authenticated");
        return Response.ok().build();
    }
}
