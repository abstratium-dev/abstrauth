package dev.abstratium.abstrauth.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.util.KeyUtils;

/**
 * Verifies the signature of abstrauth-issued JWT access tokens and decodes the
 * payload. Tokens are expected to be signed with PS256 (RSA-PSS with SHA-256).
 * <p>
 * This is a plain utility class (not CDI-managed) so it can be used from both
 * production code and tests with either a key content string or a ready-made
 * {@link PublicKey}.
 */
public class JwtSignatureVerifier {

    private static final String EXPECTED_ALGORITHM = "PS256";
    private static final String JAVA_SIGNATURE_ALGORITHM = "RSASSA-PSS";

    private final PublicKey publicKey;

    /**
     * Build a verifier from a public key content string. The key may be supplied as
     * PEM-encoded text or as a base64-encoded DER SubjectPublicKeyInfo blob (the
     * format used by {@code mp.jwt.verify.publickey}).
     */
    public JwtSignatureVerifier(String publicKeyContent) {
        try {
            this.publicKey = KeyUtils.decodePublicKey(publicKeyContent, SignatureAlgorithm.PS256);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode JWT verification public key", e);
        }
    }

    /**
     * Build a verifier from an already-loaded public key.
     */
    public JwtSignatureVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Verify the signature of the supplied JWT and return its decoded payload.
     *
     * @throws IllegalArgumentException if the token is malformed, uses an unsupported
     *                                  algorithm, or has an invalid signature
     */
    public JsonObject verifyAndDecode(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Not a valid JWT (expected 3 parts)");
        }

        JsonObject header = decodeBase64Json(parts[0]);
        String alg = header.containsKey("alg") ? header.getString("alg") : null;
        if (!EXPECTED_ALGORITHM.equals(alg)) {
            throw new IllegalArgumentException("Unsupported or missing signature algorithm: " + alg);
        }

        String signedInput = parts[0] + "." + parts[1];
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JWT signature encoding", e);
        }

        try {
            Signature signature = Signature.getInstance(JAVA_SIGNATURE_ALGORITHM);
            PSSParameterSpec pssSpec = new PSSParameterSpec(
                    "SHA-256", "MGF1",
                    java.security.spec.MGF1ParameterSpec.SHA256,
                    32, 1);
            signature.setParameter(pssSpec);
            signature.initVerify(publicKey);
            signature.update(signedInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(signatureBytes)) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | SignatureException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Failed to verify JWT signature", e);
        }

        return decodeBase64Json(parts[1]);
    }

    private JsonObject decodeBase64Json(String base64) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return Json.createReader(new java.io.StringReader(json)).readObject();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JWT base64url encoding", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT JSON content", e);
        }
    }
}
