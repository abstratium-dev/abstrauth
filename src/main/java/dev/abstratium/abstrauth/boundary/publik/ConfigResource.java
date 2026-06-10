package dev.abstratium.abstrauth.boundary.publik;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.PostConstruct;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.Roles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

@Path("/public/config")
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

    @ConfigProperty(name = "abstrauth.warning.message", defaultValue = "")
    String warningMessage;

    @ConfigProperty(name = "legal.content.file")
    Optional<String> legalContentFile;

    private String legalContent = null;

    @PostConstruct
    void init() {
        legalContentFile.ifPresent(path -> {
            try {
                legalContent = Files.readString(Paths.get(path));
            } catch (IOException e) {
                legalContent = null;
            }
        });
    }

    private static final int MIN_SECRET_LENGTH = 32;
    private static final String DEFAULT_SECRET = "dev-secret-CHANGE-IN-PROD";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get application configuration", description = "Returns public application configuration including signup settings, session timeout, and client secret security status")
    public Response getConfig() {
        boolean signupAllowed = authorizationService.isSignupAllowed();
        boolean allowNativeSignin = authorizationService.isNativeSigninAllowed();
        boolean allowGoogleSignin = authorizationService.isGoogleSigninAllowed();
        boolean allowMicrosoftSignin = authorizationService.isMicrosoftSigninAllowed();
        boolean insecureClientSecret = isClientSecretInsecure();
        return Response.ok(new ConfigResponse(signupAllowed, allowNativeSignin, allowGoogleSignin, allowMicrosoftSignin, sessionTimeoutSeconds, insecureClientSecret, warningMessage, legalContent)).build();
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
        public boolean allowGoogleSignin;
        public boolean allowMicrosoftSignin;
        public int sessionTimeoutSeconds;
        public boolean insecureClientSecret;
        public String warningMessage;
        public String legalContent;

        public ConfigResponse(boolean signupAllowed, boolean allowNativeSignin, boolean allowGoogleSignin, boolean allowMicrosoftSignin, int sessionTimeoutSeconds, boolean insecureClientSecret, String warningMessage, String legalContent) {
            this.signupAllowed = signupAllowed;
            this.allowNativeSignin = allowNativeSignin;
            this.allowGoogleSignin = allowGoogleSignin;
            this.allowMicrosoftSignin = allowMicrosoftSignin;
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
            this.insecureClientSecret = insecureClientSecret;
            this.warningMessage = warningMessage;
            this.legalContent = legalContent;
        }
    }
}
