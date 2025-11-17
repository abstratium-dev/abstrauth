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
 * OAuth 2.0 Token Introspection Endpoint
 * RFC 7662 - OAuth 2.0 Token Introspection
 */
@Path("/oauth2/introspect")
@Tag(name = "OAuth 2.0 Token", description = "OAuth 2.0 Token management endpoints")
public class IntrospectionResource {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Token Introspection Endpoint",
        description = "Returns metadata about a token including whether it is active, " +
                     "expiration time, scope, client_id, and other claims. " +
                     "Requires client authentication."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Introspection response",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = IntrospectionResponse.class),
                examples = {
                    @ExampleObject(
                        name = "Active Token",
                        value = """
                        {
                            "active": true,
                            "scope": "openid profile email",
                            "client_id": "client_12345",
                            "username": "john.doe",
                            "token_type": "Bearer",
                            "exp": 1719302400,
                            "iat": 1719298800,
                            "sub": "user_67890",
                            "aud": "https://api.example.com",
                            "iss": "https://auth.example.com"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Inactive Token",
                        value = """
                        {
                            "active": false
                        }
                        """
                    )
                }
            )
        ),
        @APIResponse(
            responseCode = "401",
            description = "Client authentication failed"
        )
    })
    public Response introspect(
        @Parameter(
            description = "The token to introspect",
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

    /**
     * Introspection Response DTO for OpenAPI documentation
     */
    @Schema(description = "OAuth 2.0 Token Introspection Response")
    public static class IntrospectionResponse {
        @Schema(description = "Whether the token is currently active", example = "true", required = true)
        public Boolean active;

        @Schema(description = "Space-delimited list of scopes", example = "openid profile email")
        public String scope;

        @Schema(description = "Client identifier", example = "client_12345")
        public String client_id;

        @Schema(description = "Username of the resource owner", example = "john.doe")
        public String username;

        @Schema(description = "Type of the token", example = "Bearer")
        public String token_type;

        @Schema(description = "Token expiration timestamp (seconds since epoch)", example = "1719302400")
        public Long exp;

        @Schema(description = "Token issued at timestamp (seconds since epoch)", example = "1719298800")
        public Long iat;

        @Schema(description = "Token not before timestamp (seconds since epoch)", example = "1719298800")
        public Long nbf;

        @Schema(description = "Subject of the token", example = "user_67890")
        public String sub;

        @Schema(description = "Audience - intended recipients", example = "https://api.example.com")
        public String aud;

        @Schema(description = "Issuer identifier", example = "https://auth.example.com")
        public String iss;

        @Schema(description = "JWT ID - unique identifier", example = "jwt_abc123")
        public String jti;
    }
}
