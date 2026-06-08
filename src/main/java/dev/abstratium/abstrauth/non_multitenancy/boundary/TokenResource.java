package dev.abstratium.abstrauth.non_multitenancy.boundary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyClientSecretService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.ClientAllowedRoleService;
import dev.abstratium.abstrauth.service.ClientSecretService;
import dev.abstratium.abstrauth.service.MetricsService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.service.ServiceAccountRoleService;
import dev.abstratium.abstrauth.service.TokenRevocationService;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
 * 
 * NOTE: This endpoint is located in the non_multitenancy package because it uses
 * NonMultitenancyAccountRoleService to retrieve roles without tenant discrimination
 * during token generation. This is necessary because the orgId comes from the
 * AuthorizationRequest (not the JWT) during the token exchange flow.
 */
@Path("/oauth2/token")
@Tag(name = "OAuth 2.0 Token", description = "OAuth 2.0 Token management endpoints")
public class TokenResource {

    @Inject
    AuthorizationService authorizationService;

    @Inject
    OAuthClientService clientService;

    @Inject
    ClientSecretService clientSecretService;

    @Inject
    NonMultitenancyClientSecretService nonMultitenancyClientSecretService;

    @Inject
    AccountService accountService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    ServiceAccountRoleService serviceAccountRoleService;

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    OrganisationService organisationService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    MetricsService metricsService;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "abstrauth.session.timeout.seconds", defaultValue = "900")
    int sessionTimeoutSeconds;

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
        @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
        
        @Parameter(
            description = "Grant type - 'authorization_code', 'refresh_token', or 'client_credentials'",
            required = true,
            example = "authorization_code",
            schema = @Schema(enumeration = {"authorization_code", "refresh_token", "client_credentials"})
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
            required = false,
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
        // Extract client credentials from HTTP Basic Auth if not in form params
        if ((clientId == null || clientId.isBlank()) && headers.getHeaderString("Authorization") != null) {
            String[] credentials = extractBasicAuth(headers.getHeaderString("Authorization"));
            if (credentials != null) {
                clientId = credentials[0];
                clientSecret = credentials[1];
            }
        }
        // Record token request
        metricsService.recordTokenRequest();

        // Validate grant_type
        if (!"authorization_code".equals(grantType) && 
            !"refresh_token".equals(grantType) && 
            !"client_credentials".equals(grantType)) {
            metricsService.recordTokenRequestFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "unsupported_grant_type",
                    "Grant type must be 'authorization_code', 'refresh_token', or 'client_credentials'");
        }

        // Handle authorization_code grant
        if ("authorization_code".equals(grantType)) {
            return handleAuthorizationCodeGrant(code, redirectUri, clientId, clientSecret, codeVerifier);
        }

        // Handle client_credentials grant
        if ("client_credentials".equals(grantType)) {
            return handleClientCredentials(clientId, clientSecret, scope);
        }

        // Handle refresh_token grant (not implemented yet)
        metricsService.recordTokenRequestFailure();
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

        // Authenticate confidential clients
        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isPresent()) {
            OAuthClient client = clientOpt.get();
            if ("confidential".equals(client.getClientType())) {
                // Confidential clients MUST authenticate with client_secret
                if (!authenticateClient(client, clientSecret)) {
                    return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                            "Client authentication failed");
                }
            }
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
            // SECURITY: Authorization code replay attack detected!
            // RFC 6749 Section 10.5: "If an authorization code is used more than once,
            // the authorization server MUST deny the request and SHOULD revoke all tokens
            // previously issued based on that authorization code."
            tokenRevocationService.revokeTokensByAuthorizationCode(
                authCode.getId(), 
                "authorization_code_replay_detected"
            );
            
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
                        "PKCE code_verifier verification failed");
            }
        }

        // Get account
        Account account = accountService.findById(authCode.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        // Get the authorization request to retrieve the auth method and orgId
        Optional<AuthorizationRequest> authRequestOpt = authorizationService.findAuthorizationRequest(authCode.getAuthorizationRequestId());
        AuthorizationRequest authRequest = authRequestOpt.orElse(null);
        String authMethod = authRequest != null ? authRequest.getAuthMethod() : "unknown";
        String orgId = authRequest != null ? authRequest.getOrgId() : null;

        // Verify account is still a member of the selected org (if orgId is set)
        if (orgId != null && !organisationService.isMember(orgId, account.getId())) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "Account is no longer a member of the selected organisation");
        }

        // Seed default roles if no AccountRole rows exist for this account + clientId + orgId
        if (orgId != null && !nonMultitenancyAccountRoleService.hasAnyRoleForClient(account.getId(), clientId, orgId)) {
            var defaultRoles = clientAllowedRoleService.findDefaultRolesByClientId(clientId);
            if (!defaultRoles.isEmpty()) {
                nonMultitenancyAccountRoleService.seedDefaultRoles(account.getId(), clientId, orgId, defaultRoles);
            }
        }

        // Mark code as used
        authorizationService.markCodeAsUsed(code);

        // Generate access token with the authentication method and orgId used for this session
        String accessToken = generateAccessToken(account, clientId, authMethod, authCode.getScope(), orgId);
        
        // Generate ID token for OIDC (if openid scope is requested)
        String idToken = null;
        if (authCode.getScope() != null && authCode.getScope().contains("openid")) {
            idToken = generateIdToken(account, clientId, authMethod, authCode.getScope(), orgId);
        }

        // Record metrics
        metricsService.recordTokenRequestSuccess();

        // Build response
        TokenResponse response = new TokenResponse();
        response.access_token = accessToken;
        response.token_type = "Bearer";
        response.expires_in = sessionTimeoutSeconds;
        response.id_token = idToken;
        response.scope = authCode.getScope();

        return Response.ok(response).build();
    }

    /**
     * Verify PKCE code_verifier against code_challenge.
     * RFC 7636 - Proof Key for Code Exchange by OAuth Public Clients
     * 
     * @param codeVerifier The code_verifier from the client
     * @param codeChallenge The code_challenge stored in the authorization code
     * @param codeChallengeMethod The code_challenge_method (S256 or plain)
     * @return true if verification succeeds, false otherwise
     */
    private boolean verifyPKCE(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }

        // Default to S256 if not specified
        String method = codeChallengeMethod != null ? codeChallengeMethod : "S256";

        if ("plain".equals(method)) {
            // For "plain" method, direct comparison
            return codeVerifier.equals(codeChallenge);
        } else if ("S256".equals(method)) {
            // For "S256" method, hash the verifier with SHA-256 and base64url encode
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                String computedChallenge = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(hash);
                return computedChallenge.equals(codeChallenge);
            } catch (Exception e) {
                return false;
            }
        }

        // Unknown method
        return false;
    }

    /**
     * Generate access token with RFC-compliant scope-based claim filtering.
     * 
     * This method uses NonMultitenancyAccountRoleService to retrieve roles because
     * during token generation the orgId comes from the AuthorizationRequest (not the JWT),
     * so we cannot rely on Hibernate's @TenantId discriminator.
     * 
     * @param account The authenticated user account
     * @param clientId The OAuth client ID
     * @param authMethod The authentication method used
     * @param scope Space-delimited scope string from the authorization request
     * @param orgId The organization ID from the authorization request
     * @return Signed JWT access token
     */
    private String generateAccessToken(Account account, String clientId, String authMethod, String scope, String orgId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(sessionTimeoutSeconds);

        // Generate unique JTI (JWT ID) for token revocation support
        String jti = UUID.randomUUID().toString();

        // Get roles (groups) for this account and client from the database
        // Uses non-multitenancy service because orgId comes from AuthorizationRequest, not JWT
        Set<String> dbRoles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(account.getId(), clientId, orgId);
        Set<String> groups = new HashSet<>();
        for (String role : dbRoles) {
            groups.add(clientId + "_" + role);
        }
        
        // Parse scopes for claim filtering
        Set<String> scopes = parseScopes(scope);
        
        // Build JWT with mandatory claims
        var jwtBuilder = Jwt.issuer(issuer)
                .claim("jti", jti)  // JWT ID for token revocation
                .subject(account.getId())  // ALWAYS include sub - it's the primary subject identifier
                .groups(groups)  // ALWAYS include groups for @RolesAllowed authorization
                .claim("scope", scope)
                .claim("client_id", clientId)
                .claim("auth_method", authMethod)
                .issuedAt(now)
                .expiresAt(expiresAt);
        
        // Emit orgId claim if available (tenant context for downstream applications)
        if (orgId != null) {
            jwtBuilder.claim("orgId", orgId);
        }
        
        // RFC-compliant scope-based claim filtering:
        // Add 'email' scope claims (OpenID Connect Core 1.0 Section 5.4)
        if (scopes.contains("email")) {
            jwtBuilder.upn(account.getEmail());  // User Principal Name for MicroProfile JWT
            jwtBuilder.claim("email", account.getEmail());
            jwtBuilder.claim("email_verified", account.getEmailVerified());
        }
        
        // Add 'profile' scope claims (OpenID Connect Core 1.0 Section 5.4)
        if (scopes.contains("profile")) {
            jwtBuilder.claim("name", account.getName());
            // Note: We only store 'name' currently. In the future, you could add:
            // family_name, given_name, middle_name, nickname, preferred_username,
            // profile, picture, website, gender, birthdate, zoneinfo, locale, updated_at
        }
        
        return jwtBuilder.jws()
                    .keyId("abstrauth-key-1")  // Must match kid in JWKS
                .sign();
    }

    /**
     * Generate OpenID Connect ID Token with RFC-compliant scope-based claim filtering.
     * 
     * ID tokens are used to convey user identity information to the client.
     * According to OpenID Connect Core 1.0:
     * - ID tokens MUST contain: iss, sub, aud, exp, iat (Section 2)
     * - Additional claims are controlled by scopes requested during authorization
     * - 'profile' scope: name and other profile claims
     * - 'email' scope: email, email_verified
     * 
     * Note: This method is only called when 'openid' scope is present, so we know
     * the client requested OpenID Connect authentication.
     * 
     * This method uses NonMultitenancyAccountRoleService because the orgId comes from
     * the AuthorizationRequest, not the JWT, during token generation.
     * 
     * @param account The authenticated user account
     * @param clientId The OAuth client ID
     * @param authMethod The authentication method used
     * @param scope Space-delimited scope string from the authorization request
     * @param orgId The organization ID from the authorization request
     * @return Signed JWT ID token
     */
    private String generateIdToken(Account account, String clientId, String authMethod, String scope, String orgId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(sessionTimeoutSeconds);

        // Get roles (groups) for this account and client
        // Uses non-multitenancy service because orgId comes from AuthorizationRequest, not JWT
        Set<String> dbRoles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(account.getId(), clientId, orgId);
        Set<String> groups = new HashSet<>();
        for (String role : dbRoles) {
            groups.add(clientId + "_" + role);
        }
        
        // Parse scopes for claim filtering
        Set<String> scopes = parseScopes(scope);

        // Build ID token with mandatory claims (OpenID Connect Core 1.0 Section 2)
        var jwtBuilder = Jwt.issuer(issuer)
                .subject(account.getId())  // REQUIRED: Subject identifier
                .audience(clientId)  // REQUIRED: ID token audience is the client_id
                .claim("jti", UUID.randomUUID().toString())  // Unique token ID
                .claim("auth_method", authMethod)
                .groups(groups)  // Add groups/roles for @RolesAllowed authorization
                .issuedAt(now)  // REQUIRED: Issued at time
                .expiresAt(expiresAt);  // REQUIRED: Expiration time
        
        // Emit orgId claim if available (tenant context for downstream applications)
        if (orgId != null) {
            jwtBuilder.claim("orgId", orgId);
        }
        
        // RFC-compliant scope-based claim filtering:
        // Add 'email' scope claims (OpenID Connect Core 1.0 Section 5.4)
        if (scopes.contains("email")) {
            jwtBuilder.upn(account.getEmail());  // User principal name for MicroProfile JWT
            jwtBuilder.claim("email", account.getEmail());
            jwtBuilder.claim("email_verified", account.getEmailVerified());
        }
        
        // Add 'profile' scope claims (OpenID Connect Core 1.0 Section 5.4)
        if (scopes.contains("profile")) {
            jwtBuilder.claim("name", account.getName());
            // Note: We only store 'name' currently. In the future, you could add:
            // family_name, given_name, middle_name, nickname, preferred_username,
            // profile, picture, website, gender, birthdate, zoneinfo, locale, updated_at
        }
        
        return jwtBuilder.jws()
                    .keyId("abstrauth-key-1")  // CRITICAL: Must match kid in JWKS
                .sign();
    }


    /**
     * Handle client credentials grant (RFC 6749 Section 4.4)
     * Used for service-to-service authentication
     */
    private Response handleClientCredentials(String clientId, String clientSecret, String requestedScope) {
        // 1. Validate required parameters
        if (clientId == null || clientId.isBlank()) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "client_id is required");
        }

        if (clientSecret == null || clientSecret.isBlank()) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "client_secret is required");
        }

        // 2. Validate client exists and is a confidential client
        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                    "Client authentication failed");
        }

        OAuthClient client = clientOpt.get();

        // 3. Authenticate client with secret
        if (!authenticateClient(client, clientSecret)) {
            return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                    "Client authentication failed");
        }

        // 4. Parse and validate requested scopes against allowed scopes
        Set<String> allowedScopes = parseScopes(client.getAllowedScopes());
        Set<String> requestedScopes = parseScopes(requestedScope);

        // If no scope requested, use all allowed scopes
        if (requestedScopes.isEmpty()) {
            requestedScopes = allowedScopes;
        }

        // Validate that all requested scopes are allowed
        if (!allowedScopes.containsAll(requestedScopes)) {
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_scope",
                    "Requested scope exceeds allowed scopes for this client");
        }

        // 5. Get service account roles for @RolesAllowed support
        Set<String> serviceRoles = serviceAccountRoleService.findRolesByClientId(clientId);
        Set<String> groups = new HashSet<>();
        for (String role : serviceRoles) {
            groups.add(clientId + "_" + role);  // Same format as user roles
        }

        // 6. Generate service token with BOTH scopes AND groups
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600);  // 1 hour for service tokens

        String accessToken = Jwt.issuer(issuer)
                .claim("jti", UUID.randomUUID().toString())
                .subject(clientId)  // Service ID as subject (for audit logging)
                .groups(groups)     // Roles for @RolesAllowed
                .claim("client_id", clientId)
                .claim("scope", String.join(" ", requestedScopes))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .jws()
                    .keyId("abstrauth-key-1")
                .sign();

        // 7. Return token response (no refresh token for client credentials)
        TokenResponse response = new TokenResponse();
        response.access_token = accessToken;
        response.token_type = "Bearer";
        response.expires_in = 3600;
        response.scope = String.join(" ", requestedScopes);

        metricsService.recordTokenRequestSuccess();
        return Response.ok(response).build();
    }

    /**
     * Parse space-separated scope string into a Set
     */
    private Set<String> parseScopes(String scopeString) {
        if (scopeString == null || scopeString.isBlank()) {
            return new HashSet<>();
        }
        return new HashSet<>(java.util.Arrays.asList(scopeString.trim().split("\\s+")));
    }

    /**
     * Authenticate a confidential client using its client_secret.
     * Uses BCrypt to verify the secret against the stored hash.
     *
     * @param client The OAuth client
     * @param clientSecret The client secret provided in the request
     * @return true if authentication succeeds, false otherwise
     */
    private boolean authenticateClient(OAuthClient client, String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            return false;
        }

        // Public clients don't have secrets
        if ("public".equals(client.getClientType())) {
            return false;
        }

        // Get all active secrets for this client using non-multitenancy service
        // Client secrets are owned by the client-owning org, not the user's org
        var activeSecrets = nonMultitenancyClientSecretService.findActiveSecrets(client.getClientId());
        if (activeSecrets.isEmpty()) {
            return false;
        }

        // Verify secret against all active secrets using BCrypt
        try {
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            return activeSecrets.stream()
                .anyMatch(secret -> passwordEncoder.matches(clientSecret, secret.getSecretHash()));
        } catch (IllegalArgumentException e) {
            // Invalid hash format
            return false;
        }
    }

    /**
     * Extract client credentials from HTTP Basic Auth header
     * @param authHeader Authorization header value
     * @return Array with [clientId, clientSecret] or null if invalid
     */
    private String[] extractBasicAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return null;
        }
        
        try {
            String base64Credentials = authHeader.substring(6);
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
            
            int colonIndex = credentials.indexOf(':');
            if (colonIndex == -1) {
                return null;
            }
            
            String extractedClientId = credentials.substring(0, colonIndex);
            String extractedClientSecret = credentials.substring(colonIndex + 1);
            
            return new String[]{extractedClientId, extractedClientSecret};
        } catch (IllegalArgumentException e) {
            return null;
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
    @RegisterForReflection
    @Schema(description = "OAuth 2.0 / OpenID Connect Token Response")
    public static class TokenResponse {
        @Schema(description = "The access token issued by the authorization server", examples = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String access_token;

        @Schema(description = "The type of token - always 'Bearer'", examples = "Bearer")
        public String token_type;

        @Schema(description = "Token lifetime in seconds", examples = "3600")
        public Integer expires_in;

        @Schema(description = "Refresh token for obtaining new access tokens", examples = "tGzv3JOkF0XG5Qx2TlKWIA")
        public String refresh_token;

        @Schema(description = "OpenID Connect ID Token (only present when openid scope is requested)", examples = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String id_token;

        @Schema(description = "Space-delimited list of granted scopes", examples = "openid profile email")
        public String scope;
    }

}
