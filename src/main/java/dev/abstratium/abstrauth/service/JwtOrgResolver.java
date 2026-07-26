package dev.abstratium.abstrauth.service;

import io.quarkus.arc.Arc;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
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
 * OAuth2 token exchange, sign-in flow, startup, scheduled tasks). The fallback logs at INFO
 * unless the caller has set {@link CurrentOrgContext#setIgnore(boolean)} to suppress expected
 * fallbacks (e.g. non-multitenancy entity access).
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
        String requestPath = null;
        String requestMethod = null;
        String description = null;
        boolean ignore = false;
        try {
            if (!Arc.container().requestContext().isActive()) {
                return fallbackToDefault("request context not active", requestPath, requestMethod, description, ignore);
            }

            // JAX-RS filter path: orgId was resolved from OIDC cookie or
            // Bearer token by OrgIdResolutionFilter and stored in the
            // request-scoped CurrentOrgContext.
            if (currentOrgContextInstance != null && currentOrgContextInstance.isResolvable()) {
                CurrentOrgContext ctx = currentOrgContextInstance.get();
                if (ctx != null) {
                    requestPath = ctx.getRequestPath();
                    requestMethod = ctx.getRequestMethod();
                    description = ctx.getContextDescription();
                    ignore = ctx.isIgnore();
                    if (ctx.getOrgId() != null && !ctx.getOrgId().isBlank()) {
                        return ctx.getOrgId();
                    }
                }
            }
        } catch (Exception e) {
            return fallbackToDefault("exception: " + e.getMessage(), requestPath, requestMethod, description, ignore);
        }
        return fallbackToDefault("request tenant not resolved", requestPath, requestMethod, description, ignore);
    }

    private String fallbackToDefault(String reason, String requestPath, String requestMethod, String contextDescription, boolean ignore) {
        if(!ignore) {
            log.infov("Falling back to defaultOrgId (reason={0}, method={1}, path={2}, context={3})",
                    reason, requestMethod, requestPath, contextDescription);
        }
        return defaultOrgId;
    }

}
