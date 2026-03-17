package org.fentanylsolutions.wawelauth.wawelcore.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Separate config file for fallback auth servers.
 * Loaded from fallback-servers.json next to server.json.
 * Order matters: servers are tried top to bottom.
 */
public class FallbackServersConfig {

    private List<FallbackServer> fallbackServers = new ArrayList<>();

    public List<FallbackServer> getFallbackServers() {
        if (fallbackServers == null) fallbackServers = new ArrayList<>();
        return fallbackServers;
    }

    public void setFallbackServers(List<FallbackServer> fallbackServers) {
        this.fallbackServers = fallbackServers;
    }

    /** Return only enabled fallback servers, preserving order. */
    public List<FallbackServer> getEnabledFallbackServers() {
        List<FallbackServer> enabled = new ArrayList<>();
        for (FallbackServer server : getFallbackServers()) {
            if (server != null && server.isEnabled()) {
                enabled.add(server);
            }
        }
        return enabled;
    }
}
