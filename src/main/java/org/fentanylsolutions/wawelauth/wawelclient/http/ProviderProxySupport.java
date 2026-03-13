package org.fentanylsolutions.wawelauth.wawelclient.http;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;

/**
 * Shared proxy transport helper for provider-scoped HTTP traffic.
 */
public final class ProviderProxySupport {

    private static final String TUNNELING_DISABLED_SCHEMES_PROPERTY = "jdk.http.auth.tunneling.disabledSchemes";
    private static final String PROXYING_DISABLED_SCHEMES_PROPERTY = "jdk.http.auth.proxying.disabledSchemes";
    private static final String HTTP_PROXY_BASIC_AUTH_JAVA8_UNSUPPORTED = "Java 8 HTTP proxy Basic auth could not be enabled automatically. Start the JVM with "
        + "-Djdk.http.auth.tunneling.disabledSchemes= (and for plain HTTP also "
        + "-Djdk.http.auth.proxying.disabledSchemes=).";
    private static final AtomicBoolean AUTHENTICATOR_INSTALLED = new AtomicBoolean(false);
    private static final ThreadLocal<ProviderProxySettings> ACTIVE_PROXY_SETTINGS = new ThreadLocal<>();
    private static final List<ProviderProxySettings> ACTIVE_GLOBAL_PROXY_SETTINGS = Collections
        .synchronizedList(new ArrayList<ProviderProxySettings>());
    private static final ReentrantLock LEGACY_HTTP_PROXY_AUTH_LOCK = new ReentrantLock();
    private static final boolean MODERN_HTTP_CLIENT_AVAILABLE = detectModernHttpClient();
    private static final boolean LEGACY_BASIC_PROXY_AUTH_AVAILABLE;

    static {
        enableBasicProxyAuth();
        LEGACY_BASIC_PROXY_AUTH_AVAILABLE = MODERN_HTTP_CLIENT_AVAILABLE || patchLegacyBasicProxyAuth();
        installAuthenticator();
    }

    private ProviderProxySupport() {}

    public static AuthContext enterAuthContext(ProviderProxySettings settings) {
        return new AuthContext(settings);
    }

    public static HttpURLConnection openConnection(String url, ProviderProxySettings settings, int connectTimeoutMs,
        int readTimeoutMs, String userAgent) throws IOException {
        if (isUnsupportedHttpProxyBasicAuth(settings)) {
            throw new IOException(HTTP_PROXY_BASIC_AUTH_JAVA8_UNSUPPORTED);
        }

        HttpURLConnection conn;
        if (settings != null && settings.isEnabled() && settings.hasEndpoint()) {
            Proxy.Type proxyType = settings.getType() == ProviderProxyType.SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            Proxy proxy = new Proxy(
                proxyType,
                InetSocketAddress.createUnresolved(
                    settings.getHost()
                        .trim(),
                    settings.getPort()
                        .intValue()));
            conn = (HttpURLConnection) new URL(url).openConnection(proxy);
            if (proxyType == Proxy.Type.HTTP && settings.hasCredentials()) {
                String user = settings.getUsername() != null ? settings.getUsername() : "";
                String pass = settings.getPassword() != null ? settings.getPassword() : "";
                String token = Base64.getEncoder()
                    .encodeToString((user + ":" + pass).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                conn.setRequestProperty("Proxy-Authorization", "Basic " + token);
            }
        } else {
            conn = (HttpURLConnection) new URL(url).openConnection();
        }

        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", userAgent);
        return conn;
    }

    public static void probeEndpoint(ProviderProxySettings settings, int connectTimeoutMs) throws IOException {
        if (settings == null || !settings.isEnabled() || !settings.hasEndpoint()) {
            throw new IllegalArgumentException("Proxy address and port are required.");
        }

        Socket socket = new Socket();
        try {
            socket.connect(
                new InetSocketAddress(
                    settings.getHost()
                        .trim(),
                    settings.getPort()
                        .intValue()),
                connectTimeoutMs);
        } finally {
            socket.close();
        }
    }

    public static boolean isUnsupportedHttpProxyBasicAuth(ProviderProxySettings settings) {
        return settings != null && settings.isEnabled()
            && settings.hasEndpoint()
            && settings.hasCredentials()
            && settings.getType() == ProviderProxyType.HTTP
            && !isModernHttpProxyAuthAvailable();
    }

    public static boolean isModernHttpProxyAuthAvailable() {
        return MODERN_HTTP_CLIENT_AVAILABLE || LEGACY_BASIC_PROXY_AUTH_AVAILABLE;
    }

    public static String httpProxyBasicAuthJava8UnsupportedMessage() {
        return HTTP_PROXY_BASIC_AUTH_JAVA8_UNSUPPORTED;
    }

    public static String describeProxySettings(ProviderProxySettings settings) {
        if (settings == null) {
            return "direct";
        }
        if (!settings.isEnabled()) {
            return "disabled";
        }
        if (!settings.hasEndpoint()) {
            return settings.getType() + " (incomplete)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(settings.getType())
            .append(' ')
            .append(settings.getHost())
            .append(':')
            .append(settings.getPort());
        if (settings.hasCredentials()) {
            sb.append(" auth=")
                .append(settings.getUsername());
        }
        return sb.toString();
    }

    static boolean matchesProxyRequest(ProviderProxySettings settings, String requestingHost,
        InetAddress requestingSite, int requestingPort) {
        if (settings == null || !settings.isEnabled() || !settings.hasEndpoint()) {
            return false;
        }

        int configuredPort = settings.getPort() != null ? settings.getPort()
            .intValue() : -1;
        if (configuredPort > 0 && requestingPort > 0 && configuredPort != requestingPort) {
            return false;
        }

        String configuredHost = settings.getHost() != null ? settings.getHost()
            .trim() : null;
        if (configuredHost == null || configuredHost.isEmpty()) {
            return true;
        }
        if (hostMatches(configuredHost, requestingHost)) {
            return true;
        }
        if (requestingSite != null) {
            if (hostMatches(configuredHost, requestingSite.getHostName())) {
                return true;
            }
            if (hostMatches(configuredHost, requestingSite.getHostAddress())) {
                return true;
            }
        }
        return requestingHost == null && requestingSite == null;
    }

    private static void enableBasicProxyAuth() {
        allowBasicAuthFor(TUNNELING_DISABLED_SCHEMES_PROPERTY);
        allowBasicAuthFor(PROXYING_DISABLED_SCHEMES_PROPERTY);
    }

    private static void allowBasicAuthFor(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null) {
            System.setProperty(propertyName, "");
            WawelAuth.LOG.info("Enabled Basic proxy auth for JVM property {}", propertyName);
            return;
        }

        String[] parts = value.split(",");
        List<String> kept = new ArrayList<>();
        boolean removedBasic = false;
        for (String part : parts) {
            String trimmed = part != null ? part.trim() : "";
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("basic".equals(trimmed.toLowerCase(Locale.ROOT))) {
                removedBasic = true;
                continue;
            }
            kept.add(trimmed);
        }

        if (!removedBasic) {
            return;
        }

        StringBuilder updated = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) {
                updated.append(',');
            }
            updated.append(kept.get(i));
        }
        System.setProperty(propertyName, updated.toString());
        WawelAuth.LOG.info("Removed Basic from JVM disabled proxy auth schemes: {}", propertyName);
    }

    private static void installAuthenticator() {
        if (!AUTHENTICATOR_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Authenticator.setDefault(new Authenticator() {

            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                ProviderProxySettings settings = resolveActiveAuthenticationSettings(
                    getRequestorType(),
                    getRequestingProtocol(),
                    getRequestingHost(),
                    getRequestingSite(),
                    getRequestingPort());
                if (settings == null) {
                    return null;
                }
                String username = settings.getUsername() != null ? settings.getUsername() : "";
                char[] password = (settings.getPassword() != null ? settings.getPassword() : "").toCharArray();
                return new PasswordAuthentication(username, password);
            }
        });
    }

    private static boolean detectModernHttpClient() {
        try {
            Class.forName("java.net.http.HttpClient");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean patchLegacyBasicProxyAuth() {
        boolean tunneling = patchDisabledSchemesField("disabledTunnelingSchemes");
        boolean proxying = patchDisabledSchemesField("disabledProxyingSchemes");
        return tunneling && proxying;
    }

    private static void resetLegacyProxyAuthStateIfNeeded(ProviderProxySettings settings) {
        if (MODERN_HTTP_CLIENT_AVAILABLE || settings == null
            || !settings.isEnabled()
            || !settings.hasCredentials()
            || settings.getType() != ProviderProxyType.HTTP) {
            return;
        }

        clearLegacyAuthenticationCache();
        clearLegacyKeepAliveCache();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean patchDisabledSchemesField(String fieldName) {
        try {
            Class<?> httpUrlConnectionClass = Class.forName("sun.net.www.protocol.http.HttpURLConnection");
            Field field = httpUrlConnectionClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object rawValue = field.get(null);
            if (!(rawValue instanceof Set)) {
                WawelAuth.LOG.warn("Legacy proxy auth patch skipped: {} is not a Set", fieldName);
                return false;
            }

            Set schemes = (Set) rawValue;
            boolean removed = false;
            List snapshot = new ArrayList(schemes);
            for (Object entry : snapshot) {
                if (entry != null && "basic".equals(
                    String.valueOf(entry)
                        .toLowerCase(Locale.ROOT))) {
                    schemes.remove(entry);
                    removed = true;
                }
            }

            for (Object entry : schemes) {
                if (entry != null && "basic".equals(
                    String.valueOf(entry)
                        .toLowerCase(Locale.ROOT))) {
                    WawelAuth.LOG.warn("Legacy proxy auth patch failed: Basic still present in {}", fieldName);
                    return false;
                }
            }

            if (removed) {
                WawelAuth.LOG.info("Enabled legacy Basic proxy auth by patching {}", fieldName);
            }
            return true;
        } catch (Throwable t) {
            WawelAuth.LOG.warn("Failed to patch legacy Basic proxy auth field {}", fieldName, t);
            return false;
        }
    }

    private static void clearLegacyAuthenticationCache() {
        try {
            Class<?> authCacheClass = Class.forName("sun.net.www.protocol.http.AuthCache");
            Class<?> authCacheImplClass = Class.forName("sun.net.www.protocol.http.AuthCacheImpl");
            Class<?> authCacheValueClass = Class.forName("sun.net.www.protocol.http.AuthCacheValue");
            Object emptyCache = authCacheImplClass.getConstructor()
                .newInstance();
            authCacheValueClass.getMethod("setAuthCache", authCacheClass)
                .invoke(null, emptyCache);
        } catch (Throwable t) {
            WawelAuth.LOG.warn("Failed to clear legacy HTTP auth cache", t);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void clearLegacyKeepAliveCache() {
        try {
            Class<?> httpClientClass = Class.forName("sun.net.www.http.HttpClient");
            Field keepAliveCacheField = httpClientClass.getDeclaredField("kac");
            keepAliveCacheField.setAccessible(true);
            Object keepAliveCache = keepAliveCacheField.get(null);
            if (keepAliveCache instanceof java.util.Map) {
                synchronized (keepAliveCache) {
                    ((java.util.Map) keepAliveCache).clear();
                }
            }
        } catch (Throwable t) {
            WawelAuth.LOG.warn("Failed to clear legacy HTTP keep-alive cache", t);
        }
    }

    private static boolean shouldSerializeLegacyProxyAuth(ProviderProxySettings settings) {
        return !MODERN_HTTP_CLIENT_AVAILABLE && settings != null
            && settings.isEnabled()
            && settings.hasCredentials()
            && settings.getType() == ProviderProxyType.HTTP;
    }

    private static ProviderProxySettings resolveActiveAuthenticationSettings(RequestorType requestorType,
        String requestingProtocol, String requestingHost, InetAddress requestingSite, int requestingPort) {
        ProviderProxySettings threadSettings = ACTIVE_PROXY_SETTINGS.get();
        if (shouldAnswerProxyAuthentication(
            threadSettings,
            requestorType,
            requestingProtocol,
            requestingHost,
            requestingSite,
            requestingPort)) {
            return threadSettings;
        }

        synchronized (ACTIVE_GLOBAL_PROXY_SETTINGS) {
            for (int i = ACTIVE_GLOBAL_PROXY_SETTINGS.size() - 1; i >= 0; i--) {
                ProviderProxySettings settings = ACTIVE_GLOBAL_PROXY_SETTINGS.get(i);
                if (shouldAnswerProxyAuthentication(
                    settings,
                    requestorType,
                    requestingProtocol,
                    requestingHost,
                    requestingSite,
                    requestingPort)) {
                    return settings;
                }
            }
        }
        return null;
    }

    private static boolean shouldAnswerProxyAuthentication(ProviderProxySettings settings, RequestorType requestorType,
        String requestingProtocol, String requestingHost, InetAddress requestingSite, int requestingPort) {
        if (settings == null || !settings.isEnabled() || !settings.hasCredentials()) {
            return false;
        }

        if (requestorType != RequestorType.PROXY && !isSocksAuthenticationRequest(requestingProtocol)) {
            return false;
        }

        return matchesProxyRequest(settings, requestingHost, requestingSite, requestingPort);
    }

    private static boolean isSocksAuthenticationRequest(String requestingProtocol) {
        if (requestingProtocol == null) {
            return false;
        }
        return requestingProtocol.toUpperCase(Locale.ROOT)
            .startsWith("SOCKS");
    }

    private static boolean hostMatches(String configuredHost, String requestingHost) {
        return requestingHost != null && configuredHost.equalsIgnoreCase(requestingHost.trim());
    }

    public static final class AuthContext implements AutoCloseable {

        private final ProviderProxySettings previous;
        private final ProviderProxySettings current;
        private final boolean locked;

        private AuthContext(ProviderProxySettings settings) {
            this.previous = ACTIVE_PROXY_SETTINGS.get();
            this.current = settings;
            this.locked = shouldSerializeLegacyProxyAuth(settings);
            if (locked) {
                LEGACY_HTTP_PROXY_AUTH_LOCK.lock();
            }

            if (settings != null && settings.isEnabled() && settings.hasCredentials()) {
                ACTIVE_PROXY_SETTINGS.set(settings);
                ACTIVE_GLOBAL_PROXY_SETTINGS.add(settings);
                resetLegacyProxyAuthStateIfNeeded(settings);
            } else {
                ACTIVE_PROXY_SETTINGS.remove();
            }
        }

        @Override
        public void close() {
            if (previous != null) {
                ACTIVE_PROXY_SETTINGS.set(previous);
            } else {
                ACTIVE_PROXY_SETTINGS.remove();
            }
            if (current != null && current.isEnabled() && current.hasCredentials()) {
                synchronized (ACTIVE_GLOBAL_PROXY_SETTINGS) {
                    for (int i = ACTIVE_GLOBAL_PROXY_SETTINGS.size() - 1; i >= 0; i--) {
                        if (ACTIVE_GLOBAL_PROXY_SETTINGS.get(i) == current) {
                            ACTIVE_GLOBAL_PROXY_SETTINGS.remove(i);
                            break;
                        }
                    }
                }
            }
            if (locked) {
                LEGACY_HTTP_PROXY_AUTH_LOCK.unlock();
            }
        }
    }
}
