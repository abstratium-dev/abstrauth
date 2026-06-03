package dev.abstratium.abstrauth.service;

import io.quarkus.arc.Arc;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Tenant resolver for discriminator-based multitenancy.
 * Reads the {@code orgId} from the request-scoped {@link CurrentOrgContext}
 * populated by {@link dev.abstratium.abstrauth.filter.OrgIdResolutionFilter}.
 * Falls back to the default org when no valid context is present (e.g. public endpoints,
 * OAuth2 token exchange, sign-in flow).
 */
@PersistenceUnitExtension
@RequestScoped
public class JwtOrgResolver implements TenantResolver {

    private static final Logger log = Logger.getLogger(JwtOrgResolver.class);

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    // JAX-RS filter path: OrgIdResolutionFilter resolves the orgId from the
    // authenticated JWT (OIDC cookie or Bearer token) and stores it here.
    @Inject
    Instance<CurrentOrgContext> currentOrgContextInstance;

    @Override
    public String getDefaultTenantId() {
        return defaultOrgId;
    }

    @Override
    public String resolveTenantId() {
        try {
            if (!Arc.container().requestContext().isActive()) {
                return fallbackToDefault("request context not active");
            }

            // JAX-RS filter path: orgId was resolved from OIDC cookie or
            // Bearer token by OrgIdResolutionFilter and stored in the
            // request-scoped CurrentOrgContext.
            if (currentOrgContextInstance != null && currentOrgContextInstance.isResolvable()) {
                CurrentOrgContext ctx = currentOrgContextInstance.get();
                if (ctx != null && ctx.getOrgId() != null && !ctx.getOrgId().isBlank()) {
                    return ctx.getOrgId();
                }
            }
        } catch (Exception e) {
            return fallbackToDefault("exception: " + e.getMessage());
        }
        return defaultOrgId;
    }

    /**
     * Falls back to defaultOrgId in dev/test mode only.
     * In production, this is a hard error — no request should resolve to default silently.
     */
    private String fallbackToDefault(String reason) {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            throw new IllegalStateException(
                    "Cannot resolve tenant in production without a valid request context. Reason: " + reason);
        }
        log.warn("Falling back to defaultOrgId (" + reason + ")");
        return defaultOrgId;
    }

}
