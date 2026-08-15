package dev.abstratium.abstrauth.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;

/**
 * Utility for building absolute redirect URIs from relative paths.
 *
 * When the application runs behind a TLS-terminating reverse proxy, the
 * backend receives plain HTTP requests.  Although Quarkus proxy settings
 * ({@code quarkus.http.proxy.proxy-address-forwarding=true} etc.) adjust
 * the Vert.x request properties, JAX-RS {@code UriInfo} and
 * {@code Response.seeOther(URI)} with a relative URI do not always reflect
 * the forwarded scheme and host in the {@code Location} header.
 *
 * This class reads the {@code X-Forwarded-Proto} and {@code X-Forwarded-Host}
 * headers directly from the request context and uses them to construct an
 * absolute URI, ensuring that internal redirects use {@code https://} when
 * the original client connection was HTTPS.  This prevents mixed-content
 * errors in the browser.
 */
public final class RedirectUtil {

    private RedirectUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Build an absolute URI for a relative path (e.g. {@code "/signin/abc"}).
     *
     * The scheme is taken from the {@code X-Forwarded-Proto} header, falling
     * back to the request scheme.  The host is taken from the
     * {@code X-Forwarded-Host} header, falling back to the request host.
     *
     * @param requestContext the JAX-RS request context (for header access)
     * @param uriInfo        the JAX-RS URI info (for the fallback scheme/host)
     * @param relativePath   the path (must start with {@code /})
     * @return an absolute URI, e.g. {@code https://host/signin/abc}
     */
    public static URI absoluteFromRequest(ContainerRequestContext requestContext,
                                          UriInfo uriInfo,
                                          String relativePath) {
        // If the path is already an absolute URI (e.g. an external redirect_uri
        // or a fully-qualified URL), return it unchanged.
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return URI.create(relativePath);
        }

        URI requestUri = uriInfo.getRequestUri();

        String scheme = headerOrDefault(requestContext, "X-Forwarded-Proto",
                                        requestUri.getScheme());
        String host = headerOrDefault(requestContext, "X-Forwarded-Host",
                                       hostFromUri(requestUri));

        return URI.create(scheme + "://" + host + relativePath);
    }

    private static String headerOrDefault(ContainerRequestContext ctx,
                                          String headerName,
                                          String defaultValue) {
        String value = ctx.getHeaderString(headerName);
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    /**
     * Extract the host (with port if non-default) from a URI.
     */
    private static String hostFromUri(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1 || isDefaultPort(uri.getScheme(), port)) {
            return host;
        }
        return host + ":" + port;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
            || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
