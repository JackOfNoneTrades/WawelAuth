package org.fentanylsolutions.wawelauth.wawelclient.oauth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.minecraft.util.StatCollector;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public abstract class ProviderOAuthClient {

    public abstract boolean supports(String providerName);

    public final boolean supports(ClientProvider provider) {
        return provider != null && supports(provider.getName());
    }

    public final LoginResult loginInteractive(ClientProvider provider, String loginHint, Consumer<String> statusSink,
        Consumer<String> deviceCodeSink) throws IOException {
        ensureConfigured(provider);
        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        Consumer<String> deviceCodeStatus = deviceCodeSink != null ? deviceCodeSink : s -> {};
        deviceCodeStatus.accept(null);

        status.accept(tr("wawelauth.gui.login.status.oauth_request_device_code"));
        DeviceCodeResponse deviceCode = requestDeviceCode(provider);
        deviceCodeStatus.accept(deviceCode.getUserCode());
        status.accept(tr("wawelauth.gui.login.status.oauth_open_browser"));
        OAuthHttpSupport.openBrowser(deviceCode.getOpenBrowserUrl());
        status.accept(
            deviceCode.getUserCode() != null
                ? tr("wawelauth.gui.login.status.oauth_waiting_code", deviceCode.getUserCode())
                : tr("wawelauth.gui.login.status.oauth_waiting"));
        OAuthTokens tokens = pollForDeviceTokens(deviceCode, provider);

        return completeLogin(tokens, provider, status, null, null, null);
    }

    public final LoginResult refreshFromToken(String refreshToken, ClientProvider provider, UUID profileUuid,
        String profileName, String currentAccessToken, Consumer<String> statusSink) throws IOException {
        ensureConfigured(provider);
        if (StringUtil.trimToNull(refreshToken) == null) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_missing_refresh_token"));
        }
        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        status.accept(tr("wawelauth.gui.login.status.oauth_refreshing"));
        OAuthTokens refreshed = refreshTokens(refreshToken, provider);
        if (StringUtil.trimToNull(refreshed.refreshToken) == null) {
            refreshed = refreshed.withRefreshToken(refreshToken);
        }
        return completeLogin(refreshed, provider, status, profileUuid, profileName, currentAccessToken);
    }

    public boolean supportsProfileValidation() {
        return false;
    }

    public MinecraftProfile fetchProfile(String accessToken, ClientProvider provider) throws IOException {
        throw new IOException("Profile validation is not supported for " + providerLabel(provider));
    }

    protected abstract LoginResult completeLogin(OAuthTokens tokens, ClientProvider provider, Consumer<String> status,
        UUID profileUuidHint, String profileNameHint, String currentAccessToken) throws IOException;

    protected abstract String getClientId();

    protected abstract String getClientSecret();

    protected abstract String getTokenUrl();

    protected abstract String getScopes();

    protected String getRedirectUri() {
        return null;
    }

    protected String getDeviceCodeUrl() {
        return null;
    }

    protected boolean includeScopesInRefreshRequest() {
        return false;
    }

    protected final JsonObject postJson(String url, JsonObject body, String authorization, ClientProvider provider)
        throws IOException {
        ProviderProxySettings proxySettings = provider != null ? provider.getProxySettings() : null;
        byte[] payload = body.toString()
            .getBytes(StandardCharsets.UTF_8);
        debugProxyRequest("POST json", url, proxySettings, provider);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = OAuthHttpSupport.openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                if (StringUtil.trimToNull(authorization) != null) {
                    conn.setRequestProperty("Authorization", authorization);
                }
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                return readJsonResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    protected final JsonObject getJson(String url, String authorization, ClientProvider provider) throws IOException {
        ProviderProxySettings proxySettings = provider != null ? provider.getProxySettings() : null;
        debugProxyRequest("GET", url, proxySettings, provider);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = OAuthHttpSupport.openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                if (StringUtil.trimToNull(authorization) != null) {
                    conn.setRequestProperty("Authorization", authorization);
                }
                return readJsonResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    protected final JsonObject parseJwtPayload(String jwt) throws IOException {
        String normalized = StringUtil.trimToNull(jwt);
        if (normalized == null) {
            throw new IOException("Missing JWT");
        }
        String[] parts = normalized.split("\\.");
        if (parts.length < 2) {
            throw new IOException("Malformed JWT");
        }
        try {
            byte[] payload = Base64.getUrlDecoder()
                .decode(parts[1]);
            return new JsonParser().parse(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Failed to decode JWT payload", e);
        }
    }

    protected static String providerLabel(ClientProvider provider) {
        if (provider == null || StringUtil.trimToNull(provider.getName()) == null) {
            return "provider";
        }
        return provider.getName();
    }

    private void ensureConfigured(ClientProvider provider) throws IOException {
        String clientId = StringUtil.trimToNull(getClientId());
        if (clientId == null || isPlaceholder(clientId)) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_unconfigured", providerLabel(provider)));
        }
    }

    private OAuthTokens refreshTokens(String refreshToken, ClientProvider provider) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", getClientId());
        if (StringUtil.trimToNull(getClientSecret()) != null) {
            params.put("client_secret", getClientSecret());
        }
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        if (StringUtil.trimToNull(getRedirectUri()) != null) {
            params.put("redirect_uri", getRedirectUri());
        }
        if (includeScopesInRefreshRequest() && StringUtil.trimToNull(getScopes()) != null) {
            params.put("scope", getScopes());
        }
        return parseOAuthTokens(postForm(getTokenUrl(), params, provider), refreshToken);
    }

    private DeviceCodeResponse requestDeviceCode(ClientProvider provider) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", getClientId());
        if (StringUtil.trimToNull(getScopes()) != null) {
            params.put("scope", getScopes());
        }
        JsonObject json = postForm(getDeviceCodeUrl(), params, provider);
        String deviceCode = OAuthHttpSupport.requireString(json, "device_code");
        String userCode = json.has("user_code") && !json.get("user_code")
            .isJsonNull() ? StringUtil.trimToNull(
                json.get("user_code")
                    .getAsString())
                : null;
        String verificationUri = OAuthHttpSupport.requireString(json, "verification_uri");
        String verificationUriComplete = json.has("verification_uri_complete") && !json.get("verification_uri_complete")
            .isJsonNull() ? StringUtil.trimToNull(
                json.get("verification_uri_complete")
                    .getAsString())
                : null;
        int interval = json.has("interval") && !json.get("interval")
            .isJsonNull() ? Math.max(
                1,
                json.get("interval")
                    .getAsInt())
                : 5;
        int expiresIn = json.has("expires_in") && !json.get("expires_in")
            .isJsonNull() ? Math.max(
                1,
                json.get("expires_in")
                    .getAsInt())
                : 300;
        return new DeviceCodeResponse(
            deviceCode,
            userCode,
            verificationUri,
            verificationUriComplete,
            interval,
            expiresIn);
    }

    private OAuthTokens pollForDeviceTokens(DeviceCodeResponse deviceCode, ClientProvider provider) throws IOException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(deviceCode.expiresInSeconds);
        int intervalSeconds = deviceCode.intervalSeconds;
        while (System.currentTimeMillis() < deadline) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            params.put("device_code", deviceCode.deviceCode);
            params.put("client_id", getClientId());
            if (StringUtil.trimToNull(getClientSecret()) != null) {
                params.put("client_secret", getClientSecret());
            }
            try {
                return parseOAuthTokens(postForm(getTokenUrl(), params, provider), null);
            } catch (HttpStatusException e) {
                JsonObject error = tryParseJsonObject(e.getResponseBody());
                String errorCode = error != null && error.has("error")
                    && !error.get("error")
                        .isJsonNull() ? StringUtil.trimToNull(
                            error.get("error")
                                .getAsString())
                            : null;
                if ("authorization_pending".equals(errorCode)) {
                    sleepSeconds(intervalSeconds);
                    continue;
                }
                if ("slow_down".equals(errorCode)) {
                    intervalSeconds = Math.max(intervalSeconds + 5, intervalSeconds + 1);
                    sleepSeconds(intervalSeconds);
                    continue;
                }
                if ("access_denied".equals(errorCode) || "expired_token".equals(errorCode)) {
                    throw new IOException(tr("wawelauth.gui.login.error.oauth_failed", errorCode));
                }
                throw e;
            }
        }
        throw new IOException(tr("wawelauth.gui.login.error.oauth_timeout"));
    }

    private JsonObject postForm(String url, Map<String, String> params, ClientProvider provider) throws IOException {
        ProviderProxySettings proxySettings = provider != null ? provider.getProxySettings() : null;
        byte[] payload = OAuthHttpSupport.encodeForm(params)
            .getBytes(StandardCharsets.UTF_8);
        debugProxyRequest("POST form", url, proxySettings, provider);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = OAuthHttpSupport.openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                return readJsonResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    private JsonObject readJsonResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = in != null ? OAuthHttpSupport.readStream(in) : "";
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, body);
        }
        if (StringUtil.trimToNull(body) == null) {
            return new JsonObject();
        }
        try {
            return new JsonParser().parse(body)
                .getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Failed to parse JSON response", e);
        }
    }

    private static OAuthTokens parseOAuthTokens(JsonObject json, String fallbackRefreshToken) throws IOException {
        String accessToken = OAuthHttpSupport.requireString(json, "access_token");
        String refreshToken = json != null && json.has("refresh_token")
            && !json.get("refresh_token")
                .isJsonNull() ? StringUtil.trimToNull(
                    json.get("refresh_token")
                        .getAsString())
                    : null;
        String idToken = json != null && json.has("id_token")
            && !json.get("id_token")
                .isJsonNull() ? StringUtil.trimToNull(
                    json.get("id_token")
                        .getAsString())
                    : null;
        return new OAuthTokens(
            accessToken,
            OAuthHttpSupport.firstNonBlank(refreshToken, fallbackRefreshToken),
            idToken);
    }

    private static JsonObject tryParseJsonObject(String body) {
        if (StringUtil.trimToNull(body) == null) {
            return null;
        }
        try {
            return new JsonParser().parse(body)
                .getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sleepSeconds(int seconds) throws IOException {
        try {
            TimeUnit.SECONDS.sleep(Math.max(1, seconds));
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            throw new IOException(tr("wawelauth.gui.login.error.oauth_interrupted"), e);
        }
    }

    private static void debugProxyRequest(String method, String url, ProviderProxySettings proxySettings,
        ClientProvider provider) {
        WawelAuth.debug(
            "Provider OAuth HTTP " + method
                + " "
                + url
                + " [provider="
                + (provider != null ? provider.getName() : "-")
                + ", key="
                + (provider != null ? provider.getName() : "-")
                + ", client="
                + ProviderProxySupport.describeProxySettings(proxySettings)
                + "]");
    }

    private static boolean isPlaceholder(String value) {
        String normalized = StringUtil.trimToNull(value);
        return normalized != null && normalized.startsWith("__") && normalized.endsWith("__");
    }

    protected static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    protected static String tr(String key, Object... args) {
        return String.format(tr(key), args);
    }

    public static final class LoginResult {

        private final String profileName;
        private final UUID profileUuid;
        private final String accessToken;
        private final String refreshToken;
        private final String clientToken;

        public LoginResult(String profileName, UUID profileUuid, String accessToken, String refreshToken,
            String clientToken) {
            this.profileName = profileName;
            this.profileUuid = profileUuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.clientToken = clientToken;
        }

        public String getProfileName() {
            return profileName;
        }

        public UUID getProfileUuid() {
            return profileUuid;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getClientToken() {
            return clientToken;
        }
    }

    public static class MinecraftProfile {

        private final String name;
        private final UUID uuid;

        public MinecraftProfile(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public UUID getUuid() {
            return uuid;
        }
    }

    protected static final class OAuthTokens {

        private final String accessToken;
        private final String refreshToken;
        private final String idToken;

        private OAuthTokens(String accessToken, String refreshToken, String idToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
        }

        private OAuthTokens withRefreshToken(String value) {
            return new OAuthTokens(accessToken, value, idToken);
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getIdToken() {
            return idToken;
        }
    }

    private static final class DeviceCodeResponse {

        private final String deviceCode;
        private final String userCode;
        private final String verificationUri;
        private final String verificationUriComplete;
        private final int intervalSeconds;
        private final int expiresInSeconds;

        private DeviceCodeResponse(String deviceCode, String userCode, String verificationUri,
            String verificationUriComplete, int intervalSeconds, int expiresInSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.verificationUriComplete = verificationUriComplete;
            this.intervalSeconds = intervalSeconds;
            this.expiresInSeconds = expiresInSeconds;
        }

        private String getUserCode() {
            return userCode;
        }

        private String getOpenBrowserUrl() {
            return OAuthHttpSupport.firstNonBlank(verificationUriComplete, verificationUri);
        }
    }

    public static class HttpStatusException extends IOException {

        private final int statusCode;
        private final String responseBody;

        public HttpStatusException(int statusCode, String responseBody) {
            super("HTTP " + statusCode + (StringUtil.trimToNull(responseBody) != null ? ": " + responseBody : ""));
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
