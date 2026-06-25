package dev.abstratium.abstrauth.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JwtSignatureVerifier}.
 * <p>
 * Tests cover both positive verification (a correctly signed PS256 token is
 * accepted and decoded) and negative cases (tampered signature, wrong algorithm,
 * malformed token, missing signature).
 */
public class JwtSignatureVerifierTest {

    private static final String ISSUER = "https://auth.abstratium.dev";
    private static final String SUBJECT = "user-123";
    private static final String AUDIENCE = "abstratium-abstrauth";

    private KeyPair keyPair;
    private JwtSignatureVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = generateRsaKeyPair();
        verifier = new JwtSignatureVerifier(keyPair.getPublic());
    }

    @Test
    void verifyAndDecode_withValidPs256Token_returnsDecodedPayload() throws Exception {
        String token = buildJwt(keyPair.getPrivate(), "PS256", Instant.now().plusSeconds(900));

        JsonObject payload = verifier.verifyAndDecode(token);

        assertEquals(ISSUER, payload.getString("iss"));
        assertEquals(SUBJECT, payload.getString("sub"));
        assertEquals(AUDIENCE, payload.getString("aud"));
    }

    @Test
    void verifyAndDecode_withTamperedSignature_throwsInvalidSignature() throws Exception {
        String token = buildJwt(keyPair.getPrivate(), "PS256", Instant.now().plusSeconds(900));
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1)
                + "A".repeat(signature.length());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndDecode(tamperedToken));
        assertTrue(ex.getMessage().contains("Invalid JWT signature"));
    }

    @Test
    void verifyAndDecode_withWrongAlgorithm_throwsUnsupportedAlgorithm() throws Exception {
        String token = buildJwt(keyPair.getPrivate(), "RS256", Instant.now().plusSeconds(900));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndDecode(token));
        assertTrue(ex.getMessage().contains("Unsupported or missing signature algorithm"));
    }

    @Test
    void verifyAndDecode_withMalformedToken_throwsInvalidJwt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndDecode("not.a.jwt"));
        assertTrue(ex.getMessage().contains("Invalid JWT"));
    }

    @Test
    void verifyAndDecode_withMissingSignature_throwsInvalidJwt() throws Exception {
        String token = buildJwt(keyPair.getPrivate(), "PS256", Instant.now().plusSeconds(900));
        String withoutSignature = token.substring(0, token.lastIndexOf('.'));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndDecode(withoutSignature));
        assertTrue(ex.getMessage().contains("Not a valid JWT"));
    }

    @Test
    void verifyAndDecode_withTokenSignedByDifferentKey_throwsInvalidSignature() throws Exception {
        KeyPair otherKeyPair = generateRsaKeyPair();
        String token = buildJwt(otherKeyPair.getPrivate(), "PS256", Instant.now().plusSeconds(900));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndDecode(token));
        assertTrue(ex.getMessage().contains("Invalid JWT signature"));
    }

    @Test
    void constructor_withValidBase64PublicKey_initializesVerifier() throws Exception {
        String publicKeyContent = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String token = buildJwt(keyPair.getPrivate(), "PS256", Instant.now().plusSeconds(900));

        JwtSignatureVerifier contentVerifier = new JwtSignatureVerifier(publicKeyContent);
        JsonObject payload = contentVerifier.verifyAndDecode(token);

        assertEquals(SUBJECT, payload.getString("sub"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String buildJwt(PrivateKey privateKey, String algorithm, Instant expiresAt) throws Exception {
        String header = base64Url(Json.createObjectBuilder()
                .add("alg", algorithm)
                .add("typ", "JWT")
                .add("kid", "test-key-1")
                .build().toString());

        String payload = base64Url(Json.createObjectBuilder()
                .add("iss", ISSUER)
                .add("sub", SUBJECT)
                .add("aud", AUDIENCE)
                .add("exp", expiresAt.getEpochSecond())
                .add("iat", Instant.now().getEpochSecond())
                .add("jti", UUID.randomUUID().toString())
                .build().toString());

        String signedInput = header + "." + payload;
        byte[] signatureBytes = sign(privateKey, algorithm, signedInput);
        String signature = base64Url(signatureBytes);

        return signedInput + "." + signature;
    }

    private static byte[] sign(PrivateKey privateKey, String algorithm, String signedInput) throws Exception {
        Signature signature = Signature.getInstance(algorithmInstanceName(algorithm));
        if ("PS256".equals(algorithm)) {
            PSSParameterSpec pssSpec = new PSSParameterSpec(
                    "SHA-256", "MGF1",
                    java.security.spec.MGF1ParameterSpec.SHA256,
                    32, 1);
            signature.setParameter(pssSpec);
        }
        signature.initSign(privateKey);
        signature.update(signedInput.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static String algorithmInstanceName(String algorithm) {
        return switch (algorithm) {
            case "PS256" -> "RSASSA-PSS";
            case "RS256" -> "SHA256withRSA";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }

    private static String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }
}
