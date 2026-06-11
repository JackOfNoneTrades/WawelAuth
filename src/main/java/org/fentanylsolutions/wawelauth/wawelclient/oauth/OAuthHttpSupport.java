package org.fentanylsolutions.wawelauth.wawelclient.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;

import com.google.gson.JsonObject;

/**
 * Shared HTTP transport, form encoding, and browser-launch helpers for the OAuth clients.
 */
final class OAuthHttpSupport {

    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int READ_TIMEOUT_MS = 15_000;
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private OAuthHttpSupport() {}

    static HttpURLConnection openConnection(String url, ProviderProxySettings proxySettings) throws IOException {
        return ProviderProxySupport
            .openConnection(url, proxySettings, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, "WawelAuth");
    }

    static String requireString(JsonObject obj, String field) throws IOException {
        if (obj == null || !obj.has(field)
            || obj.get(field)
                .isJsonNull()) {
            throw new IOException("Missing field: " + field);
        }
        String value = obj.get(field)
            .getAsString();
        if (StringUtil.trimToNull(value) == null) {
            throw new IOException("Field is empty: " + field);
        }
        return value;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = StringUtil.trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    static String encodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = StringUtil.trimToNull(entry.getKey());
            String value = StringUtil.trimToNull(entry.getValue());
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

    static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String readStream(InputStream stream) throws IOException {
        return new String(readBytes(stream), StandardCharsets.UTF_8);
    }

    static byte[] readBytes(InputStream stream) throws IOException {
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

    static void openBrowser(String url) throws IOException {
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
}
