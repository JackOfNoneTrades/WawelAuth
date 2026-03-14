package org.fentanylsolutions.wawelauth.wawelclient.data;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/**
 * A stored Yggdrasil-compatible authentication provider.
 * <p>
 * Built-in providers (e.g. Mojang) have separate auth/session/services URLs
 * on different domains. Custom providers derive all endpoints from their
 * API root URL per the ALI specification.
 * <p>
 * The provider's name acts as its primary key (unique identifier).
 */
public class ClientProvider {

    /**
     * Unique human-readable name. Primary key.
     */
    private String name;

    /**
     * BUILTIN or CUSTOM.
     */
    private ProviderType type;

    /**
     * API root URL. For custom providers, all endpoints are derived from this.
     * For BUILTIN (Mojang), this is informational only.
     */
    private String apiRoot;

    /**
     * Auth server URL, e.g. "https://authserver.mojang.com".
     */
    private String authServerUrl;

    /**
     * Session server URL, e.g. "https://sessionserver.mojang.com".
     */
    private String sessionServerUrl;

    /**
     * Services URL. Nullable.
     */
    private String servicesUrl;

    /**
     * Skin domains from the provider's metadata, stored as a JSON array string.
     * E.g. '["textures.minecraft.net",".example.com"]'.
     */
    private String skinDomains;

    /**
     * Base64-encoded public key from the provider's signaturePublickey
     * metadata field (PEM body without headers, or raw DER base64).
     * Used to verify signed profile properties. Null if unavailable.
     */
    private String publicKeyBase64;

    /**
     * SHA-256 fingerprint of the public key, hex-encoded.
     * Shown to the user for TOFU confirmation when adding a custom provider.
     */
    private String publicKeyFingerprint;

    /**
     * Epoch millis when this provider was added.
     */
    private long createdAt;

    /**
     * True if this provider was explicitly added by the user.
     */
    private boolean manualEntry = true;

    /**
     * Per-provider proxy toggle.
     */
    private boolean proxyEnabled;

    /**
     * Proxy type when proxyEnabled is true.
     */
    private ProviderProxyType proxyType = ProviderProxyType.HTTP;

    /**
     * Proxy host/address.
     */
    private String proxyHost;

    /**
     * Proxy port.
     */
    private Integer proxyPort;

    /**
     * Optional proxy username.
     */
    private String proxyUsername;

    /**
     * Optional proxy password.
     */
    private String proxyPassword;

    public ClientProvider() {}

    // --- URL builders ---

    /**
     * Build a full auth endpoint URL.
     *
     * @param path endpoint path, e.g. "/authenticate"
     * @return full URL, e.g. "https://authserver.mojang.com/authenticate"
     * @throws IllegalStateException if authServerUrl is null
     */
    public String authUrl(String path) {
        if (authServerUrl == null) {
            throw new IllegalStateException("authServerUrl is null for provider '" + name + "'");
        }
        return stripTrailingSlash(authServerUrl) + path;
    }

    /**
     * Build a full session endpoint URL.
     *
     * @param path endpoint path, e.g. "/session/minecraft/join"
     * @return full URL
     * @throws IllegalStateException if sessionServerUrl is null
     */
    public String sessionUrl(String path) {
        if (sessionServerUrl == null) {
            throw new IllegalStateException("sessionServerUrl is null for provider '" + name + "'");
        }
        return stripTrailingSlash(sessionServerUrl) + path;
    }

    /**
     * Parse the skinDomains JSON array into a list of strings.
     * Returns an empty list if skinDomains is null or malformed.
     */
    public List<String> getSkinDomainList() {
        if (skinDomains == null || skinDomains.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JsonArray arr = new JsonParser().parse(skinDomains)
                .getAsJsonArray();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                result.add(
                    arr.get(i)
                        .getAsString());
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    // --- Getters/Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProviderType getType() {
        return type;
    }

    public void setType(ProviderType type) {
        this.type = type;
    }

    public String getApiRoot() {
        return apiRoot;
    }

    public void setApiRoot(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    public String getAuthServerUrl() {
        return authServerUrl;
    }

    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }

    public String getSessionServerUrl() {
        return sessionServerUrl;
    }

    public void setSessionServerUrl(String sessionServerUrl) {
        this.sessionServerUrl = sessionServerUrl;
    }

    public String getServicesUrl() {
        return servicesUrl;
    }

    public void setServicesUrl(String servicesUrl) {
        this.servicesUrl = servicesUrl;
    }

    public String getSkinDomains() {
        return skinDomains;
    }

    public void setSkinDomains(String skinDomains) {
        this.skinDomains = skinDomains;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isManualEntry() {
        return manualEntry;
    }

    public void setManualEntry(boolean manualEntry) {
        this.manualEntry = manualEntry;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public ProviderProxyType getProxyType() {
        return proxyType != null ? proxyType : ProviderProxyType.HTTP;
    }

    public void setProxyType(ProviderProxyType proxyType) {
        this.proxyType = proxyType != null ? proxyType : ProviderProxyType.HTTP;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public ProviderProxySettings getProxySettings() {
        ProviderProxySettings settings = new ProviderProxySettings();
        settings.setEnabled(proxyEnabled);
        settings.setType(getProxyType());
        settings.setHost(proxyHost);
        settings.setPort(proxyPort);
        settings.setUsername(proxyUsername);
        settings.setPassword(proxyPassword);
        return settings;
    }

    public void setProxySettings(ProviderProxySettings settings) {
        if (settings == null) {
            proxyEnabled = false;
            proxyType = ProviderProxyType.HTTP;
            proxyHost = null;
            proxyPort = null;
            proxyUsername = null;
            proxyPassword = null;
            return;
        }
        proxyEnabled = settings.isEnabled();
        proxyType = settings.getType();
        proxyHost = settings.getHost();
        proxyPort = settings.getPort();
        proxyUsername = settings.getUsername();
        proxyPassword = settings.getPassword();
    }
}
