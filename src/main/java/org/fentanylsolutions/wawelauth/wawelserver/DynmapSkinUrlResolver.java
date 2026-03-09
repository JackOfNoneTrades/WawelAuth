package org.fentanylsolutions.wawelauth.wawelserver;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;
import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.fentanylsolutions.wawelauth.wawelcore.util.StringUtil;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * Resolves an absolute skin URL for Dynmap's face generator.
 */
public final class DynmapSkinUrlResolver {

    private DynmapSkinUrlResolver() {}

    public static String resolve(EntityPlayer player, String currentSkinUrl) {
        String fromProfile = resolveFromProfile(player == null ? null : player.getGameProfile());
        if (fromProfile != null) {
            return fromProfile;
        }

        String apiRoot = resolveLocalApiRoot();
        String normalizedCurrent = absolutizeSkinUrl(currentSkinUrl, apiRoot);
        if (normalizedCurrent != null) {
            return normalizedCurrent;
        }

        if (player == null || apiRoot == null) {
            return currentSkinUrl;
        }

        WawelServer server = WawelServer.instance();
        if (server == null || server.getProfileDAO() == null) {
            return currentSkinUrl;
        }

        WawelProfile profile = server.getProfileDAO()
            .findByUuid(player.getUniqueID());
        if (profile == null) {
            return currentSkinUrl;
        }

        String hash = StringUtil.trimToNull(profile.getSkinHash());
        if (hash == null) {
            return currentSkinUrl;
        }

        return apiRoot + "/textures/" + hash;
    }

    static String resolveFromProfile(GameProfile profile) {
        if (profile == null) return null;

        Collection<Property> properties = profile.getProperties()
            .get("textures");
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        String apiRoot = resolveLocalApiRoot();
        for (Property property : properties) {
            if (property == null) continue;
            String url = extractSkinUrlFromTexturesProperty(property.getValue());
            if (url != null) {
                return absolutizeSkinUrl(url, apiRoot);
            }
        }

        return null;
    }

    static String extractSkinUrlFromTexturesProperty(String texturesBase64) {
        String value = StringUtil.trimToNull(texturesBase64);
        if (value == null) return null;

        try {
            byte[] jsonBytes = Base64.getDecoder()
                .decode(value);
            JsonObject root = new JsonParser().parse(new String(jsonBytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) return null;
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) return null;
            return optionalString(skin, "url");
        } catch (Exception e) {
            return null;
        }
    }

    static String absolutizeSkinUrl(String rawSkinUrl, String apiRoot) {
        String value = StringUtil.trimToNull(rawSkinUrl);
        if (value == null) return null;

        if (isAbsoluteHttpUrl(value)) {
            return value;
        }

        String base = normalizeApiRoot(apiRoot);
        if (base == null) {
            return value;
        }

        if (value.startsWith("/")) {
            return base + value;
        }
        if (value.startsWith("textures/")) {
            return base + "/" + value;
        }

        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        if (object == null || key == null
            || !object.has(key)
            || object.get(key)
                .isJsonNull()) {
            return null;
        }
        try {
            return object.get(key)
                .getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isAbsoluteHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String resolveLocalApiRoot() {
        String configured = normalizeApiRoot(
            WawelPingPayload.normalizeUrl(
                Config.server() == null ? null
                    : Config.server()
                        .getApiRoot()));
        if (configured != null) {
            return configured;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;

        int port = server.getServerPort();
        if (port <= 0) {
            port = server.getPort();
        }
        if (port <= 0) return null;

        InetAddress loopback = InetAddress.getLoopbackAddress();
        String host = loopback == null ? "127.0.0.1" : loopback.getHostAddress();
        return "http://" + NetworkAddressUtil.formatHostPort(host, port);
    }

    private static String normalizeApiRoot(String apiRoot) {
        String value = WawelPingPayload.normalizeUrl(apiRoot);
        if (value == null) {
            return null;
        }
        if (isAbsoluteHttpUrl(value)) {
            return value;
        }
        return "http://" + value;
    }
}
