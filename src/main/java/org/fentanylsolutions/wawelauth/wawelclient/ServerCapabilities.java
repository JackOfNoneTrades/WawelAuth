package org.fentanylsolutions.wawelauth.wawelclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Runtime-only ping capability snapshot for one server entry.
 * Never persisted to servers.dat.
 */
public final class ServerCapabilities {

    private final boolean wawelAuthAdvertised;
    private final boolean localAuthSupported;
    private final String localAuthApiRoot;
    private final String localAuthPublicKeyFingerprint;
    private final String localAuthPublicKeyBase64;
    private final List<String> localAuthSkinDomains;
    private final List<String> acceptedProviderNames;
    private final List<String> acceptedAuthServerUrls;
    private final List<AcceptedProviderDescriptor> acceptedProviders;
    private final long updatedAtMs;
    private final String rawPayloadJson;

    private ServerCapabilities(boolean wawelAuthAdvertised, boolean localAuthSupported, String localAuthApiRoot,
        String localAuthPublicKeyFingerprint, String localAuthPublicKeyBase64, List<String> localAuthSkinDomains,
        List<String> acceptedProviderNames, List<String> acceptedAuthServerUrls,
        List<AcceptedProviderDescriptor> acceptedProviders, long updatedAtMs, String rawPayloadJson) {
        this.wawelAuthAdvertised = wawelAuthAdvertised;
        this.localAuthSupported = localAuthSupported;
        this.localAuthApiRoot = localAuthApiRoot;
        this.localAuthPublicKeyFingerprint = localAuthPublicKeyFingerprint;
        this.localAuthPublicKeyBase64 = localAuthPublicKeyBase64;
        this.localAuthSkinDomains = localAuthSkinDomains;
        this.acceptedProviderNames = acceptedProviderNames;
        this.acceptedAuthServerUrls = acceptedAuthServerUrls;
        this.acceptedProviders = acceptedProviders;
        this.updatedAtMs = updatedAtMs;
        this.rawPayloadJson = rawPayloadJson;
    }

    public static ServerCapabilities empty() {
        return new ServerCapabilities(
            false,
            false,
            null,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            0L,
            null);
    }

    /**
     * No WawelAuth payload in ping response.
     * Provider set is unknown; do not assume Mojang/Microsoft compatibility.
     */
    public static ServerCapabilities unadvertised(long nowMs) {
        return new ServerCapabilities(
            false,
            false,
            null,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            nowMs,
            null);
    }

    /**
     * Synthetic capability snapshot reconstructed from persisted local-auth
     * metadata on a server entry. This is used only for offline/local UI flows.
     */
    public static ServerCapabilities persistedLocalAuth(String apiRoot, String fingerprint, String publicKeyBase64) {
        String normalizedApiRoot = WawelPingPayload.normalizeUrl(apiRoot);
        String normalizedFingerprint = normalizeFingerprint(fingerprint);
        String normalizedPublicKeyBase64 = normalizeString(publicKeyBase64);
        if (normalizedApiRoot == null || normalizedFingerprint == null) {
            return empty();
        }

        return new ServerCapabilities(
            false,
            true,
            normalizedApiRoot,
            normalizedFingerprint,
            normalizedPublicKeyBase64,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            0L,
            null);
    }

    public static ServerCapabilities fromPayload(JsonObject payload, long nowMs) {
        // Backward-compatible: older servers may still send provider names.
        List<String> names = toUnmodifiableCopy(WawelPingPayload.parseStringArray(payload, "acceptedProviderNames"));
        List<String> urls = toUnmodifiableCopy(
            WawelPingPayload.normalizeUrls(
                WawelPingPayload.parseStringArray(payload, WawelPingPayload.KEY_ACCEPTED_AUTH_SERVER_URLS)));

        String legacyApiRoot = WawelPingPayload.normalizeUrl(getString(payload, WawelPingPayload.KEY_API_ROOT));
        boolean legacyProvides = getBoolean(payload, WawelPingPayload.KEY_PROVIDES_YGGDRASIL_SERVICE, false);

        boolean localAuth = getBoolean(payload, WawelPingPayload.KEY_LOCAL_AUTH_SUPPORTED, legacyProvides);
        String localApiRoot = WawelPingPayload
            .normalizeUrl(getString(payload, WawelPingPayload.KEY_LOCAL_AUTH_API_ROOT));
        if (localApiRoot == null) {
            localApiRoot = legacyApiRoot;
        }
        String localFingerprint = normalizeFingerprint(
            getString(payload, WawelPingPayload.KEY_LOCAL_AUTH_PUBLIC_KEY_FINGERPRINT));
        String localPublicKeyBase64 = normalizeString(
            getString(payload, WawelPingPayload.KEY_LOCAL_AUTH_PUBLIC_KEY_BASE64));
        List<String> localSkinDomains = toUnmodifiableCopy(
            WawelPingPayload.parseStringArray(payload, WawelPingPayload.KEY_LOCAL_AUTH_SKIN_DOMAINS));
        List<AcceptedProviderDescriptor> acceptedProviders = parseAcceptedProviders(payload);

        boolean localDescriptorComplete = localApiRoot != null && localFingerprint != null;
        if (!localDescriptorComplete) {
            localAuth = false;
        }

        return new ServerCapabilities(
            true,
            localAuth,
            localApiRoot,
            localFingerprint,
            localPublicKeyBase64,
            localSkinDomains,
            names,
            urls,
            acceptedProviders,
            nowMs,
            payload == null ? null : payload.toString());
    }

    private static List<String> toUnmodifiableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public boolean isWawelAuthAdvertised() {
        return wawelAuthAdvertised;
    }

    public boolean isLocalAuthSupported() {
        return localAuthSupported;
    }

    public String getLocalAuthApiRoot() {
        return localAuthApiRoot;
    }

    public String getLocalAuthPublicKeyFingerprint() {
        return localAuthPublicKeyFingerprint;
    }

    public String getLocalAuthPublicKeyBase64() {
        return localAuthPublicKeyBase64;
    }

    public List<String> getLocalAuthSkinDomains() {
        return localAuthSkinDomains;
    }

    public List<String> getAcceptedProviderNames() {
        return acceptedProviderNames;
    }

    public List<String> getAcceptedAuthServerUrls() {
        return acceptedAuthServerUrls;
    }

    public List<AcceptedProviderDescriptor> getAcceptedProviders() {
        return acceptedProviders;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public String getRawPayloadJson() {
        return rawPayloadJson;
    }

    private static String getString(JsonObject payload, String key) {
        if (payload == null || key == null
            || !payload.has(key)
            || payload.get(key)
                .isJsonNull()) {
            return null;
        }
        try {
            return payload.get(key)
                .getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean getBoolean(JsonObject payload, String key, boolean defaultValue) {
        if (payload == null || key == null || !payload.has(key)) {
            return defaultValue;
        }
        try {
            return payload.get(key)
                .getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String normalizeString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeFingerprint(String value) {
        String normalized = normalizeString(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static List<AcceptedProviderDescriptor> parseAcceptedProviders(JsonObject payload) {
        if (payload == null || !payload.has(WawelPingPayload.KEY_ACCEPTED_PROVIDER_DESCRIPTORS)
            || !payload.get(WawelPingPayload.KEY_ACCEPTED_PROVIDER_DESCRIPTORS)
                .isJsonArray()) {
            return Collections.emptyList();
        }

        JsonArray array = payload.getAsJsonArray(WawelPingPayload.KEY_ACCEPTED_PROVIDER_DESCRIPTORS);
        List<AcceptedProviderDescriptor> providers = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }

            JsonObject obj = element.getAsJsonObject();
            List<String> skinDomains = toUnmodifiableCopy(
                WawelPingPayload.parseStringArray(obj, WawelPingPayload.KEY_PROVIDER_SKIN_DOMAINS));
            providers.add(
                new AcceptedProviderDescriptor(
                    normalizeString(getString(obj, WawelPingPayload.KEY_PROVIDER_NAME)),
                    WawelPingPayload.normalizeUrl(getString(obj, WawelPingPayload.KEY_PROVIDER_API_ROOT)),
                    WawelPingPayload.normalizeUrl(getString(obj, WawelPingPayload.KEY_PROVIDER_AUTH_SERVER_URL)),
                    WawelPingPayload.normalizeUrl(getString(obj, WawelPingPayload.KEY_PROVIDER_SESSION_SERVER_URL)),
                    WawelPingPayload.normalizeUrl(getString(obj, WawelPingPayload.KEY_PROVIDER_SERVICES_URL)),
                    skinDomains));
        }

        return providers.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(providers);
    }

    public static final class AcceptedProviderDescriptor {

        private final String name;
        private final String apiRoot;
        private final String authServerUrl;
        private final String sessionServerUrl;
        private final String servicesUrl;
        private final List<String> skinDomains;

        private AcceptedProviderDescriptor(String name, String apiRoot, String authServerUrl, String sessionServerUrl,
            String servicesUrl, List<String> skinDomains) {
            this.name = name;
            this.apiRoot = apiRoot;
            this.authServerUrl = authServerUrl;
            this.sessionServerUrl = sessionServerUrl;
            this.servicesUrl = servicesUrl;
            this.skinDomains = skinDomains != null ? skinDomains : Collections.emptyList();
        }

        public String getName() {
            return name;
        }

        public String getApiRoot() {
            return apiRoot;
        }

        public String getAuthServerUrl() {
            return authServerUrl;
        }

        public String getSessionServerUrl() {
            return sessionServerUrl;
        }

        public String getServicesUrl() {
            return servicesUrl;
        }

        public List<String> getSkinDomains() {
            return skinDomains;
        }
    }
}
