package dev.abstratium.abstrauth.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CompleteOAuthFlowTest: Tests the token exchange phase and cryptographic verification
 */
@QuarkusTest
public class CompleteOAuthFlowTest {

    private static final String CLIENT_ID = "abstrauth_admin_app";
    private static final String REDIRECT_URI = "http://localhost:8080/auth-callback";
    private static final String TEST_USERNAME = "flowtest_" + System.currentTimeMillis();
    private static final String TEST_EMAIL = "flowtest_" + System.currentTimeMillis() + "@example.com";
    private static final String TEST_PASSWORD = "SecurePassword123";
    private static final String TEST_NAME = "Flow Test User";

    @BeforeEach
    public void setup() {
        // Sign up a test user
        given()
            .formParam("email", TEST_EMAIL)
            .formParam("name", TEST_NAME)
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .when()
            .post("/api/signup")
            .then()
            .statusCode(anyOf(is(201), is(409)));
    }

    @Test
    public void testCompleteOAuthFlowWithTokenExchange() throws Exception {
        // Step 1: Generate PKCE parameters
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // Step 2: Initiate authorization request
        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test_state_123")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.asString());
        assertNotNull(requestId);

        // Step 3: Submit login credentials
        Response loginResponse = given()
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        assertTrue(loginResponse.asString().contains("Authorize Application"));

        // Step 4: Approve consent
        Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String redirectLocation = consentResponse.getHeader("Location");
        String authCode = extractParameter(redirectLocation, "code");
        assertNotNull(authCode);

        // Step 5: Exchange authorization code for access token
        Response tokenResponse = given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", authCode)
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", codeVerifier)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("token_type", equalTo("Bearer"))
            .body("expires_in", equalTo(3600))
            .body("scope", equalTo("openid profile email"))
            .extract()
            .response();

        String accessToken = tokenResponse.jsonPath().getString("access_token");
        assertNotNull(accessToken);
        assertTrue(accessToken.length() > 100, "JWT token should be substantial");

        // Verify token contains expected claims (basic check)
        String[] tokenParts = accessToken.split("\\.");
        assertEquals(3, tokenParts.length, "JWT should have 3 parts");

        System.out.println("✓ Complete OAuth flow successful!");
        System.out.println("Access Token: " + accessToken.substring(0, 50) + "...");
    }

    @Test
    public void testTokenExchangeWithInvalidCode() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", "invalid_code")
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", "some_verifier")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"))
            .body("error_description", containsString("invalid or expired"));
    }

    @Test
    public void testTokenExchangeWithInvalidPKCE() throws Exception {
        // Generate PKCE and get authorization code
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.asString());

        given()
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String authCode = extractParameter(consentResponse.getHeader("Location"), "code");

        // Try to exchange with wrong verifier
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", authCode)
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", "wrong_verifier_12345678901234567890123456789012")
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"))
            .body("error_description", containsString("Invalid code_verifier"));
    }

    @Test
    public void testTokenExchangeWithoutPKCEWhenRequired() throws Exception {
        // Generate PKCE and get authorization code
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.asString());

        given()
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String authCode = extractParameter(consentResponse.getHeader("Location"), "code");

        // Try to exchange without code_verifier
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", authCode)
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("code_verifier is required"));
    }


    // Helper methods

    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] code = new byte[32];
        secureRandom.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String extractRequestId(String html) {
        Pattern pattern = Pattern.compile("name='request_id'\\s+value='([^']+)'");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractParameter(String url, String paramName) {
        Pattern pattern = Pattern.compile(paramName + "=([^&]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Test
    public void testTokenSignatureCanBeVerifiedWithPublicKey() throws Exception {
        // Create unique user for this test
        String testUser = "sigtest_" + System.nanoTime();
        String testEmail = "sigtest_" + System.nanoTime() + "@example.com";
        String testPassword = "SecurePassword123";
        
        given()
            .formParam("email", testEmail)
            .formParam("name", "Signature Test User")
            .formParam("username", testUser)
            .formParam("password", testPassword)
            .when()
            .post("/api/signup")
            .then()
            .statusCode(201);

        // Step 1: Complete OAuth flow to get a token
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Response authResponse = given()
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile email")
            .queryParam("state", "test_state")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .when()
            .get("/oauth2/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String requestId = extractRequestId(authResponse.asString());

        // Submit login credentials
        given()
            .formParam("username", testUser)
            .formParam("password", testPassword)
            .formParam("request_id", requestId)
            .when()
            .post("/oauth2/authorize/authenticate")
            .then()
            .statusCode(200);

        // Approve consent
        Response consentResponse = given()
            .formParam("consent", "approve")
            .formParam("request_id", requestId)
            .redirects().follow(false)
            .when()
            .post("/oauth2/authorize")
            .then()
            .statusCode(303)
            .extract()
            .response();

        String authCode = extractParameter(consentResponse.getHeader("Location"), "code");

        Response tokenResponse = given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", authCode)
            .formParam("client_id", CLIENT_ID)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("code_verifier", codeVerifier)
            .when()
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String accessToken = tokenResponse.jsonPath().getString("access_token");

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

        // Step 3: Reconstruct public key and verify signature
        RSAPublicKey publicKey = reconstructPublicKey(n, e);
        boolean isValid = verifyJwtSignature(accessToken, publicKey);

        assertTrue(isValid, "Token signature must be valid using public key from JWKS endpoint");
        System.out.println("✓ Token signature verified successfully with public key from JWKS!");
    }

    private RSAPublicKey reconstructPublicKey(String nBase64, String eBase64) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(nBase64);
        byte[] eBytes = Base64.getUrlDecoder().decode(eBase64);

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
            e.printStackTrace();
            return false;
        }
    }
}
