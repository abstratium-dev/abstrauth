package dev.abstratium.abstrauth.filter;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Vert.x route filter that adds cache-prevention headers to {@code index.html}
 * responses so that browsers always fetch a fresh entry point after a deploy.
 *
 * <p>Angular's {@code outputHashing: "all"} produces content-hashed filenames
 * for all JS/CSS bundles, which can be cached indefinitely. However
 * {@code index.html} itself must not be cached, otherwise the browser keeps
 * referencing old chunk filenames that no longer exist on the server (leading
 * to "Failed to fetch dynamically imported module" errors). This filter ensures
 * {@code index.html} (and the root path) is always revalidated.</p>
 *
 * <p>This is implemented as a Vert.x route handler (registered via observing the
 * {@link Router} CDI event) rather than a JAX-RS {@code ContainerResponseFilter}
 * because Quinoa serves static resources at the Vert.x layer, bypassing the
 * JAX-RS pipeline entirely. A JAX-RS filter would never fire for
 * {@code index.html} in production.</p>
 */
@ApplicationScoped
public class IndexHtmlCacheControlFilter {

    static final String CACHE_CONTROL_VALUE =
        "no-cache, no-store, must-revalidate, proxy-revalidate";

    void registerRoute(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(rc -> {
            String path = rc.request().path();

            if (isIndexHtmlRequest(path)) {
                rc.response().headers()
                    .set("Cache-Control", CACHE_CONTROL_VALUE)
                    .set("Pragma", "no-cache")
                    .set("Expires", "0");
            }

            rc.next();
        });
    }

    private boolean isIndexHtmlRequest(String path) {
        return path == null
            || path.isEmpty()
            || path.equals("/")
            || path.equals("/index.html")
            || path.endsWith("/index.html");
    }
}
