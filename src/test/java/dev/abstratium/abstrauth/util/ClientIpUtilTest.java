package dev.abstratium.abstrauth.util;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for ClientIpUtil.
 * Tests IP extraction from various HTTP headers set by reverse proxies.
 */
@QuarkusTest
public class ClientIpUtilTest {

    @Test
    void getClientIp_withXForwardedFor_returnsFirstIp() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("203.0.113.195");

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("203.0.113.195", result);
    }

    @Test
    void getClientIp_withXForwardedForMultipleIps_returnsFirstIp() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For"))
                .thenReturn("203.0.113.195, 70.41.3.18, 150.172.238.178");

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("203.0.113.195", result);
    }

    @Test
    void getClientIp_withXRealIpAndNoXForwardedFor_returnsXRealIp() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("198.51.100.42");

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("198.51.100.42", result);
    }

    @Test
    void getClientIp_withNoHeaders_returnsRemoteHost() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn(null);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://192.168.1.1/api/test"));
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("192.168.1.1", result);
    }

    @Test
    void getClientIp_withEmptyXForwardedFor_fallsBackToXRealIp() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("");
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("198.51.100.42");

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("198.51.100.42", result);
    }

    @Test
    void getClientIp_withEmptyXForwardedForAndEmptyXRealIp_fallsBackToRemoteHost() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("");
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("");
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://10.0.0.1/health"));
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("10.0.0.1", result);
    }

    @Test
    void getClientIp_withXForwardedForWithSpaces_trimsCorrectly() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-Forwarded-For"))
                .thenReturn("  203.0.113.195  , 70.41.3.18  ");

        String result = ClientIpUtil.getClientIp(requestContext);

        assertEquals("203.0.113.195", result);
    }
}
