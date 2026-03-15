package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;
import org.fentanylsolutions.wawelauth.wawelcore.util.JsonUtil;
import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.fentanylsolutions.wawelauth.wawelcore.util.StringUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.util.UUIDTypeAdapter;

/**
 * Server-side login verification bridge.
 * <p>
 * When the WawelAuth server module is enabled, this verifier queries the local
 * /sessionserver/session/minecraft/hasJoined endpoint first.
 */
public final class LocalSessionVerifier {

    private static final ThreadLocal<String> DISCONNECT_REASON = new ThreadLocal<String>();

    private LocalSessionVerifier() {}

    public static GameProfile hasJoinedServer(GameProfile user, String serverId, MinecraftSessionService vanillaService)
        throws AuthenticationUnavailableException {
        clearDisconnectReason();
        if (user == null || user.getName() == null
            || user.getName()
                .isEmpty()
            || serverId == null
            || serverId.isEmpty()) {
            return null;
        }
        if (isUsernameAlreadyOnline(user.getName())) {
            setDisconnectReason("A player with that username is already online.");
            WawelAuth.LOG.warn("Rejecting login for '{}' because that username is already online", user.getName());
            return null;
        }

        ServerConfig config = Config.server();
        boolean localEnabled = config != null && config.isWawelAuthEnabled() && config.isLocalAuthEnabled();

        if (config == null || !config.isWawelAuthEnabled()) {
            return vanillaService.hasJoinedServer(user, serverId);
        }

        if (localEnabled) {
            if (WawelServer.instance() == null) {
                WawelAuth.LOG.warn("WawelAuth local auth is enabled but server module is not running");
                return null;
            }
            return queryLocalHasJoined(user.getName(), serverId);
        }

        GameProfile fallbackResult = queryConfiguredFallbacks(user.getName(), serverId, config);
        if (fallbackResult != null) {
            return fallbackResult;
        }

        return vanillaService.hasJoinedServer(user, serverId);
    }

    private static GameProfile queryLocalHasJoined(String username, String serverId)
        throws AuthenticationUnavailableException {
        String baseUrl = resolveLocalApiRoot();
        if (baseUrl == null) {
            WawelAuth.LOG.warn("WawelAuth enabled but local API root could not be resolved for hasJoined");
            return null;
        }

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("username", username);
        args.put("serverId", serverId);
        URL checkUrl = HttpAuthenticationService.constantURL(baseUrl + "/sessionserver/session/minecraft/hasJoined");
        URL url = HttpAuthenticationService.concatenateURL(checkUrl, HttpAuthenticationService.buildQuery(args));
        return fetchProfile(url, username);
    }

    private static GameProfile queryConfiguredFallbacks(String username, String serverId, ServerConfig config)
        throws AuthenticationUnavailableException {
        if (config == null || config.getFallbackServers()
            .isEmpty()) {
            return null;
        }

        for (FallbackServer fallback : config.getFallbackServers()) {
            if (fallback == null) continue;

            String rawSessionUrl = normalizeUrl(fallback.getSessionServerUrl());
            if (rawSessionUrl == null) continue;

            String sessionMinecraftBase = resolveSessionMinecraftBase(rawSessionUrl);
            URL checkUrl = HttpAuthenticationService.constantURL(sessionMinecraftBase + "/hasJoined");
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("username", username);
            args.put("serverId", serverId);
            URL url = HttpAuthenticationService.concatenateURL(checkUrl, HttpAuthenticationService.buildQuery(args));

            String displayName = trimToNull(fallback.getName());
            if (displayName == null) {
                displayName = rawSessionUrl;
            }

            try {
                WawelAuth.debug("Trying fallback hasJoined: " + displayName + " -> " + url);
                GameProfile profile = fetchProfile(url, username);
                if (profile != null) {
                    WawelAuth.debug("Fallback hasJoined hit: " + displayName);
                    return profile;
                }
            } catch (AuthenticationUnavailableException e) {
                WawelAuth.debug("Fallback hasJoined unavailable for " + displayName + ": " + e.getMessage());
            }
        }

        return null;
    }

    private static String resolveSessionMinecraftBase(String sessionServerUrl) {
        String base = normalizeUrl(sessionServerUrl);
        if (base == null) return null;

        if (base.endsWith("/session/minecraft")) {
            return base;
        }
        if (base.endsWith("/sessionserver")) {
            return base + "/session/minecraft";
        }
        if (base.endsWith("/session")) {
            return base + "/minecraft";
        }

        try {
            URI uri = URI.create(base);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host != null && "sessionserver.mojang.com".equalsIgnoreCase(host) && (path == null || path.isEmpty())) {
                return base + "/session/minecraft";
            }
        } catch (Exception ignored) {}

        // Default compatibility for entries configured as server roots.
        return base + "/session/minecraft";
    }

    private static String resolveLocalApiRoot() {
        String apiRoot = WawelPingPayload.normalizeUrl(
            Config.server() == null ? null
                : Config.server()
                    .getApiRoot());
        if (apiRoot != null) {
            return apiRoot;
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

    public static String consumeDisconnectReason(String defaultReason) {
        String reason = trimToNull(DISCONNECT_REASON.get());
        DISCONNECT_REASON.remove();
        return reason == null ? defaultReason : reason;
    }

    private static void setDisconnectReason(String reason) {
        String normalized = trimToNull(reason);
        if (normalized == null) {
            DISCONNECT_REASON.remove();
        } else {
            DISCONNECT_REASON.set(normalized);
        }
    }

    private static void clearDisconnectReason() {
        DISCONNECT_REASON.remove();
    }

    private static boolean isUsernameAlreadyOnline(String username) {
        String normalizedTarget = trimToNull(username);
        if (normalizedTarget == null) return false;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return false;

        Object configManager = server.getConfigurationManager();
        if (configManager == null) return false;

        if (invokePlayerLookup(configManager, "getPlayerByUsername", normalizedTarget)) return true;
        if (invokePlayerLookup(configManager, "getPlayerForUsername", normalizedTarget)) return true;
        if (invokePlayerLookup(configManager, "func_72361_f", normalizedTarget)) return true; // getPlayerForUsername
        // (SRG)

        try {
            Field listField = configManager.getClass()
                .getField("playerEntityList");
            Object rawList = listField.get(configManager);
            if (rawList instanceof List<?>) {
                Iterator<?> it = ((List<?>) rawList).iterator();
                while (it.hasNext()) {
                    Object player = it.next();
                    String onlineName = extractPlayerName(player);
                    if (onlineName != null && onlineName.equalsIgnoreCase(normalizedTarget)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean invokePlayerLookup(Object configManager, String methodName, String username) {
        try {
            Method method = configManager.getClass()
                .getMethod(methodName, String.class);
            return method.invoke(configManager, username) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String extractPlayerName(Object player) {
        if (player == null) return null;

        try {
            Method getCommandSenderName = player.getClass()
                .getMethod("getCommandSenderName");
            Object value = getCommandSenderName.invoke(player);
            if (value instanceof String) {
                return trimToNull((String) value);
            }
        } catch (Throwable ignored) {}

        try {
            Method getGameProfile = player.getClass()
                .getMethod("getGameProfile");
            Object profile = getGameProfile.invoke(player);
            if (profile instanceof GameProfile) {
                return trimToNull(((GameProfile) profile).getName());
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static GameProfile fetchProfile(URL url, String fallbackName) throws AuthenticationUnavailableException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            if (status == 204 || status == 403 || status == 404) {
                return null;
            }
            if (status != 200) {
                WawelAuth.debug("Local hasJoined returned non-success HTTP " + status);
                return null;
            }

            String body = readUtf8(conn.getInputStream());
            JsonObject root;
            try {
                root = new JsonParser().parse(body)
                    .getAsJsonObject();
            } catch (Exception e) {
                WawelAuth.debug("Local hasJoined returned invalid JSON: " + e.getMessage());
                return null;
            }

            String id = getString(root, "id");
            if (id == null || id.isEmpty()) {
                return null;
            }

            UUID uuid;
            try {
                uuid = UUIDTypeAdapter.fromString(id);
            } catch (Exception e) {
                WawelAuth.debug("Local hasJoined returned invalid UUID: " + id);
                return null;
            }

            String name = getString(root, "name");
            String safeName = trimToNull(name);
            if (safeName == null) {
                safeName = trimToNull(fallbackName);
            }
            if (safeName == null) {
                safeName = "UnknownPlayer";
            }
            GameProfile result = new GameProfile(uuid, safeName);

            JsonArray properties = root.has("properties") && root.get("properties")
                .isJsonArray() ? root.getAsJsonArray("properties") : null;
            if (properties != null) {
                for (JsonElement element : properties) {
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject property = element.getAsJsonObject();
                    String propName = getString(property, "name");
                    String propValue = getString(property, "value");
                    if (propName == null || propValue == null) continue;

                    // 1.7.10 S0CPacketSpawnPlayer writes signature as a mandatory
                    // string field. Unsigned properties (null signature) would crash
                    // packet encoding on the server thread, so keep only signed ones.
                    String signature = trimToNull(getString(property, "signature"));
                    if (signature == null) {
                        continue;
                    }
                    result.getProperties()
                        .put(propName, new Property(propName, propValue, signature));
                }
            }

            return result;
        } catch (IOException e) {
            throw new AuthenticationUnavailableException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readUtf8(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String getString(JsonObject obj, String key) {
        return JsonUtil.getString(obj, key);
    }

    private static String normalizeUrl(String raw) {
        return StringUtil.normalizeHttpUrl(raw);
    }

    private static String trimToNull(String value) {
        return StringUtil.trimToNull(value);
    }
}
