package dev.abstratium.abstrauth.boundary;

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
 * OAuth 2.0 Token Endpoint
 * RFC 6749 Section 3.2 - Token Endpoint
 * RFC 7636 - PKCE Extension
 */
@Path("/oauth2/token")
@Tag(name = "OAuth 2.0 Token", description = "OAuth 2.0 Token management endpoints")
public class TokenResource {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Token Endpoint",
        description = "Exchanges authorization code for access token. " +
                     "Supports PKCE code_verifier validation. " +
                     "Returns access_token, token_type, expires_in, and optional refresh_token."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successful token response",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = TokenResponse.class),
                examples = @ExampleObject(
                    value = """
                    {
                        "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                        "token_type": "Bearer",
                        "expires_in": 3600,
                        "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
                        "scope": "openid profile email"
                    }
                    """
                )
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request - invalid grant, unsupported grant type, or invalid PKCE verifier",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                examples = @ExampleObject(
                    value = """
                    {
                        "error": "invalid_grant",
                        "error_description": "Authorization code is invalid or expired"
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
        )
    })
    public Response token(
        @Parameter(
            description = "Grant type - 'authorization_code' or 'refresh_token'",
            required = true,
            example = "authorization_code",
            schema = @Schema(enumeration = {"authorization_code", "refresh_token"})
        )
        @FormParam("grant_type") String grantType,

        @Parameter(
            description = "Authorization code received from authorization endpoint (required for authorization_code grant)",
            required = false,
            example = "SplxlOBeZQQYbYS6WxSbIA"
        )
        @FormParam("code") String code,

        @Parameter(
            description = "Redirection URI used in authorization request (required if included in authorization request)",
            required = false,
            example = "https://client.example.com/callback"
        )
        @FormParam("redirect_uri") String redirectUri,

        @Parameter(
            description = "Client identifier",
            required = true,
            example = "client_12345"
        )
        @FormParam("client_id") String clientId,

        @Parameter(
            description = "Client secret for confidential clients",
            required = false,
            example = "client_secret_xyz"
        )
        @FormParam("client_secret") String clientSecret,

        @Parameter(
            description = "PKCE code verifier - high-entropy cryptographic random string (43-128 characters)",
            required = false,
            example = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        )
        @FormParam("code_verifier") String codeVerifier,

        @Parameter(
            description = "Refresh token (required for refresh_token grant)",
            required = false,
            example = "tGzv3JOkF0XG5Qx2TlKWIA"
        )
        @FormParam("refresh_token") String refreshToken,

        @Parameter(
            description = "Scope of the access request for refresh token grant",
            required = false,
            example = "openid profile"
        )
        @FormParam("scope") String scope
    ) {
        // Implementation pending
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Token Response DTO for OpenAPI documentation
     */
    @Schema(description = "OAuth 2.0 Token Response")
    public static class TokenResponse {
        @Schema(description = "The access token issued by the authorization server", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String access_token;

        @Schema(description = "The type of token - always 'Bearer'", example = "Bearer")
        public String token_type;

        @Schema(description = "Token lifetime in seconds", example = "3600")
        public Integer expires_in;

        @Schema(description = "Refresh token for obtaining new access tokens", example = "tGzv3JOkF0XG5Qx2TlKWIA")
        public String refresh_token;

        @Schema(description = "Space-delimited list of granted scopes", example = "openid profile email")
        public String scope;
    }

    /**
     * Error Response DTO for OpenAPI documentation
     */
    @Schema(description = "OAuth 2.0 Error Response")
    public static class ErrorResponse {
        @Schema(
            description = "Error code",
            example = "invalid_grant",
            enumeration = {
                "invalid_request",
                "invalid_client",
                "invalid_grant",
                "unauthorized_client",
                "unsupported_grant_type",
                "invalid_scope"
            }
        )
        public String error;

        @Schema(description = "Human-readable error description", example = "Authorization code is invalid or expired")
        public String error_description;

        @Schema(description = "URI identifying a human-readable web page with error information", example = "https://auth.example.com/error/invalid_grant")
        public String error_uri;
    }
}
