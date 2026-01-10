package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.util.ClientIpUtil;
import dev.abstratium.abstrauth.util.SecureRandomProvider;
import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

/**
 * Custom CSRF protection filter that only applies to /api/* endpoints.
 * 
 * OAuth2 endpoints (/oauth2/*) have their own CSRF protection via request_id parameter.
 * This filter implements the Signed Double Submit Cookie pattern with HMAC.
 * 
 * Security:
 * - Tokens are HMAC-signed and bound to the user's session
 * - Token format: base64(hmac).base64(random)
 * - HMAC is calculated over: random_value + session_id
 * - Cookie is not HttpOnly (so Angular can read it)
 * - Token must match in both cookie and header
 * - HMAC signature must be valid
 * - Only applies to authenticated /api/* endpoints
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 1) // Run after authentication
public class ApiCsrfFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(ApiCsrfFilter.class);
    
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    @Inject
    SecureRandomProvider secureRandomProvider;
    
    @Inject
    SecurityIdentity securityIdentity;
    
    @StaticInitSafe
    @ConfigProperty(name = "csrf.token.signature.key", defaultValue = "")
    String signatureKey;
    
    @StaticInitSafe
    @ConfigProperty(name = "csrf.protection.enabled", defaultValue = "true")
    boolean csrfEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Check if CSRF protection is enabled
        if (!csrfEnabled) {
            return;
        }
        
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();
        
        // Only apply CSRF protection to /api/* endpoints
        if (!path.startsWith("/api/")) {
            return;
        }
        
        // Only check CSRF on mutating methods
        if (!MUTATING_METHODS.contains(method)) {
            return;
        }
        
        // Only apply to authenticated requests
        if (securityIdentity.isAnonymous()) {
            String clientIp = ClientIpUtil.getClientIp(requestContext);
            LOG.debugf("Skipping CSRF check for anonymous request to %s from IP %s", path, clientIp);
            return;
        }
        
        // Get user info for logging
        String userId = securityIdentity.getPrincipal().getName();
        String clientIp = ClientIpUtil.getClientIp(requestContext);
        
        // Get CSRF token from cookie
        Cookie csrfCookie = requestContext.getCookies().get(CSRF_COOKIE_NAME);
        if (csrfCookie == null) {
            LOG.warnf("CSRF cookie missing for %s %s - User: %s, IP: %s", method, path, userId, clientIp);
            requestContext.abortWith(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("CSRF token cookie missing")
                    .build()
            );
            return;
        }
        
        // Get CSRF token from header
        String csrfHeader = requestContext.getHeaderString(CSRF_HEADER_NAME);
        if (csrfHeader == null || csrfHeader.isBlank()) {
            LOG.warnf("CSRF header missing for %s %s - User: %s, IP: %s", method, path, userId, clientIp);
            requestContext.abortWith(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("CSRF token header missing")
                    .build()
            );
            return;
        }
        
        // Verify tokens match (constant-time comparison to prevent timing attacks)
        String cookieValue = csrfCookie.getValue();
        if (!constantTimeEquals(cookieValue, csrfHeader)) {
            LOG.warnf("CSRF token mismatch for %s %s - User: %s, IP: %s", method, path, userId, clientIp);
            requestContext.abortWith(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("CSRF token mismatch")
                    .build()
            );
            return;
        }
        
        // Verify HMAC signature
        if (!verifyHmacSignature(cookieValue)) {
            LOG.warnf("CSRF token HMAC verification failed for %s %s - User: %s, IP: %s", method, path, userId, clientIp);
            requestContext.abortWith(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("CSRF token signature invalid")
                    .build()
            );
            return;
        }
        
        LOG.debugf("CSRF check passed for %s %s - User: %s, IP: %s", method, path, userId, clientIp);
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        
        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();
        
        if (aBytes.length != bBytes.length) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        
        return result == 0;
    }
    
    /**
     * Verify HMAC signature of CSRF token
     * Token format: base64(hmac).base64(random)
     */
    private boolean verifyHmacSignature(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                LOG.debug("Invalid token format - expected hmac.random");
                return false;
            }
            
            String receivedHmac = parts[0];
            String randomValue = parts[1];
            
            // Get session ID from security identity
            String sessionId = getSessionId();
            if (sessionId == null) {
                LOG.debug("No session ID available for HMAC verification");
                return false;
            }
            
            // Calculate expected HMAC
            String expectedHmac = calculateHmac(randomValue, sessionId);
            
            // Constant-time comparison
            return constantTimeEquals(receivedHmac, expectedHmac);
            
        } catch (Exception e) {
            LOG.error("Error verifying HMAC signature", e);
            return false;
        }
    }
    
    /**
     * Calculate HMAC signature for CSRF token
     */
    private String calculateHmac(String randomValue, String sessionId) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(signatureKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(keySpec);
        
        // HMAC over: random_value + session_id
        String data = randomValue + sessionId;
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }
    
    /**
     * Get session ID from security identity
     * Uses the principal name as session identifier
     */
    private String getSessionId() {
        if (securityIdentity.isAnonymous()) {
            return null;
        }
        return securityIdentity.getPrincipal().getName();
    }
    
    /**
     * Generate a random CSRF token with HMAC signature
     * Token format: base64(hmac).base64(random)
     */
    public String generateToken(String sessionId) throws NoSuchAlgorithmException, InvalidKeyException {
        // Generate random value (128 bits)
        byte[] randomBytes = new byte[16];
        secureRandomProvider.getSecureRandom().nextBytes(randomBytes);
        String randomValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
        // Calculate HMAC
        String hmac = calculateHmac(randomValue, sessionId);
        
        // Return token in format: hmac.random
        return hmac + "." + randomValue;
    }
}
