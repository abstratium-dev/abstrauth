package dev.abstratium.abstrauth.boundary;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * OAuth 2.0 Authorization Endpoint
 * RFC 6749 Section 3.1 - Authorization Endpoint
 * RFC 7636 - PKCE Extension
 */
@Path("/oauth2/authorize")
@Tag(name = "OAuth 2.0 Authorization", description = "OAuth 2.0 Authorization Code Flow endpoints")
public class AuthorizationResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(
        summary = "Authorization Endpoint",
        description = "Initiates the OAuth 2.0 Authorization Code Flow. " +
                     "Supports PKCE (RFC 7636) for enhanced security. " +
                     "Returns an authorization page or redirects with authorization code."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to client callback URI with authorization code or error"
        ),
        @APIResponse(
            responseCode = "200",
            description = "Authorization consent page",
            content = @Content(mediaType = MediaType.TEXT_HTML)
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request parameters"
        ),
        @APIResponse(
            responseCode = "401",
            description = "User not authenticated"
        )
    })
    public Response authorize(
        @Parameter(
            description = "Response type - MUST be 'code' for authorization code flow",
            required = true,
            example = "code"
        )
        @QueryParam("response_type") String responseType,

        @Parameter(
            description = "Client identifier",
            required = true,
            example = "client_12345"
        )
        @QueryParam("client_id") String clientId,

        @Parameter(
            description = "Redirection URI where the response will be sent",
            required = false,
            example = "https://client.example.com/callback"
        )
        @QueryParam("redirect_uri") String redirectUri,

        @Parameter(
            description = "Scope of the access request - space-delimited list",
            required = false,
            example = "openid profile email"
        )
        @QueryParam("scope") String scope,

        @Parameter(
            description = "Opaque value used to maintain state between request and callback",
            required = false,
            example = "xyz123"
        )
        @QueryParam("state") String state,

        @Parameter(
            description = "PKCE code challenge - Base64-URL encoded SHA256 hash of code_verifier",
            required = false,
            example = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        )
        @QueryParam("code_challenge") String codeChallenge,

        @Parameter(
            description = "PKCE code challenge method - 'S256' or 'plain'",
            required = false,
            example = "S256",
            schema = @Schema(enumeration = {"S256", "plain"})
        )
        @QueryParam("code_challenge_method") String codeChallengeMethod
    ) {
        // Implementation pending
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    @Operation(
        summary = "Authorization Consent Handler",
        description = "Processes user consent for authorization request"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "302",
            description = "Redirect to client callback URI with authorization code"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid consent submission"
        )
    })
    public Response processConsent(
        @Parameter(description = "User consent decision", required = true)
        @FormParam("consent") String consent,

        @Parameter(description = "Authorization request identifier", required = true)
        @FormParam("request_id") String requestId
    ) {
        // Implementation pending
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
