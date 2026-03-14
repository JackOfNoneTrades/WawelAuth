package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilRequestException;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;

import com.google.gson.JsonObject;

/**
 * Manages authentication providers.
 * <p>
 * Ensures the built-in Mojang provider always exists. Provides methods
 * to add custom providers (with ALI discovery) and remove them.
 */
public class ProviderRegistry {

    private static final String MOJANG_API_ROOT = "https://authserver.mojang.com";
    private static final String MOJANG_AUTH_URL = "https://authserver.mojang.com";
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com";
    private static final String MOJANG_SERVICES_URL = "https://api.minecraftservices.com";
    private static final String MOJANG_SKIN_DOMAINS = "[\"textures.minecraft.net\"]";
    private static final String OFFLINE_API_ROOT = "offline://account";
    private static final String OFFLINE_AUTH_URL = OFFLINE_API_ROOT + "/authserver";
    private static final String OFFLINE_SESSION_URL = OFFLINE_API_ROOT + "/sessionserver";
    private static final int PROXY_CONNECT_TIMEOUT_MS = 10_000;

    private final ClientProviderDAO providerDAO;
    private final YggdrasilHttpClient httpClient;

    public ProviderRegistry(ClientProviderDAO providerDAO, YggdrasilHttpClient httpClient) {
        this.providerDAO = providerDAO;
        this.httpClient = httpClient;
    }

    /**
     * Ensures the built-in Mojang provider exists in the database.
     * Called once during WawelClient startup.
     */
    public void ensureBuiltinProviders() {
        if (providerDAO.findByName(BuiltinProviders.MOJANG_PROVIDER_NAME) == null) {
            ClientProvider mojang = new ClientProvider();
            mojang.setName(BuiltinProviders.MOJANG_PROVIDER_NAME);
            mojang.setType(ProviderType.BUILTIN);
            mojang.setApiRoot(MOJANG_API_ROOT);
            mojang.setAuthServerUrl(MOJANG_AUTH_URL);
            mojang.setSessionServerUrl(MOJANG_SESSION_URL);
            mojang.setServicesUrl(MOJANG_SERVICES_URL);
            mojang.setSkinDomains(MOJANG_SKIN_DOMAINS);
            mojang.setCreatedAt(System.currentTimeMillis());
            mojang.setManualEntry(true);
            providerDAO.create(mojang);
            WawelAuth.LOG.info("Created built-in Mojang provider");
        }
        if (providerDAO.findByName(BuiltinProviders.OFFLINE_PROVIDER_NAME) == null) {
            ClientProvider offline = new ClientProvider();
            offline.setName(BuiltinProviders.OFFLINE_PROVIDER_NAME);
            offline.setType(ProviderType.BUILTIN);
            offline.setApiRoot(OFFLINE_API_ROOT);
            offline.setAuthServerUrl(OFFLINE_AUTH_URL);
            offline.setSessionServerUrl(OFFLINE_SESSION_URL);
            offline.setServicesUrl(OFFLINE_API_ROOT);
            offline.setCreatedAt(System.currentTimeMillis());
            offline.setManualEntry(true);
            providerDAO.create(offline);
            WawelAuth.LOG.info("Created built-in offline account provider");
        }
    }

    /**
     * Add a custom provider by discovering its endpoints via the ALI protocol
     * and persisting it in one step.
     *
     * @param name    user-chosen name for this provider
     * @param userUrl the URL provided by the user
     * @return the created provider (with fingerprint for user confirmation)
     * @throws IOException               on network errors
     * @throws YggdrasilRequestException on HTTP errors
     * @throws IllegalArgumentException  if name already taken
     */
    public ClientProvider addCustomProvider(String name, String userUrl) throws IOException, YggdrasilRequestException {
        ClientProvider provider = discoverCustomProvider(name, userUrl);
        persistProvider(provider);
        return provider;
    }

    /**
     * Discover a custom provider's endpoints via the ALI protocol without
     * persisting it. Returns an in-memory {@link ClientProvider} for the
     * user to review (e.g. confirm the public key fingerprint) before
     * calling {@link #persistProvider(ClientProvider)}.
     *
     * @param name    user-chosen name for this provider
     * @param userUrl the URL provided by the user
     * @return the discovered provider (NOT yet persisted)
     * @throws IOException               on network errors
     * @throws YggdrasilRequestException on HTTP errors
     * @throws IllegalArgumentException  if name already taken
     */
    public ClientProvider discoverCustomProvider(String name, String userUrl)
        throws IOException, YggdrasilRequestException {

        if (providerDAO.findByName(name) != null) {
            throw new IllegalArgumentException("Provider name already taken: " + name);
        }

        // 1. Resolve API root via ALI header
        String apiRoot = httpClient.resolveApiRoot(userUrl);
        WawelAuth.debug("Resolved API root for '" + name + "': " + apiRoot);

        // 2. Fetch metadata
        JsonObject metadata = httpClient.getJson(apiRoot);

        // 3. Extract skin domains (JSON array)
        String skinDomainsJson = null;
        if (metadata.has("skinDomains") && metadata.get("skinDomains")
            .isJsonArray()) {
            skinDomainsJson = metadata.getAsJsonArray("skinDomains")
                .toString();
        }

        // 4. Extract and process public key
        String publicKeyBase64 = null;
        String fingerprint = null;
        if (metadata.has("signaturePublickey") && !metadata.get("signaturePublickey")
            .isJsonNull()) {
            String rawKey = metadata.get("signaturePublickey")
                .getAsString();
            publicKeyBase64 = extractKeyBase64(rawKey);
            fingerprint = computeKeyFingerprint(publicKeyBase64);
        }

        // 5. Derive endpoint URLs from API root
        String authServerUrl = apiRoot + "/authserver";
        String sessionServerUrl = apiRoot + "/sessionserver";
        String servicesUrl = apiRoot;

        // 6. Build provider WITHOUT persisting
        ClientProvider provider = new ClientProvider();
        provider.setName(name);
        provider.setType(ProviderType.CUSTOM);
        provider.setApiRoot(apiRoot);
        provider.setAuthServerUrl(authServerUrl);
        provider.setSessionServerUrl(sessionServerUrl);
        provider.setServicesUrl(servicesUrl);
        provider.setSkinDomains(skinDomainsJson);
        provider.setPublicKeyBase64(publicKeyBase64);
        provider.setPublicKeyFingerprint(fingerprint);
        provider.setCreatedAt(System.currentTimeMillis());
        provider.setManualEntry(true);

        return provider;
    }

    /**
     * Persist a previously discovered provider.
     * Call this only after the user has confirmed the provider's fingerprint.
     *
     * @param provider the provider returned by {@link #discoverCustomProvider}
     * @throws IllegalArgumentException if the name is already taken
     */
    public void persistProvider(ClientProvider provider) {
        if (providerDAO.findByName(provider.getName()) != null) {
            throw new IllegalArgumentException("Provider name already taken: " + provider.getName());
        }
        providerDAO.create(provider);
        WawelAuth.LOG.info("Added custom provider '{}' at {}", provider.getName(), provider.getApiRoot());
    }

    /**
     * Remove a custom provider and all its accounts.
     * Built-in providers cannot be removed.
     *
     * @throws IllegalArgumentException if the provider is built-in or not found
     */
    public void removeProvider(String name) {
        ClientProvider provider = providerDAO.findByName(name);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + name);
        }
        if (provider.getType() == ProviderType.BUILTIN) {
            throw new IllegalArgumentException("Cannot remove built-in provider: " + name);
        }
        // FK ON DELETE CASCADE handles account cleanup
        providerDAO.delete(name);
        WawelClient client = WawelClient.instance();
        if (client != null) {
            ServerBindingPersistence.clearMissingAccountBindings(client.getAccountManager());
        }
        WawelAuth.LOG.info("Removed provider '{}' and all associated accounts", name);
    }

    /**
     * Rename a custom provider while preserving all accounts bound to it.
     * Built-in providers cannot be renamed.
     *
     * @throws IllegalArgumentException if provider is missing/built-in, new name is invalid, or already taken
     */
    public void renameProvider(String oldName, String newName) {
        if (oldName == null || newName == null) {
            throw new IllegalArgumentException("Provider names must not be null");
        }

        String oldTrimmed = oldName.trim();
        String newTrimmed = newName.trim();
        if (oldTrimmed.isEmpty() || newTrimmed.isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be empty");
        }
        if (oldTrimmed.equals(newTrimmed)) {
            return;
        }

        ClientProvider provider = providerDAO.findByName(oldTrimmed);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + oldTrimmed);
        }
        if (provider.getType() == ProviderType.BUILTIN) {
            throw new IllegalArgumentException("Cannot rename built-in provider: " + oldTrimmed);
        }

        providerDAO.rename(oldTrimmed, newTrimmed);
        WawelAuth.LOG.info("Renamed provider '{}' -> '{}'", oldTrimmed, newTrimmed);
    }

    public List<ClientProvider> listProviders() {
        return providerDAO.listAll();
    }

    public ClientProvider getProvider(String name) {
        return providerDAO.findByName(name);
    }

    public void updateProxySettings(String providerName, ProviderProxySettings settings) {
        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + providerName);
        }

        ProviderProxySettings normalized = normalizeProxySettings(settings);
        validateProxySettings(normalized);
        provider.setProxySettings(normalized);
        providerDAO.update(provider);
        WawelAuth.LOG.info(
            "Updated proxy settings for provider '{}' (enabled={}, type={}, host={})",
            providerName,
            normalized.isEnabled(),
            normalized.getType(),
            normalized.getHost());
    }

    public ProviderProbeResult probeProviderConnection(String providerName, ProviderProxySettings settings)
        throws IOException {
        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + providerName);
        }
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            throw new IllegalArgumentException("Offline Account does not use a remote auth provider.");
        }

        ProviderProxySettings normalized = normalizeProxySettings(settings);
        validateProxySettings(normalized);
        if (normalized == null || !normalized.isEnabled() || !normalized.hasEndpoint()) {
            return new ProviderProbeResult(
                new ProbeLine(ProbeOutcome.NEUTRAL, "Not configured."),
                new ProbeLine(ProbeOutcome.NEUTRAL, "Skipped: configure a proxy first."));
        }

        ProbeLine proxyLine = probeProxyEndpoint(normalized);
        ProbeLine providerLine = probeProviderApi(provider, normalized);
        return new ProviderProbeResult(proxyLine, providerLine);
    }

    /**
     * Extract the base64-encoded key body from a PEM string or raw base64.
     * Strips PEM headers/footers and whitespace if present.
     */
    static String extractKeyBase64(String rawKey) {
        if (rawKey == null) return null;
        return rawKey.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
    }

    /**
     * Compute SHA-256 fingerprint of the DER-encoded public key.
     * Returns hex-encoded hash, or null if the key is null/invalid.
     */
    static String computeKeyFingerprint(String base64Key) {
        if (base64Key == null || base64Key.isEmpty()) return null;
        try {
            byte[] derBytes = Base64.getDecoder()
                .decode(base64Key);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(derBytes);
            char[] hexChars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                hexChars[i * 2] = "0123456789abcdef".charAt(v >>> 4);
                hexChars[i * 2 + 1] = "0123456789abcdef".charAt(v & 0x0F);
            }
            return new String(hexChars);
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to compute key fingerprint: {}", e.getMessage());
            return null;
        }
    }

    private static ProviderProxySettings normalizeProxySettings(ProviderProxySettings settings) {
        ProviderProxySettings normalized = new ProviderProxySettings();
        if (settings == null) {
            return normalized;
        }

        normalized.setEnabled(settings.isEnabled());
        normalized.setType(settings.getType());
        normalized.setHost(trimToNull(settings.getHost()));
        normalized.setPort(settings.getPort());
        normalized.setUsername(trimToNull(settings.getUsername()));
        normalized.setPassword(trimToNull(settings.getPassword()));
        return normalized;
    }

    private static void validateProxySettings(ProviderProxySettings settings) {
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        if (trimToNull(settings.getHost()) == null) {
            throw new IllegalArgumentException("Proxy address is required.");
        }
        Integer port = settings.getPort();
        if (port == null || port.intValue() < 1 || port.intValue() > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535.");
        }
        if (trimToNull(settings.getPassword()) != null && trimToNull(settings.getUsername()) == null) {
            throw new IllegalArgumentException("Proxy username is required when a proxy password is set.");
        }
    }

    private static String probeUrlFor(ClientProvider provider) {
        String apiRoot = trimToNull(provider.getApiRoot());
        if (apiRoot != null && (apiRoot.startsWith("http://") || apiRoot.startsWith("https://"))) {
            return apiRoot;
        }

        String authUrl = trimToNull(provider.getAuthServerUrl());
        if (authUrl != null && (authUrl.startsWith("http://") || authUrl.startsWith("https://"))) {
            return authUrl;
        }

        String sessionUrl = trimToNull(provider.getSessionServerUrl());
        if (sessionUrl != null && (sessionUrl.startsWith("http://") || sessionUrl.startsWith("https://"))) {
            return sessionUrl;
        }

        String servicesUrl = trimToNull(provider.getServicesUrl());
        if (servicesUrl != null && (servicesUrl.startsWith("http://") || servicesUrl.startsWith("https://"))) {
            return servicesUrl;
        }

        throw new IllegalArgumentException("Provider has no reachable HTTP endpoint.");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProbeLine probeProxyEndpoint(ProviderProxySettings settings) {
        try {
            ProviderProxySupport.probeEndpoint(settings, PROXY_CONNECT_TIMEOUT_MS);
            return new ProbeLine(ProbeOutcome.SUCCESS, "Reachable.");
        } catch (IOException e) {
            return new ProbeLine(ProbeOutcome.ERROR, describeProxyProbeFailure(settings, e));
        }
    }

    private ProbeLine probeProviderApi(ClientProvider provider, ProviderProxySettings settings) {
        try {
            int status = httpClient.probeReachability(probeUrlFor(provider), settings);
            if (status == HttpURLConnection.HTTP_PROXY_AUTH) {
                if (settings != null && settings.hasCredentials()) {
                    return new ProbeLine(
                        ProbeOutcome.ERROR,
                        "Proxy authentication failed. Check username and password.");
                }
                return new ProbeLine(ProbeOutcome.ERROR, "Proxy requires authentication. Enter username and password.");
            }
            if (status >= 200 && status < 400) {
                return new ProbeLine(ProbeOutcome.SUCCESS, "Reachable (HTTP " + status + ").");
            }
            return new ProbeLine(ProbeOutcome.ERROR, "Provider API responded with HTTP " + status + ".");
        } catch (IOException e) {
            return new ProbeLine(ProbeOutcome.ERROR, describeProviderProbeFailure(provider, settings, e));
        }
    }

    private static String describeProxyProbeFailure(ProviderProxySettings settings, IOException error) {
        Throwable root = rootCause(error);
        String target = NetworkAddressUtil.formatHostPort(
            settings.getHost(),
            settings.getPort() != null ? settings.getPort()
                .intValue() : -1);

        if (root instanceof UnknownHostException) {
            return "Could not resolve proxy host: " + target;
        }
        if (root instanceof ConnectException) {
            String detail = trimToNull(root.getMessage());
            return "Could not connect to proxy " + target + (detail != null ? " (" + detail + ")" : "");
        }
        if (root instanceof SocketTimeoutException) {
            return "Timed out while connecting to proxy " + target;
        }
        if (root instanceof NoRouteToHostException) {
            return "No route to proxy " + target;
        }

        String detail = trimToNull(root.getMessage());
        if (detail != null && !detail.equals(settings.getHost())) {
            return "Proxy test failed via " + target + ": " + detail;
        }
        return "Proxy test failed via " + target;
    }

    private static String describeProviderProbeFailure(ClientProvider provider, ProviderProxySettings settings,
        IOException error) {
        Throwable root = rootCause(error);
        String proxyTarget = settings != null && settings.hasEndpoint() ? NetworkAddressUtil.formatHostPort(
            settings.getHost(),
            settings.getPort() != null ? settings.getPort()
                .intValue() : -1)
            : null;
        String providerTarget = provider.getName();

        if (root instanceof UnknownHostException) {
            return proxyTarget != null ? "Could not resolve host while using proxy " + proxyTarget
                : "Could not resolve provider host for " + providerTarget;
        }
        if (root instanceof ConnectException) {
            String detail = trimToNull(root.getMessage());
            return proxyTarget != null
                ? "Could not reach provider API through proxy " + proxyTarget
                    + (detail != null ? " (" + detail + ")" : "")
                : "Could not connect to provider " + providerTarget + (detail != null ? " (" + detail + ")" : "");
        }
        if (root instanceof SocketTimeoutException) {
            return proxyTarget != null ? "Timed out while reaching provider API through proxy " + proxyTarget
                : "Timed out while reaching provider " + providerTarget;
        }
        if (root instanceof NoRouteToHostException) {
            return proxyTarget != null ? "No route to provider API through proxy " + proxyTarget
                : "No route to provider " + providerTarget;
        }

        String detail = trimToNull(root.getMessage());
        if (detail != null && (detail.startsWith("Proxy requires authentication")
            || detail.startsWith("Proxy authentication failed"))) {
            return detail;
        }
        if (detail != null && detail.startsWith(ProviderProxySupport.httpProxyBasicAuthJava8UnsupportedMessage())) {
            return detail;
        }
        if (detail != null && !(settings != null && detail.equals(settings.getHost()))) {
            return proxyTarget != null ? "Provider API test failed through proxy " + proxyTarget + ": " + detail
                : "Provider test failed for " + providerTarget + ": " + detail;
        }
        return proxyTarget != null ? "Provider API test failed through proxy " + proxyTarget
            : "Provider test failed for " + providerTarget;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    public static final class ProviderProbeResult {

        private final ProbeLine proxyStatus;
        private final ProbeLine providerApiStatus;

        public ProviderProbeResult(ProbeLine proxyStatus, ProbeLine providerApiStatus) {
            this.proxyStatus = proxyStatus;
            this.providerApiStatus = providerApiStatus;
        }

        public ProbeLine getProxyStatus() {
            return proxyStatus;
        }

        public ProbeLine getProviderApiStatus() {
            return providerApiStatus;
        }
    }

    public static final class ProbeLine {

        private final ProbeOutcome outcome;
        private final String message;

        public ProbeLine(ProbeOutcome outcome, String message) {
            this.outcome = outcome;
            this.message = message;
        }

        public ProbeOutcome getOutcome() {
            return outcome;
        }

        public String getMessage() {
            return message;
        }
    }

    public enum ProbeOutcome {
        NEUTRAL,
        SUCCESS,
        ERROR
    }
}
