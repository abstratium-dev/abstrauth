package dev.abstratium.abstrauth.filter;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.abstratium.abstrauth.service.AuthorizationService;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

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
 *
 * <p>On the OAuth consent page ({@code /signin/{requestId}}) the CSP
 * {@code form-action} directive is dynamically extended with the origin of the
 * OAuth client's redirect URI. Chrome and Safari enforce {@code form-action}
 * against the redirect target of a form submission, so {@code form-action
 * 'self'} alone would block the consent form's redirect back to the
 * (cross-origin) client callback. The redirect URI stored on the authorization
 * request was validated against the client's registered redirect URIs when the
 * request was created, so only origins the server may redirect to are
 * allowlisted.</p>
 */
@ApplicationScoped
public class SecurityHeadersFilter {

    /** Path of the OAuth consent page, e.g. /signin/{requestId}. */
    static final Pattern SIGNIN_PATH = Pattern.compile("^/signin/([^/]+)$");

    @Inject
    AuthorizationService authorizationService;

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
        // Common security headers (X-Frame-Options, X-Content-Type-Options,
        // Referrer-Policy, etc.) are set on every response. The CSP header is
        // set in a headersEndHandler (which fires just before headers are
        // written to the wire) so that it is not overwritten by downstream
        // handlers (notably Quinoa's static resource handler).
        router.route().order(Integer.MIN_VALUE).handler(rc -> {
            setCommonSecurityHeaders(rc.response().headers());
            rc.addHeadersEndHandler(v -> {
                if (cspEnabled) {
                    // For /signin/{requestId} the CSP is set by the blocking
                    // handler below (which stashes the dynamic policy on the
                    // routing context). If that handler has not run (e.g.
                    // non-signin path), use the default policy.
                    String csp = rc.get(CSP_KEY);
                    rc.response().headers().set("Content-Security-Policy", csp != null ? csp : cspPolicy);
                }
            });
            rc.next();
        });

        // On the OAuth consent page (/signin/{requestId}) the CSP form-action
        // directive is dynamically extended with the origin of the OAuth
        // client's redirect URI. Chrome and Safari enforce form-action against
        // the redirect target of a form submission, so form-action 'self'
        // alone would block the consent form's redirect back to the
        // (cross-origin) client callback. The redirect URI stored on the
        // authorization request was validated against the client's registered
        // redirect URIs when the request was created, so only origins the
        // server may redirect to are allowlisted.
        //
        // This runs as a blocking handler (on a worker thread) because the
        // EntityManager lookup is blocking. It resolves the redirect origin,
        // builds the CSP, stashes it on the routing context (so the
        // headersEndHandler registered above can apply it), and registers its
        // own headersEndHandler as a backup. Using a blocking handler ensures
        // the lookup completes before downstream handlers (Quinoa) commit the
        // response.
        router.route("/signin/*").order(Integer.MIN_VALUE).blockingHandler(rc -> {
            if (cspEnabled) {
                String requestId = requestIdFromPath(rc.request().path());
                String redirectOrigin = null;
                if (requestId != null) {
                    redirectOrigin = authorizationService.findAuthorizationRequest(requestId)
                            .map(authRequest -> cspSourceForUri(authRequest.getRedirectUri()))
                            .orElse(null);
                }
                rc.put(CSP_KEY, buildCspPolicy(redirectOrigin));
            }
            rc.next();
        });
    }

    /** Routing-context key for the resolved CSP policy (set by the blocking handler). */
    private static final String CSP_KEY = SecurityHeadersFilter.class.getName() + ".csp";

    private String requestIdFromPath(String path) {
        if (path == null) {
            return null;
        }
        Matcher matcher = SIGNIN_PATH.matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Builds the Content Security Policy, extending {@code form-action 'self'}
     * with the given origin (a CSP source expression) when present.
     *
     * @param redirectOrigin CSP source expression for the OAuth client's
     *                       redirect URI origin, or {@code null} to leave the
     *                       policy unchanged
     * @return the CSP policy to send in the {@code Content-Security-Policy} header
     */
    String buildCspPolicy(String redirectOrigin) {
        if (redirectOrigin == null) {
            return cspPolicy;
        }
        return cspPolicy.replace("form-action 'self'", "form-action 'self' " + redirectOrigin);
    }

    private void setCommonSecurityHeaders(MultiMap headers) {
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
    }

    /**
     * Converts a redirect URI into a CSP source expression that matches any
     * redirect target on that origin:
     * <ul>
     *   <li>http/https URIs: {@code scheme://host[:port]}</li>
     *   <li>other schemes (e.g. custom URL schemes): {@code scheme:}</li>
     * </ul>
     *
     * @param redirectUri the OAuth client's registered redirect URI
     * @return a CSP source expression, or {@code null} if it cannot be derived
     */
    static String cspSourceForUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(redirectUri);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                String host = uri.getHost();
                if (host == null) {
                    return null;
                }
                StringBuilder source = new StringBuilder(scheme).append("://").append(host);
                int port = uri.getPort();
                if (port != -1 && !isDefaultPort(scheme, port)) {
                    source.append(':').append(port);
                }
                return source.toString();
            }
            return scheme + ":";
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
            || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
