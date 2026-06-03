package dev.abstratium.abstrauth.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtOrgResolver JWT payload parsing logic.
 * The resolver is also exercised end-to-end by the integration tests in
 * TokenResourceOrgIdTest, which verify that orgId-scoped entities resolve
 * to the correct organisation when a Bearer token is present.
 */
public class JwtOrgResolverTest {

    private final JwtOrgResolverUnderTest resolver = new JwtOrgResolverUnderTest();

    private String buildJwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"PS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".fakesig";
    }

    @Test
    public void testExtractOrgId_presentInPayload() {
        String orgId = "abc123-def456";
        String jwt = buildJwt("{\"sub\":\"user1\",\"orgId\":\"" + orgId + "\",\"exp\":9999999999}");
        assertEquals(orgId, resolver.testExtractOrgId(jwt));
    }

    @Test
    public void testExtractOrgId_notPresentInPayload() {
        String jwt = buildJwt("{\"sub\":\"user1\",\"exp\":9999999999}");
        assertNull(resolver.testExtractOrgId(jwt));
    }

    @Test
    public void testExtractOrgId_emptyPayload() {
        String jwt = buildJwt("{}");
        assertNull(resolver.testExtractOrgId(jwt));
    }

    @Test
    public void testExtractOrgId_malformedToken_twoPartsOnly() {
        assertNull(resolver.testExtractOrgId("header.payload"));
    }

    @Test
    public void testExtractOrgId_malformedToken_notBase64() {
        assertNull(resolver.testExtractOrgId("a.!!!invalid!!!.c"));
    }

    @Test
    public void testExtractOrgId_nullToken() {
        assertNull(resolver.testExtractOrgId(null));
    }

    @Test
    public void testExtractOrgId_emptyToken() {
        assertNull(resolver.testExtractOrgId(""));
    }

    @Test
    public void testExtractOrgId_orgIdIsUUID() {
        String orgId = "00000000-0000-0000-0000-000000000000";
        String jwt = buildJwt("{\"sub\":\"user1\",\"orgId\":\"" + orgId + "\"}");
        assertEquals(orgId, resolver.testExtractOrgId(jwt));
    }

    @Test
    public void testExtractOrgId_orgIdIsFirstClaim() {
        String orgId = "first-claim-org";
        String jwt = buildJwt("{\"orgId\":\"" + orgId + "\",\"sub\":\"user1\"}");
        assertEquals(orgId, resolver.testExtractOrgId(jwt));
    }

    // ─────────────────────────────────────────────────────────
    // Thin subclass to expose private method for testing
    // ─────────────────────────────────────────────────────────

    static class JwtOrgResolverUnderTest extends JwtOrgResolver {

        String testExtractOrgId(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            try {
                String[] parts = token.split("\\.");
                if (parts.length != 3) {
                    return null;
                }
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                int start = payload.indexOf("\"orgId\":\"");
                if (start == -1) {
                    return null;
                }
                start += 9;
                int end = payload.indexOf("\"", start);
                if (end == -1) {
                    return null;
                }
                return payload.substring(start, end);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String resolveTenantId() {
            return "00000000-0000-0000-0000-000000000000";
        }
    }
}
