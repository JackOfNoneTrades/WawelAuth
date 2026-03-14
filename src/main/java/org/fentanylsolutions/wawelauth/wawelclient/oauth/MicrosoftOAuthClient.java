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
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpServer;

/**
 * Microsoft OAuth flow for Minecraft accounts (MSA -> XBL -> XSTS -> Minecraft services).
 * <p>
 * Flow:
 * <p>
 * 1. Browser authorization code login (loopback redirect)
 * <p>
 * 2. Exchange auth code for Microsoft access+refresh token
 * <p>
 * 3. Get XBL token + user hash (uhs)
 * <p>
 * 4. Get XSTS token
 * <p>
 * 5. Exchange for Minecraft access token
 * <p>
 * 6. Fetch Minecraft profile
 */
public class MicrosoftOAuthClient {

    private static final String MS_AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String MS_TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    // Default values from the oauth reference implementation.
    // To use your own Azure app registration (and therefore app name shown by Microsoft),
    // override via:
    // -Dwawelauth.ms.clientId=<id> and -Dwawelauth.ms.redirectUri=<uri>
    // or env:
    // WAWELAUTH_MS_CLIENT_ID / WAWELAUTH_MS_REDIRECT_URI
    private static final String DEFAULT_CLIENT_ID = "907a248d-3eb5-4d01-99d2-ff72d79c5eb1";
    private static final String DEFAULT_REDIRECT_URI = "http://localhost:26669/relogin";
    private static final String CLIENT_ID = getConfiguredValue(
        "wawelauth.ms.clientId",
        "WAWELAUTH_MS_CLIENT_ID",
        DEFAULT_CLIENT_ID);
    private static final URI REDIRECT_URI_PARSED = parseRedirectUri(
        getConfiguredValue("wawelauth.ms.redirectUri", "WAWELAUTH_MS_REDIRECT_URI", DEFAULT_REDIRECT_URI));
    private static final String REDIRECT_URI = REDIRECT_URI_PARSED.toString();
    private static final int LOOPBACK_PORT = resolvePort(REDIRECT_URI_PARSED);
    private static final String REDIRECT_PATH = normalizePath(REDIRECT_URI_PARSED.getPath());

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int CALLBACK_TIMEOUT_SECONDS = 300;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String CALLBACK_LOGO_DATA_URL = loadCallbackLogoDataUrl();
    private static final Object LOOPBACK_LOCK = new Object();
    private static ActiveLoopback activeLoopback;

    /**
     * Browser-based Microsoft login that returns Minecraft credentials.
     */
    public LoginResult loginInteractive(ProviderProxySettings proxySettings, Consumer<String> statusSink)
        throws IOException {
        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        String state = UUID.randomUUID()
            .toString()
            .replace("-", "");

        status.accept(tr("wawelauth.gui.login.status.microsoft_open_browser"));
        String authCode = awaitAuthorizationCode(state);

        status.accept(tr("wawelauth.gui.login.status.microsoft_exchange_code"));
        MsToken msToken = exchangeAuthorizationCode(authCode, proxySettings);

        return loginWithMsToken(msToken, proxySettings, status);
    }

    /**
     * Refresh Microsoft credentials and return fresh Minecraft credentials.
     */
    public LoginResult refreshFromToken(String refreshToken, ProviderProxySettings proxySettings,
        Consumer<String> statusSink) throws IOException {
        if (refreshToken == null || refreshToken.trim()
            .isEmpty()) {
            throw new IOException(tr("wawelauth.gui.login.error.microsoft_missing_refresh_token"));
        }

        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        status.accept(tr("wawelauth.gui.login.status.microsoft_refreshing"));
        MsToken msToken = refreshMsToken(refreshToken, proxySettings);
        return loginWithMsToken(msToken, proxySettings, status);
    }

    /**
     * Validate the current Minecraft token by loading profile data.
     */
    public MinecraftProfile fetchMinecraftProfile(String minecraftAccessToken, ProviderProxySettings proxySettings)
        throws IOException {
        if (minecraftAccessToken == null || minecraftAccessToken.isEmpty()) {
            throw new IOException(tr("wawelauth.gui.login.error.microsoft_missing_access_token"));
        }
        JsonObject obj = getJson(MINECRAFT_PROFILE_URL, "Bearer " + minecraftAccessToken, proxySettings);
        return parseMinecraftProfile(obj);
    }

    private LoginResult loginWithMsToken(MsToken msToken, ProviderProxySettings proxySettings, Consumer<String> status)
        throws IOException {
        status.accept(tr("wawelauth.gui.login.status.microsoft_xbox"));
        XblToken xbl = getXblToken(msToken.accessToken, proxySettings);

        status.accept(tr("wawelauth.gui.login.status.microsoft_xsts"));
        String xsts = getXstsToken(xbl.token, proxySettings);

        status.accept(tr("wawelauth.gui.login.status.microsoft_minecraft_token"));
        String minecraftToken = getMinecraftAccessToken(xbl.uhs, xsts, proxySettings);

        status.accept(tr("wawelauth.gui.login.status.microsoft_profile"));
        MinecraftProfile profile = fetchMinecraftProfile(minecraftToken, proxySettings);

        return new LoginResult(profile.name, profile.uuid, minecraftToken, msToken.refreshToken);
    }

    private String awaitAuthorizationCode(String expectedState) throws IOException {
        AtomicReference<String> codeRef = new AtomicReference<>();
        AtomicReference<String> stateRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<String> errorDescriptionRef = new AtomicReference<>();
        AtomicReference<String> cancelMessageRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        HttpServer server;
        ActiveLoopback loopback;
        synchronized (LOOPBACK_LOCK) {
            cancelActiveLoopbackLocked(tr("wawelauth.gui.login.error.microsoft_restarted"));
            try {
                server = HttpServer.create(new InetSocketAddress(LOOPBACK_PORT), 0);
            } catch (BindException e) {
                throw new IOException(tr("wawelauth.gui.login.error.microsoft_callback_port_busy"), e);
            }
            server.createContext(REDIRECT_PATH, exchange -> {
                try {
                    Map<String, String> query = parseQuery(
                        exchange.getRequestURI()
                            .getRawQuery());
                    codeRef.set(query.get("code"));
                    stateRef.set(query.get("state"));
                    errorRef.set(query.get("error"));
                    errorDescriptionRef.set(query.get("error_description"));

                    boolean hasCode = codeRef.get() != null && !codeRef.get()
                        .isEmpty();
                    boolean hasError = errorRef.get() != null && !errorRef.get()
                        .isEmpty();
                    boolean stateMatches = expectedState != null && expectedState.equals(stateRef.get());
                    String response = buildCallbackPageHtml(
                        hasCode,
                        hasError,
                        stateMatches,
                        errorRef.get(),
                        errorDescriptionRef.get(),
                        codeRef.get(),
                        stateRef.get());
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
            openBrowser(buildAuthorizeUrl(expectedState));

            boolean completed;
            try {
                completed = latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                throw new IOException(tr("wawelauth.gui.login.error.microsoft_interrupted"), e);
            }

            if (!completed) {
                throw new IOException(tr("wawelauth.gui.login.error.microsoft_timeout"));
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

        if (errorRef.get() != null) {
            throw new IOException(tr("wawelauth.gui.login.error.microsoft_failed", errorRef.get()));
        }

        String code = codeRef.get();
        if (code == null || code.isEmpty()) {
            throw new IOException(tr("wawelauth.gui.login.error.microsoft_missing_code"));
        }

        String returnedState = stateRef.get();
        if (expectedState != null && !expectedState.equals(returnedState)) {
            throw new IOException(tr("wawelauth.gui.login.error.microsoft_state_mismatch"));
        }

        return code;
    }

    private MsToken exchangeAuthorizationCode(String code, ProviderProxySettings proxySettings) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("scope", "XboxLive.signin offline_access");
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        params.put("redirect_uri", REDIRECT_URI);

        JsonObject obj = postForm(MS_TOKEN_URL, params, proxySettings);
        return parseMsToken(obj);
    }

    private MsToken refreshMsToken(String refreshToken, ProviderProxySettings proxySettings) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("refresh_token", refreshToken);
        params.put("grant_type", "refresh_token");
        params.put("redirect_uri", REDIRECT_URI);

        JsonObject obj = postForm(MS_TOKEN_URL, params, proxySettings);
        return parseMsToken(obj);
    }

    private XblToken getXblToken(String msAccessToken, ProviderProxySettings proxySettings) throws IOException {
        JsonObject body = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msAccessToken);
        body.add("Properties", props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        JsonObject response = postJson(XBL_AUTH_URL, body, null, proxySettings);
        String token = requireString(response, "Token");

        JsonObject claims = response.getAsJsonObject("DisplayClaims");
        if (claims == null || !claims.has("xui")
            || !claims.get("xui")
                .isJsonArray()) {
            throw new IOException("Missing Xbox user hash in DisplayClaims.xui");
        }

        JsonArray xui = claims.getAsJsonArray("xui");
        if (xui.size() == 0 || !xui.get(0)
            .isJsonObject()) {
            throw new IOException("Missing Xbox user hash in DisplayClaims.xui[0]");
        }

        String uhs = requireString(
            xui.get(0)
                .getAsJsonObject(),
            "uhs");
        return new XblToken(token, uhs);
    }

    private String getXstsToken(String xblToken, ProviderProxySettings proxySettings) throws IOException {
        JsonObject body = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(xblToken));
        props.add("UserTokens", userTokens);
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        JsonObject response = postJson(XSTS_AUTH_URL, body, null, proxySettings);
        if (response.has("XErr")) {
            long xerr = response.get("XErr")
                .getAsLong();
            throw new IOException("XSTS rejected account (XErr=" + xerr + ")");
        }
        return requireString(response, "Token");
    }

    private String getMinecraftAccessToken(String uhs, String xstsToken, ProviderProxySettings proxySettings)
        throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject response = postJson(MINECRAFT_AUTH_URL, body, null, proxySettings);
        return requireString(response, "access_token");
    }

    private MinecraftProfile parseMinecraftProfile(JsonObject obj) throws IOException {
        if (obj == null) {
            throw new IOException("Empty Minecraft profile response");
        }
        String name = requireString(obj, "name");
        String id = requireString(obj, "id");
        return new MinecraftProfile(name, UuidUtil.fromUnsigned(id));
    }

    private MsToken parseMsToken(JsonObject obj) throws IOException {
        String accessToken = requireString(obj, "access_token");
        String refreshToken = requireString(obj, "refresh_token");
        return new MsToken(accessToken, refreshToken);
    }

    private static String requireString(JsonObject obj, String field) throws IOException {
        if (obj == null || !obj.has(field)
            || obj.get(field)
                .isJsonNull()) {
            throw new IOException("Missing field: " + field);
        }
        String value = obj.get(field)
            .getAsString();
        if (value == null || value.isEmpty()) {
            throw new IOException("Field is empty: " + field);
        }
        return value;
    }

    private String buildAuthorizeUrl(String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("response_type", "code");
        params.put("redirect_uri", REDIRECT_URI);
        params.put("scope", "XboxLive.signin offline_access");
        params.put("prompt", "select_account");
        params.put("state", state);
        return MS_AUTHORIZE_URL + "?" + encodeForm(params);
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

    /**
     * Mirrors Catalogue-Vintage behavior:
     * 1) java.awt.Desktop
     * 2) lwjgl3ify Desktop redirect
     * 3) org.lwjglx.Sys.openURL
     * 4) org.lwjgl.Sys.openURL
     */
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

    private static String buildCallbackPageHtml(boolean hasCode, boolean hasError, boolean stateMatches, String error,
        String errorDescription, String code, String state) {
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

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String tr(String key, Object... args) {
        return String.format(tr(key), args);
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

    private static String getConfiguredValue(String propertyName, String envName, String fallback) {
        String fromProperty = trimToNull(System.getProperty(propertyName));
        if (fromProperty != null) {
            return fromProperty;
        }
        String fromEnv = trimToNull(System.getenv(envName));
        if (fromEnv != null) {
            return fromEnv;
        }
        return fallback;
    }

    private static URI parseRedirectUri(String rawUri) {
        try {
            URI uri = new URI(rawUri);
            if (trimToNull(uri.getHost()) == null) {
                return URI.create(DEFAULT_REDIRECT_URI);
            }
            if (trimToNull(uri.getPath()) == null) {
                return URI.create(DEFAULT_REDIRECT_URI);
            }
            return uri;
        } catch (URISyntaxException e) {
            return URI.create(DEFAULT_REDIRECT_URI);
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
        return 26669;
    }

    private static String normalizePath(String path) {
        String p = trimToNull(path);
        if (p == null) {
            return "/relogin";
        }
        return p.startsWith("/") ? p : ("/" + p);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private static String loadCallbackLogoDataUrl() {
        try (InputStream in = MicrosoftOAuthClient.class
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

    private JsonObject postForm(String url, Map<String, String> params, ProviderProxySettings proxySettings)
        throws IOException {
        byte[] payload = encodeForm(params).getBytes(StandardCharsets.UTF_8);
        debugProxyRequest("POST form", url, proxySettings);
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

    private JsonObject postJson(String url, JsonObject body, String authorization, ProviderProxySettings proxySettings)
        throws IOException {
        byte[] payload = body.toString()
            .getBytes(StandardCharsets.UTF_8);
        debugProxyRequest("POST json", url, proxySettings);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                if (authorization != null && !authorization.isEmpty()) {
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

    private JsonObject getJson(String url, String authorization, ProviderProxySettings proxySettings)
        throws IOException {
        debugProxyRequest("GET", url, proxySettings);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(proxySettings)) {
            HttpURLConnection conn = openConnection(url, proxySettings);
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                if (authorization != null && !authorization.isEmpty()) {
                    conn.setRequestProperty("Authorization", authorization);
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

    private static void debugProxyRequest(String method, String url, ProviderProxySettings proxySettings) {
        WawelAuth.debug(
            "Microsoft OAuth HTTP " + method
                + " "
                + url
                + " [proxy="
                + ProviderProxySupport.describeProxySettings(proxySettings)
                + ", transport=urlconnection]");
    }

    private JsonObject readJsonResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = in != null ? readStream(in) : "";

        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, body);
        }

        if (body == null || body.trim()
            .isEmpty()) {
            return new JsonObject();
        }
        return new JsonParser().parse(body)
            .getAsJsonObject();
    }

    private static String readStream(InputStream stream) throws IOException {
        return new String(readBytes(stream), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream stream) throws IOException {
        try (InputStream is = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Response too large");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String encodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(urlEncode(entry.getKey()))
                .append('=')
                .append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = urlDecode(part.substring(0, idx));
            String value = urlDecode(part.substring(idx + 1));
            result.put(key, value);
        }
        return result;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static final class MsToken {

        private final String accessToken;
        private final String refreshToken;

        private MsToken(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    private static final class XblToken {

        private final String token;
        private final String uhs;

        private XblToken(String token, String uhs) {
            this.token = token;
            this.uhs = uhs;
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

    public static final class MinecraftProfile {

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

    public static final class LoginResult {

        private final String profileName;
        private final UUID profileUuid;
        private final String minecraftAccessToken;
        private final String microsoftRefreshToken;

        public LoginResult(String profileName, UUID profileUuid, String minecraftAccessToken,
            String microsoftRefreshToken) {
            this.profileName = profileName;
            this.profileUuid = profileUuid;
            this.minecraftAccessToken = minecraftAccessToken;
            this.microsoftRefreshToken = microsoftRefreshToken;
        }

        public String getProfileName() {
            return profileName;
        }

        public UUID getProfileUuid() {
            return profileUuid;
        }

        public String getMinecraftAccessToken() {
            return minecraftAccessToken;
        }

        public String getMicrosoftRefreshToken() {
            return microsoftRefreshToken;
        }
    }

    /**
     * HTTP failure with status code and response payload.
     */
    public static class HttpStatusException extends IOException {

        private final int statusCode;
        private final String responseBody;

        public HttpStatusException(int statusCode, String responseBody) {
            super(
                "HTTP " + statusCode + (responseBody != null && !responseBody.isEmpty() ? (": " + responseBody) : ""));
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
