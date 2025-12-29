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

@Path("/api/config")
@Tag(name = "Config", description = "Application configuration endpoints")
@PermitAll
public class ConfigResource {

    @Inject
    AuthorizationService authorizationService;

    @ConfigProperty(name = "abstrauth.session.timeout.seconds", defaultValue = "900")
    int sessionTimeoutSeconds;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get application configuration", description = "Returns public application configuration including signup settings and session timeout")
    public Response getConfig() {
        boolean signupAllowed = authorizationService.isSignupAllowed();
        boolean allowNativeSignin = authorizationService.isNativeSigninAllowed();
        return Response.ok(new ConfigResponse(signupAllowed, allowNativeSignin, sessionTimeoutSeconds)).build();
    }

    @RegisterForReflection
    public static class ConfigResponse {
        public boolean signupAllowed;
        public boolean allowNativeSignin;
        public int sessionTimeoutSeconds;

        public ConfigResponse(boolean signupAllowed, boolean allowNativeSignin, int sessionTimeoutSeconds) {
            this.signupAllowed = signupAllowed;
            this.allowNativeSignin = allowNativeSignin;
            this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        }
    }
}
