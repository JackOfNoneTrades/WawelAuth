package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.fentanylsolutions.fentlib.util.NetUtil;
import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.util.JsonUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.util.UUIDTypeAdapter;

/**
 * Resolves whitelist lookups using explicit fallback provider selection:
 * <username>@<providerName>.
 * <p>
 * providerName must match either:
 * <p>
 * - a fallback entry "name" in server config, or
 * <p>
 * - a local alias ("local", "localauth", "wawelauth", "self") for local profiles.
 */
public final class FallbackWhitelistLookup {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_JSON_BYTES = 1_048_576;

    private static final String API_LOCATION_HEADER = "X-Authlib-Injector-API-Location";
    private static final String API_LOCATION_HEADER_ALT = "X-Authlib-Injector-Api-Location";

    private FallbackWhitelistLookup() {}

    public static boolean isQualifiedProviderUsername(String rawInput) {
        QualifiedName qualified = parseQualifiedName(rawInput);
        return qualified != null;
    }

    public static GameProfile resolveQualifiedProfile(String rawInput) {
        QualifiedName qualified = parseQualifiedName(rawInput);
        if (qualified == null) return null;

        if (isLocalProviderAlias(qualified.providerName)) {
            GameProfile localProfile = resolveLocalProfile(qualified.username);
            if (localProfile != null) {
                return localProfile;
            }
            WawelAuth.debug(
                "Local whitelist lookup failed for username '" + qualified.username
                    + "' using provider alias '"
                    + qualified.providerName
                    + "'");
            return null;
        }

        FallbackServer fallback = findFallbackByName(qualified.providerName);
        if (fallback == null) {
            WawelAuth.debug(
                "Whitelist provider '" + qualified.providerName
                    + "' not found for username '"
                    + qualified.username
                    + "'");
            return null;
        }

        try {
            return lookupProfileByName(qualified.username, fallback);
        } catch (IOException e) {
            WawelAuth.LOG.warn(
                "Whitelist provider lookup failed for '{}@{}': {}",
                qualified.username,
                qualified.providerName,
                e.getMessage());
            return null;
        }
    }

    private static GameProfile resolveLocalProfile(String username) {
        WawelServer server = WawelServer.instance();
        if (server == null) {
            return null;
        }

        WawelProfile profile = server.getProfileDAO()
            .findByName(username);
        if (profile == null) {
            return null;
        }
        return new GameProfile(profile.getUuid(), profile.getName());
    }

    private static GameProfile lookupProfileByName(String username, FallbackServer fallback) throws IOException {
        List<String> candidateUrls = buildLookupUrls(fallback);
        if (candidateUrls.isEmpty()) {
            return null;
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String url : candidateUrls) {
            seen.add(url);
        }

        for (int i = 0; i < candidateUrls.size(); i++) {
            String url = candidateUrls.get(i);
            QueryResult result = queryLookup(url, username);

            if (result.discoveredApiRoot != null) {
                addIfNew(candidateUrls, seen, appendPath(result.discoveredApiRoot, "/api/profiles/minecraft"));
                addIfNew(
                    candidateUrls,
                    seen,
                    appendPath(result.discoveredApiRoot, "/minecraft/profile/lookup/bulk/byname"));
            }

            if (result.profile != null) {
                return result.profile;
            }
        }

        return null;
    }

    private static QueryResult queryLookup(String url, String username) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            byte[] payload = buildLookupPayload(username);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();
            String discoveredApiRoot = normalizeUrl(header(conn, API_LOCATION_HEADER, API_LOCATION_HEADER_ALT));

            if (status != 200) {
                return new QueryResult(null, discoveredApiRoot);
            }

            InputStream stream = conn.getInputStream();
            String body = readUtf8(stream, MAX_JSON_BYTES);
            GameProfile profile = parseLookupResponse(body, username);
            return new QueryResult(profile, discoveredApiRoot);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static byte[] buildLookupPayload(String username) {
        JsonArray request = new JsonArray();
        request.add(new JsonPrimitive(username));
        return request.toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    private static GameProfile parseLookupResponse(String body, String username) {
        if (body == null || body.isEmpty()) return null;

        JsonElement root;
        try {
            root = new JsonParser().parse(body);
        } catch (Exception e) {
            return null;
        }

        if (root == null || root.isJsonNull()) {
            return null;
        }

        if (root.isJsonArray()) {
            return parseProfileArray(root.getAsJsonArray(), username);
        }

        if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.has("profiles") && object.get("profiles")
                .isJsonArray()) {
                return parseProfileArray(object.getAsJsonArray("profiles"), username);
            }
            return parseSingleProfile(object, username);
        }

        return null;
    }

    private static GameProfile parseProfileArray(JsonArray array, String username) {
        GameProfile first = null;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            GameProfile candidate = parseSingleProfile(element.getAsJsonObject(), username);
            if (candidate == null) continue;
            if (first == null) {
                first = candidate;
            }
            String candidateName = candidate.getName();
            if (candidateName != null && candidateName.equalsIgnoreCase(username)) {
                return candidate;
            }
        }
        return first;
    }

    private static GameProfile parseSingleProfile(JsonObject profile, String requestedUsername) {
        String id = getString(profile, "id");
        if (id == null) return null;

        UUID uuid;
        try {
            uuid = UUIDTypeAdapter.fromString(id);
        } catch (Exception e) {
            return null;
        }

        String name = trimToNull(getString(profile, "name"));
        if (name == null) {
            name = requestedUsername;
        }
        return new GameProfile(uuid, name);
    }

    private static List<String> buildLookupUrls(FallbackServer fallback) {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        String services = normalizeUrl(fallback.getServicesUrl());
        String account = normalizeUrl(fallback.getAccountUrl());
        String session = normalizeUrl(fallback.getSessionServerUrl());

        addIfNew(urls, seen, appendPath(services, "/minecraft/profile/lookup/bulk/byname"));
        addIfNew(urls, seen, appendPath(services, "/api/profiles/minecraft"));

        addIfNew(urls, seen, appendPath(account, "/minecraft/profile/lookup/bulk/byname"));
        addIfNew(urls, seen, appendPath(account, "/api/profiles/minecraft"));

        addIfNew(urls, seen, appendPath(session, "/minecraft/profile/lookup/bulk/byname"));
        addIfNew(urls, seen, appendPath(session, "/api/profiles/minecraft"));

        if (account != null && account.endsWith("/authserver")) {
            String root = account.substring(0, account.length() - "/authserver".length());
            addIfNew(urls, seen, appendPath(root, "/minecraft/profile/lookup/bulk/byname"));
            addIfNew(urls, seen, appendPath(root, "/api/profiles/minecraft"));
        }

        if (session != null && session.endsWith("/sessionserver")) {
            String root = session.substring(0, session.length() - "/sessionserver".length());
            addIfNew(urls, seen, appendPath(root, "/minecraft/profile/lookup/bulk/byname"));
            addIfNew(urls, seen, appendPath(root, "/api/profiles/minecraft"));
        }

        return urls;
    }

    private static void addIfNew(List<String> urls, Set<String> seen, String value) {
        if (value == null) return;
        if (seen.add(value)) {
            urls.add(value);
        }
    }

    private static String appendPath(String base, String suffix) {
        if (base == null || suffix == null) return null;
        return base + suffix;
    }

    private static FallbackServer findFallbackByName(String providerName) {
        ServerConfig config = Config.server();
        if (config == null) return null;

        String wanted = trimToNull(providerName);
        if (wanted == null) return null;

        for (FallbackServer fallback : config.getFallbackServers()) {
            if (fallback == null) continue;
            String currentName = trimToNull(fallback.getName());
            if (currentName != null && currentName.equalsIgnoreCase(wanted)) {
                return fallback;
            }
        }
        return null;
    }

    private static QualifiedName parseQualifiedName(String rawInput) {
        String value = trimToNull(rawInput);
        if (value == null) return null;

        int at = value.lastIndexOf('@');
        if (at <= 0 || at >= value.length() - 1) {
            return null;
        }

        String username = trimToNull(value.substring(0, at));
        String provider = trimToNull(value.substring(at + 1));
        if (username == null || provider == null) {
            return null;
        }

        return new QualifiedName(username, provider);
    }

    private static String header(HttpURLConnection conn, String first, String second) {
        String value = conn.getHeaderField(first);
        if (value != null) return value;
        return conn.getHeaderField(second);
    }

    private static String readUtf8(InputStream in, int maxBytes) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Response body too large");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String getString(JsonObject object, String key) {
        return JsonUtil.getString(object, key);
    }

    private static String normalizeUrl(String raw) {
        return NetUtil.normalizeHttpUrl(raw);
    }

    private static String trimToNull(String value) {
        return StringUtil.trimToNull(value);
    }

    private static boolean isLocalProviderAlias(String providerName) {
        String normalized = trimToNull(providerName);
        if (normalized == null) return false;
        return "local".equalsIgnoreCase(normalized) || "localauth".equalsIgnoreCase(normalized)
            || "wawelauth".equalsIgnoreCase(normalized)
            || "self".equalsIgnoreCase(normalized);
    }

    private static final class QualifiedName {

        final String username;
        final String providerName;

        QualifiedName(String username, String providerName) {
            this.username = username;
            this.providerName = providerName;
        }
    }

    private static final class QueryResult {

        final GameProfile profile;
        final String discoveredApiRoot;

        QueryResult(GameProfile profile, String discoveredApiRoot) {
            this.profile = profile;
            this.discoveredApiRoot = discoveredApiRoot;
        }
    }
}
