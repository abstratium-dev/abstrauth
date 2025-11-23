package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationCode;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * OAuth 2.0 Authorization Endpoint
 * RFC 6749 Section 3.1 - Authorization Endpoint
 * RFC 7636 - PKCE Extension
 */
@Path("/oauth2/authorize")
@Tag(name = "OAuth 2.0 Authorization", description = "OAuth 2.0 Authorization Code Flow endpoints")
public class AuthorizationResource {

    @Inject
    OAuthClientService clientService;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    AccountService accountService;

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
            example = "https://client.example.com/admin/callback"
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
        // Validate response_type
        if (!"code".equals(responseType)) {
            return buildErrorRedirect(redirectUri, "unsupported_response_type", 
                    "Only 'code' response type is supported", state);
        }

        // Validate client_id
        if (clientId == null || clientId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>client_id is required</p></body></html>")
                    .build();
        }

        Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Invalid client_id</p></body></html>")
                    .build();
        }

        OAuthClient client = clientOpt.get();

        // Validate redirect_uri
        if (redirectUri == null || redirectUri.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>redirect_uri is required</p></body></html>")
                    .build();
        }

        if (!clientService.isRedirectUriAllowed(client, redirectUri)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Invalid redirect_uri</p></body></html>")
                    .build();
        }

        // Validate scope
        if (!clientService.isScopeAllowed(client, scope)) {
            return buildErrorRedirect(redirectUri, "invalid_scope", 
                    "Requested scope is not allowed", state);
        }

        // Validate PKCE if required
        if (client.getRequirePkce() && (codeChallenge == null || codeChallenge.isBlank())) {
            return buildErrorRedirect(redirectUri, "invalid_request", 
                    "code_challenge is required for this client", state);
        }

        if (codeChallenge != null && (codeChallengeMethod == null || codeChallengeMethod.isBlank())) {
            codeChallengeMethod = "plain"; // Default to plain if not specified
        }

        if (codeChallengeMethod != null && 
            !"S256".equals(codeChallengeMethod) && !"plain".equals(codeChallengeMethod)) {
            return buildErrorRedirect(redirectUri, "invalid_request", 
                    "code_challenge_method must be 'S256' or 'plain'", state);
        }

        // Create authorization request
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod);

        return Response.seeOther(URI.create("/signin/" + authRequest.getId())).build();
    }

    @GET
    @Path("/details/{requestId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Authorization Request Details",
        description = "Fetches the details to a request made previously using GET /authorize"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Authorization request details",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AuthRequestDetails.class),
                examples = {
                    @ExampleObject(
                        name = "Authorization Request Details",
                        value = """
                        {
                            "clientName": "Example Client",
                            "scope": "openid profile email"
                        }
                        """
                    )
                }
            )
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
    public Response getAuthorizationRequestDetails(
        @Parameter(description = "Authorization request identifier", required = true)
        @PathParam("requestId") String requestId
    ) {
        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(requestId);
        if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AuthorizationRequest authRequest = requestOpt.get();
        OAuthClient client = clientService.findByClientId(authRequest.getClientId()).get();

        return Response.ok(new AuthRequestDetails(client.getClientName(), authRequest.getScope())).build();
    }

    public record AuthRequestDetails(String clientName, String scope) {

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
        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(requestId);
        if (requestOpt.isEmpty() || !"approved".equals(requestOpt.get().getStatus())) { // it is approved when the user signs in with the right password
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>Invalid request</p></body></html>")
                    .build();
        }

        AuthorizationRequest authRequest = requestOpt.get();

        if (!"approve".equals(consent)) {
            // User denied consent
            return buildErrorRedirect(authRequest.getRedirectUri(), "access_denied",
                    "User denied authorization", authRequest.getState());
        }

        // Generate authorization code
        AuthorizationCode authCode = authorizationService.generateAuthorizationCode(requestId);

        // Redirect back to client with authorization code
        String redirectUrl = authRequest.getRedirectUri() +
                (authRequest.getRedirectUri().contains("?") ? "&" : "?") +
                "code=" + URLEncoder.encode(authCode.getCode(), StandardCharsets.UTF_8);

        if (authRequest.getState() != null && !authRequest.getState().isBlank()) {
            redirectUrl += "&state=" + URLEncoder.encode(authRequest.getState(), StandardCharsets.UTF_8);
        }

        return Response.seeOther(URI.create(redirectUrl)).build();
    }

    @POST
    @Path("/authenticate")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Authenticate user", description = "Authenticates user and shows consent page")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "User authenticated successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AuthenticationResponse.class),
                examples = {
                    @ExampleObject(
                        name = "Authorization Request Details",
                        value = """
                        {
                            "clientName": "Example Client",
                            "scope": "openid profile email"
                        }
                        """
                    )
                }
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid username or password"
        ),
        @APIResponse(
            responseCode = "401",
            description = "User not authenticated"
        )
    })
    public Response authenticate(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("request_id") String requestId) {

        Optional<AuthorizationRequest> requestOpt = authorizationService.findAuthorizationRequest(requestId);
        if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid request")
                    .build();
        }

        // Authenticate user
        Optional<Account> accountOpt = accountService.authenticate(username, password);
        if (accountOpt.isEmpty()) {
            // Authentication failed - return error
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid username or password")
                    .build();
        }

        Account account = accountOpt.get();

        // Approve the authorization request
        authorizationService.approveAuthorizationRequest(requestId, account.getId());

        // Show consent page
        return Response.ok(new AuthenticationResponse(account.getName())).build();
    }

    private Response buildErrorRedirect(String redirectUri, String error, String errorDescription, String state) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error</h1><p>" + error + ": " + errorDescription + "</p></body></html>")
                    .build();
        }

        String redirectUrl = redirectUri +
                (redirectUri.contains("?") ? "&" : "?") +
                "error=" + URLEncoder.encode(error, StandardCharsets.UTF_8) +
                "&error_description=" + URLEncoder.encode(errorDescription, StandardCharsets.UTF_8);

        if (state != null && !state.isBlank()) {
            redirectUrl += "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
        }

        return Response.seeOther(URI.create(redirectUrl)).build();
    }

    public static final class AuthenticationResponse {
        private final String name;

        public AuthenticationResponse(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
