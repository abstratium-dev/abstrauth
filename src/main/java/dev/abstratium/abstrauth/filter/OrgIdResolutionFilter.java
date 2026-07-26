package dev.abstratium.abstrauth.filter;

import io.quarkus.oidc.IdToken;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.service.CurrentOrgContext;

import java.io.IOException;

/**
 * Resolves the {@code orgId} claim from the authenticated JWT and stores it in
 * the request-scoped {@link CurrentOrgContext}.
 *
 * <p>This filter runs <em>after</em> authentication (OIDC cookie or MP-JWT
 * Bearer) so that the injected tokens are fully populated, then makes the
 * organisation identifier available to {@link dev.abstratium.abstrauth.service.JwtOrgResolver}
 * without duplicating cookie-decryption logic.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class OrgIdResolutionFilter implements ContainerRequestFilter {

    private static final Logger log = Logger.getLogger(OrgIdResolutionFilter.class);

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    @IdToken
    Instance<JsonWebToken> idTokenInstance;

    @Inject
    Instance<JsonWebToken> accessTokenInstance;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        currentOrgContext.setRequestPath(requestContext.getUriInfo().getPath());
        currentOrgContext.setRequestMethod(requestContext.getMethod());

        String orgId = extractOrgIdFromIdToken();
        if (orgId == null) {
            orgId = extractOrgIdFromAccessToken();
        }
        if (orgId != null && !orgId.isBlank()) {
            currentOrgContext.setOrgId(orgId);
            log.debugv("Resolved orgId={0} for request {1}", orgId, requestContext.getUriInfo().getPath());
            return;
        }
    }

    private String extractOrgIdFromIdToken() {
        if (idTokenInstance != null && idTokenInstance.isResolvable()) {
            try {
                Object claim = idTokenInstance.get().getClaim("orgId");
                if (claim != null) {
                    String orgId = claim.toString();
                    if (!orgId.isBlank()) {
                        return orgId;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract orgId from ID token", e);
            }
        }
        return null;
    }

    private String extractOrgIdFromAccessToken() {
        if (accessTokenInstance != null && accessTokenInstance.isResolvable()) {
            try {
                Object claim = accessTokenInstance.get().getClaim("orgId");
                if (claim != null) {
                    String orgId = claim.toString();
                    if (!orgId.isBlank()) {
                        return orgId;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract orgId from access token", e);
            }
        }
        return null;
    }

}
