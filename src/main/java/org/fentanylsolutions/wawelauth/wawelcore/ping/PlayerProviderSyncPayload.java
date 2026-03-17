package org.fentanylsolutions.wawelauth.wawelcore.ping;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.util.UUIDTypeAdapter;

/**
 * Encode/decode player-provider sync payload (WAUTH|PP).
 * Maps player UUID to the session server URL of the provider that authenticated them.
 */
public final class PlayerProviderSyncPayload {

    public static final String CHANNEL = "WAUTH|PP";

    private PlayerProviderSyncPayload() {}

    /** Encode a player-provider mapping to send to a client. */
    public static byte[] encode(Map<UUID, String> playerProviders) {
        JsonObject root = new JsonObject();
        JsonObject players = new JsonObject();
        for (Map.Entry<UUID, String> entry : playerProviders.entrySet()) {
            players.addProperty(UUIDTypeAdapter.fromUUID(entry.getKey()), entry.getValue());
        }
        root.add("players", players);
        return root.toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encode a single player-provider association.
     */
    public static byte[] encodeSingle(UUID playerUuid, String providerSessionUrl) {
        JsonObject root = new JsonObject();
        JsonObject players = new JsonObject();
        players.addProperty(UUIDTypeAdapter.fromUUID(playerUuid), providerSessionUrl);
        root.add("players", players);
        return root.toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    /** Decode a player-provider mapping, or empty map on failure. */
    public static Map<UUID, String> decode(byte[] data) {
        if (data == null || data.length == 0) {
            return Collections.emptyMap();
        }

        try {
            JsonElement parsed = new JsonParser().parse(new String(data, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return Collections.emptyMap();
            }

            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("players") || !root.get("players")
                .isJsonObject()) {
                return Collections.emptyMap();
            }

            JsonObject players = root.getAsJsonObject("players");
            Map<UUID, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                try {
                    UUID uuid = UUIDTypeAdapter.fromString(entry.getKey());
                    if (entry.getValue() instanceof JsonPrimitive) {
                        String sessionUrl = entry.getValue()
                            .getAsString();
                        if (sessionUrl != null && !sessionUrl.trim()
                            .isEmpty()) {
                            result.put(uuid, sessionUrl);
                        }
                    }
                } catch (Exception ignored) {}
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
