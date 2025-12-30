package dev.abstratium.abstrauth.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

/**
 * HTTP Response Filter that adds security headers to all responses.
 * 
 * This filter implements defense-in-depth security by adding multiple
 * security-related HTTP headers to protect against common web vulnerabilities.
 */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @ConfigProperty(name = "security.csp.enabled", defaultValue = "true")
    boolean cspEnabled;

    @ConfigProperty(name = "security.csp.policy", defaultValue = 
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https:; " +
        "font-src 'self' data:; " +
        "connect-src 'self'; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self'"
    )
    String cspPolicy;

    @ConfigProperty(name = "security.hsts.enabled", defaultValue = "false")
    boolean hstsEnabled;

    @ConfigProperty(name = "security.hsts.max-age", defaultValue = "31536000")
    int hstsMaxAge;

    @ConfigProperty(name = "security.hsts.include-subdomains", defaultValue = "true")
    boolean hstsIncludeSubDomains;

    @ConfigProperty(name = "security.hsts.preload", defaultValue = "true")
    boolean hstsPreload;

    @Override
    public void filter(ContainerRequestContext requestContext, 
                      ContainerResponseContext responseContext) throws IOException {
        
        // Content Security Policy - Prevents XSS, clickjacking, and other code injection attacks
        if (cspEnabled) {
            responseContext.getHeaders().add("Content-Security-Policy", cspPolicy);
        }

        // X-Content-Type-Options - Prevents MIME type sniffing
        responseContext.getHeaders().add("X-Content-Type-Options", "nosniff");

        // X-Frame-Options - Prevents clickjacking (backup for CSP frame-ancestors)
        responseContext.getHeaders().add("X-Frame-Options", "DENY");

        // X-XSS-Protection - Legacy XSS protection for older browsers
        responseContext.getHeaders().add("X-XSS-Protection", "1; mode=block");

        // Referrer-Policy - Controls how much referrer information is sent
        responseContext.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions-Policy - Controls which browser features can be used
        responseContext.getHeaders().add("Permissions-Policy", 
            "geolocation=(), microphone=(), camera=(), payment=()");

        // Strict-Transport-Security - Forces HTTPS (enabled via configuration)
        // Only enable in production when serving over HTTPS
        if (hstsEnabled) {
            StringBuilder hsts = new StringBuilder("max-age=" + hstsMaxAge);
            if (hstsIncludeSubDomains) {
                hsts.append("; includeSubDomains");
            }
            if (hstsPreload) {
                hsts.append("; preload");
            }
            responseContext.getHeaders().add("Strict-Transport-Security", hsts.toString());
        }
    }
}
