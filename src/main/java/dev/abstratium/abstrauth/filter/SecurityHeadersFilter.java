package dev.abstratium.abstrauth.filter;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Vert.x route filter that adds security headers to all responses.
 *
 * <p>This filter implements defense-in-depth security by adding multiple
 * security-related HTTP headers to protect against common web vulnerabilities
 * (XSS, clickjacking, MIME sniffing, referrer leakage).</p>
 *
 * <p>This is implemented as a Vert.x route handler (registered via observing the
 * {@link Router} CDI event) rather than a JAX-RS {@code ContainerResponseFilter}
 * because Quinoa serves static resources (including {@code index.html}, which
 * hosts the OAuth login and consent screens) at the Vert.x layer, bypassing the
 * JAX-RS pipeline entirely. A JAX-RS filter would never fire for those
 * resources, leaving the most security-sensitive pages undefended.</p>
 */
@ApplicationScoped
public class SecurityHeadersFilter {

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

    void registerRoute(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(this::applySecurityHeaders);
    }

    private void applySecurityHeaders(RoutingContext rc) {
        var headers = rc.response().headers();

        // Content Security Policy - Prevents XSS, clickjacking, and other code injection attacks
        if (cspEnabled) {
            headers.set("Content-Security-Policy", cspPolicy);
        }

        // X-Content-Type-Options - Prevents MIME type sniffing
        headers.set("X-Content-Type-Options", "nosniff");

        // X-Frame-Options - Prevents clickjacking (backup for CSP frame-ancestors)
        headers.set("X-Frame-Options", "DENY");

        // X-XSS-Protection - Legacy XSS protection for older browsers
        headers.set("X-XSS-Protection", "1; mode=block");

        // Referrer-Policy - Controls how much referrer information is sent
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions-Policy - Controls which browser features can be used
        headers.set("Permissions-Policy",
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
            headers.set("Strict-Transport-Security", hsts.toString());
        }

        rc.next();
    }
}
