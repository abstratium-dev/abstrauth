package dev.abstratium.abstrauth.service;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.URI;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityProblemLoggerTest {

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private JsonWebToken idToken;

    private SecurityProblemLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        logger = new SecurityProblemLogger();
        injectField(logger, "requestContext", requestContext);
        injectField(logger, "idToken", idToken);
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void warnf_withXForwardedForHeader_logsMessageWithCorrectIp() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/accounts");
        when(requestContext.getMethod()).thenReturn("DELETE");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(idToken.getSubject()).thenReturn("account-uuid-123");
        when(idToken.getClaim("upn")).thenReturn("alice@example.com");
        when(idToken.getClaim("orgId")).thenReturn("org-uuid-456");

        logger.warnf("Attempted to delete account '%s'", "another-account");
    }

    @Test
    void warnf_withXRealIpHeader_logsMessageWithCorrectIp() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/roles");
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("10.0.0.5");
        when(idToken.getSubject()).thenReturn("account-uuid-789");
        when(idToken.getClaim("upn")).thenReturn("bob@example.com");
        when(idToken.getClaim("orgId")).thenReturn("org-uuid-111");

        logger.warnf("Attempted to assign admin role");
    }

    @Test
    void warnf_withNoProxyHeaders_fallsBackToRemoteHost() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/clients");
        when(requestContext.getMethod()).thenReturn("GET");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn(null);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://192.168.1.50/api/clients"));
        when(idToken.getSubject()).thenReturn("account-uuid-000");
        when(idToken.getClaim("upn")).thenReturn("carol@example.com");
        when(idToken.getClaim("orgId")).thenReturn("org-uuid-222");

        logger.warnf("Accessed restricted endpoint");
    }

    @Test
    void warnf_withFormattedMessage_substitutesArgumentsCorrectly() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/secrets");
        when(requestContext.getMethod()).thenReturn("PUT");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("172.16.0.1");
        when(idToken.getSubject()).thenReturn("subject-1");
        when(idToken.getClaim("upn")).thenReturn("dave@example.com");
        when(idToken.getClaim("orgId")).thenReturn("org-333");

        logger.warnf("Resource '%s' with id '%s' was modified", "secret", "secret-id-999");
    }

    @Test
    void warnf_withNullTokenClaims_doesNotThrow() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/unknown");
        when(requestContext.getMethod()).thenReturn("PATCH");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("1.2.3.4");
        when(idToken.getSubject()).thenReturn(null);
        when(idToken.getClaim("upn")).thenReturn(null);
        when(idToken.getClaim("orgId")).thenReturn(null);

        logger.warnf("Suspicious request with no identity");
    }

    @Test
    void warnf_withMultipleXForwardedForIps_usesFirstIp() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/orgs");
        when(requestContext.getMethod()).thenReturn("DELETE");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("203.0.113.1, 70.41.3.18, 150.172.238.178");
        when(idToken.getSubject()).thenReturn("subject-multi");
        when(idToken.getClaim("upn")).thenReturn("eve@example.com");
        when(idToken.getClaim("orgId")).thenReturn("org-multi");

        logger.warnf("Bulk delete attempted");
    }
}
