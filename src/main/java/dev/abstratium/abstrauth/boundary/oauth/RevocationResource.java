package dev.abstratium.abstrauth.boundary.oauth;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * OAuth 2.0 Token Revocation Endpoint
 * RFC 7009 - OAuth 2.0 Token Revocation
 */
@Path("/oauth2/revoke")
@Tag(name = "OAuth 2.0 Token", description = "OAuth 2.0 Token management endpoints")
public class RevocationResource {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Token Revocation Endpoint",
        description = "Revokes an access token or refresh token. " +
                     "The authorization server invalidates the token and, " +
                     "if applicable, other tokens based on the same authorization grant. " +
                     "Requires client authentication."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Token successfully revoked or token was already invalid. " +
                         "The response is always 200 for valid requests to prevent token scanning."
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request - missing or invalid parameters",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                examples = @ExampleObject(
                    value = """
                    {
                        "error": "invalid_request",
                        "error_description": "Missing required parameter: token"
                    }
                    """
                )
            )
        ),
        @APIResponse(
            responseCode = "401",
            description = "Client authentication failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                examples = @ExampleObject(
                    value = """
                    {
                        "error": "invalid_client",
                        "error_description": "Client authentication failed"
                    }
                    """
                )
            )
        ),
        @APIResponse(
            responseCode = "503",
            description = "Service temporarily unavailable"
        )
    })
    public Response revoke(
        @Parameter(
            description = "The token to revoke",
            required = true,
            example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        @FormParam("token") String token,

        @Parameter(
            description = "Type hint about the token - 'access_token' or 'refresh_token'",
            required = false,
            example = "access_token",
            schema = @Schema(enumeration = {"access_token", "refresh_token"})
        )
        @FormParam("token_type_hint") String tokenTypeHint,

        @Parameter(
            description = "Client identifier",
            required = true,
            example = "client_12345"
        )
        @FormParam("client_id") String clientId,

        @Parameter(
            description = "Client secret",
            required = false,
            example = "client_secret_xyz"
        )
        @FormParam("client_secret") String clientSecret
    ) {
        // Implementation pending
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
