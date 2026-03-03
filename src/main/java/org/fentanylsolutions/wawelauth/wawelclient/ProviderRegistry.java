package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilRequestException;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;

import com.google.gson.JsonObject;

/**
 * Manages authentication providers.
 *
 * Ensures the built-in Mojang provider always exists. Provides methods
 * to add custom providers (with ALI discovery) and remove them.
 */
public class ProviderRegistry {

    private static final String MOJANG_NAME = "Mojang";
    private static final String MOJANG_API_ROOT = "https://authserver.mojang.com";
    private static final String MOJANG_AUTH_URL = "https://authserver.mojang.com";
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com";
    private static final String MOJANG_SERVICES_URL = "https://api.minecraftservices.com";
    private static final String MOJANG_SKIN_DOMAINS = "[\"textures.minecraft.net\"]";

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
        if (providerDAO.findByName(MOJANG_NAME) == null) {
            ClientProvider mojang = new ClientProvider();
            mojang.setName(MOJANG_NAME);
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
}
