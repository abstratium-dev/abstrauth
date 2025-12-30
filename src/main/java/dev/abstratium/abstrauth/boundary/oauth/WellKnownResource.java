package dev.abstratium.abstrauth.boundary.oauth;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * OAuth 2.0 Discovery and JWKS Endpoints
 * RFC 8414 - OAuth 2.0 Authorization Server Metadata
 * RFC 7517 - JSON Web Key (JWK)
 */
@Path("/.well-known")
@Tag(name = "OAuth 2.0 Discovery", description = "OAuth 2.0 server metadata and key discovery")
public class WellKnownResource {

    @ConfigProperty(name = "smallrye.jwt.sign.key")
    String privateKeyPem;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "abstrauth")
    String issuer;

    @ConfigProperty(name = "server.base.url", defaultValue = "http://localhost:8080")
    String baseUrl;

    JwksResponse jwksResponse;

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
        return Response.ok(buildMetadata()).build();
    }

    @GET
    @Path("/openid-configuration")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "OpenID Connect Discovery",
        description = "Returns OpenID Connect provider metadata (same as OAuth 2.0 metadata)"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "OpenID Connect provider metadata",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ServerMetadata.class)
            )
        )
    })
    public Response openidConfiguration() {
        return Response.ok(buildMetadata()).build();
    }

    private ServerMetadata buildMetadata() {
        ServerMetadata metadata = new ServerMetadata();
        metadata.issuer = baseUrl;
        metadata.authorization_endpoint = baseUrl + "/oauth2/authorize";
        metadata.token_endpoint = baseUrl + "/oauth2/token";
        metadata.introspection_endpoint = baseUrl + "/oauth2/introspect";
        metadata.revocation_endpoint = baseUrl + "/oauth2/revoke";
        metadata.end_session_endpoint = baseUrl + "/api/auth/logout";
        metadata.jwks_uri = baseUrl + "/.well-known/jwks.json";
        metadata.response_types_supported = new String[]{"code"};
        metadata.grant_types_supported = new String[]{"authorization_code", "refresh_token"};
        metadata.code_challenge_methods_supported = new String[]{"S256", "plain"};
        metadata.scopes_supported = new String[]{"openid", "profile", "email"};
        metadata.token_endpoint_auth_methods_supported = new String[]{"client_secret_post", "client_secret_basic"};
        
        return metadata;
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
        return Response.ok(this.jwksResponse).build();
    }
    
    @PostConstruct
    public void init() throws NoSuchAlgorithmException, InvalidKeySpecException {
        RSAPublicKey publicKey = extractPublicKey(privateKeyPem);
        
        JwkKey jwkKey = new JwkKey();
        jwkKey.kty = "RSA";
        jwkKey.use = "sig";
        jwkKey.kid = "abstrauth-key-1";
        jwkKey.alg = "PS256";
        
        // Extract modulus (n) and exponent (e) from public key
        jwkKey.n = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(publicKey.getModulus().toByteArray());
        jwkKey.e = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(publicKey.getPublicExponent().toByteArray());
        
        JwksResponse response = new JwksResponse();
        response.keys = new JwkKey[]{jwkKey};

        this.jwksResponse = response;
    }
    
    /**
     * Extract RSA public key from PEM-encoded private key
     */
    private RSAPublicKey extractPublicKey(String privateKeyPem) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Remove PEM headers/footers and whitespace
        String keyContent = privateKeyPem
            .replaceAll("-----BEGIN.*?-----", "")
            .replaceAll("-----END.*?-----", "")
            .replaceAll("\\s+", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        
        // Parse PKCS#8 format to extract RSA key components
        // This is a simplified parser for RSA keys
        java.security.KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        java.security.PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        
        // Get the public key from the private key
        if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey) {
            java.security.interfaces.RSAPrivateCrtKey rsaPrivateKey = 
                (java.security.interfaces.RSAPrivateCrtKey) privateKey;
            
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                rsaPrivateKey.getModulus(),
                rsaPrivateKey.getPublicExponent()
            );
            
            return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
        }
        
        throw new IllegalArgumentException("Private key is not an RSA CRT key");
    }

    @RegisterForReflection
    @Schema(description = "OAuth 2.0 Authorization Server Metadata")
    public static class ServerMetadata {
        @Schema(description = "Authorization server issuer identifier", examples = "https://auth.example.com")
        public String issuer;

        @Schema(description = "Authorization endpoint URL", examples = "https://auth.example.com/oauth2/authorize")
        public String authorization_endpoint;

        @Schema(description = "Token endpoint URL", examples = "https://auth.example.com/oauth2/token")
        public String token_endpoint;

        @Schema(description = "Token introspection endpoint URL", examples = "https://auth.example.com/oauth2/introspect")
        public String introspection_endpoint;

        @Schema(description = "Token revocation endpoint URL", examples = "https://auth.example.com/oauth2/revoke")
        public String revocation_endpoint;

        @Schema(description = "End session endpoint URL for RP-Initiated Logout", examples = "https://auth.example.com/oauth2/logout")
        public String end_session_endpoint;

        @Schema(description = "JWKS URI", examples = "https://auth.example.com/.well-known/jwks.json")
        public String jwks_uri;

        @Schema(description = "Supported response types", examples = "[\"code\"]")
        public String[] response_types_supported;

        @Schema(description = "Supported grant types", examples = "[\"authorization_code\", \"refresh_token\"]")
        public String[] grant_types_supported;

        @Schema(description = "Supported PKCE code challenge methods", examples = "[\"S256\", \"plain\"]")
        public String[] code_challenge_methods_supported;

        @Schema(description = "Supported scopes", examples = "[\"openid\", \"profile\", \"email\"]")
        public String[] scopes_supported;

        @Schema(description = "Supported token endpoint authentication methods", examples = "[\"client_secret_post\", \"client_secret_basic\"]")
        public String[] token_endpoint_auth_methods_supported;
    }

    @RegisterForReflection
    @Schema(description = "JSON Web Key Set")
    public static class JwksResponse {
        @Schema(description = "Array of JSON Web Keys")
        public JwkKey[] keys;
    }

    @RegisterForReflection
    @Schema(description = "JSON Web Key")
    public static class JwkKey {
        @Schema(description = "Key type", examples = "RSA")
        public String kty;

        @Schema(description = "Key use", examples = "sig")
        public String use;

        @Schema(description = "Key ID", examples = "key-2024-01")
        public String kid;

        @Schema(description = "Algorithm", examples = "RS256")
        public String alg;

        @Schema(description = "RSA modulus")
        public String n;

        @Schema(description = "RSA exponent")
        public String e;
    }
}
