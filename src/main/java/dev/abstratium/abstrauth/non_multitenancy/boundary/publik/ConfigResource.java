package dev.abstratium.abstrauth.non_multitenancy.boundary.publik;

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

import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyAuthorizationService;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancyOAuthClientService;
import dev.abstratium.abstrauth.service.CurrentOrgContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

@Path("/public/config")
@Tag(name = "Config", description = "Application configuration endpoints")
@PermitAll
public class ConfigResource {

    @Inject
    NonMultitenancyAuthorizationService nonMultitenancyAuthorizationService;

    @Inject
    NonMultitenancyOAuthClientService clientService;

    @Inject
    CurrentOrgContext orgCtx;

    @ConfigProperty(name = "abstrauth.session.timeout.seconds", defaultValue = "900")
    int sessionTimeoutSeconds;

    @ConfigProperty(name = "abstrauth.audit.retention.days", defaultValue = "90")
    int auditRetentionDays;

    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;

    @ConfigProperty(name = "abstrauth.warning.message", defaultValue = "")
    String warningMessage;

    @ConfigProperty(name = "brand.logo.url", defaultValue = "https://abstratium.dev/abstratium-logo-small.png")
    String brandLogoUrl;

    @ConfigProperty(name = "brand.logo.alt", defaultValue = "Abstratium Logo")
    String brandLogoAlt;

    @ConfigProperty(name = "brand.name", defaultValue = "ABSTRATIUM")
    String brandName;

    @ConfigProperty(name = "abstratium.stage", defaultValue = "dev")
    String stage;

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get application configuration", description = "Returns public application configuration including signup settings, session timeout, and client secret security status")
    public Response getConfig() {
        orgCtx.setContextDescription("ConfigResource#getConfig");
        orgCtx.setIgnore(true);

        boolean signupAllowed = nonMultitenancyAuthorizationService.isSignupAllowed();
        boolean allowNativeSignin = nonMultitenancyAuthorizationService.isNativeSigninAllowed();
        boolean allowGoogleSignin = nonMultitenancyAuthorizationService.isGoogleSigninAllowed();
        boolean allowMicrosoftSignin = nonMultitenancyAuthorizationService.isMicrosoftSigninAllowed();
        boolean insecureClientSecret = isClientSecretInsecure();
        return Response.ok(new ConfigResponse(signupAllowed, allowNativeSignin, allowGoogleSignin, allowMicrosoftSignin, sessionTimeoutSeconds, insecureClientSecret, warningMessage, legalContent, brandLogoUrl, brandLogoAlt, brandName, stage, auditRetentionDays)).build();
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
        orgCtx.setContextDescription("ConfigResource#isClientSecretInsecure");
        orgCtx.setIgnore(true);
        return clientService.abstrauthClientSecretMatches();
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
        public String brandLogoUrl;
        public String brandLogoAlt;
        public String brandName;
        public String stage;
        public int auditRetentionDays;

        public ConfigResponse(boolean signupAllowed, boolean allowNativeSignin, boolean allowGoogleSignin, boolean allowMicrosoftSignin, int sessionTimeoutSeconds, boolean insecureClientSecret, String warningMessage, String legalContent, String brandLogoUrl, String brandLogoAlt, String brandName, String stage, int auditRetentionDays) {
            this.signupAllowed = signupAllowed;
            this.allowNativeSignin = allowNativeSignin;
            this.allowGoogleSignin = allowGoogleSignin;
            this.allowMicrosoftSignin = allowMicrosoftSignin;
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
            this.insecureClientSecret = insecureClientSecret;
            this.warningMessage = warningMessage;
            this.legalContent = legalContent;
            this.brandLogoUrl = brandLogoUrl;
            this.brandLogoAlt = brandLogoAlt;
            this.brandName = brandName;
            this.stage = stage;
            this.auditRetentionDays = auditRetentionDays;
        }
    }
}
