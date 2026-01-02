package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to ensure that redirect URIs are generated with HTTPS scheme
 * when the application is behind a reverse proxy that sets X-Forwarded-Proto.
 * 
 * This prevents the "Invalid redirect_uri" error in production where
 * nginx terminates TLS and forwards requests as HTTP to the application.
 * 
 * Key configuration properties:
 * - quarkus.oidc.bff.authentication.force-redirect-https-scheme=true (forces HTTPS in redirect_uri in prod)
 * 
 * Root cause of the issue:
 * When nginx terminates TLS and forwards HTTP to the application, Quarkus OIDC
 * would generate redirect_uri=http://auth.abstratium.dev/api/auth/callback
 * but the database has redirect_uri=https://auth.abstratium.dev/api/auth/callback
 * causing an "Invalid redirect_uri" error.
 */
@QuarkusTest
public class RedirectUriHttpsTest {

    @Test
    public void testProxyConfigurationIsEnabled() {
        // Verify that proxy forwarding is enabled
        String proxyForwarding = ConfigProvider.getConfig()
            .getValue("quarkus.http.proxy.proxy-address-forwarding", String.class);
        assertEquals("true", proxyForwarding,
            "quarkus.http.proxy.proxy-address-forwarding must be true to support reverse proxy");
    }

    @Test
    public void testAuthorizationEndpointAcceptsHttpsRedirectUri() {
        // Given: A request to the OAuth authorization endpoint with HTTPS redirect_uri
        //        and X-Forwarded-Proto: https
        // When: Making an authorization request
        Response response = given()
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "auth.abstratium.dev")
            .queryParam("response_type", "code")
            .queryParam("client_id", "abstratium-abstrauth")
            .queryParam("redirect_uri", "https://auth.abstratium.dev/api/auth/callback")
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test-state-12345")
            .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
            .queryParam("code_challenge_method", "S256")
            .redirects().follow(false)
            .when()
            .get("/oauth2/authorize");

        // Then: Should NOT return "Invalid redirect_uri" error
        // The response should be 303 (redirect to signin page) since we're not authenticated
        int statusCode = response.statusCode();
        
        // Should redirect to signin page
        assertEquals(303, statusCode, 
            "Should redirect to signin page when not authenticated");
        String location = response.header("Location");
        assertTrue(location != null && location.contains("/signin/"),
            "Should redirect to signin page. Location: " + location);
    }

    @Test
    public void testHttpRedirectUriIsRejectedEvenWithHttpsForwardedProto() {
        // Given: A request with HTTPS X-Forwarded-Proto but HTTP redirect_uri
        // When: Making an authorization request with mismatched scheme
        Response response = given()
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "auth.abstratium.dev")
            .queryParam("response_type", "code")
            .queryParam("client_id", "abstratium-abstrauth")
            .queryParam("redirect_uri", "http://auth.abstratium.dev/api/auth/callback")  // HTTP instead of HTTPS
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test-state-67890")
            .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
            .queryParam("code_challenge_method", "S256")
            .redirects().follow(false)
            .when()
            .get("/oauth2/authorize");

        // Then: Should return "Invalid redirect_uri" error
        assertEquals(400, response.statusCode(),
            "Should reject HTTP redirect_uri when configured redirect_uri is HTTPS");
        
        String body = response.body().asString();
        assertTrue(body.contains("Invalid redirect_uri"),
            "Should return 'Invalid redirect_uri' error when using HTTP instead of HTTPS. Body: " + body);
    }
}
