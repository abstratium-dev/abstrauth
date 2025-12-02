package dev.abstratium.abstrauth.boundary.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.service.AccountRoleService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * OAuth 2.0 Token Endpoint
 * RFC 6749 Section 3.2 - Token Endpoint
 * RFC 7636 - PKCE Extension
 */
@Path("/oauth2/token")
@Tag(name = "OAuth 2.0 Token", description = "OAuth 2.0 Token management endpoints")
public class TokenResource {

    @Inject
    AuthorizationService authorizationService;

    @Inject
    OAuthClientService clientService;

    @Inject
    AccountService accountService;

    @Inject
    AccountRoleService accountRoleService;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "default.roles", defaultValue = "abstratium-abstrauth_user")
    String defaultRoles;

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
            example = "https://client.example.com/admin/callback"
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
        // Validate grant_type
        if (!"authorization_code".equals(grantType) && !"refresh_token".equals(grantType)) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "unsupported_grant_type",
                    "Grant type must be 'authorization_code' or 'refresh_token'");
        }

        // Handle authorization_code grant
        if ("authorization_code".equals(grantType)) {
            return handleAuthorizationCodeGrant(code, redirectUri, clientId, clientSecret, codeVerifier);
        }

        // Handle refresh_token grant (not implemented yet)
        return buildErrorResponse(Response.Status.BAD_REQUEST, "unsupported_grant_type",
                "Refresh token grant not yet implemented");
    }

    private Response handleAuthorizationCodeGrant(String code, String redirectUri, String clientId,
                                                   String clientSecret, String codeVerifier) {
        // Validate required parameters
        if (code == null || code.isBlank()) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "code is required");
        }

        if (clientId == null || clientId.isBlank()) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "client_id is required");
        }

        // Find authorization code
        Optional<AuthorizationCode> authCodeOpt = authorizationService.findAuthorizationCode(code);
        if (authCodeOpt.isEmpty()) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "Authorization code is invalid or expired");
        }

        AuthorizationCode authCode = authCodeOpt.get();

        // Check if code has been used
        if (authCode.getUsed()) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "Authorization code has already been used");
        }

        // Check if code is expired
        if (authCode.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "Authorization code has expired");
        }

        // Validate client_id matches
        if (!authCode.getClientId().equals(clientId)) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "Client ID does not match authorization code");
        }

        // Validate redirect_uri if provided
        if (redirectUri != null && !redirectUri.equals(authCode.getRedirectUri())) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "Redirect URI does not match authorization request");
        }

        // Validate PKCE code_verifier if code_challenge was used
        if (authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isBlank()) {
            if (codeVerifier == null || codeVerifier.isBlank()) {
                return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                        "code_verifier is required for PKCE");
            }

            if (!verifyPKCE(codeVerifier, authCode.getCodeChallenge(), authCode.getCodeChallengeMethod())) {
                return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                        "Invalid code_verifier");
            }
        }

        // Get account
        Account account = accountService.findById(authCode.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        // Get the authorization request to retrieve the auth method
        Optional<AuthorizationRequest> authRequestOpt = authorizationService.findAuthorizationRequest(authCode.getAuthorizationRequestId());
        String authMethod = authRequestOpt.map(AuthorizationRequest::getAuthMethod).orElse("unknown");

        // Mark code as used
        authorizationService.markCodeAsUsed(code);

        // Generate access token with the authentication method used for this session
        String accessToken = generateAccessToken(account, authCode.getScope(), clientId, authMethod);

        // Build token response
        TokenResponse response = new TokenResponse();
        response.access_token = accessToken;
        response.token_type = "Bearer";
        response.expires_in = 3600; // 1 hour
        response.scope = authCode.getScope();

        return Response.ok(response).build();
    }

    private boolean verifyPKCE(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
        try {
            String computedChallenge;
            
            if ("S256".equals(codeChallengeMethod)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
                computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            } else if ("plain".equals(codeChallengeMethod)) {
                computedChallenge = codeVerifier;
            } else {
                return false;
            }

            return computedChallenge.equals(codeChallenge);
        } catch (Exception e) {
            return false;
        }
    }

    private String generateAccessToken(Account account, String scope, String clientId, String authMethod) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600); // 1 hour

        // Get roles (groups) for this account and client from the database
        // Roles are stored as just the role name, so we need to prefix with clientId
        Set<String> dbRoles = accountRoleService.getRolesForAccountAndClient(account.getId(), clientId);
        Set<String> groups = new HashSet<>();
        for (String role : dbRoles) {
            groups.add(clientId + "_" + role);
        }
        
        // Add default roles from configuration (already in full format with client prefix)
        // This allows users to have roles without database entries, simplifying user management
        addDefaultRoles(groups, clientId);
        
        return Jwt.issuer(issuer)
                .upn(account.getEmail())
                .subject(account.getId())
                .groups(groups)
                .claim("email", account.getEmail())
                .claim("name", account.getName())
                .claim("email_verified", account.getEmailVerified())
                .claim("scope", scope)
                .claim("client_id", clientId)
                .claim("auth_method", authMethod)  // Use the auth method from this login session
                .issuedAt(now)
                .expiresAt(expiresAt)
                .sign();
    }

    /**
     * Add default roles from configuration to the groups set.
     * Roles are parsed from the default.roles configuration property.
     * 
     * Supported formats:
     * - "clientId_roleName" - Only added if clientId matches current client (e.g., "abstratium-abstrauth_user")
     * - "roleName" - Auto-prefixed with current clientId and added (e.g., "user" becomes "abstratium-abstrauth_user")
     * 
     * @param groups The set of groups to add default roles to
     * @param clientId The current OAuth client ID
     */
    private void addDefaultRoles(Set<String> groups, String clientId) {
        if (defaultRoles == null || defaultRoles.trim().isEmpty()) {
            return;
        }

        String[] roles = defaultRoles.split(",");
        for (String roleSpec : roles) {
            roleSpec = roleSpec.trim();
            if (roleSpec.isEmpty()) {
                continue;
            }

            // Role format: "clientId_roleName" - only add if it matches current client
            if (roleSpec.contains("_")) {
                int underscoreIndex = roleSpec.indexOf('_');
                String roleClientId = roleSpec.substring(0, underscoreIndex);
                
                if (roleClientId.equals(clientId)) {
                    // Add the full role name with client prefix
                    groups.add(roleSpec);
                }
            } else {
                // If no underscore, add it as it is, for all clients
                groups.add(clientId + "_" + roleSpec);
            }
        }
    }

    private Response buildErrorResponse(Response.Status status, String error, String errorDescription) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.error = error;
        errorResponse.error_description = errorDescription;
        return Response.status(status).entity(errorResponse).build();
    }

    /**
     * Token Response DTO for OpenAPI documentation
     */
    @Schema(description = "OAuth 2.0 Token Response")
    public static class TokenResponse {
        @Schema(description = "The access token issued by the authorization server", examples = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String access_token;

        @Schema(description = "The type of token - always 'Bearer'", examples = "Bearer")
        public String token_type;

        @Schema(description = "Token lifetime in seconds", examples = "3600")
        public Integer expires_in;

        @Schema(description = "Refresh token for obtaining new access tokens", examples = "tGzv3JOkF0XG5Qx2TlKWIA")
        public String refresh_token;

        @Schema(description = "Space-delimited list of granted scopes", examples = "openid profile email")
        public String scope;
    }

    /**
     * Error Response DTO for OpenAPI documentation
     */
    @Schema(description = "OAuth 2.0 Error Response")
    public static class ErrorResponse {
        @Schema(
            description = "Error code",
            examples = "invalid_grant",
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

        @Schema(description = "Human-readable error description", examples = "Authorization code is invalid or expired")
        public String error_description;

        @Schema(description = "URI identifying a human-readable web page with error information", examples = "https://auth.example.com/error/invalid_grant")
        public String error_uri;
    }
}
