package dev.abstratium.abstrauth.boundary;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * OAuth 2.0 Discovery and JWKS Endpoints
 * RFC 8414 - OAuth 2.0 Authorization Server Metadata
 * RFC 7517 - JSON Web Key (JWK)
 */
@Path("/.well-known")
@Tag(name = "OAuth 2.0 Discovery", description = "OAuth 2.0 server metadata and key discovery")
public class WellKnownResource {

    @GET
    @Path("/oauth-authorization-server")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "OAuth 2.0 Authorization Server Metadata",
        description = "Returns OAuth 2.0 authorization server metadata including endpoints, " +
                     "supported grant types, response types, and other capabilities."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Authorization server metadata",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ServerMetadata.class)
            )
        )
    })
    public Response serverMetadata() {
        // Implementation pending
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "JSON Web Key Set",
        description = "Returns the public keys used by the authorization server to sign tokens. " +
                     "Clients use these keys to verify JWT signatures."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "JSON Web Key Set",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = JwksResponse.class)
            )
        )
    })
    public Response jwks() {
        // Implementation pending
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Schema(description = "OAuth 2.0 Authorization Server Metadata")
    public static class ServerMetadata {
        @Schema(description = "Authorization server issuer identifier", example = "https://auth.example.com")
        public String issuer;

        @Schema(description = "Authorization endpoint URL", example = "https://auth.example.com/oauth2/authorize")
        public String authorization_endpoint;

        @Schema(description = "Token endpoint URL", example = "https://auth.example.com/oauth2/token")
        public String token_endpoint;

        @Schema(description = "Token introspection endpoint URL", example = "https://auth.example.com/oauth2/introspect")
        public String introspection_endpoint;

        @Schema(description = "Token revocation endpoint URL", example = "https://auth.example.com/oauth2/revoke")
        public String revocation_endpoint;

        @Schema(description = "JWKS URI", example = "https://auth.example.com/.well-known/jwks.json")
        public String jwks_uri;

        @Schema(description = "Supported response types", example = "[\"code\"]")
        public String[] response_types_supported;

        @Schema(description = "Supported grant types", example = "[\"authorization_code\", \"refresh_token\"]")
        public String[] grant_types_supported;

        @Schema(description = "Supported PKCE code challenge methods", example = "[\"S256\", \"plain\"]")
        public String[] code_challenge_methods_supported;

        @Schema(description = "Supported scopes", example = "[\"openid\", \"profile\", \"email\"]")
        public String[] scopes_supported;

        @Schema(description = "Supported token endpoint authentication methods", example = "[\"client_secret_post\", \"client_secret_basic\"]")
        public String[] token_endpoint_auth_methods_supported;
    }

    @Schema(description = "JSON Web Key Set")
    public static class JwksResponse {
        @Schema(description = "Array of JSON Web Keys")
        public JwkKey[] keys;
    }

    @Schema(description = "JSON Web Key")
    public static class JwkKey {
        @Schema(description = "Key type", example = "RSA")
        public String kty;

        @Schema(description = "Key use", example = "sig")
        public String use;

        @Schema(description = "Key ID", example = "key-2024-01")
        public String kid;

        @Schema(description = "Algorithm", example = "RS256")
        public String alg;

        @Schema(description = "RSA modulus")
        public String n;

        @Schema(description = "RSA exponent")
        public String e;
    }
}
