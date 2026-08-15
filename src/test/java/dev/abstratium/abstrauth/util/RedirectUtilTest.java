package dev.abstratium.abstrauth.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedirectUtil}.
 *
 * Verifies that internal redirect URIs are built with the scheme and host
 * from the X-Forwarded-* headers so that mixed-content errors do not occur
 * when the auth server runs behind a TLS-terminating reverse proxy.
 */
class RedirectUtilTest {

    @Test
    void absoluteFromRequest_withForwardedProtoHttps_usesHttpsScheme() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn("https");
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn("auth-t.abstratium.dev");

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://10.0.0.1:8080/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/abc-123");

        assertEquals(URI.create("https://auth-t.abstratium.dev/signin/abc-123"), result);
    }

    @Test
    void absoluteFromRequest_withoutForwardedHeaders_usesRequestSchemeAndHost() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn(null);
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost:8080/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/abc-123");

        assertEquals(URI.create("http://localhost:8080/signin/abc-123"), result);
    }

    @Test
    void absoluteFromRequest_withForwardedHostContainingPort_includesPort() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn("https");
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn("auth.example.com:8443");

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://10.0.0.1:8080/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/org-selection/xyz");

        assertEquals(URI.create("https://auth.example.com:8443/org-selection/xyz"), result);
    }

    @Test
    void absoluteFromRequest_withAlreadyAbsoluteHttpsUrl_passesThroughUnchanged() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo,
                "https://auth.abstratium.dev/api/auth/callback?code=abc");

        assertEquals(URI.create("https://auth.abstratium.dev/api/auth/callback?code=abc"), result);
    }

    @Test
    void absoluteFromRequest_withAlreadyAbsoluteHttpUrl_passesThroughUnchanged() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo,
                "http://localhost:3000/callback?error=access_denied");

        assertEquals(URI.create("http://localhost:3000/callback?error=access_denied"), result);
    }

    @Test
    void absoluteFromRequest_withEmptyForwardedHeaders_fallsBackToRequestUri() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn("");
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn("  ");

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://internal-host:9090/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/");

        assertEquals(URI.create("http://internal-host:9090/"), result);
    }

    @Test
    void absoluteFromRequest_withForwardedProtoOnly_usesForwardedSchemeAndRequestHost() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn("https");
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://auth.abstratium.dev/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/test-id");

        assertEquals(URI.create("https://auth.abstratium.dev/signin/test-id"), result);
    }

    @Test
    void absoluteFromRequest_withRequestUriOnDefaultHttpPort_omitsPort() {
        // A request URI with port 80 (default for http) should omit the port.
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn(null);
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://auth.example.com:80/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/id-1");

        assertEquals(URI.create("http://auth.example.com/signin/id-1"), result);
    }

    @Test
    void absoluteFromRequest_withRequestUriOnDefaultHttpsPort_omitsPort() {
        // A request URI with port 443 (default for https) should omit the port.
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn("https");
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://auth.example.com:443/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/id-2");

        assertEquals(URI.create("https://auth.example.com/signin/id-2"), result);
    }

    @Test
    void absoluteFromRequest_withRequestUriWithoutPort_omitsPort() {
        // A request URI with no explicit port (port == -1) should omit the port.
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn(null);
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://auth.example.com/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/org-selection/id-3");

        assertEquals(URI.create("http://auth.example.com/org-selection/id-3"), result);
    }

    @Test
    void absoluteFromRequest_withNonDefaultPort_includesPort() {
        // A request URI with a non-default port should include the port.
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn(null);
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://auth.example.com:9090/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/id-4");

        assertEquals(URI.create("http://auth.example.com:9090/signin/id-4"), result);
    }

    @Test
    void absoluteFromRequest_withHttpsSchemeAndNonDefaultPort_includesPort() {
        // A request URI with https scheme but non-443 port should include the port.
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString("X-Forwarded-Proto")).thenReturn(null);
        when(ctx.getHeaderString("X-Forwarded-Host")).thenReturn(null);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://auth.example.com:8443/oauth2/authorize"));

        URI result = RedirectUtil.absoluteFromRequest(ctx, uriInfo, "/signin/id-5");

        assertEquals(URI.create("https://auth.example.com:8443/signin/id-5"), result);
    }
}
