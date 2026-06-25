package dev.abstratium.abstrauth.non_multitenancy.boundary;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.boundary.ErrorResponse;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAccountRoleService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyClientSecretService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOAuthClientService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancySubscriptionService;
import dev.abstratium.abstrauth.service.ClientAllowedRoleService;
import dev.abstratium.abstrauth.service.MetricsService;
import dev.abstratium.abstrauth.service.TokenRevocationService;
import dev.abstratium.abstrauth.util.JwtSignatureVerifier;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * RFC 8693 OAuth 2.0 Token Exchange Endpoint.
 *
 * Swaps an existing abstrauth access token for a new token scoped to a different
 * client, incorporating that client's role assignments for the same user.
 *
 * Located in non_multitenancy/boundary because:
 * - The subject token's orgId is used directly (not from the Hibernate session).
 * - Subscription checks and role lookups are cross-tenant by nature.
 */
@Path("/oauth2/token/exchange")
@Tag(name = "OAuth 2.0 Token Exchange", description = "RFC 8693 Token Exchange")
public class TokenExchangeResource {

    private static final Logger log = Logger.getLogger(TokenExchangeResource.class);

    private static final String GRANT_TYPE_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

    @Inject
    NonMultitenancyOAuthClientService nonMultitenancyOAuthClientService;

    @Inject
    NonMultitenancyClientSecretService nonMultitenancyClientSecretService;

    @Inject
    NonMultitenancySubscriptionService nonMultitenancySubscriptionService;

    @Inject
    NonMultitenancyAccountRoleService nonMultitenancyAccountRoleService;

    @Inject
    ClientAllowedRoleService clientAllowedRoleService;

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    MetricsService metricsService;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.verify.publickey")
    String publicKeyBase64;

    @ConfigProperty(name = "abstrauth.session.timeout.seconds", defaultValue = "900")
    int sessionTimeoutSeconds;

    @ConfigProperty(name = "abstrauth.token.exchange.max.depth", defaultValue = "3")
    int maxExchangeDepth;

    private JwtSignatureVerifier jwtSignatureVerifier;

    @PostConstruct
    public void init() {
        this.jwtSignatureVerifier = new JwtSignatureVerifier(publicKeyBase64);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Token Exchange (RFC 8693)",
        description = "Exchanges a valid abstrauth access token for a new token scoped to a different " +
                      "client. The new token carries the user's roles for the target client and is " +
                      "bound to the same subject and orgId as the original token."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successful token exchange",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = TokenExchangeResponse.class),
                examples = @ExampleObject(value = """
                    {
                        "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                        "token_type": "Bearer",
                        "expires_in": 900,
                        "scope": "openid profile",
                        "issued_token_type": "urn:ietf:params:oauth:token-type:access_token"
                    }
                    """)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request, invalid grant, invalid scope, or unauthorised client",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        ),
        @APIResponse(
            responseCode = "401",
            description = "Client authentication failed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
        )
    })
    public Response exchange(
        @Context HttpHeaders headers,

        @Parameter(description = "Must be 'urn:ietf:params:oauth:grant-type:token-exchange'", required = true)
        @FormParam("grant_type") String grantType,

        @Parameter(description = "The existing abstrauth access token to exchange", required = true)
        @FormParam("subject_token") String subjectToken,

        @Parameter(description = "Must be 'urn:ietf:params:oauth:token-type:access_token'", required = true)
        @FormParam("subject_token_type") String subjectTokenType,

        @Parameter(description = "Defaults to 'urn:ietf:params:oauth:token-type:access_token'")
        @FormParam("requested_token_type") String requestedTokenType,

        @Parameter(description = "The target client_id for which the new token should carry roles", required = true)
        @FormParam("audience") String audience,

        @Parameter(description = "The client making the exchange request", required = true)
        @FormParam("client_id") String clientId,

        @Parameter(description = "Secret of the requesting client (required for confidential clients)")
        @FormParam("client_secret") String clientSecret,

        @Parameter(description = "Space-delimited scope list; cannot exceed original token scope")
        @FormParam("scope") String scope,

        @Parameter(description = "JSON object with transaction context, e.g. {\"orderId\":\"abc123\"}")
        @FormParam("context") String context
    ) {
        metricsService.recordTokenExchangeRequest();

        // Extract client credentials from Basic Auth header if not provided as form params
        if ((clientId == null || clientId.isBlank()) && headers.getHeaderString("Authorization") != null) {
            String[] credentials = extractBasicAuth(headers.getHeaderString("Authorization"));
            if (credentials != null) {
                clientId = credentials[0];
                clientSecret = credentials[1];
            }
        }

        // --- Step 1: Validate grant_type ---
        if (!GRANT_TYPE_TOKEN_EXCHANGE.equals(grantType)) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "unsupported_grant_type",
                    "grant_type must be '" + GRANT_TYPE_TOKEN_EXCHANGE + "'");
        }

        // --- Step 2: Validate required parameters ---
        if (subjectToken == null || subjectToken.isBlank()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request", "subject_token is required");
        }
        if (subjectTokenType == null || !TOKEN_TYPE_ACCESS_TOKEN.equals(subjectTokenType)) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "subject_token_type must be '" + TOKEN_TYPE_ACCESS_TOKEN + "'");
        }
        if (audience == null || audience.isBlank()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request", "audience is required");
        }
        if (clientId == null || clientId.isBlank()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request", "client_id is required");
        }

        // --- Step 3: Parse and validate the subject token ---
        JsonObject subjectClaims;
        try {
            subjectClaims = decodeJwtPayload(subjectToken);
        } catch (IllegalArgumentException e) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "subject_token is not a valid JWT");
        }

        // Enforce delegation chain depth limit
        int currentDepth = actChainDepth(subjectClaims);
        if (currentDepth >= maxExchangeDepth) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "Maximum token exchange depth (" + maxExchangeDepth + ") exceeded");
        }

        // Verify issuer
        String tokenIssuer = subjectClaims.containsKey("iss") ? subjectClaims.getString("iss") : null;
        if (!issuer.equals(tokenIssuer)) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "subject_token issuer is invalid");
        }

        // Verify expiry
        long exp = subjectClaims.containsKey("exp") ? subjectClaims.getJsonNumber("exp").longValue() : 0;
        if (exp == 0 || Instant.now().getEpochSecond() >= exp) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "subject_token has expired");
        }

        // Verify nbf if present
        if (subjectClaims.containsKey("nbf")) {
            long nbf = subjectClaims.getJsonNumber("nbf").longValue();
            if (Instant.now().getEpochSecond() < nbf) {
                metricsService.recordTokenExchangeFailure();
                return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                        "subject_token is not yet valid");
            }
        }

        // Check JTI revocation
        String jti = subjectClaims.containsKey("jti") ? subjectClaims.getString("jti") : null;
        if (jti != null && tokenRevocationService.isTokenRevoked(jti)) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
                    "subject_token has been revoked");
        }

        // --- Step 4: Extract claims from subject token ---
        String subjectAccountId = subjectClaims.containsKey("sub") ? subjectClaims.getString("sub") : null;
        String subjectOrgId = subjectClaims.containsKey("orgId") ? subjectClaims.getString("orgId") : null;
        String originalScope = subjectClaims.containsKey("scope") ? subjectClaims.getString("scope") : "";
        String authMethod = subjectClaims.containsKey("auth_method") ? subjectClaims.getString("auth_method") : "unknown";
        String originalTxn = subjectClaims.containsKey("txn") ? subjectClaims.getString("txn") : null;
        JsonObject inheritedCtx = subjectClaims.containsKey("ctx") ? subjectClaims.getJsonObject("ctx") : null;

        if (subjectAccountId == null || subjectOrgId == null) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "subject_token is missing required claims (sub, orgId)");
        }

        // --- Step 5: Authenticate the calling client ---
        var callerClientOpt = nonMultitenancyOAuthClientService.findByClientId(clientId);
        if (callerClientOpt.isEmpty()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                    "Client authentication failed");
        }
        var callerClient = callerClientOpt.get();
        if ("confidential".equals(callerClient.getClientType())) {
            if (!authenticateClient(clientId, clientSecret)) {
                metricsService.recordTokenExchangeFailure();
                return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                        "Client authentication failed");
            }
        }

        // --- Step 6: Authorise the exchange via subscription checks ---
        if (nonMultitenancyOAuthClientService.findByClientId(audience).isEmpty()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_request",
                    "audience client does not exist");
        }
        if (nonMultitenancySubscriptionService.findNonMultitenancySubscription(subjectOrgId, audience).isEmpty()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "unauthorized_client",
                    "User's organisation is not subscribed to the requested audience");
        }
        if (nonMultitenancySubscriptionService.findNonMultitenancySubscription(subjectOrgId, clientId).isEmpty()) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "unauthorized_client",
                    "User's organisation is not subscribed to the calling client");
        }

        // --- Step 7: Intersect requested scope (before DB writes) ---
        Set<String> originalScopes = parseScopes(originalScope);
        Set<String> requestedScopes = scope != null && !scope.isBlank() ? parseScopes(scope) : new HashSet<>(originalScopes);
        Set<String> grantedScopes = new HashSet<>(requestedScopes);
        grantedScopes.retainAll(originalScopes);

        if (scope != null && !scope.isBlank() && !originalScopes.containsAll(requestedScopes)) {
            metricsService.recordTokenExchangeFailure();
            return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_scope",
                    "Requested scope exceeds the scope of the subject token");
        }

        // --- Step 8: Resolve target roles (with default role seeding) ---
        if (!nonMultitenancyAccountRoleService.hasAnyRoleForClient(subjectAccountId, audience, subjectOrgId)) {
            var defaultRoles = clientAllowedRoleService.findDefaultRolesByClientIdForOrg(audience, subjectOrgId);
            if (!defaultRoles.isEmpty()) {
                nonMultitenancyAccountRoleService.seedDefaultRoles(subjectAccountId, audience, subjectOrgId, defaultRoles);
            }
        }
        Set<String> dbRoles = nonMultitenancyAccountRoleService.findRolesByAccountIdAndClientIdAndOrgId(
                subjectAccountId, audience, subjectOrgId);

        String displayAudienceId = dev.abstratium.abstrauth.util.ClientIdUtil.stripOrgPrefix(audience);
        Set<String> groups = new HashSet<>();
        for (String role : dbRoles) {
            groups.add(displayAudienceId + "_" + role);
        }

        // --- Step 9: Build the act claim (RFC 8693 §4.1 actor chaining) ---
        // The new act wraps the caller; any existing act chain is nested inside it.
        var actBuilder = Json.createObjectBuilder().add("sub", clientId);
        if (subjectClaims.containsKey("act")) {
            actBuilder.add("act", subjectClaims.getJsonObject("act"));
        }
        JsonObject actClaim = actBuilder.build();

        // --- Step 10: Generate the new access token ---
        Instant now = Instant.now();
        String newJti = UUID.randomUUID().toString();
        String grantedScopeString = String.join(" ", grantedScopes);

        var jwtBuilder = Jwt.issuer(issuer)
                .claim("jti", newJti)
                .subject(subjectAccountId)
                .audience(audience)
                .groups(groups)
                .claim("scope", grantedScopeString)
                .claim("client_id", audience)
                .claim("auth_method", authMethod)
                .claim("orgId", subjectOrgId)
                .claim("act", actClaim)
                .claim("txn", originalTxn != null ? originalTxn : UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(sessionTimeoutSeconds));

        // Scope-based claim filtering for profile/email
        if (grantedScopes.contains("email") && subjectClaims.containsKey("email")) {
            jwtBuilder.claim("email", subjectClaims.getString("email"));
        }
        if (grantedScopes.contains("profile") && subjectClaims.containsKey("name")) {
            jwtBuilder.claim("name", subjectClaims.getString("name"));
        }

        // Inherit or override ctx (transaction context)
        JsonObject resolvedCtx = null;
        if (context != null && !context.isBlank()) {
            try {
                resolvedCtx = Json.createReader(new java.io.StringReader(context)).readObject();
            } catch (Exception e) {
                log.warnf("Ignoring unparseable context parameter: %s", e.getMessage());
            }
        }
        if (resolvedCtx != null) {
            jwtBuilder.claim("ctx", resolvedCtx);
        } else if (inheritedCtx != null) {
            jwtBuilder.claim("ctx", inheritedCtx);
        }

        String newAccessToken = jwtBuilder.jws()
                .keyId("abstrauth-key-1")
                .sign();

        TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
        tokenResponse.access_token = newAccessToken;
        tokenResponse.token_type = "Bearer";
        tokenResponse.expires_in = sessionTimeoutSeconds;
        tokenResponse.scope = grantedScopeString;
        tokenResponse.issued_token_type = TOKEN_TYPE_ACCESS_TOKEN;

        metricsService.recordTokenExchangeSuccess();
        return Response.ok(tokenResponse).build();
    }

    /**
     * Delegate to the shared verifier to decode and cryptographically verify the
     * signature of the subject JWT access token.
     */
    private JsonObject decodeJwtPayload(String jwt) {
        return jwtSignatureVerifier.verifyAndDecode(jwt);
    }

    private boolean authenticateClient(String clientIdParam, String clientSecretParam) {
        if (clientSecretParam == null || clientSecretParam.isBlank()) {
            return false;
        }
        var activeSecrets = nonMultitenancyClientSecretService.findActiveSecrets(clientIdParam);
        if (activeSecrets.isEmpty()) {
            return false;
        }
        try {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            return activeSecrets.stream()
                    .anyMatch(secret -> encoder.matches(clientSecretParam, secret.getSecretHash()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String[] extractBasicAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return null;
        }
        try {
            String base64 = authHeader.substring(6);
            byte[] decoded = Base64.getDecoder().decode(base64);
            String credentials = new String(decoded, StandardCharsets.UTF_8);
            int colon = credentials.indexOf(':');
            if (colon == -1) {
                return null;
            }
            return new String[]{credentials.substring(0, colon), credentials.substring(colon + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the current delegation chain depth by counting nested {@code act} claims
     * per RFC 8693 §4.1. A token with no {@code act} has depth 0. A once-exchanged
     * token has depth 1. A twice-exchanged token has depth 2, and so on.
     */
    private int actChainDepth(JsonObject claims) {
        int depth = 0;
        JsonObject current = claims;
        while (current.containsKey("act")) {
            depth++;
            current = current.getJsonObject("act");
        }
        return depth;
    }

    private Set<String> parseScopes(String scopeString) {
        if (scopeString == null || scopeString.isBlank()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(scopeString.trim().split("\\s+")));
    }

    private Response buildErrorResponse(Response.Status status, String error, String errorDescription) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.error = error;
        errorResponse.error_description = errorDescription;
        return Response.status(status).entity(errorResponse).build();
    }

    @RegisterForReflection
    @Schema(description = "RFC 8693 Token Exchange Response")
    public static class TokenExchangeResponse {
        @Schema(description = "The newly issued access token", examples = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String access_token;

        @Schema(description = "Always 'Bearer'", examples = "Bearer")
        public String token_type;

        @Schema(description = "Token lifetime in seconds", examples = "900")
        public Integer expires_in;

        @Schema(description = "Granted scope", examples = "openid profile")
        public String scope;

        @Schema(description = "Type of issued token", examples = "urn:ietf:params:oauth:token-type:access_token")
        public String issued_token_type;
    }
}
