package dev.abstratium.abstrauth.boundary.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * OIDC Authentication Callback Handler
 * 
 * This endpoint handles the final redirect after OIDC authentication completes.
 * Quarkus OIDC redirects here after setting the session cookie.
 */
@Path("/api/auth/callback")
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthCallbackResource {

    private static final Logger log = Logger.getLogger(AuthCallbackResource.class);

    @GET
    @Operation(
        summary = "OIDC Callback Handler",
        description = "Handles the final redirect after OIDC authentication completes. " +
                     "Redirects authenticated users to the home page."
    )
    @APIResponse(
        responseCode = "302",
        description = "Redirect to home page after successful authentication"
    )
    public Response handleCallback() {
        log.info("OIDC authentication completed, redirecting to home page");
        return Response.seeOther(URI.create("/")).build();
    }
}
