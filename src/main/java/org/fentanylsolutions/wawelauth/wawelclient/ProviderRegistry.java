package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fentanylsolutions.fentlib.util.NetworkAddressUtil;
import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilRequestException;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Manages authentication providers.
 * <p>
 * Ensures local config-backed default providers plus the special offline
 * provider exist. Provides methods to add custom providers (with ALI
 * discovery) and remove them.
 */
public class ProviderRegistry {

    private static final String DEFAULT_PROVIDER_DIR_NAME = "default-providers";
    private static final String OFFLINE_API_ROOT = "offline://account";
    private static final String OFFLINE_AUTH_URL = OFFLINE_API_ROOT + "/authserver";
    private static final String OFFLINE_SESSION_URL = OFFLINE_API_ROOT + "/sessionserver";
    private static final int PROXY_CONNECT_TIMEOUT_MS = 10_000;
    private static final Map<String, String> SEEDED_DEFAULT_PROVIDER_RESOURCES;

    static {
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("elyby.json", "/assets/wawelauth/default-providers/elyby.json");
        resources.put("littleskin.json", "/assets/wawelauth/default-providers/littleskin.json");
        resources.put("microsoft.json", "/assets/wawelauth/default-providers/microsoft.json");
        SEEDED_DEFAULT_PROVIDER_RESOURCES = Collections.unmodifiableMap(resources);
    }

    private final ClientProviderDAO providerDAO;
    private final YggdrasilHttpClient httpClient;
    private final Map<String, DefaultProviderDefinition> runtimeDefaultOverrides = new LinkedHashMap<>();

    public ProviderRegistry(ClientProviderDAO providerDAO, YggdrasilHttpClient httpClient) {
        this.providerDAO = providerDAO;
        this.httpClient = httpClient;
    }

    /**
     * Sync config-backed default providers and ensure the special offline
     * provider exists. Called once during WawelClient startup.
     */
    public void ensureDefaultProviders() {
        syncDiskDefaultProviders();
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

    private void syncDiskDefaultProviders() {
        File directory = ensureDefaultProviderDirectory();
        seedDefaultProviderDirectoryIfNeeded(directory);

        File[] files = directory.listFiles(
            (dir, name) -> name != null && name.toLowerCase()
                .endsWith(".json"));
        if (files == null || files.length == 0) {
            WawelAuth.LOG.warn(
                "No default providers found in {}. Default Microsoft/provider presets will be unavailable.",
                directory.getAbsolutePath());
            return;
        }

        Arrays.sort(
            files,
            (a, b) -> a.getName()
                .compareToIgnoreCase(b.getName()));
        int synced = 0;
        Map<String, DefaultProviderDefinition> loadedOverrides = new LinkedHashMap<>();
        for (File file : files) {
            DefaultProviderDefinition definition = loadDefaultProviderDefinition(file);
            if (definition == null) {
                continue;
            }

            ClientProvider desired;
            try {
                desired = toDefaultProvider(definition);
            } catch (IllegalArgumentException e) {
                WawelAuth.LOG.warn("Skipping default provider '{}': {}", file.getAbsolutePath(), e.getMessage());
                continue;
            }

            upsertDefaultProvider(desired);
            loadedOverrides.put(desired.getName(), definition);
            synced++;
        }

        synchronized (runtimeDefaultOverrides) {
            runtimeDefaultOverrides.clear();
            runtimeDefaultOverrides.putAll(loadedOverrides);
        }
        releaseMissingDefaultProviders(loadedOverrides.keySet());

        if (synced > 0) {
            WawelAuth.LOG.info("Synced {} default provider(s) from {}", synced, directory.getAbsolutePath());
        }
    }

    private void upsertDefaultProvider(ClientProvider desired) {
        ClientProvider existing = providerDAO.findByName(desired.getName());
        if (existing == null) {
            providerDAO.create(desired);
            WawelAuth.LOG.info("Created default provider '{}' from local config", desired.getName());
            return;
        }

        desired.setCreatedAt(existing.getCreatedAt() > 0L ? existing.getCreatedAt() : desired.getCreatedAt());
        desired.setProxyEnabled(existing.isProxyEnabled());
        desired.setProxyType(existing.getProxyType());
        desired.setProxyHost(existing.getProxyHost());
        desired.setProxyPort(existing.getProxyPort());
        desired.setProxyUsername(existing.getProxyUsername());
        desired.setProxyPassword(existing.getProxyPassword());
        providerDAO.update(desired);
    }

    private void releaseMissingDefaultProviders(java.util.Collection<String> activeDefaultNames) {
        List<ClientProvider> allProviders = providerDAO.listAll();
        for (ClientProvider provider : allProviders) {
            if (provider == null || provider.getType() != ProviderType.DEFAULT) {
                continue;
            }
            if (activeDefaultNames != null && activeDefaultNames.contains(provider.getName())) {
                continue;
            }

            ClientProvider released = copyProvider(provider);
            released.setType(ProviderType.CUSTOM);
            released.setManualEntry(true);
            providerDAO.update(released);
            WawelAuth.LOG
                .info("Released missing default provider '{}' to custom/manual management", provider.getName());
        }
    }

    public ClientProvider applyRuntimeOverrides(ClientProvider provider) {
        if (provider == null || provider.getType() != ProviderType.DEFAULT) {
            return provider;
        }

        DefaultProviderDefinition definition;
        synchronized (runtimeDefaultOverrides) {
            definition = runtimeDefaultOverrides.get(provider.getName());
        }
        if (definition == null) {
            return provider;
        }

        ClientProvider effective = copyProvider(provider);
        ClientProvider defaults = toDefaultProvider(definition);
        effective.setType(defaults.getType());
        effective.setApiRoot(defaults.getApiRoot());
        effective.setAuthServerUrl(defaults.getAuthServerUrl());
        effective.setSessionServerUrl(defaults.getSessionServerUrl());
        effective.setServicesUrl(defaults.getServicesUrl());
        effective.setMsAuthorizeUrl(defaults.getMsAuthorizeUrl());
        effective.setMsTokenUrl(defaults.getMsTokenUrl());
        effective.setXblAuthUrl(defaults.getXblAuthUrl());
        effective.setXstsAuthUrl(defaults.getXstsAuthUrl());
        effective.setMinecraftAuthUrl(defaults.getMinecraftAuthUrl());
        effective.setMinecraftProfileUrl(defaults.getMinecraftProfileUrl());
        effective.setSkinDomains(defaults.getSkinDomains());
        effective.setPublicKeyBase64(defaults.getPublicKeyBase64());
        effective.setPublicKeyFingerprint(defaults.getPublicKeyFingerprint());
        effective.setManualEntry(false);
        return effective;
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
        if (provider.getType() != ProviderType.CUSTOM) {
            throw new IllegalArgumentException("Cannot remove managed provider: " + name);
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
        if (provider.getType() != ProviderType.CUSTOM) {
            throw new IllegalArgumentException("Cannot rename managed provider: " + oldTrimmed);
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

    private static ClientProvider toDefaultProvider(DefaultProviderDefinition definition) {
        String name = trimToNull(definition.name);
        if (name == null) {
            throw new IllegalArgumentException("Missing provider name");
        }

        String apiRoot = trimToNull(definition.apiRoot);
        String accountUrl = trimToNull(definition.accountUrl);
        String explicitAuthServerUrl = trimToNull(definition.authServerUrl);
        String authServerUrl = null;
        String sessionServerUrl = trimToNull(definition.sessionServerUrl);
        String servicesUrl = trimToNull(definition.servicesUrl);
        String msAuthorizeUrl = trimToNull(definition.msAuthorizeUrl);
        String msTokenUrl = trimToNull(definition.msTokenUrl);
        String xblAuthUrl = trimToNull(definition.xblAuthUrl);
        String xstsAuthUrl = trimToNull(definition.xstsAuthUrl);
        String minecraftAuthUrl = trimToNull(definition.minecraftAuthUrl);
        String minecraftProfileUrl = trimToNull(definition.minecraftProfileUrl);

        if (apiRoot == null) {
            apiRoot = deriveApiRoot(explicitAuthServerUrl, accountUrl, sessionServerUrl, servicesUrl);
        }

        authServerUrl = deriveAuthServerUrl(explicitAuthServerUrl, accountUrl, sessionServerUrl, apiRoot);

        if (apiRoot != null) {
            String base = stripTrailingSlash(apiRoot);
            if (authServerUrl == null) {
                authServerUrl = base + "/authserver";
            }
            if (sessionServerUrl == null) {
                sessionServerUrl = base + "/sessionserver";
            }
            if (servicesUrl == null) {
                servicesUrl = base;
            }
        }

        if (servicesUrl != null) {
            String base = stripTrailingSlash(servicesUrl);
            if (minecraftAuthUrl == null) {
                minecraftAuthUrl = base + "/authentication/login_with_xbox";
            }
            if (minecraftProfileUrl == null) {
                minecraftProfileUrl = base + "/minecraft/profile";
            }
        }

        if (authServerUrl == null || sessionServerUrl == null) {
            throw new IllegalArgumentException("Default provider must define apiRoot or explicit account/session URLs");
        }

        String publicKeyBase64 = extractKeyBase64(
            firstNonBlank(definition.publicKeyBase64, definition.signaturePublickey, definition.publicKey));
        String fingerprint = trimToNull(definition.publicKeyFingerprint);
        if (fingerprint == null && publicKeyBase64 != null) {
            fingerprint = computeKeyFingerprint(publicKeyBase64);
        }

        ClientProvider provider = new ClientProvider();
        provider.setName(name);
        provider.setType(ProviderType.DEFAULT);
        provider.setApiRoot(apiRoot);
        provider.setAuthServerUrl(authServerUrl);
        provider.setSessionServerUrl(sessionServerUrl);
        provider.setServicesUrl(servicesUrl);
        provider.setMsAuthorizeUrl(msAuthorizeUrl);
        provider.setMsTokenUrl(msTokenUrl);
        provider.setXblAuthUrl(xblAuthUrl);
        provider.setXstsAuthUrl(xstsAuthUrl);
        provider.setMinecraftAuthUrl(minecraftAuthUrl);
        provider.setMinecraftProfileUrl(minecraftProfileUrl);
        provider.setSkinDomains(toSkinDomainsJson(definition.skinDomains));
        provider.setPublicKeyBase64(publicKeyBase64);
        provider.setPublicKeyFingerprint(fingerprint);
        provider.setCreatedAt(System.currentTimeMillis());
        provider.setManualEntry(false);
        return provider;
    }

    private static String toSkinDomainsJson(List<String> skinDomains) {
        if (skinDomains == null || skinDomains.isEmpty()) {
            return null;
        }
        JsonArray array = new JsonArray();
        for (String domain : skinDomains) {
            String normalized = trimToNull(domain);
            if (normalized != null) {
                array.add(new com.google.gson.JsonPrimitive(normalized));
            }
        }
        return array.size() > 0 ? array.toString() : null;
    }

    private static String deriveApiRoot(String authUrl, String accountUrl, String sessionUrl, String servicesUrl) {
        String normalizedServices = stripTrailingSlash(trimToNull(servicesUrl));
        if (normalizedServices != null) {
            if (normalizedServices.endsWith("/minecraftservices")) {
                return normalizedServices.substring(0, normalizedServices.length() - "/minecraftservices".length());
            }
            return normalizedServices;
        }

        String normalizedAuth = stripTrailingSlash(trimToNull(authUrl));
        if (normalizedAuth != null && normalizedAuth.endsWith("/authserver")) {
            return normalizedAuth.substring(0, normalizedAuth.length() - "/authserver".length());
        }

        String normalizedAccount = stripTrailingSlash(trimToNull(accountUrl));
        if (normalizedAccount != null && normalizedAccount.endsWith("/authserver")) {
            return normalizedAccount.substring(0, normalizedAccount.length() - "/authserver".length());
        }

        String normalizedSession = stripTrailingSlash(trimToNull(sessionUrl));
        if (normalizedSession != null && normalizedSession.endsWith("/sessionserver")) {
            return normalizedSession.substring(0, normalizedSession.length() - "/sessionserver".length());
        }

        return null;
    }

    private static String deriveAuthServerUrl(String authUrl, String accountUrl, String sessionUrl, String apiRoot) {
        String normalizedAuth = stripTrailingSlash(trimToNull(authUrl));
        String normalizedAccount = stripTrailingSlash(trimToNull(accountUrl));
        String normalizedSession = stripTrailingSlash(trimToNull(sessionUrl));
        String normalizedApiRoot = stripTrailingSlash(trimToNull(apiRoot));

        if (normalizedAuth != null) {
            return normalizedAuth;
        }
        if (isHost(normalizedAccount, "api.mojang.com") || isHost(normalizedSession, "sessionserver.mojang.com")) {
            return "https://authserver.mojang.com";
        }
        if (normalizedSession != null && normalizedSession.endsWith("/sessionserver")) {
            return normalizedSession.substring(0, normalizedSession.length() - "/sessionserver".length())
                + "/authserver";
        }
        if (normalizedAccount != null && normalizedAccount.endsWith("/authserver")) {
            return normalizedAccount;
        }
        if (normalizedApiRoot != null) {
            return normalizedApiRoot + "/authserver";
        }
        return normalizedAccount;
    }

    private static boolean isHost(String url, String expectedHost) {
        if (url == null) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            return host != null && host.equalsIgnoreCase(expectedHost);
        } catch (Exception e) {
            return false;
        }
    }

    private static ClientProvider copyProvider(ClientProvider provider) {
        if (provider == null) {
            return null;
        }
        ClientProvider copy = new ClientProvider();
        copy.setName(provider.getName());
        copy.setType(provider.getType());
        copy.setApiRoot(provider.getApiRoot());
        copy.setAuthServerUrl(provider.getAuthServerUrl());
        copy.setSessionServerUrl(provider.getSessionServerUrl());
        copy.setServicesUrl(provider.getServicesUrl());
        copy.setMsAuthorizeUrl(provider.getMsAuthorizeUrl());
        copy.setMsTokenUrl(provider.getMsTokenUrl());
        copy.setXblAuthUrl(provider.getXblAuthUrl());
        copy.setXstsAuthUrl(provider.getXstsAuthUrl());
        copy.setMinecraftAuthUrl(provider.getMinecraftAuthUrl());
        copy.setMinecraftProfileUrl(provider.getMinecraftProfileUrl());
        copy.setSkinDomains(provider.getSkinDomains());
        copy.setPublicKeyBase64(provider.getPublicKeyBase64());
        copy.setPublicKeyFingerprint(provider.getPublicKeyFingerprint());
        copy.setCreatedAt(provider.getCreatedAt());
        copy.setManualEntry(provider.isManualEntry());
        copy.setProxyEnabled(provider.isProxyEnabled());
        copy.setProxyType(provider.getProxyType());
        copy.setProxyHost(provider.getProxyHost());
        copy.setProxyPort(provider.getProxyPort());
        copy.setProxyUsername(provider.getProxyUsername());
        copy.setProxyPassword(provider.getProxyPassword());
        return copy;
    }

    private static DefaultProviderDefinition loadDefaultProviderDefinition(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) {
                WawelAuth.LOG
                    .warn("Skipping default provider '{}': root must be a JSON object", file.getAbsolutePath());
                return null;
            }
            return DefaultProviderDefinition.fromJson(root.getAsJsonObject());
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to read default provider '{}': {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private static File ensureDefaultProviderDirectory() {
        File root = Config.getLocalConfigDir();
        if (root == null) {
            root = Config.getConfigDir();
        }
        File directory = new File(root, DEFAULT_PROVIDER_DIR_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create default provider directory '{}'", directory.getAbsolutePath());
        }
        return directory;
    }

    private static void seedDefaultProviderDirectoryIfNeeded(File directory) {
        if (directory == null) {
            return;
        }
        for (Map.Entry<String, String> entry : SEEDED_DEFAULT_PROVIDER_RESOURCES.entrySet()) {
            File destination = new File(directory, entry.getKey());
            if (!destination.exists()) {
                copyBundledFile(entry.getValue(), destination);
            }
        }
    }

    private static void copyBundledFile(String resourcePath, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create default provider directory '{}'", parent.getAbsolutePath());
            return;
        }

        byte[] data = loadClasspathBytes(resourcePath);
        if (data == null) {
            WawelAuth.LOG.warn("Missing bundled default provider resource '{}'", resourcePath);
            return;
        }

        try {
            java.nio.file.Files.write(destination.toPath(), data);
        } catch (IOException e) {
            WawelAuth.LOG
                .warn("Failed to seed default provider file '{}': {}", destination.getAbsolutePath(), e.getMessage());
        }
    }

    private static byte[] loadClasspathBytes(String path) {
        try (InputStream in = ProviderRegistry.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to read bundled default provider resource '{}': {}", path, e.getMessage());
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

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
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

    private static final class DefaultProviderDefinition {

        private String name;
        private String apiRoot;
        private String accountUrl;
        private String authServerUrl;
        private String sessionServerUrl;
        private String servicesUrl;
        private Integer cacheTtlSeconds;
        private String msAuthorizeUrl;
        private String msTokenUrl;
        private String xblAuthUrl;
        private String xstsAuthUrl;
        private String minecraftAuthUrl;
        private String minecraftProfileUrl;
        private List<String> skinDomains;
        private String publicKeyBase64;
        private String publicKeyFingerprint;
        private String signaturePublickey;
        private String publicKey;

        private static DefaultProviderDefinition fromJson(JsonObject json) {
            DefaultProviderDefinition definition = new DefaultProviderDefinition();
            definition.name = optionalString(json, "name");
            definition.apiRoot = optionalString(json, "apiRoot");
            definition.accountUrl = optionalString(json, "accountUrl");
            definition.authServerUrl = optionalString(json, "authServerUrl");
            definition.sessionServerUrl = optionalString(json, "sessionServerUrl");
            definition.servicesUrl = optionalString(json, "servicesUrl");
            definition.cacheTtlSeconds = optionalInteger(json, "cacheTtlSeconds");
            definition.msAuthorizeUrl = optionalString(json, "msAuthorizeUrl");
            definition.msTokenUrl = optionalString(json, "msTokenUrl");
            definition.xblAuthUrl = optionalString(json, "xblAuthUrl");
            definition.xstsAuthUrl = optionalString(json, "xstsAuthUrl");
            definition.minecraftAuthUrl = optionalString(json, "minecraftAuthUrl");
            definition.minecraftProfileUrl = optionalString(json, "minecraftProfileUrl");
            definition.skinDomains = optionalStringList(json, "skinDomains");
            definition.publicKeyBase64 = optionalString(json, "publicKeyBase64");
            definition.publicKeyFingerprint = optionalString(json, "publicKeyFingerprint");
            definition.signaturePublickey = optionalString(json, "signaturePublickey");
            definition.publicKey = optionalString(json, "publicKey");
            return definition;
        }

        private static String optionalString(JsonObject json, String field) {
            if (json == null || field == null
                || !json.has(field)
                || json.get(field)
                    .isJsonNull()) {
                return null;
            }
            try {
                return json.get(field)
                    .getAsString();
            } catch (Exception e) {
                return null;
            }
        }

        private static List<String> optionalStringList(JsonObject json, String field) {
            if (json == null || field == null
                || !json.has(field)
                || json.get(field)
                    .isJsonNull()) {
                return Collections.emptyList();
            }
            try {
                JsonArray array = json.getAsJsonArray(field);
                List<String> values = new ArrayList<>();
                for (int i = 0; i < array.size(); i++) {
                    String value = trimToNull(
                        array.get(i)
                            .getAsString());
                    if (value != null) {
                        values.add(value);
                    }
                }
                return values;
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        private static Integer optionalInteger(JsonObject json, String field) {
            if (json == null || field == null
                || !json.has(field)
                || json.get(field)
                    .isJsonNull()) {
                return null;
            }
            try {
                return Integer.valueOf(
                    json.get(field)
                        .getAsInt());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
