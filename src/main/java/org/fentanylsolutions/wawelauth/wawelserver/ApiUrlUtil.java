package org.fentanylsolutions.wawelauth.wawelserver;

import java.net.InetAddress;

import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.fentlib.util.NetUtil;
import org.fentanylsolutions.fentlib.util.NetworkAddressUtil;
import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;

/**
 * URL resolution helpers shared by services that talk to the local or
 * fallback Yggdrasil endpoints.
 */
public final class ApiUrlUtil {

    private ApiUrlUtil() {}

    /**
     * Resolves a configured session server URL to its /session/minecraft base.
     */
    public static String resolveSessionMinecraftBase(String rawSessionServerUrl) {
        String base = NetUtil.normalizeHttpUrl(rawSessionServerUrl);
        if (base == null) return null;

        if (base.endsWith("/session/minecraft")) {
            return base;
        }
        if (base.endsWith("/sessionserver")) {
            return base + "/session/minecraft";
        }
        if (base.endsWith("/session")) {
            return base + "/minecraft";
        }

        // Default for entries configured as server roots
        return base + "/session/minecraft";
    }

    /**
     * Resolves the local embedded API root: the configured effective API root
     * if present, otherwise loopback on the MC port.
     */
    public static String resolveLocalApiRoot() {
        String configured = ensureHttpScheme(
            WawelPingPayload.normalizeUrl(
                Config.server() == null ? null
                    : Config.server()
                        .getEffectiveApiRoot()));
        if (configured != null) {
            return configured;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;

        int port = server.getServerPort();
        if (port <= 0) {
            port = server.getPort();
        }
        if (port <= 0) return null;

        InetAddress loopback = InetAddress.getLoopbackAddress();
        String host = loopback == null ? "127.0.0.1" : loopback.getHostAddress();
        return "http://" + NetworkAddressUtil.formatHostPort(host, port);
    }

    private static String ensureHttpScheme(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "http://" + value;
    }
}
