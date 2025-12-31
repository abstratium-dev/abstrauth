package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Resource for handling OIDC authentication errors.
 * This endpoint is called when the OAuth/OIDC flow fails (e.g., invalid_scope, access_denied).
 */
@Path("/api/auth/error")
@Tag(name = "Authentication", description = "Authentication error handling")
public class AuthErrorResource {

    private static final Logger LOG = Logger.getLogger(AuthErrorResource.class);

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Handle OIDC authentication errors", 
               description = "Displays authentication errors from the OIDC flow")
    public Response handleAuthError(@Context HttpServerRequest request) {
        String error = request.getParam("error");
        String errorDescription = request.getParam("error_description");
        String state = request.getParam("state");

        LOG.warnf("OIDC authentication error: error=%s, description=%s, state=%s", 
                  error, errorDescription, state);

        // Build a user-friendly error page
        String html = buildErrorPage(error, errorDescription);
        
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(html)
                .build();
    }

    private String buildErrorPage(String error, String errorDescription) {
        String title = "Authentication Error";
        String message = getErrorMessage(error, errorDescription);
        
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                    }
                    .error-container {
                        background: white;
                        padding: 2rem;
                        border-radius: 8px;
                        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                        max-width: 500px;
                        text-align: center;
                    }
                    h1 {
                        color: #e53e3e;
                        margin-top: 0;
                    }
                    p {
                        color: #4a5568;
                        line-height: 1.6;
                    }
                    .error-code {
                        background: #fed7d7;
                        color: #c53030;
                        padding: 0.5rem 1rem;
                        border-radius: 4px;
                        font-family: monospace;
                        margin: 1rem 0;
                    }
                    a {
                        display: inline-block;
                        margin-top: 1rem;
                        padding: 0.75rem 1.5rem;
                        background: #667eea;
                        color: white;
                        text-decoration: none;
                        border-radius: 4px;
                        transition: background 0.2s;
                    }
                    a:hover {
                        background: #5a67d8;
                    }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <h1 id="error-title">%s</h1>
                    <p id="error-message">%s</p>
                    <div class="error-code" id="error-code">%s</div>
                    <a href="/">Return to Home</a>
                </div>
            </body>
            </html>
            """.formatted(title, title, message, error != null ? error : "unknown_error");
    }

    private String getErrorMessage(String error, String errorDescription) {
        if (errorDescription != null && !errorDescription.isEmpty()) {
            return errorDescription;
        }
        
        return switch (error != null ? error : "") {
            case "invalid_scope" -> 
                "The requested permissions are not allowed. Please contact your administrator.";
            case "access_denied" -> 
                "Access was denied. You may have cancelled the authorization or lack the required permissions.";
            case "invalid_request" -> 
                "The authentication request was invalid. Please try again.";
            case "unauthorized_client" -> 
                "This application is not authorized to perform this action.";
            case "unsupported_response_type" -> 
                "The authentication method is not supported.";
            case "server_error" -> 
                "An error occurred on the authentication server. Please try again later.";
            case "temporarily_unavailable" -> 
                "The authentication service is temporarily unavailable. Please try again later.";
            default -> 
                "An authentication error occurred. Please try again or contact support.";
        };
    }
}
