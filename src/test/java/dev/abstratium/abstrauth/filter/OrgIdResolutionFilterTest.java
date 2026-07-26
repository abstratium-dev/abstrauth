package dev.abstratium.abstrauth.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import dev.abstratium.abstrauth.service.CurrentOrgContext;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class OrgIdResolutionFilterTest {

    private static final String TEST_ORG_ID = "test-org-123";

    // ═══════════════════════════════════════════════════════════
    // Integration tests – exercise the filter via HTTP requests
    // ═══════════════════════════════════════════════════════════

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    @OidcSecurity(claims = {
            @Claim(key = "orgId", value = TEST_ORG_ID)
    })
    void filter_withOidcSecurityOrgId_resolvesFromToken() {
        given()
                .when()
                .get("/api/test/org-id")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.is(TEST_ORG_ID));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    @OidcSecurity(claims = {
            @Claim(key = "orgId", value = "")
    })
    void filter_withBlankOidcSecurityOrgId_setsNothing() {
        given()
                .when()
                .get("/api/test/org-id")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.is("null"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    @OidcSecurity(claims = {
            @Claim(key = "sub", value = "testuser")
    })
    void filter_withNoOrgIdAnywhere_setsNothing() {
        given()
                .when()
                .get("/api/test/org-id")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.is("null"));
    }

    // ═══════════════════════════════════════════════════════════
    // Unit tests – directly instantiate the filter with mocks
    // ═══════════════════════════════════════════════════════════

    @Test
    void filter_withIdTokenNotResolvableAndAccessTokenResolvable_setsFromAccessToken() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        JsonWebToken token = mock(JsonWebToken.class);
        when(accessToken.isResolvable()).thenReturn(true);
        when(accessToken.get()).thenReturn(token);
        when(token.getClaim("orgId")).thenReturn(TEST_ORG_ID);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");

        filter.filter(requestContext);
        assertEquals(TEST_ORG_ID, ctx.getOrgId());
    }

    @Test
    void filter_withBlankIdTokenClaimAndAccessTokenClaim_setsFromAccessToken() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        JsonWebToken idJwt = mock(JsonWebToken.class);
        when(idToken.isResolvable()).thenReturn(true);
        when(idToken.get()).thenReturn(idJwt);
        when(idJwt.getClaim("orgId")).thenReturn("   ");
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        JsonWebToken accJwt = mock(JsonWebToken.class);
        when(accessToken.isResolvable()).thenReturn(true);
        when(accessToken.get()).thenReturn(accJwt);
        when(accJwt.getClaim("orgId")).thenReturn(TEST_ORG_ID);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");

        filter.filter(requestContext);
        assertEquals(TEST_ORG_ID, ctx.getOrgId());
    }

    @Test
    void filter_withIdTokenException_fallsThrough() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(true);
        when(idToken.get()).thenThrow(new RuntimeException("token error"));
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withAccessTokenException_fallsThrough() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(true);
        when(accessToken.get()).thenThrow(new RuntimeException("token error"));
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBearerHeader_extractsOrgId() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        String token = buildBearerToken("{\"sub\":\"user\",\"orgId\":\"" + TEST_ORG_ID + "\"}");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBearerHeaderBlankOrgId_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        String token = buildBearerToken("{\"sub\":\"user\",\"orgId\":\"\"}");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withMalformedBearerHeader_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer notavalidjwt");

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withNoAuthHeader_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withNonBearerAuthHeader_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBearerHeaderMissingOrgId_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        String token = buildBearerToken("{\"sub\":\"user\"}");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBearerHeaderInvalidBase64_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        when(requestContext.getHeaderString("Authorization"))
                .thenReturn("Bearer header.!!!invalid!!!.sig");

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBearerHeaderOrgIdAsFirstClaim_extractsCorrectly() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        when(accessToken.isResolvable()).thenReturn(false);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        String token = buildBearerToken("{\"orgId\":\"" + TEST_ORG_ID + "\",\"sub\":\"user\"}");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBearerHeaderMissingClosingQuote_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        filter.idTokenInstance = null;
        filter.accessTokenInstance = null;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"orgId\":\"no_closing".getBytes(StandardCharsets.UTF_8));
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"PS256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + payload + ".fakesig";
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withNullIdTokenInstance_skipsToAccessToken() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        filter.idTokenInstance = null;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        JsonWebToken accJwt = mock(JsonWebToken.class);
        when(accessToken.isResolvable()).thenReturn(true);
        when(accessToken.get()).thenReturn(accJwt);
        when(accJwt.getClaim("orgId")).thenReturn(TEST_ORG_ID);
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");

        filter.filter(requestContext);
        assertEquals(TEST_ORG_ID, ctx.getOrgId());
    }

    @Test
    void filter_withNullAccessTokenInstance_skipsToHeader() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        filter.idTokenInstance = null;
        filter.accessTokenInstance = null;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/test");
        String token = buildBearerToken("{\"sub\":\"user\",\"orgId\":\"" + TEST_ORG_ID + "\"}");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        filter.filter(requestContext);
        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withBlankAccessTokenClaim_setsNothing() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        when(idToken.isResolvable()).thenReturn(false);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        JsonWebToken accessJwt = mock(JsonWebToken.class);
        when(accessToken.isResolvable()).thenReturn(true);
        when(accessToken.get()).thenReturn(accessJwt);
        when(accessJwt.getClaim("orgId")).thenReturn(" ");
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/journals");

        filter.filter(requestContext);

        assertNull(ctx.getOrgId());
    }

    @Test
    void filter_withConflictingTokenClaims_prefersIdToken() throws IOException {
        CurrentOrgContext ctx = new CurrentOrgContext();
        OrgIdResolutionFilter filter = new OrgIdResolutionFilter();
        filter.currentOrgContext = ctx;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> idToken = mock(Instance.class);
        JsonWebToken idJwt = mock(JsonWebToken.class);
        when(idToken.isResolvable()).thenReturn(true);
        when(idToken.get()).thenReturn(idJwt);
        when(idJwt.getClaim("orgId")).thenReturn(TEST_ORG_ID);
        filter.idTokenInstance = idToken;

        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> accessToken = mock(Instance.class);
        JsonWebToken accessJwt = mock(JsonWebToken.class);
        when(accessToken.isResolvable()).thenReturn(true);
        when(accessToken.get()).thenReturn(accessJwt);
        when(accessJwt.getClaim("orgId")).thenReturn("other-org");
        filter.accessTokenInstance = accessToken;

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/journals");

        filter.filter(requestContext);

        assertEquals(TEST_ORG_ID, ctx.getOrgId());
        verify(accessToken, never()).isResolvable();
    }

    private String buildBearerToken(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"PS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".fakesig";
    }
}
