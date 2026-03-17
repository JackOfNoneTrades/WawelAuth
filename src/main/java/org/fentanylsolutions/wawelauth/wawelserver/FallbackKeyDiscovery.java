package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Fetches fallback providers' public signing keys from their API root metadata on startup.
 * Populates FallbackServer.signaturePublicKeyBase64 for WAUTH|CAPS.
 */
public final class FallbackKeyDiscovery {

    private FallbackKeyDiscovery() {}

    /** Fetch missing signaturePublicKeyBase64 for each fallback from their API root. */
    public static void discoverKeys(ServerConfig config) {
        if (config == null || config.getFallbackServers()
            .isEmpty()) {
            return;
        }

        for (FallbackServer fallback : config.getFallbackServers()) {
            if (fallback == null) continue;

            String existing = trimToNull(fallback.getSignaturePublicKeyBase64());
            if (existing != null) {
                WawelAuth.LOG.info(
                    "[KeyDiscovery] Fallback '{}' already has signaturePublicKeyBase64, skipping",
                    fallback.getName());
                continue;
            }

            java.util.List<String> candidates = deriveMetadataUrls(fallback);
            if (candidates.isEmpty()) {
                WawelAuth.LOG.info(
                    "[KeyDiscovery] Fallback '{}': cannot derive metadata URL from config, skipping",
                    fallback.getName());
                continue;
            }

            boolean found = false;
            for (String candidateUrl : candidates) {
                try {
                    WawelAuth.LOG.info(
                        "[KeyDiscovery] Trying metadata for fallback '{}' at {}",
                        fallback.getName(),
                        candidateUrl);
                    String key = fetchSignaturePublicKey(candidateUrl);
                    if (key != null) {
                        fallback.setSignaturePublicKeyBase64(key);
                        WawelAuth.LOG.info(
                            "[KeyDiscovery] Discovered signaturePublicKey for fallback '{}' from {} ({}...)",
                            fallback.getName(),
                            candidateUrl,
                            key.substring(0, Math.min(16, key.length())));
                        found = true;
                        break;
                    }
                } catch (Exception inner) {
                    WawelAuth.debug("[KeyDiscovery] " + candidateUrl + " failed: " + inner.getMessage());
                }
            }
            if (!found) {
                WawelAuth.LOG.warn(
                    "[KeyDiscovery] Fallback '{}': no signaturePublickey found at any candidate URL: {}",
                    fallback.getName(),
                    candidates);
            }

        }
    }

    private static String fetchSignaturePublicKey(String apiRoot) throws IOException {
        URL url = new URL(apiRoot);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            if (status != 200) {
                WawelAuth.debug("API root metadata returned HTTP " + status + " for " + apiRoot);
                return null;
            }

            String body = readUtf8(conn.getInputStream());
            com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(
                new java.io.StringReader(body));
            reader.setLenient(true);
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }

            JsonObject metadata = parsed.getAsJsonObject();

            // "signaturePublickey" (authlib-injector standard)
            if (metadata.has("signaturePublickey") && !metadata.get("signaturePublickey")
                .isJsonNull()) {
                String raw = metadata.get("signaturePublickey")
                    .getAsString();
                return extractKeyBase64(raw);
            }

            // "signaturePublickeys" array (some implementations)
            if (metadata.has("signaturePublickeys") && metadata.get("signaturePublickeys")
                .isJsonArray()) {
                for (JsonElement element : metadata.getAsJsonArray("signaturePublickeys")) {
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject keyObj = element.getAsJsonObject();
                    if (keyObj.has("publicKey") && !keyObj.get("publicKey")
                        .isJsonNull()) {
                        return extractKeyBase64(
                            keyObj.get("publicKey")
                                .getAsString());
                    }
                }
            }

            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Derive candidate metadata URLs to try. */
    private static java.util.List<String> deriveMetadataUrls(FallbackServer fallback) {
        java.util.List<String> candidates = new java.util.ArrayList<>();

        // 1. Explicit apiRoot
        String apiRoot = WawelPingPayload.normalizeUrl(fallback.getApiRoot());
        if (apiRoot != null) {
            candidates.add(apiRoot);
        }

        // 2. Derive from sessionServerUrl
        String sessionUrl = WawelPingPayload.normalizeUrl(fallback.getSessionServerUrl());
        if (sessionUrl != null) {
            if (sessionUrl.endsWith("/session")) {
                String base = sessionUrl.substring(0, sessionUrl.length() - "/session".length());
                addIfNew(candidates, base);
                addIfNew(candidates, base + "/authlib-injector");
            } else if (sessionUrl.endsWith("/sessionserver")) {
                String base = sessionUrl.substring(0, sessionUrl.length() - "/sessionserver".length());
                addIfNew(candidates, base);
                addIfNew(candidates, base + "/authlib-injector");
            }
        }

        // 3. Derive from accountUrl
        String accountUrl = WawelPingPayload.normalizeUrl(fallback.getAccountUrl());
        if (accountUrl != null) {
            if (accountUrl.endsWith("/account")) {
                String base = accountUrl.substring(0, accountUrl.length() - "/account".length());
                addIfNew(candidates, base);
                addIfNew(candidates, base + "/authlib-injector");
            } else if (accountUrl.endsWith("/authserver")) {
                String base = accountUrl.substring(0, accountUrl.length() - "/authserver".length());
                addIfNew(candidates, base);
                addIfNew(candidates, base + "/authlib-injector");
            }
        }

        return candidates;
    }

    private static void addIfNew(java.util.List<String> list, String url) {
        if (url != null && !list.contains(url)) {
            list.add(url);
        }
    }

    private static String extractKeyBase64(String rawKey) {
        if (rawKey == null) return null;
        String cleaned = rawKey.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
        return cleaned.isEmpty() ? null : cleaned;
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

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
