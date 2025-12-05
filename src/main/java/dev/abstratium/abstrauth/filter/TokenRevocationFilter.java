package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.service.TokenRevocationService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Base64;

/**
 * Filter that checks if a JWT token has been revoked before allowing access to protected resources.
 * This enforces token revocation for authorization code replay attacks and explicit revocations.
 * 
 * Runs after authentication (AUTHENTICATION priority) but before authorization (AUTHORIZATION priority).
 */
@Provider
@Priority(Priorities.AUTHORIZATION - 1)
public class TokenRevocationFilter implements ContainerRequestFilter {

    private static final Logger logger = Logger.getLogger(TokenRevocationFilter.class);

    @Inject
    TokenRevocationService tokenRevocationService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Extract Authorization header
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        
        // Skip if no Authorization header or not a Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        // Extract JWT token
        String token = authHeader.substring(7);
        
        // Extract JTI from JWT payload
        String jti = extractJtiFromToken(token);
        
        if (jti == null || jti.isBlank()) {
            // Tokens without JTI cannot be revoked individually
            // This is acceptable for tokens issued before the revocation system was implemented
            logger.debug("JWT does not contain JTI claim, skipping revocation check");
            return;
        }

        // Check if the token has been revoked
        if (tokenRevocationService.isTokenRevoked(jti)) {
            logger.warn("SECURITY: Attempt to use revoked token with JTI: " + jti);
            
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"invalid_token\", \"error_description\": \"The access token has been revoked\"}")
                    .type("application/json")
                    .build()
            );
        }
    }

    /**
     * Extract JTI claim from JWT token payload.
     * 
     * @param token The JWT token string
     * @return The JTI value, or null if not found
     */
    private String extractJtiFromToken(String token) {
        try {
            // JWT format: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.warn("Invalid JWT format");
                return null;
            }

            // Decode the payload (middle part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            
            // Extract JTI using simple JSON parsing
            // Format: "jti":"<uuid>"
            int jtiStart = payload.indexOf("\"jti\":\"");
            if (jtiStart == -1) {
                return null;
            }
            
            jtiStart += 7; // Length of "jti":"
            int jtiEnd = payload.indexOf("\"", jtiStart);
            
            if (jtiEnd == -1) {
                return null;
            }
            
            return payload.substring(jtiStart, jtiEnd);
        } catch (Exception e) {
            logger.error("Error extracting JTI from token", e);
            return null;
        }
    }
}
