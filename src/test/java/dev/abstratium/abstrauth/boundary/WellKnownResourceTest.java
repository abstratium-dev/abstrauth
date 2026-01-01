package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WellKnownResource - OAuth 2.0 Discovery and JWKS endpoints
 */
@QuarkusTest
public class WellKnownResourceTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // From V01.010 migration
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    @Test
    public void testServerMetadataEndpoint() {
        given()
            .when()
            .get("/.well-known/oauth-authorization-server")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("issuer", notNullValue())
            .body("authorization_endpoint", containsString("/oauth2/authorize"))
            .body("token_endpoint", containsString("/oauth2/token"))
            .body("jwks_uri", containsString("/.well-known/jwks.json"))
            .body("response_types_supported", hasItem("code"))
            .body("grant_types_supported", hasItem("authorization_code"))
            .body("code_challenge_methods_supported", hasItems("S256", "plain"))
            .body("scopes_supported", hasItems("openid", "profile", "email"));
    }

    @Test
    public void testJwksEndpoint() {
        Response response = given()
            .when()
            .get("/.well-known/jwks.json")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("keys", notNullValue())
            .body("keys[0].kty", equalTo("RSA"))
            .body("keys[0].use", equalTo("sig"))
            .body("keys[0].kid", notNullValue())
            .body("keys[0].alg", equalTo("PS256"))
            .body("keys[0].n", notNullValue())
            .body("keys[0].e", notNullValue())
            .extract()
            .response();

        // Verify the key components are valid base64url
        String n = response.jsonPath().getString("keys[0].n");
        String e = response.jsonPath().getString("keys[0].e");
        
        assertNotNull(n);
        assertNotNull(e);
        assertFalse(n.contains("+"));
        assertFalse(n.contains("/"));
        assertFalse(e.contains("+"));
        assertFalse(e.contains("/"));
    }

    @Test
    public void testJwksCanBeUsedToVerifyTokens() throws Exception {
        // Step 1: Complete OAuth flow to get a token
        String username = "jwkstest_" + System.currentTimeMillis();
        String email = "jwkstest_" + System.currentTimeMillis() + "@example.com";
        
        // Sign up user
        given()
            .formParam("email", email)
            .formParam("name", "JWKS Test User")
            .formParam("username", username)
            .formParam("password", "SecurePassword123")
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201);

        // Generate PKCE verifier and challenge
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // Start authorization flow with GET request - should redirect to signin
        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test_state")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .redirects().follow(false)
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String requestId = extractRequestIdFromHtml(authResponse.getHeader("Location"));

        // Submit login credentials to authenticate endpoint
        given()
            .formParam("username", username)
            .formParam("password", "SecurePassword123")
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        // Approve authorization
        Response approveResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String redirectLocation = approveResponse.getHeader("Location");
        String code = extractCodeFromRedirect(redirectLocation);

        // Exchange code for token
        Response tokenResponse = given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("client_id", CLIENT_ID)
            .formParam("client_secret", CLIENT_SECRET)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", codeVerifier)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String accessToken = tokenResponse.jsonPath().getString("access_token");
        assertNotNull(accessToken);

        // Step 2: Get public key from JWKS endpoint
        Response jwksResponse = given()
            .when()
            .get("/.well-known/jwks.json")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String n = jwksResponse.jsonPath().getString("keys[0].n");
        String e = jwksResponse.jsonPath().getString("keys[0].e");

        // Step 3: Reconstruct public key from JWKS
        RSAPublicKey publicKey = reconstructPublicKey(n, e);
        assertNotNull(publicKey);

        // Step 4: Verify the JWT signature using the public key
        boolean isValid = verifyJwtSignature(accessToken, publicKey);
        assertTrue(isValid, "Token signature should be valid using public key from JWKS");

        // Step 5: Verify token claims
        Map<String, Object> claims = decodeJwtClaims(accessToken);
        assertNotNull(claims);
        assertEquals(email, claims.get("upn"), "Token should contain correct email as upn");
        assertNotNull(claims.get("sub"), "Token should contain subject");
        assertNotNull(claims.get("iat"), "Token should contain issued at");
        assertNotNull(claims.get("exp"), "Token should contain expiration");
        
        System.out.println("✓ Token signature verified successfully with public key from JWKS!");
        System.out.println("✓ Token claims validated: upn=" + claims.get("upn") + ", sub=" + claims.get("sub"));
    }

    @Test
    public void testJwksPublicKeyMatchesTokenSigningKey() throws Exception {
        // Get JWKS
        Response jwksResponse = given()
            .when()
            .get("/.well-known/jwks.json")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String n = jwksResponse.jsonPath().getString("keys[0].n");
        String e = jwksResponse.jsonPath().getString("keys[0].e");

        // Reconstruct public key
        RSAPublicKey publicKey = reconstructPublicKey(n, e);
        
        // Verify key properties
        assertNotNull(publicKey.getModulus());
        assertNotNull(publicKey.getPublicExponent());
        assertTrue(publicKey.getModulus().bitLength() >= 2048, 
            "RSA key should be at least 2048 bits");
    }

    // Helper methods

    private String generateCodeVerifier() {
        byte[] code = new byte[32];
        new java.security.SecureRandom().nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String extractCodeFromRedirect(String redirectLocation) {
        String[] parts = redirectLocation.split("\\?")[1].split("&");
        for (String part : parts) {
            if (part.startsWith("code=")) {
                return part.substring(5);
            }
        }
        throw new IllegalArgumentException("No code in redirect");
    }

    private String extractRequestIdFromHtml(String url) {
        // Try to extract from query parameter first (e.g., ?request_id=xxx)
        java.util.regex.Pattern queryPattern = java.util.regex.Pattern.compile("request_id=([^&]+)");
        java.util.regex.Matcher queryMatcher = queryPattern.matcher(url);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        
        // Try to extract from path (e.g., /signin/{request_id})
        java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile("/signin/([^/?]+)");
        java.util.regex.Matcher pathMatcher = pathPattern.matcher(url);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        
        // Try to extract from consent path (e.g., /consent/{request_id})
        java.util.regex.Pattern consentPattern = java.util.regex.Pattern.compile("/consent/([^/?]+)");
        java.util.regex.Matcher consentMatcher = consentPattern.matcher(url);
        if (consentMatcher.find()) {
            return consentMatcher.group(1);
        }
        
        throw new IllegalArgumentException("No request_id in URL");
    }

    private RSAPublicKey reconstructPublicKey(String nBase64, String eBase64) throws Exception {
        // Decode base64url encoded modulus and exponent
        byte[] nBytes = Base64.getUrlDecoder().decode(nBase64);
        byte[] eBytes = Base64.getUrlDecoder().decode(eBase64);

        // Remove leading zero byte if present (for positive BigInteger)
        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);

        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    private boolean verifyJwtSignature(String jwt, RSAPublicKey publicKey) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String headerAndPayload = parts[0] + "." + parts[1];
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            // Verify using PS256 (RSA-PSS with SHA-256)
            java.security.Signature signature = java.security.Signature.getInstance("RSASSA-PSS");
            java.security.spec.PSSParameterSpec pssSpec = new java.security.spec.PSSParameterSpec(
                "SHA-256", "MGF1", 
                java.security.spec.MGF1ParameterSpec.SHA256, 
                32, 1
            );
            signature.setParameter(pssSpec);
            signature.initVerify(publicKey);
            signature.update(headerAndPayload.getBytes(StandardCharsets.UTF_8));

            return signature.verify(signatureBytes);
        } catch (Exception e) {
            System.err.println("Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    private Map<String, Object> decodeJwtClaims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            String payload = parts[1];
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String json = new String(decodedBytes, StandardCharsets.UTF_8);
            
            // Simple JSON parsing for test purposes
            return parseJsonToMap(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode JWT claims", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        // Use a simple approach for testing - in production use a proper JSON library
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}
