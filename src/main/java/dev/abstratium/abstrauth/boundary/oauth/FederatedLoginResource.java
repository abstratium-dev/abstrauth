package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.GoogleOAuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.Optional;

/**
 * Federated Login Initiation Endpoints
 */
@Path("/oauth2/federated")
@Tag(name = "OAuth 2.0 Federated Login", description = "Federated login with external identity providers")
public class FederatedLoginResource {

    private static final Logger log = Logger.getLogger(FederatedLoginResource.class); 

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    AuthorizationService authorizationService;

    @GET
    @Path("/google")
    @Operation(
        summary = "Initiate Google Login",
        description = "Redirects the user to Google for authentication. " +
                     "The request_id parameter identifies the original OAuth authorization request."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to Google authentication"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request_id"
        )
    })
    public Response initiateGoogleLogin(
        @Parameter(
            description = "Authorization request identifier",
            required = true
        )
        @QueryParam("request_id") String requestId
    ) {
        // Validate request_id
        if (requestId == null || requestId.isBlank()) {
            log.info("Missing request_id parameter");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing request_id parameter")
                    .build();
        }

        // Verify the authorization request exists and is pending
        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(requestId);
        if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
            log.info("Invalid or expired authorization request");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid or expired authorization request")
                    .build();
        }

        // Generate Google authorization URL with request_id as state
        String googleAuthUrl = googleOAuthService.getAuthorizationUrl(requestId);

        log.info("Redirecting to Google authorization URL");
        return Response.seeOther(URI.create(googleAuthUrl)).build();
    }
}
