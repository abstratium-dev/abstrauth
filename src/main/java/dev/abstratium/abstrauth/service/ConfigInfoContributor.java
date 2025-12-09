package dev.abstratium.abstrauth.service;

import io.quarkus.info.runtime.spi.InfoContributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * Contributes runtime configuration information to the /info endpoint.
 * This allows exposing selected configuration values through the management interface.
 */
@ApplicationScoped
public class ConfigInfoContributor implements InfoContributor {

    @Inject
    @ConfigProperty(name = "allow.signup")
    boolean allowSignup;

    @Inject
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String jwtIssuer;

    @Inject
    @ConfigProperty(name = "oauth.google.redirect-uri")
    String googleRedirectUri;

    @Inject
    @ConfigProperty(name = "rate-limit.enabled")
    boolean rateLimitEnabled;

    @Override
    public String name() {
        return "config";
    }

    @Override
    public Map<String, Object> data() {
        return Map.of(
            "allowSignup", allowSignup,
            "jwtIssuer", jwtIssuer,
            "googleRedirectUri", googleRedirectUri,
            "rateLimitEnabled", rateLimitEnabled
        );
    }
}
