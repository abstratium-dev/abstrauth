package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.service.CurrentOrgContext;
import io.quarkus.oidc.IdToken;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Base64;

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
        String orgId = extractOrgIdFromIdToken();
        if (orgId == null) {
            orgId = extractOrgIdFromAccessToken();
        }
        if (orgId == null) {
            orgId = extractOrgIdFromHeader(requestContext);
        }
        if (orgId != null && !orgId.isBlank()) {
            currentOrgContext.setOrgId(orgId);
            log.debugv("Resolved orgId={0} for request {1}", orgId, requestContext.getUriInfo().getPath());
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

    private String extractOrgIdFromHeader(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
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
            log.debug("Failed to extract orgId from Authorization header JWT", e);
            return null;
        }
    }
}
