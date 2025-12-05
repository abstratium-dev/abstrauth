package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.RevokedToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Service for managing token revocation.
 * Handles revocation due to authorization code replay attacks and explicit revocation requests.
 */
@ApplicationScoped
public class TokenRevocationService {

    private static final Logger logger = Logger.getLogger(TokenRevocationService.class);

    @Inject
    EntityManager em;

    /**
     * Revoke all tokens associated with an authorization code.
     * This is called when an authorization code replay attack is detected.
     *
     * @param authCodeId The authorization code ID
     * @param reason The reason for revocation
     */
    @Transactional
    public void revokeTokensByAuthorizationCode(String authCodeId, String reason) {
        logger.warn("SECURITY: Revoking all tokens for authorization code: " + authCodeId + ", reason: " + reason);
        
        // Find all tokens issued from this authorization code
        var query = em.createQuery(
            "SELECT ac.code FROM AuthorizationCode ac WHERE ac.id = :authCodeId", 
            String.class
        );
        query.setParameter("authCodeId", authCodeId);
        
        List<String> codes = query.getResultList();
        if (codes.isEmpty()) {
            logger.warn("No authorization code found for ID: " + authCodeId);
            return;
        }
        
        // Create revocation entry for this authorization code
        // Note: In a full implementation with refresh tokens, we would track JTIs
        // For now, we mark the authorization code as compromised
        RevokedToken revocation = new RevokedToken();
        revocation.setAuthorizationCodeId(authCodeId);
        revocation.setReason(reason);
        revocation.setTokenJti("AUTH_CODE_" + authCodeId); // Marker for auth code revocation
        em.persist(revocation);
    }

    /**
     * Revoke a specific token by its JTI (JWT ID).
     *
     * @param jti The JWT ID to revoke
     * @param reason The reason for revocation
     */
    @Transactional
    public void revokeToken(String jti, String reason) {
        logger.info("Revoking token with JTI: " + jti + ", reason: " + reason);
        
        RevokedToken revocation = new RevokedToken();
        revocation.setTokenJti(jti);
        revocation.setReason(reason);
        em.persist(revocation);
    }

    /**
     * Check if a token has been revoked by its JTI.
     *
     * @param jti The JWT ID to check
     * @return true if the token is revoked, false otherwise
     */
    public boolean isTokenRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        
        var query = em.createQuery(
            "SELECT COUNT(r) FROM RevokedToken r WHERE r.tokenJti = :jti", 
            Long.class
        );
        query.setParameter("jti", jti);
        return query.getSingleResult() > 0;
    }

    /**
     * Check if any tokens from an authorization code have been revoked.
     * This indicates an authorization code replay attack.
     *
     * @param authCodeId The authorization code ID
     * @return true if tokens from this auth code are revoked, false otherwise
     */
    public boolean isAuthorizationCodeCompromised(String authCodeId) {
        if (authCodeId == null || authCodeId.isBlank()) {
            return false;
        }
        
        var query = em.createQuery(
            "SELECT COUNT(r) FROM RevokedToken r WHERE r.authorizationCodeId = :authCodeId", 
            Long.class
        );
        query.setParameter("authCodeId", authCodeId);
        return query.getSingleResult() > 0;
    }
}
