package org.fentanylsolutions.wawelauth.wawelclient;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

/**
 * Providers advertised by the current server. Populated on join, cleared on disconnect.
 * Thread-safe.
 */
public class ConnectionProviderCache {

    private volatile List<ClientProvider> providers = Collections.emptyList();
    private final ConcurrentHashMap<UUID, ClientProvider> playerProviders = new ConcurrentHashMap<>();

    /** Store providers advertised by the server. */
    public void setProviders(List<ClientProvider> providers) {
        this.providers = providers != null ? new CopyOnWriteArrayList<>(providers) : Collections.emptyList();
    }

    /** Default provider for the current connection, or null if vanilla/disconnected. */
    public ClientProvider getDefaultProvider() {
        List<ClientProvider> current = this.providers;
        return current.isEmpty() ? null : current.get(0);
    }

    /**
     * Get all providers for the current connection.
     */
    public List<ClientProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /** Associate a player UUID with the provider that authenticated them. */
    public void associatePlayer(UUID playerUuid, ClientProvider provider) {
        if (playerUuid == null || provider == null) return;
        playerProviders.put(playerUuid, provider);
    }

    /** Get the provider associated with a player, or null. */
    public ClientProvider getPlayerProvider(UUID playerUuid) {
        if (playerUuid == null) return null;
        return playerProviders.get(playerUuid);
    }

    /** Find a provider by session server URL, or null. */
    public ClientProvider findBySessionServerUrl(String sessionServerUrl) {
        if (sessionServerUrl == null || sessionServerUrl.trim()
            .isEmpty()) return null;
        String normalized = normalizeUrl(sessionServerUrl);
        for (ClientProvider provider : providers) {
            if (normalized.equals(normalizeUrl(provider.getSessionServerUrl()))) {
                return provider;
            }
            // Also check apiRoot-derived session URL
            String apiRoot = provider.getApiRoot();
            if (apiRoot != null && normalized.equals(normalizeUrl(apiRoot + "/sessionserver"))) {
                return provider;
            }
        }
        return null;
    }

    /** Find a provider by public key fingerprint, or null. */
    public ClientProvider findByPublicKeyFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.trim()
            .isEmpty()) return null;
        String normalized = fingerprint.trim()
            .toLowerCase();
        for (ClientProvider provider : providers) {
            String providerFingerprint = provider.getPublicKeyFingerprint();
            if (providerFingerprint != null && normalized.equals(
                providerFingerprint.trim()
                    .toLowerCase())) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Whether this cache has providers (i.e. connected to a WA server).
     */
    public boolean isActive() {
        return !providers.isEmpty();
    }

    /**
     * Clear all state. Called on disconnect.
     */
    public void clear() {
        providers = Collections.emptyList();
        playerProviders.clear();
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim()
            .toLowerCase();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
