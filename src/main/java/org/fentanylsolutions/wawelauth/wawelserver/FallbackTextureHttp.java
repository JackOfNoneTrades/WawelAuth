package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;

import org.fentanylsolutions.fentlib.util.NetUtil;
import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;

/**
 * Shared guarded HTTP fetcher for texture URLs returned by fallback providers.
 */
final class FallbackTextureHttp {

    static final int DEFAULT_MAX_REDIRECTS = 5;

    private static final String USER_AGENT = "WawelAuth";

    private FallbackTextureHttp() {}

    static URI validateAllowedTextureUrl(FallbackServer fallback, String upstreamUrl) {
        URI uri;
        try {
            uri = URI.create(upstreamUrl);
        } catch (Exception e) {
            throw NetException.illegalArgument("Invalid upstream texture URL.");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw NetException.illegalArgument("Invalid upstream texture URL.");
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw NetException.forbidden("Only HTTP(S) texture URLs are allowed.");
        }
        if (!isHostAllowedForFallback(fallback, host)) {
            throw NetException.forbidden("Texture URL host is not allowed for this fallback.");
        }

        return uri;
    }

    static Response fetch(FallbackServer fallback, String url, int maxBytes, int maxRedirects, int connectTimeoutMs,
        int readTimeoutMs, String accept) throws IOException {
        String currentUrl = url;
        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            URI currentUri = validateAllowedTextureUrl(fallback, currentUrl);
            validatePublicHost(currentUri.getHost());

            HttpURLConnection conn = openConnection(currentUri.toString(), connectTimeoutMs, readTimeoutMs, accept);
            try {
                int status = conn.getResponseCode();
                if (isRedirectStatus(status)) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.trim()
                        .isEmpty()) {
                        return new Response(status, null, conn.getContentType());
                    }
                    if (redirectCount == maxRedirects) {
                        throw new IOException("Upstream texture redirect limit exceeded");
                    }
                    currentUrl = resolveRedirectUrl(currentUri, location);
                    continue;
                }

                InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (stream == null) {
                    return new Response(status, null, conn.getContentType());
                }
                return new Response(status, readAll(stream, maxBytes), conn.getContentType());
            } finally {
                conn.disconnect();
            }
        }

        throw new IOException("Upstream texture redirect limit exceeded");
    }

    private static HttpURLConnection openConnection(String url, int connectTimeoutMs, int readTimeoutMs, String accept)
        throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", accept == null ? "*/*" : accept);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        return conn;
    }

    private static boolean isHostAllowedForFallback(FallbackServer fallback, String host) {
        if (fallback == null || host == null) {
            return false;
        }

        String loweredHost = host.toLowerCase();

        for (String pattern : fallback.getSkinDomains()) {
            if (hostMatchesPattern(loweredHost, pattern)) {
                return true;
            }
        }

        String sessionHost = extractHost(fallback.getSessionServerUrl());
        if (sessionHost != null && loweredHost.equals(sessionHost)) {
            return true;
        }

        String accountHost = extractHost(fallback.getAccountUrl());
        if (accountHost != null && loweredHost.equals(accountHost)) {
            return true;
        }

        String servicesHost = extractHost(fallback.getServicesUrl());
        return servicesHost != null && loweredHost.equals(servicesHost);
    }

    private static String extractHost(String rawUrl) {
        String normalized = normalizeUrl(rawUrl);
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hostMatchesPattern(String host, String patternRaw) {
        String pattern = trimToNull(patternRaw);
        if (pattern == null) return false;

        String lower = pattern.toLowerCase();
        if (lower.startsWith(".")) {
            return host.equals(lower.substring(1)) || host.endsWith(lower);
        }
        return host.equals(lower);
    }

    private static String normalizeUrl(String raw) {
        return NetUtil.normalizeHttpUrl(raw);
    }

    private static String trimToNull(String value) {
        return StringUtil.trimToNull(value);
    }

    private static boolean isRedirectStatus(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
            || status == HttpURLConnection.HTTP_SEE_OTHER
            || status == 307
            || status == 308;
    }

    private static String resolveRedirectUrl(URI currentUri, String location) throws IOException {
        try {
            URI redirected = currentUri.resolve(location.trim());
            return redirected.toString();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid upstream texture redirect", e);
        }
    }

    private static void validatePublicHost(String host) throws IOException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Unable to resolve texture URL host: " + host, e);
        }

        if (addresses.length == 0) {
            throw new IOException("Unable to resolve texture URL host: " + host);
        }

        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IOException("Texture URL resolves to a private or local address: " + host);
            }
        }
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4Address(bytes);
        }
        if (bytes.length == 16) {
            if (isIpv4MappedIpv6Address(bytes)) {
                byte[] ipv4 = new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] };
                return isBlockedIpv4Address(ipv4);
            }
            return isBlockedIpv6Address(bytes);
        }

        return true;
    }

    private static boolean isBlockedIpv4Address(byte[] bytes) {
        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;
        int b2 = bytes[2] & 0xff;

        return b0 == 0 || b0 == 10
            || b0 == 127
            || (b0 == 100 && b1 >= 64 && b1 <= 127)
            || (b0 == 169 && b1 == 254)
            || (b0 == 172 && b1 >= 16 && b1 <= 31)
            || (b0 == 192 && b1 == 0 && b2 == 0)
            || (b0 == 192 && b1 == 0 && b2 == 2)
            || (b0 == 192 && b1 == 168)
            || (b0 == 198 && (b1 == 18 || b1 == 19))
            || (b0 == 198 && b1 == 51 && b2 == 100)
            || (b0 == 203 && b1 == 0 && b2 == 113)
            || b0 >= 224;
    }

    private static boolean isBlockedIpv6Address(byte[] bytes) {
        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;

        return (b0 & 0xfe) == 0xfc
            || (b0 == 0x20 && b1 == 0x01 && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8);
    }

    private static boolean isIpv4MappedIpv6Address(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static byte[] readAll(InputStream stream, int maxBytes) throws IOException {
        try (InputStream is = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
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

    static final class Response {

        final int status;
        final byte[] data;
        final String contentType;

        Response(int status, byte[] data, String contentType) {
            this.status = status;
            this.data = data;
            this.contentType = contentType;
        }
    }
}
