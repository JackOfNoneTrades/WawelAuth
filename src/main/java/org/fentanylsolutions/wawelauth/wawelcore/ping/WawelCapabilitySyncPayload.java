package org.fentanylsolutions.wawelauth.wawelcore.ping;

import java.nio.charset.StandardCharsets;

import org.fentanylsolutions.wawelauth.config.ServerConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Shared encoder/decoder for the post-join capability sync payload.
 */
public final class WawelCapabilitySyncPayload {

    public static final String CHANNEL = "WAUTH|CAPS";

    private WawelCapabilitySyncPayload() {}

    public static byte[] encodeServerPayload(ServerConfig config, String localPublicKeyBase64) {
        JsonObject payload = WawelPingPayload.buildServerPayload(config, localPublicKeyBase64);
        return payload.toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    public static JsonObject decodePayload(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            JsonElement parsed = new JsonParser().parse(new String(data, StandardCharsets.UTF_8));
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
