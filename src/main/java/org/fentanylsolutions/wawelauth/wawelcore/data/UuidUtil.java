package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * UUID format utilities for Yggdrasil API compatibility.
 * <p>
 * The Yggdrasil spec uses "unsigned UUIDs": 32 hex characters with no dashes.
 * Java's UUID.toString() produces dashed format (8-4-4-4-12).
 * <p>
 * These helpers convert between the two representations.
 */
public final class UuidUtil {

    private UuidUtil() {}

    /**
     * Convert a UUID to unsigned format (32 hex chars, no dashes).
     * e.g. "853c80ef3c3749fdaa49938b674adae6"
     */
    public static String toUnsigned(UUID uuid) {
        return uuid.toString()
            .replace("-", "");
    }

    /**
     * Parse an unsigned UUID string (32 hex chars, no dashes) into a UUID.
     * Also accepts standard dashed format for convenience.
     */
    public static UUID fromUnsigned(String unsigned) {
        if (unsigned == null) return null;
        if (unsigned.contains("-")) {
            return UUID.fromString(unsigned);
        }
        if (unsigned.length() != 32) {
            throw new IllegalArgumentException("Invalid unsigned UUID: " + unsigned);
        }
        String dashed = unsigned.substring(0, 8) + "-"
            + unsigned.substring(8, 12)
            + "-"
            + unsigned.substring(12, 16)
            + "-"
            + unsigned.substring(16, 20)
            + "-"
            + unsigned.substring(20);
        return UUID.fromString(dashed);
    }

    /**
     * Compute the vanilla offline-mode UUID for a player name.
     */
    public static UUID offlinePlayerUuid(String playerName) {
        if (playerName == null) {
            throw new IllegalArgumentException("playerName must not be null");
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
