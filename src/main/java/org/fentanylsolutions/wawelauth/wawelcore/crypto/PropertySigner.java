package org.fentanylsolutions.wawelauth.wawelcore.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProfileProperty;

/**
 * SHA1withRSA signing and verification of Yggdrasil profile property values.
 * <p>
 * Per the Yggdrasil spec, the signature is computed over the property value
 * (the base64-encoded JSON string for textures properties) and encoded as
 * base64 in the response. Uses PKCS#1 v1.5 signature scheme.
 */
public class PropertySigner {

    private static final String ALGORITHM = "SHA1withRSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public PropertySigner(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public PropertySigner(KeyManager keyManager) {
        this(keyManager.getPrivateKey(), keyManager.getPublicKey());
    }

    /**
     * Sign a property value and return the base64-encoded signature.
     *
     * @param value the property value string (e.g. base64-encoded textures JSON)
     * @return base64-encoded SHA1withRSA signature
     */
    public String sign(String value) {
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                .encodeToString(sig.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Failed to sign property value", e);
        }
    }

    /**
     * Sign a ProfileProperty in place, setting its signature field.
     *
     * @param property the property to sign
     */
    public void signProperty(ProfileProperty property) {
        property.setSignature(sign(property.getValue()));
    }

    /**
     * Verify a signature against a property value.
     *
     * @param value           the property value string
     * @param signatureBase64 the base64-encoded signature to verify
     * @return true if the signature is valid
     */
    public boolean verify(String value, String signatureBase64) {
        return verifyWithKey(value, signatureBase64, publicKey);
    }

    /**
     * Verify a signature against a property value using a specific public key.
     * Useful for verifying signatures from external providers.
     *
     * @param value           the property value string
     * @param signatureBase64 the base64-encoded signature to verify
     * @param key             the public key to verify against
     * @return true if the signature is valid
     */
    public static boolean verifyWithKey(String value, String signatureBase64, PublicKey key) {
        try {
            byte[] sigBytes = Base64.getDecoder()
                .decode(signatureBase64);
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(key);
            sig.update(value.getBytes(StandardCharsets.UTF_8));
            return sig.verify(sigBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IllegalArgumentException e) {
            return false;
        }
    }
}
