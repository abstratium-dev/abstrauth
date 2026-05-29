package dev.abstratium.abstrauth.service;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

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

    // Raw Vert.x request — JsonWebToken cannot be injected here because this resolver
    // is invoked by Hibernate before the MicroProfile JWT security layer has run.
    @Inject
    HttpServerRequest request;

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_ORG_ID;
    }

    @Override
    public String resolveTenantId() {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return DEFAULT_ORG_ID;
            }

            String token = authHeader.substring(7);
            String orgId = extractOrgIdFromJwt(token);
            if (orgId != null && !orgId.isBlank()) {
                return orgId;
            }
        } catch (Exception e) {
            log.debug("Could not resolve orgId from JWT, using default", e);
        }
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
