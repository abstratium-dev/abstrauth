package dev.abstratium.abstrauth;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify PKCE code challenge generation matches between JavaScript and Java
 */
@QuarkusTest
public class PKCETest {

    @Test
    public void testPKCECodeChallengeGeneration() throws Exception {
        // Test with a known code_verifier
        String codeVerifier = "qzm3Wha8q91KfWi6uxhvFnfb3fjApYOJbTgrcJ775uQwPuGrpiiWtiIls4gTt76oz45xJlSrbZZMAC08ZYhApycfQJBwPPxlq9bXc4DEv3fadTpf8lmyF8ss3YkiqWnZ";
        
        // Generate code challenge using the same algorithm as the server
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        
        System.out.println("Code Verifier: " + codeVerifier);
        System.out.println("Code Challenge: " + codeChallenge);
        
        // Verify the challenge is base64url encoded (no +, /, or =)
        assertFalse(codeChallenge.contains("+"), "Code challenge should not contain +");
        assertFalse(codeChallenge.contains("/"), "Code challenge should not contain /");
        assertFalse(codeChallenge.contains("="), "Code challenge should not contain =");
        
        // Verify it's 43 characters (256 bits / 6 bits per base64 char, no padding)
        assertEquals(43, codeChallenge.length(), "Code challenge should be 43 characters");
        
        // Expected value - this is the SHA-256 hash of the verifier, base64url encoded
        // You can verify in browser console with:
        // const verifier = "qzm3Wha8q91KfWi6uxhvFnfb3fjApYOJbTgrcJ775uQwPuGrpiiWtiIls4gTt76oz45xJlSrbZZMAC08ZYhApycfQJBwPPxlq9bXc4DEv3fadTpf8lmyF8ss3YkiqWnZ";
        // const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
        // btoa(String.fromCharCode(...new Uint8Array(digest))).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=/g, '')
        String expected = "-SP-ECNprkCj4uXN335qZBTszctq5QJibPKkTk0HvJs";
        assertEquals(expected, codeChallenge, "Code challenge should match expected value");
    }

    @Test
    public void testPKCEVerification() throws Exception {
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        
        // Generate challenge
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        
        assertEquals(expectedChallenge, computedChallenge, 
            "Computed challenge should match expected challenge from RFC 7636 example");
    }

    @Test
    public void testBase64UrlEncoding() {
        // Test that our encoding produces base64url format
        byte[] testBytes = new byte[]{(byte)0xFF, (byte)0xFE, (byte)0xFD};
        
        String base64url = Base64.getUrlEncoder().withoutPadding().encodeToString(testBytes);
        String base64 = Base64.getEncoder().encodeToString(testBytes);
        
        System.out.println("Base64:    " + base64);
        System.out.println("Base64URL: " + base64url);
        
        // base64url should use - instead of + and _ instead of /
        assertFalse(base64url.contains("+"));
        assertFalse(base64url.contains("/"));
        assertFalse(base64url.endsWith("="));
    }
}
