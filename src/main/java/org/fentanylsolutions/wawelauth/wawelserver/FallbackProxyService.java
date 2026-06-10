package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.fentanylsolutions.fentlib.util.NetUtil;
import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.util.JsonUtil;
import org.fentanylsolutions.wawelauth.wawelnet.BinaryResponse;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;
import org.fentanylsolutions.wawelauth.wawelnet.RequestContext;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Proxies configured fallback session servers for hasJoined/profile lookups.
 * <p>
 * Features:
 * <p>
 * - Ordered fallback probing
 * <p>
 * - Per-fallback TTL cache for profile lookups
 * <p>
 * - Binary texture proxy with in-memory cache
 * <p>
 * - Preserves upstream texture payloads/signatures (no local rewrite/re-sign)
 */
public class FallbackProxyService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_JSON_BYTES = 1_048_576; // 1 MB
    private static final int MAX_TEXTURE_BYTES = 4_194_304; // 4 MB
    private static final long CACHE_EXPIRE_AFTER_ACCESS_MINUTES = 30L;
    private static final long PROFILE_CACHE_MAX_BYTES = 16L * 1024L * 1024L;
    private static final long TEXTURE_CACHE_MAX_BYTES = 64L * 1024L * 1024L;
    private static final int PROFILE_CACHE_ENTRY_OVERHEAD_BYTES = 2048;
    private static final int TEXTURE_CACHE_ENTRY_OVERHEAD_BYTES = 16 * 1024;

    private final ServerConfig serverConfig;
    private final Gson gson;

    private final Cache<String, CachedJson> profileCache;
    private final Cache<String, CachedTexture> textureCache;

    public FallbackProxyService(ServerConfig serverConfig) {
        this(serverConfig, PROFILE_CACHE_MAX_BYTES, TEXTURE_CACHE_MAX_BYTES);
    }

    FallbackProxyService(ServerConfig serverConfig, long profileCacheMaxBytes, long textureCacheMaxBytes) {
        this.serverConfig = serverConfig;
        this.gson = new GsonBuilder().disableHtmlEscaping()
            .create();
        this.profileCache = CacheBuilder.newBuilder()
            .maximumWeight(Math.max(1L, profileCacheMaxBytes))
            .weigher(new Weigher<String, CachedJson>() {

                @Override
                public int weigh(String key, CachedJson value) {
                    return value.cacheWeight;
                }
            })
            .expireAfterAccess(CACHE_EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
            .concurrencyLevel(4)
            .build();
        this.textureCache = CacheBuilder.newBuilder()
            .maximumWeight(Math.max(1L, textureCacheMaxBytes))
            .weigher(new Weigher<String, CachedTexture>() {

                @Override
                public int weigh(String key, CachedTexture value) {
                    return value.cacheWeight;
                }
            })
            .expireAfterAccess(CACHE_EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
            .concurrencyLevel(4)
            .build();
    }

    /**
     * Try fallback session servers for hasJoined. Returns null when no fallback
     * resolves the profile.
     */
    public JsonObject resolveHasJoined(String username, String serverId, String ip) {
        for (FallbackEntry entry : getFallbackEntries()) {
            StringBuilder url = new StringBuilder(entry.sessionBase).append("/session/minecraft/hasJoined?username=")
                .append(urlEncode(username))
                .append("&serverId=")
                .append(urlEncode(serverId));
            if (ip != null && !ip.isEmpty()) {
                url.append("&ip=")
                    .append(urlEncode(ip));
            }

            try {
                JsonResponse response = getJson(url.toString());
                if (response.status == 200 && response.body != null) {
                    WawelAuth.debug("Fallback hasJoined hit: " + entry.displayName);
                    return normalizeProfile(response.body, true);
                }
                if (response.status != 204 && response.status != 404) {
                    WawelAuth.debug(
                        "Fallback hasJoined non-terminal status from " + entry.displayName + ": " + response.status);
                }
            } catch (IOException e) {
                WawelAuth.debug("Fallback hasJoined failed for " + entry.displayName + ": " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Try fallback session servers for profile lookup. Uses per-fallback TTL cache.
     * Returns null when unresolved.
     */
    public JsonObject resolveProfileByUuid(String uuidUnsigned, String unsignedParam) {
        boolean signed = "false".equalsIgnoreCase(unsignedParam);

        for (FallbackEntry entry : getFallbackEntries()) {
            String cacheKey = entry.key + "|" + uuidUnsigned + "|unsigned=" + normalizeUnsignedParam(unsignedParam);
            JsonObject cached = getCachedProfile(cacheKey);
            if (cached != null) {
                WawelAuth.debug("Fallback profile cache hit: " + entry.displayName + " " + uuidUnsigned);
                return cached;
            }

            StringBuilder url = new StringBuilder(entry.sessionBase).append("/session/minecraft/profile/")
                .append(uuidUnsigned);
            if (unsignedParam != null) {
                url.append("?unsigned=")
                    .append(urlEncode(unsignedParam));
            }

            try {
                JsonResponse response = getJson(url.toString());
                if (response.status == 200 && response.body != null) {
                    JsonObject transformed = normalizeProfile(response.body, signed);
                    cacheProfile(cacheKey, transformed, entry.ttlMs);
                    WawelAuth.debug("Fallback profile hit: " + entry.displayName + " " + uuidUnsigned);
                    return transformed;
                }
                if (response.status != 204 && response.status != 404) {
                    WawelAuth.debug(
                        "Fallback profile non-terminal status from " + entry.displayName + ": " + response.status);
                }
            } catch (IOException e) {
                WawelAuth.debug("Fallback profile failed for " + entry.displayName + ": " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Handles local texture proxy endpoint:
     * GET /textures/proxy/{fallbackKey}/{encodedUrl}
     */
    public BinaryResponse proxyTexture(RequestContext ctx) {
        String fallbackKey = ctx.getPathParam("fallbackKey");
        String encodedUrl = ctx.getPathParam("encodedUrl");
        if (fallbackKey == null || fallbackKey.isEmpty() || encodedUrl == null || encodedUrl.isEmpty()) {
            throw NetException.notFound("Texture proxy route not found.");
        }

        FallbackEntry entry = getFallbackByKey(fallbackKey);
        if (entry == null) {
            throw NetException.notFound("Unknown fallback server key.");
        }

        String upstreamUrl = decodeUrlPathSegment(encodedUrl);
        FallbackTextureHttp.validateAllowedTextureUrl(entry.fallback, upstreamUrl);

        String cacheKey = entry.key + "|" + upstreamUrl;
        CachedTexture cached = getCachedTexture(cacheKey);
        if (cached != null) {
            return buildBinary(cached.data, cached.contentType, entry.ttlMs);
        }

        TextureResponse response;
        try {
            response = getBinary(entry, upstreamUrl);
        } catch (IOException e) {
            throw new NetException(
                HttpResponseStatus.BAD_GATEWAY,
                "UpstreamException",
                "Failed to fetch upstream texture: " + e.getMessage());
        }

        if (response.status == 404) {
            throw NetException.notFound("Texture not found on fallback server.");
        }
        if (response.status != 200 || response.data == null) {
            throw new NetException(
                HttpResponseStatus.BAD_GATEWAY,
                "UpstreamException",
                "Upstream texture request failed with status " + response.status);
        }

        if (entry.ttlMs > 0L) {
            cacheTexture(cacheKey, response.data, response.contentType, entry.ttlMs);
        }

        return buildBinary(response.data, response.contentType, entry.ttlMs);
    }

    private BinaryResponse buildBinary(byte[] data, String contentType, long ttlMs) {
        String ct = contentType;
        if (ct == null || ct.isEmpty()) {
            ct = "image/png";
        }
        if (!ct.toLowerCase()
            .startsWith("image/")) {
            ct = "image/png";
        }

        Map<String, String> headers = new LinkedHashMap<>();
        long ttlSeconds = ttlMs <= 0L ? 0L : ttlMs / 1000L;
        headers.put("Cache-Control", "public, max-age=" + ttlSeconds);
        return new BinaryResponse(data, ct, headers);
    }

    private JsonObject normalizeProfile(JsonObject upstreamProfile, boolean signed) {
        JsonObject profile = deepCopy(upstreamProfile);

        JsonArray properties = profile.has("properties") && profile.get("properties")
            .isJsonArray() ? profile.getAsJsonArray("properties") : null;
        if (properties == null) {
            return profile;
        }

        if (!signed) {
            for (JsonElement element : properties) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject property = element.getAsJsonObject();
                property.remove("signature");
            }
        }

        return profile;
    }

    private JsonObject getCachedProfile(String cacheKey) {
        CachedJson cached = profileCache.getIfPresent(cacheKey);
        if (cached == null) return null;
        if (cached.isExpired()) {
            profileCache.invalidate(cacheKey);
            return null;
        }
        return deepCopy(cached.body);
    }

    private void cacheProfile(String cacheKey, JsonObject profile, long ttlMs) {
        if (ttlMs <= 0L) return;
        JsonObject copy = deepCopy(profile);
        profileCache.put(cacheKey, new CachedJson(copy, System.currentTimeMillis() + ttlMs, estimateJsonWeight(copy)));
    }

    private CachedTexture getCachedTexture(String cacheKey) {
        CachedTexture cached = textureCache.getIfPresent(cacheKey);
        if (cached == null) return null;
        if (cached.isExpired()) {
            textureCache.invalidate(cacheKey);
            return null;
        }
        return cached;
    }

    private void cacheTexture(String cacheKey, byte[] data, String contentType, long ttlMs) {
        if (ttlMs <= 0L || data == null) return;
        textureCache.put(
            cacheKey,
            new CachedTexture(data, contentType, System.currentTimeMillis() + ttlMs, estimateTextureWeight(data)));
    }

    private FallbackEntry getFallbackByKey(String key) {
        for (FallbackEntry entry : getFallbackEntries()) {
            if (entry.key.equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private List<FallbackEntry> getFallbackEntries() {
        List<FallbackEntry> entries = new ArrayList<>();
        List<FallbackServer> fallbackServers = serverConfig.getFallbackServers();
        for (int i = 0; i < fallbackServers.size(); i++) {
            FallbackServer fallback = fallbackServers.get(i);
            if (fallback == null) continue;

            String sessionBase = resolveSessionBase(fallback);
            if (sessionBase == null) continue;

            String displayName = trimToNull(fallback.getName());
            if (displayName == null) {
                displayName = "fallback-" + i;
            }

            String key = i + "-" + sanitizePathPart(displayName);
            long ttlMs = Math.max(0L, fallback.getCacheTtlSeconds()) * 1000L;
            entries.add(new FallbackEntry(key, displayName, fallback, sessionBase, ttlMs));
        }
        return entries;
    }

    private static String resolveSessionBase(FallbackServer fallback) {
        String session = normalizeUrl(fallback.getSessionServerUrl());
        if (session != null) {
            return session;
        }

        String account = normalizeUrl(fallback.getAccountUrl());
        if (account != null && account.endsWith("/authserver")) {
            return account.substring(0, account.length() - "/authserver".length()) + "/sessionserver";
        }

        return null;
    }

    private static String sanitizePathPart(String input) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                out.append(c);
            } else if (c == ' ' || c == '.' || c == ':') {
                out.append('-');
            }
        }
        if (out.length() == 0) {
            return "fallback";
        }
        return out.toString();
    }

    private static String normalizeUnsignedParam(String unsignedParam) {
        if (unsignedParam == null) return "null";
        if ("true".equalsIgnoreCase(unsignedParam)) return "true";
        if ("false".equalsIgnoreCase(unsignedParam)) return "false";
        return unsignedParam;
    }

    private static String normalizeUrl(String raw) {
        return NetUtil.normalizeHttpUrl(raw);
    }

    private static String trimToNull(String value) {
        return StringUtil.trimToNull(value);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static String decodeUrlPathSegment(String encodedUrl) {
        try {
            int rem = encodedUrl.length() % 4;
            StringBuilder sb = new StringBuilder(encodedUrl);
            if (rem > 0) {
                for (int i = rem; i < 4; i++) {
                    sb.append('=');
                }
            }
            byte[] bytes = Base64.getUrlDecoder()
                .decode(sb.toString());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw NetException.illegalArgument("Invalid encoded texture URL.");
        }
    }

    private JsonResponse getJson(String url) throws IOException {
        HttpURLConnection conn = openConnection(url);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        try {
            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                return new JsonResponse(status, null);
            }

            String body = readStream(stream, MAX_JSON_BYTES);
            if (body == null || body.isEmpty()) {
                return new JsonResponse(status, null);
            }
            try {
                JsonObject json = new JsonParser().parse(body)
                    .getAsJsonObject();
                return new JsonResponse(status, json);
            } catch (Exception ignored) {
                return new JsonResponse(status, null);
            }
        } finally {
            conn.disconnect();
        }
    }

    private TextureResponse getBinary(FallbackEntry entry, String url) throws IOException {
        FallbackTextureHttp.Response response = FallbackTextureHttp.fetch(
            entry.fallback,
            url,
            MAX_TEXTURE_BYTES,
            FallbackTextureHttp.DEFAULT_MAX_REDIRECTS,
            CONNECT_TIMEOUT_MS,
            READ_TIMEOUT_MS,
            "image/png,image/*;q=0.8,*/*;q=0.1");
        return new TextureResponse(response.status, response.data, response.contentType);
    }

    private static HttpURLConnection openConnection(String url) throws IOException {
        return openConnection(url, true);
    }

    private static HttpURLConnection openConnection(String url, boolean followRedirects) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(followRedirects);
        conn.setRequestProperty("User-Agent", "WawelAuth");
        return conn;
    }

    private static String readStream(InputStream stream, int maxBytes) throws IOException {
        byte[] data = readStreamBytes(stream, maxBytes);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static byte[] readStreamBytes(InputStream stream, int maxBytes) throws IOException {
        try (InputStream is = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Upstream response exceeds " + maxBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private JsonObject deepCopy(JsonObject json) {
        return new JsonParser().parse(gson.toJson(json))
            .getAsJsonObject();
    }

    private static int estimateJsonWeight(JsonObject json) {
        int bodyBytes = json == null ? 0
            : json.toString()
                .getBytes(StandardCharsets.UTF_8).length;
        return saturatedCacheWeight((long) bodyBytes + PROFILE_CACHE_ENTRY_OVERHEAD_BYTES);
    }

    private static int estimateTextureWeight(byte[] data) {
        int bodyBytes = data == null ? 0 : data.length;
        return saturatedCacheWeight((long) bodyBytes + TEXTURE_CACHE_ENTRY_OVERHEAD_BYTES);
    }

    private static int saturatedCacheWeight(long weight) {
        if (weight <= 0L) {
            return 1;
        }
        return weight > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) weight;
    }

    private static String getAsString(JsonObject obj, String key) {
        return JsonUtil.getString(obj, key);
    }

    private static class FallbackEntry {

        final String key;
        final String displayName;
        final FallbackServer fallback;
        final String sessionBase;
        final long ttlMs;

        FallbackEntry(String key, String displayName, FallbackServer fallback, String sessionBase, long ttlMs) {
            this.key = key;
            this.displayName = displayName;
            this.fallback = fallback;
            this.sessionBase = sessionBase;
            this.ttlMs = ttlMs;
        }
    }

    private static class JsonResponse {

        final int status;
        final JsonObject body;

        JsonResponse(int status, JsonObject body) {
            this.status = status;
            this.body = body;
        }
    }

    private static class TextureResponse {

        final int status;
        final byte[] data;
        final String contentType;

        TextureResponse(int status, byte[] data, String contentType) {
            this.status = status;
            this.data = data;
            this.contentType = contentType;
        }
    }

    private static class CachedJson {

        final JsonObject body;
        final long expiresAt;
        final int cacheWeight;

        CachedJson(JsonObject body, long expiresAt, int cacheWeight) {
            this.body = body;
            this.expiresAt = expiresAt;
            this.cacheWeight = cacheWeight;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private static class CachedTexture {

        final byte[] data;
        final String contentType;
        final long expiresAt;
        final int cacheWeight;

        CachedTexture(byte[] data, String contentType, long expiresAt, int cacheWeight) {
            this.data = data;
            this.contentType = contentType;
            this.expiresAt = expiresAt;
            this.cacheWeight = cacheWeight;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
