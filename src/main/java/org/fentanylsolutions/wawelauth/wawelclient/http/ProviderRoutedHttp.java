package org.fentanylsolutions.wawelauth.wawelclient.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;

/**
 * Opens direct or provider-proxied connections for one explicitly chosen
 * provider.
 * <p>
 * Callers that cannot infer the provider should pass null and
 * use direct access.
 */
public final class ProviderRoutedHttp {

    private static final int MAX_BINARY_RESPONSE_BYTES = 16 * 1024 * 1024;

    private ProviderRoutedHttp() {}

    public static RoutedConnection openConnection(String url, ClientProvider provider, int connectTimeoutMs,
        int readTimeoutMs, String userAgent, String purpose) throws IOException {
        return openConnection(
            url,
            settingsFor(provider),
            providerName(provider),
            connectTimeoutMs,
            readTimeoutMs,
            userAgent,
            purpose);
    }

    public static RoutedConnection openConnection(String url, ProviderProxySettings settings, String providerName,
        int connectTimeoutMs, int readTimeoutMs, String userAgent, String purpose) throws IOException {
        String label = providerName != null && !providerName.trim()
            .isEmpty() ? providerName : "direct";

        WawelAuth.debug(
            purpose + " "
                + url
                + " [provider="
                + label
                + ", proxy="
                + ProviderProxySupport.describeProxySettings(settings)
                + "]");

        ProviderProxySupport.AuthContext authContext = ProviderProxySupport.enterAuthContext(settings);
        try {
            HttpURLConnection connection = shouldUseProviderProxy(settings)
                ? ProviderProxySupport.openConnection(url, settings, connectTimeoutMs, readTimeoutMs, userAgent)
                : openDirectConnection(url, connectTimeoutMs, readTimeoutMs, userAgent);
            return new RoutedConnection(connection, authContext);
        } catch (IOException e) {
            authContext.close();
            throw e;
        }
    }

    public static byte[] downloadBytes(String url, ClientProvider provider, int connectTimeoutMs, int readTimeoutMs,
        String userAgent, String purpose) throws IOException {
        return downloadBytes(
            url,
            settingsFor(provider),
            providerName(provider),
            connectTimeoutMs,
            readTimeoutMs,
            userAgent,
            purpose);
    }

    public static byte[] downloadBytes(String url, ProviderProxySettings settings, String providerName,
        int connectTimeoutMs, int readTimeoutMs, String userAgent, String purpose) throws IOException {
        JdkProxyHttpClient jdkProxyHttpClient = new JdkProxyHttpClient(
            connectTimeoutMs,
            readTimeoutMs,
            MAX_BINARY_RESPONSE_BYTES,
            userAgent);
        if (shouldUseProviderProxy(settings) && jdkProxyHttpClient.supports(settings)) {
            String label = providerName != null && !providerName.trim()
                .isEmpty() ? providerName : "direct";
            WawelAuth.debug(
                purpose + " "
                    + url
                    + " [provider="
                    + label
                    + ", proxy="
                    + ProviderProxySupport.describeProxySettings(settings)
                    + ", transport=jdk-http-client]");
            return jdkProxyHttpClient.downloadBytes(url, settings);
        }

        try (RoutedConnection routed = openConnection(
            url,
            settings,
            providerName,
            connectTimeoutMs,
            readTimeoutMs,
            userAgent,
            purpose)) {
            HttpURLConnection connection = routed.getConnection();
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + " from " + url);
            }

            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                return baos.toByteArray();
            }
        }
    }

    private static ProviderProxySettings settingsFor(ClientProvider provider) {
        if (provider == null) {
            return null;
        }

        ProviderProxySettings original = provider.getProxySettings();
        if (original == null) {
            return null;
        }

        ProviderProxySettings copy = new ProviderProxySettings();
        copy.setEnabled(original.isEnabled());
        copy.setType(original.getType());
        copy.setHost(original.getHost());
        copy.setPort(original.getPort());
        copy.setUsername(original.getUsername());
        copy.setPassword(original.getPassword());
        return copy;
    }

    private static String providerName(ClientProvider provider) {
        if (provider == null || provider.getName() == null) {
            return null;
        }
        String trimmed = provider.getName()
            .trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean shouldUseProviderProxy(ProviderProxySettings settings) {
        return settings != null && settings.isEnabled() && settings.hasEndpoint();
    }

    private static HttpURLConnection openDirectConnection(String url, int connectTimeoutMs, int readTimeoutMs,
        String userAgent) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", userAgent);
        return conn;
    }

    public static final class RoutedConnection implements AutoCloseable {

        private final HttpURLConnection connection;
        private final ProviderProxySupport.AuthContext authContext;

        private RoutedConnection(HttpURLConnection connection, ProviderProxySupport.AuthContext authContext) {
            this.connection = connection;
            this.authContext = authContext;
        }

        public HttpURLConnection getConnection() {
            return connection;
        }

        @Override
        public void close() {
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } finally {
                if (authContext != null) {
                    authContext.close();
                }
            }
        }
    }
}
