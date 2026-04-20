package org.fentanylsolutions.wawelauth.wawelclient.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import net.minecraft.util.StatCollector;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

public abstract class ProviderOAuthClient {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int CALLBACK_TIMEOUT_SECONDS = 300;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String CALLBACK_LOGO_DATA_URL = loadCallbackLogoDataUrl();
    private static final Object LOOPBACK_LOCK = new Object();
    private static ActiveLoopback activeLoopback;

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

        OAuthTokens tokens;
        if (usesDeviceCodeFlow()) {
            status.accept(tr("wawelauth.gui.login.status.oauth_request_device_code"));
            DeviceCodeResponse deviceCode = requestDeviceCode(provider);
            deviceCodeStatus.accept(deviceCode.getUserCode());
            status.accept(tr("wawelauth.gui.login.status.oauth_open_browser"));
            openBrowser(deviceCode.getOpenBrowserUrl());
            status.accept(
                deviceCode.getUserCode() != null
                    ? tr("wawelauth.gui.login.status.oauth_waiting_code", deviceCode.getUserCode())
                    : tr("wawelauth.gui.login.status.oauth_waiting"));
            tokens = pollForDeviceTokens(deviceCode, provider);
        } else {
            String state = UUID.randomUUID()
                .toString()
                .replace("-", "");
            status.accept(tr("wawelauth.gui.login.status.oauth_open_browser"));
            String code = awaitAuthorizationCode(state, buildAuthorizeUrl(state, loginHint));
            status.accept(tr("wawelauth.gui.login.status.oauth_exchange_code"));
            tokens = exchangeAuthorizationCode(code, provider);
        }

        return completeLogin(tokens, provider, status, null, null, null);
    }

    public final LoginResult refreshFromToken(String refreshToken, ClientProvider provider, UUID profileUuid,
        String profileName, String currentAccessToken, Consumer<String> statusSink) throws IOException {
        ensureConfigured(provider);
        if (trimToNull(refreshToken) == null) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_missing_refresh_token"));
        }
        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        status.accept(tr("wawelauth.gui.login.status.oauth_refreshing"));
        OAuthTokens refreshed = refreshTokens(refreshToken, provider);
        if (trimToNull(refreshed.refreshToken) == null) {
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

    protected String getAuthorizeUrl() {
        return null;
    }

    protected String getRedirectUri() {
        return null;
    }

    protected String getDeviceCodeUrl() {
        return null;
    }

    protected boolean includeScopesInRefreshRequest() {
        return false;
    }

    protected boolean usesDeviceCodeFlow() {
        return trimToNull(getDeviceCodeUrl()) != null;
    }

    protected void customizeAuthorizeParams(Map<String, String> params, String loginHint) {
        if (trimToNull(loginHint) != null) {
            params.put("login_hint", loginHint.trim());
        }
    }

    protected final JsonObject postJson(String url, JsonObject body, String authorization, ClientProvider provider)
        throws IOException {
        ProviderProxySettings proxySettings = provider != null ? provider.getProxySettings() : null;
        byte[] payload = body.toString()
            .getBytes(StandardCharsets.UTF_8);
        debugProxyRequest("POST json", url, proxySettings, provider);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                if (trimToNull(authorization) != null) {
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
            HttpURLConnection conn = openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                if (trimToNull(authorization) != null) {
                    conn.setRequestProperty("Authorization", authorization);
                }
                return readJsonResponse(conn);
            } finally {
                conn.disconnect();
            }
        }
    }

    protected final JsonObject parseJwtPayload(String jwt) throws IOException {
        String normalized = trimToNull(jwt);
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

    protected static String requireString(JsonObject obj, String field) throws IOException {
        if (obj == null || !obj.has(field)
            || obj.get(field)
                .isJsonNull()) {
            throw new IOException("Missing field: " + field);
        }
        String value = obj.get(field)
            .getAsString();
        if (trimToNull(value) == null) {
            throw new IOException("Field is empty: " + field);
        }
        return value;
    }

    protected static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected static String providerLabel(ClientProvider provider) {
        if (provider == null || trimToNull(provider.getName()) == null) {
            return "provider";
        }
        return provider.getName();
    }

    private void ensureConfigured(ClientProvider provider) throws IOException {
        String clientId = trimToNull(getClientId());
        if (clientId == null || isPlaceholder(clientId)) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_unconfigured", providerLabel(provider)));
        }
        if (!usesDeviceCodeFlow()) {
            String clientSecret = trimToNull(getClientSecret());
            String redirectUri = trimToNull(getRedirectUri());
            String authorizeUrl = trimToNull(getAuthorizeUrl());
            if (authorizeUrl == null || redirectUri == null || clientSecret == null || isPlaceholder(clientSecret)) {
                throw new IOException(tr("wawelauth.gui.login.error.oauth_unconfigured", providerLabel(provider)));
            }
            parseLoopbackRedirectUri(redirectUri);
        }
    }

    private String buildAuthorizeUrl(String state, String loginHint) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", getClientId());
        params.put("response_type", "code");
        params.put("redirect_uri", getRedirectUri());
        if (trimToNull(getScopes()) != null) {
            params.put("scope", getScopes());
        }
        params.put("state", state);
        customizeAuthorizeParams(params, loginHint);
        return getAuthorizeUrl() + "?" + encodeForm(params);
    }

    private OAuthTokens exchangeAuthorizationCode(String code, ClientProvider provider) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", getClientId());
        if (trimToNull(getClientSecret()) != null) {
            params.put("client_secret", getClientSecret());
        }
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", getRedirectUri());
        return parseOAuthTokens(postForm(getTokenUrl(), params, provider), null);
    }

    private OAuthTokens refreshTokens(String refreshToken, ClientProvider provider) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", getClientId());
        if (trimToNull(getClientSecret()) != null) {
            params.put("client_secret", getClientSecret());
        }
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        if (trimToNull(getRedirectUri()) != null) {
            params.put("redirect_uri", getRedirectUri());
        }
        if (includeScopesInRefreshRequest() && trimToNull(getScopes()) != null) {
            params.put("scope", getScopes());
        }
        return parseOAuthTokens(postForm(getTokenUrl(), params, provider), refreshToken);
    }

    private DeviceCodeResponse requestDeviceCode(ClientProvider provider) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", getClientId());
        if (trimToNull(getScopes()) != null) {
            params.put("scope", getScopes());
        }
        JsonObject json = postForm(getDeviceCodeUrl(), params, provider);
        String deviceCode = requireString(json, "device_code");
        String userCode = json.has("user_code") && !json.get("user_code")
            .isJsonNull() ? trimToNull(
                json.get("user_code")
                    .getAsString())
                : null;
        String verificationUri = requireString(json, "verification_uri");
        String verificationUriComplete = json.has("verification_uri_complete") && !json.get("verification_uri_complete")
            .isJsonNull() ? trimToNull(
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
            if (trimToNull(getClientSecret()) != null) {
                params.put("client_secret", getClientSecret());
            }
            try {
                return parseOAuthTokens(postForm(getTokenUrl(), params, provider), null);
            } catch (HttpStatusException e) {
                JsonObject error = tryParseJsonObject(e.getResponseBody());
                String errorCode = error != null && error.has("error")
                    && !error.get("error")
                        .isJsonNull() ? trimToNull(
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

    private String awaitAuthorizationCode(String expectedState, String authorizeUrl) throws IOException {
        URI redirectUri = parseLoopbackRedirectUri(getRedirectUri());
        AtomicReference<String> codeRef = new AtomicReference<>();
        AtomicReference<String> stateRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<String> errorDescriptionRef = new AtomicReference<>();
        AtomicReference<String> cancelMessageRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        HttpServer server;
        ActiveLoopback loopback;
        synchronized (LOOPBACK_LOCK) {
            cancelActiveLoopbackLocked(tr("wawelauth.gui.login.error.oauth_restarted"));
            try {
                server = HttpServer.create(new InetSocketAddress(resolvePort(redirectUri)), 0);
            } catch (BindException e) {
                throw new IOException(tr("wawelauth.gui.login.error.oauth_callback_port_busy"), e);
            }
            String redirectPath = normalizePath(redirectUri.getPath());
            server.createContext(redirectPath, exchange -> {
                try {
                    Map<String, String> query = parseQuery(
                        exchange.getRequestURI()
                            .getRawQuery());
                    codeRef.set(query.get("code"));
                    stateRef.set(query.get("state"));
                    errorRef.set(firstNonBlank(query.get("error"), query.get("error_code")));
                    errorDescriptionRef.set(firstNonBlank(query.get("error_description"), query.get("error_message")));

                    boolean hasCode = trimToNull(codeRef.get()) != null;
                    boolean hasError = trimToNull(errorRef.get()) != null;
                    boolean stateMatches = expectedState != null && expectedState.equals(stateRef.get());
                    String response = buildCallbackPageHtml(
                        hasCode,
                        hasError,
                        stateMatches,
                        errorRef.get(),
                        errorDescriptionRef.get());
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders()
                        .set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } finally {
                    latch.countDown();
                }
            });
            server.start();
            loopback = new ActiveLoopback(server, latch, cancelMessageRef);
            activeLoopback = loopback;
        }

        try {
            openBrowser(authorizeUrl);
            boolean completed;
            try {
                completed = latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                throw new IOException(tr("wawelauth.gui.login.error.oauth_interrupted"), e);
            }
            if (!completed) {
                throw new IOException(tr("wawelauth.gui.login.error.oauth_timeout"));
            }
        } finally {
            server.stop(0);
            synchronized (LOOPBACK_LOCK) {
                if (activeLoopback == loopback) {
                    activeLoopback = null;
                }
            }
        }

        if (cancelMessageRef.get() != null) {
            throw new IOException(cancelMessageRef.get());
        }
        if (trimToNull(errorRef.get()) != null) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_failed", errorRef.get()));
        }
        if (trimToNull(codeRef.get()) == null) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_missing_code"));
        }
        if (expectedState != null && !expectedState.equals(stateRef.get())) {
            throw new IOException(tr("wawelauth.gui.login.error.oauth_state_mismatch"));
        }
        return codeRef.get();
    }

    private JsonObject postForm(String url, Map<String, String> params, ClientProvider provider) throws IOException {
        ProviderProxySettings proxySettings = provider != null ? provider.getProxySettings() : null;
        byte[] payload = encodeForm(params).getBytes(StandardCharsets.UTF_8);
        debugProxyRequest("POST form", url, proxySettings, provider);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = openConnection(url, proxySettings);
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

    private HttpURLConnection openConnection(String url, ProviderProxySettings proxySettings) throws IOException {
        return ProviderProxySupport
            .openConnection(url, proxySettings, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, "WawelAuth");
    }

    private JsonObject readJsonResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = in != null ? readStream(in) : "";
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, body);
        }
        if (trimToNull(body) == null) {
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
        String accessToken = requireString(json, "access_token");
        String refreshToken = json != null && json.has("refresh_token")
            && !json.get("refresh_token")
                .isJsonNull() ? trimToNull(
                    json.get("refresh_token")
                        .getAsString())
                    : null;
        String idToken = json != null && json.has("id_token")
            && !json.get("id_token")
                .isJsonNull() ? trimToNull(
                    json.get("id_token")
                        .getAsString())
                    : null;
        return new OAuthTokens(accessToken, firstNonBlank(refreshToken, fallbackRefreshToken), idToken);
    }

    private static JsonObject tryParseJsonObject(String body) {
        if (trimToNull(body) == null) {
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

    private static String buildCallbackPageHtml(boolean hasCode, boolean hasError, boolean stateMatches, String error,
        String errorDescription) {
        String title;
        String badgeClass;
        String message;
        if (hasError) {
            title = tr("wawelauth.oauth.callback.title_failed");
            badgeClass = "badge-error";
            message = tr("wawelauth.oauth.callback.message_failed");
        } else if (!hasCode) {
            title = tr("wawelauth.oauth.callback.title_incomplete");
            badgeClass = "badge-error";
            message = tr("wawelauth.oauth.callback.message_incomplete");
        } else if (!stateMatches) {
            title = tr("wawelauth.oauth.callback.title_rejected");
            badgeClass = "badge-error";
            message = tr("wawelauth.oauth.callback.message_rejected");
        } else {
            title = tr("wawelauth.oauth.callback.title_complete");
            badgeClass = "badge-ok";
            message = tr("wawelauth.oauth.callback.message_complete");
        }

        String errorSummary = trimToNull(firstNonBlank(errorDescription, error));
        if (errorSummary != null && hasError) {
            message = message + " " + errorSummary;
        }

        return "<!doctype html>" + "<html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>"
            + escapeHtml(tr("wawelauth.oauth.callback.page_title"))
            + "</title>"
            + "<style>"
            + ":root{--bg:#0f1117;--panel:#171a22;--muted:#9aa3b2;--text:#f3f5fa;--ok:#46d483;--err:#ff6b6b;}"
            + "*{box-sizing:border-box}"
            + "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Inter,Roboto,Arial,sans-serif;background:radial-gradient(1200px 700px at 20% -10%,#1c2230 0,var(--bg) 60%);color:var(--text)}"
            + ".wrap{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}"
            + ".card{width:min(860px,100%);background:linear-gradient(180deg,#1a1f2b,var(--panel));border:1px solid #252b39;border-radius:14px;box-shadow:0 24px 60px rgba(0,0,0,.45);padding:22px}"
            + ".head{display:flex;gap:14px;align-items:center;margin-bottom:14px}"
            + ".logo{display:block;width:56px;height:56px;object-fit:contain;flex:0 0 auto}"
            + ".logo-fallback{width:56px;height:56px;display:flex;align-items:center;justify-content:center;color:#7d8aa3;font-size:28px;font-weight:700;line-height:1;flex:0 0 auto}"
            + ".title{margin:0;font-size:22px;line-height:1.2}"
            + ".sub{margin:4px 0 0;color:var(--muted);font-size:14px}"
            + ".badge{display:inline-block;margin-top:4px;padding:6px 10px;border-radius:999px;font-size:12px;font-weight:700;letter-spacing:.02em}"
            + ".badge-ok{background:rgba(70,212,131,.16);color:var(--ok);border:1px solid rgba(70,212,131,.4)}"
            + ".badge-error{background:rgba(255,107,107,.15);color:var(--err);border:1px solid rgba(255,107,107,.4)}"
            + ".msg{margin:14px 0 0;color:var(--text);font-size:18px;line-height:1.35}"
            + "</style></head><body><div class=\"wrap\"><div class=\"card\">"
            + "<div class=\"head\">"
            + logoMarkup()
            + "<div>"
            + "<h1 class=\"title\">"
            + escapeHtml(tr("wawelauth.oauth.callback.brand"))
            + "</h1>"
            + "<p class=\"sub\">"
            + escapeHtml(tr("wawelauth.oauth.callback.subtitle"))
            + "</p>"
            + "<span class=\"badge "
            + badgeClass
            + "\">"
            + escapeHtml(title)
            + "</span>"
            + "</div></div>"
            + "<p class=\"msg\">"
            + escapeHtml(message)
            + "</p>"
            + "</div></div></body></html>";
    }

    private static String logoMarkup() {
        if (CALLBACK_LOGO_DATA_URL == null) {
            return "<div class=\"logo-fallback\">W</div>";
        }
        return "<img class=\"logo\" src=\"" + CALLBACK_LOGO_DATA_URL + "\" alt=\"Wawel Auth logo\">";
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#39;");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
        return out.toString();
    }

    private static String loadCallbackLogoDataUrl() {
        try (InputStream in = ProviderOAuthClient.class
            .getResourceAsStream("/assets/wawelauth/Logo_Dragon_Outline.png")) {
            if (in == null) {
                return null;
            }
            byte[] bytes = readBytes(in);
            if (bytes.length == 0) {
                return null;
            }
            return "data:image/png;base64," + Base64.getEncoder()
                .encodeToString(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    private static URI parseLoopbackRedirectUri(String rawUri) throws IOException {
        try {
            URI uri = new URI(rawUri);
            String host = trimToNull(uri.getHost());
            if (host == null
                || (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host) && !"::1".equals(host))) {
                throw new IOException("OAuth redirect URI must use localhost or loopback");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IOException("Invalid OAuth redirect URI: " + rawUri, e);
        }
    }

    private static int resolvePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 28061;
    }

    private static String normalizePath(String path) {
        String normalized = trimToNull(path);
        if (normalized == null) {
            return "/oauth/callback";
        }
        return normalized.startsWith("/") ? normalized : ("/" + normalized);
    }

    private static void cancelActiveLoopbackLocked(String reason) {
        if (activeLoopback == null) {
            return;
        }
        activeLoopback.cancelMessage.compareAndSet(null, reason);
        try {
            activeLoopback.server.stop(0);
        } catch (Exception ignored) {}
        activeLoopback.latch.countDown();
        activeLoopback = null;
    }

    private static void openBrowser(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IOException("Invalid browser URL: " + url, e);
        }
        if (openWithAwtDesktop(uri)) return;
        if (openWithLwjgl3ifyDesktop(uri)) return;
        if (openWithSys("org.lwjglx.Sys", uri.toString())) return;
        if (openWithSys("org.lwjgl.Sys", uri.toString())) return;
        throw new IOException("Failed to open browser URL: " + uri);
    }

    private static boolean openWithAwtDesktop(URI uri) {
        try {
            Class<?> desktopCls = Class.forName("java.awt.Desktop");
            Object desktop = desktopCls.getMethod("getDesktop")
                .invoke(null);
            desktopCls.getMethod("browse", URI.class)
                .invoke(desktop, uri);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean openWithLwjgl3ifyDesktop(URI uri) {
        try {
            Class<?> desktopCls = Class.forName("me.eigenraven.lwjgl3ify.redirects.Desktop");
            Object desktop = desktopCls.getMethod("getDesktop")
                .invoke(null);
            desktopCls.getMethod("browse", URI.class)
                .invoke(desktop, uri);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean openWithSys(String className, String url) {
        try {
            Class<?> sysCls = Class.forName(className);
            Object result = sysCls.getMethod("openURL", String.class)
                .invoke(null, url);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
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

    private static String readStream(InputStream stream) throws IOException {
        return new String(readBytes(stream), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Response too large");
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String encodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key == null || value == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(urlEncode(key))
                .append('=')
                .append(urlEncode(value));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (trimToNull(rawQuery) == null) {
            return result;
        }
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            result.put(urlDecode(part.substring(0, idx)), urlDecode(part.substring(idx + 1)));
        }
        return result;
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    protected static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static boolean isPlaceholder(String value) {
        String normalized = trimToNull(value);
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
            return firstNonBlank(verificationUriComplete, verificationUri);
        }
    }

    private static final class ActiveLoopback {

        private final HttpServer server;
        private final CountDownLatch latch;
        private final AtomicReference<String> cancelMessage;

        private ActiveLoopback(HttpServer server, CountDownLatch latch, AtomicReference<String> cancelMessage) {
            this.server = server;
            this.latch = latch;
            this.cancelMessage = cancelMessage;
        }
    }

    public static class HttpStatusException extends IOException {

        private final int statusCode;
        private final String responseBody;

        public HttpStatusException(int statusCode, String responseBody) {
            super("HTTP " + statusCode + (trimToNull(responseBody) != null ? ": " + responseBody : ""));
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
