package dev.abstratium.abstrauth.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link IndexHtmlCacheControlFilter} verifying that cache-prevention
 * headers are added to {@code index.html} / root responses but not to other
 * resources.
 *
 * <p>This filter operates at the Vert.x layer (registered via observing the
 * {@link io.vertx.ext.web.Router} CDI event), so it fires for all requests
 * regardless of whether Quinoa is enabled. In tests, Quinoa is disabled so
 * {@code /} and {@code /index.html} return 404 — but the filter still adds the
 * cache-prevention headers to the response. In production, Quinoa serves the
 * actual {@code index.html} with a 200, and the headers are present there too.</p>
 */
@QuarkusTest
class IndexHtmlCacheControlFilterTest {

    @Test
    void testRootPathHasNoCacheHeaders() {
        // Root path (/) should have cache-prevention headers.
        // Status code may be 404 in tests (Quinoa disabled) or 200 in prod;
        // the filter fires at the Vert.x layer either way.
        given()
            .when()
            .get("/")
            .then()
            .statusCode(anyOf(is(200), is(404)))
            .header("Cache-Control", equalTo(IndexHtmlCacheControlFilter.CACHE_CONTROL_VALUE))
            .header("Pragma", equalTo("no-cache"))
            .header("Expires", equalTo("0"));
    }

    @Test
    void testIndexHtmlPathHasNoCacheHeaders() {
        // Explicit index.html path should have cache-prevention headers.
        given()
            .when()
            .get("/index.html")
            .then()
            .statusCode(anyOf(is(200), is(404)))
            .header("Cache-Control", equalTo(IndexHtmlCacheControlFilter.CACHE_CONTROL_VALUE))
            .header("Pragma", equalTo("no-cache"))
            .header("Expires", equalTo("0"));
    }

    @Test
    void testPublicPathDoesNotHaveCacheControlHeadersFromFilter() {
        // Non-index.html paths should not have the cache-prevention headers.
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .header("Cache-Control",
                org.hamcrest.Matchers.not(equalTo(IndexHtmlCacheControlFilter.CACHE_CONTROL_VALUE)));
    }
}
