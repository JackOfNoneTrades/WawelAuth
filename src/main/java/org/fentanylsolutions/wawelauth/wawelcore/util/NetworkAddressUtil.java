package org.fentanylsolutions.wawelauth.wawelcore.util;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Helpers for parsing and formatting network addresses in an IPv4/IPv6-safe way.
 */
public final class NetworkAddressUtil {

    private NetworkAddressUtil() {}

    /**
     * Extracts an IP literal from a socket address.
     *
     * Prefers {@link InetSocketAddress#getAddress()} host-address text and falls
     * back to parsing the string form for uncommon address implementations.
     */
    public static String extractIp(SocketAddress address) {
        if (address == null) return "";

        if (address instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) address;
            if (inet.getAddress() != null) {
                String hostAddress = inet.getAddress()
                    .getHostAddress();
                if (hostAddress != null && !hostAddress.isEmpty()) {
                    return hostAddress;
                }
            }
        }

        String raw = address.toString();
        String parsed = extractIpFromSocketString(raw);
        return parsed != null && !parsed.isEmpty() ? parsed : raw;
    }

    /**
     * Parses an address string such as:
     *
     * <ul>
     * <li>{@code /127.0.0.1:25565}</li>
     * <li>{@code /[2001:db8::1]:25565}</li>
     * <li>{@code hostname/127.0.0.1:25565}</li>
     * </ul>
     */
    public static String extractIpFromSocketString(String raw) {
        if (raw == null) return "";

        String value = raw.trim();
        if (value.isEmpty()) return value;

        if (value.startsWith("/")) {
            value = value.substring(1);
        }

        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }

        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 1) {
                return value.substring(1, end);
            }
            return value;
        }

        int colon = value.indexOf(':');
        if (colon > 0) {
            return value.substring(0, colon);
        }

        return value;
    }

    /**
     * True for simple IPv4/IPv6 literals accepted by vanilla IP ban commands.
     */
    public static boolean looksLikeIp(String value) {
        if (value == null) return false;
        return value.contains(".") || value.contains(":");
    }

    /**
     * Formats {@code host[:port]} with brackets for IPv6 host literals.
     */
    public static String formatHostPort(String host, int port) {
        if (host == null || host.isEmpty()) return host;
        String displayHost = host;
        if (displayHost.indexOf(':') >= 0 && !(displayHost.startsWith("[") && displayHost.endsWith("]"))) {
            displayHost = "[" + displayHost + "]";
        }
        return port > 0 ? displayHost + ":" + port : displayHost;
    }
}
