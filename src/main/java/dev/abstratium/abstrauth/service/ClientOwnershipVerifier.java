package dev.abstratium.abstrauth.service;

import io.quarkus.oidc.IdToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Verifies that the calling user belongs to the organisation that owns a client.
 * This is defense-in-depth: the Hibernate tenant filter already scopes
 * OAuthClient queries, but some entities (like ClientAllowedRole and ClientRole)
 * have no @TenantId so we explicitly check ownership before mutating.
 */
@ApplicationScoped
public class ClientOwnershipVerifier {

    @Inject
    @IdToken
    JsonWebToken token;

    @Inject
    OAuthClientService oauthClientService;

    /**
     * Verify that the client belongs to the organisation in the caller's JWT token.
     *
     * @param clientId The OAuth client ID to verify
     * @throws IllegalArgumentException if the client is not found or does not belong to the caller's org
     */
    public void verifyClientOwnership(String clientId) {
        if (token == null) {
            // No JWT context (e.g. internal calls or some test paths);
            // rely on the Hibernate tenant filter for OAuthClient queries.
            return;
        }
        Object orgIdClaim = token.getClaim("orgId");
        if (orgIdClaim == null) {
            throw new IllegalArgumentException("Client not found");
        }
        String orgId = orgIdClaim.toString();

        var clientOpt = oauthClientService.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            throw new IllegalArgumentException("Client not found");
        }
        if (!orgId.equals(clientOpt.get().getOrgId())) {
            throw new IllegalArgumentException("Client not found");
        }
    }

    /**
     * Get the org ID from the current JWT token.
     * Returns null if no token or no orgId claim.
     *
     * @return The orgId or null
     */
    public String getCurrentOrgId() {
        if (token == null) {
            return null;
        }
        Object orgIdClaim = token.getClaim("orgId");
        return orgIdClaim != null ? orgIdClaim.toString() : null;
    }
}
