package dev.abstratium.abstrauth.service;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.quarkus.runtime.LaunchMode;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import io.quarkus.arc.Arc;

import java.util.Base64;

/**
 * Tenant resolver for discriminator-based multitenancy.
 * Extracts the {@code orgId} claim from the {@code Authorization: Bearer <jwt>} header.
 * Falls back to the default org when no valid Bearer token is present (e.g. public endpoints,
 * OAuth2 token exchange, sign-in flow).
 */
@PersistenceUnitExtension
@RequestScoped
public class JwtOrgResolver implements TenantResolver {

    private static final Logger log = Logger.getLogger(JwtOrgResolver.class);

    // Default organisation ID from V01.021__migrate_existing_data_to_default_org.sql
    public static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";

    // Use Instance to safely check availability before accessing the request.
    // The HttpServerRequest producer can return null during startup (Flyway, Bootstrap)
    // even when the request context is technically active.
    @Inject
    Instance<HttpServerRequest> requestInstance;

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_ORG_ID;
    }

    @Override
    public String resolveTenantId() {
        try {
            if (!Arc.container().requestContext().isActive()) {
                return fallbackToDefault("request context not active");
            }

            HttpServerRequest request = requestInstance.get();
            if (request == null) {
                return fallbackToDefault("HttpServerRequest is null");
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return DEFAULT_ORG_ID;
            }

            String token = authHeader.substring(7);
            String orgId = extractOrgIdFromJwt(token);
            if (orgId != null && !orgId.isBlank()) {
                return orgId;
            }
        } catch (jakarta.enterprise.inject.IllegalProductException e) {
            // HttpServerRequest producer returns null during startup (Flyway, Bootstrap)
            return fallbackToDefault("no HTTP request available");
        } catch (Exception e) {
            return fallbackToDefault("exception: " + e.getMessage());
        }
        return DEFAULT_ORG_ID;
    }

    /**
     * Falls back to DEFAULT_ORG_ID in dev/test mode only.
     * In production, this is a hard error — no request should resolve to default silently.
     */
    private String fallbackToDefault(String reason) {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            throw new IllegalStateException(
                    "Cannot resolve tenant in production without a valid request context. Reason: " + reason);
        }
        log.debug("Falling back to DEFAULT_ORG_ID (" + reason + ")");
        return DEFAULT_ORG_ID;
    }

    private String extractOrgIdFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            int start = payload.indexOf("\"orgId\":\"");
            if (start == -1) {
                return null;
            }
            start += 9;
            int end = payload.indexOf("\"", start);
            if (end == -1) {
                return null;
            }
            return payload.substring(start, end);
        } catch (Exception e) {
            log.debug("Failed to extract orgId from JWT payload", e);
            return null;
        }
    }
}
