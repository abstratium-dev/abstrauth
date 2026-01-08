package dev.abstratium.abstrauth.filter;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Generates and sets CSRF tokens as cookies for GET requests to /api/* endpoints.
 * 
 * This works in conjunction with ApiCsrfFilter to implement the Signed Double Submit Cookie pattern.
 * Tokens are HMAC-signed and bound to the user's session for enhanced security.
 * Angular will read this cookie and send it as a header on mutating requests.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class ApiCsrfTokenGenerator implements ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(ApiCsrfTokenGenerator.class);
    
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    
    @Inject
    SecurityIdentity securityIdentity;
    
    @Inject
    ApiCsrfFilter csrfFilter;
    
    @ConfigProperty(name = "abstrauth.session.timeout.seconds", defaultValue = "900")
    int sessionTimeoutSeconds;
    
    @ConfigProperty(name = "csrf.protection.enabled", defaultValue = "true")
    boolean csrfEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // Check if CSRF protection is enabled
        if (!csrfEnabled) {
            return;
        }
        
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();
        
        // Only generate tokens for GET requests to /api/*
        if (!"GET".equals(method) || !path.startsWith("/api/")) {
            return;
        }
        
        // Only generate for authenticated users
        if (securityIdentity.isAnonymous()) {
            return;
        }
        
        // Check if cookie already exists
        Cookie existingCookie = requestContext.getCookies().get(CSRF_COOKIE_NAME);
        if (existingCookie != null && !existingCookie.getValue().isBlank()) {
            // Token already exists, no need to generate a new one
            return;
        }
        
        // Get session ID from security identity
        String sessionId = securityIdentity.getPrincipal().getName();
        
        // Generate new HMAC-signed CSRF token
        String token;
        try {
            token = csrfFilter.generateToken(sessionId);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOG.error("Failed to generate CSRF token", e);
            return;
        }
        
        // Create cookie
        NewCookie csrfCookie = new NewCookie.Builder(CSRF_COOKIE_NAME)
            .value(token)
            .path("/")
            .maxAge(sessionTimeoutSeconds)
            .httpOnly(false) // Must be false so Angular can read it
            .secure(false) // Will be set by reverse proxy in production
            .sameSite(NewCookie.SameSite.STRICT)
            .build();
        
        responseContext.getHeaders().add("Set-Cookie", csrfCookie);
        
        LOG.debugf("Generated CSRF token for %s %s", method, path);
    }
}
