package org.fentanylsolutions.wawelauth.wawelcore.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.fentanylsolutions.wawelauth.wawelcore.util.HexUtil;

/**
 * Password hashing using PBKDF2-HMAC-SHA256.
 * <p>
 * Built into Java 8, no external dependencies needed. Uses OWASP-recommended
 * parameters: 210,000 iterations, 256-bit output, 128-bit random salt.
 * Salt and hash are stored as hex strings in the database.
 */
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Hash a password with a fresh random salt.
     *
     * @param password the plaintext password
     * @return result containing hex-encoded hash and salt
     */
    public static HashResult hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt);
        return new HashResult(bytesToHex(hash), bytesToHex(salt));
    }

    /**
     * Verify a password against a stored hash and salt.
     * <p>
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param password   the plaintext password to check
     * @param storedHash hex-encoded hash from the database
     * @param storedSalt hex-encoded salt from the database
     * @return true if the password matches
     */
    public static boolean verify(String password, String storedHash, String storedSalt) {
        if (!isValidHex(storedHash) || !isValidHex(storedSalt)) {
            return false;
        }
        byte[] salt = hexToBytes(storedSalt);
        byte[] expected = hexToBytes(storedHash);
        byte[] actual = pbkdf2(password.toCharArray(), salt);
        return constantTimeEquals(expected, actual);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_BITS);
            try {
                SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
                return skf.generateSecret(spec)
                    .getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 not available", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    static String bytesToHex(byte[] bytes) {
        return HexUtil.bytesToHex(bytes);
    }

    static byte[] hexToBytes(String hex) {
        return HexUtil.hexToBytes(hex);
    }

    private static boolean isValidHex(String s) {
        return HexUtil.isValidHex(s);
    }

    /**
     * Result of hashing a password.
     */
    public static class HashResult {

        private final String hash;
        private final String salt;

        public HashResult(String hash, String salt) {
            this.hash = hash;
            this.salt = salt;
        }

        /**
         * Hex-encoded PBKDF2 hash.
         */
        public String getHash() {
            return hash;
        }

        /**
         * Hex-encoded random salt.
         */
        public String getSalt() {
            return salt;
        }
    }
}
