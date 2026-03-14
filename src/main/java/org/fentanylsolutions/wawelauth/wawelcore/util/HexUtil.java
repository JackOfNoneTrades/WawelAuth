package org.fentanylsolutions.wawelauth.wawelcore.util;

/**
 * Shared hex encoding/decoding utilities used across the WawelAuth codebase.
 */
public final class HexUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HexUtil() {}

    /**
     * Encode a byte array as a lowercase hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }

    /**
     * Decode a hex string to a byte array.
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Checks whether a string is valid hex (non-null, even length, all hex digits).
     */
    public static boolean isValidHex(String s) {
        if (s == null || s.isEmpty() || s.length() % 2 != 0) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) == -1) return false;
        }
        return true;
    }
}
