package dev.abstratium.abstrauth.boundary.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;

@Path("/api/config")
@Tag(name = "Config", description = "Application configuration endpoints")
@PermitAll
public class ConfigResource {

    @Inject
    AuthorizationService authorizationService;

    @Inject
    OAuthClientService clientService;

    @ConfigProperty(name = "abstrauth.session.timeout.seconds", defaultValue = "900")
    int sessionTimeoutSeconds;

    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;

    private static final int MIN_SECRET_LENGTH = 32;
    private static final String DEFAULT_SECRET = "dev-secret-CHANGE-IN-PROD";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get application configuration", description = "Returns public application configuration including signup settings, session timeout, and client secret security status")
    public Response getConfig() {
        boolean signupAllowed = authorizationService.isSignupAllowed();
        boolean allowNativeSignin = authorizationService.isNativeSigninAllowed();
        boolean insecureClientSecret = isClientSecretInsecure();
        return Response.ok(new ConfigResponse(signupAllowed, allowNativeSignin, sessionTimeoutSeconds, insecureClientSecret)).build();
    }

    /**
     * Checks if the client secret is insecure (too short or using default value).
     * @return true if the secret is insecure, false otherwise
     */
    private boolean isClientSecretInsecure() {
        // Check length
        if (clientSecret.length() < MIN_SECRET_LENGTH) {
            return true;
        }
        
        // Check if hash matches default secret using the service
        return clientService.clientSecretMatches(Roles.CLIENT_ID, DEFAULT_SECRET);
    }

    @RegisterForReflection
    public static class ConfigResponse {
        public boolean signupAllowed;
        public boolean allowNativeSignin;
        public int sessionTimeoutSeconds;
        public boolean insecureClientSecret;

        public ConfigResponse(boolean signupAllowed, boolean allowNativeSignin, int sessionTimeoutSeconds, boolean insecureClientSecret) {
            this.signupAllowed = signupAllowed;
            this.allowNativeSignin = allowNativeSignin;
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
            this.insecureClientSecret = insecureClientSecret;
        }
    }
}
