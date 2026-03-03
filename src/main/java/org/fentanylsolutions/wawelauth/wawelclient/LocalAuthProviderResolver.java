package org.fentanylsolutions.wawelauth.wawelclient;

import java.util.List;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

/**
 * Resolves/creates deterministic local-auth providers from live server
 * capability payloads.
 *
 * Identity is tied to public-key fingerprint, not server address.
 */
public final class LocalAuthProviderResolver {

    private final ClientProviderDAO providerDAO;

    public LocalAuthProviderResolver(ClientProviderDAO providerDAO) {
        this.providerDAO = providerDAO;
    }

    /**
     * Resolve or create the managed local provider for this server identity.
     *
     * Existing providers are reused by matching public key, regardless of name.
     */
    public ClientProvider resolveOrCreate(ServerCapabilities capabilities) {
        if (capabilities == null || !capabilities.isLocalAuthSupported()) {
            throw new IllegalArgumentException("Server does not advertise local auth support.");
        }

        String apiRoot = WawelPingPayload.normalizeUrl(capabilities.getLocalAuthApiRoot());
        String fingerprint = requiredFingerprint(capabilities);
        String publicKeyBase64 = normalizeString(capabilities.getLocalAuthPublicKeyBase64());

        if (apiRoot == null || fingerprint == null) {
            throw new IllegalArgumentException("Local auth metadata is incomplete (missing apiRoot or fingerprint).");
        }

        ClientProvider provider = findByPublicKey(publicKeyBase64, fingerprint);
        if (provider == null) {
            String providerName = resolveProviderName(fingerprint);
            provider = createProvider(providerName, apiRoot, fingerprint, publicKeyBase64, capabilities);
            providerDAO.create(provider);
            return provider;
        }

        boolean dirty = false;
        if (!apiRoot.equals(WawelPingPayload.normalizeUrl(provider.getApiRoot()))) {
            provider.setApiRoot(apiRoot);
            dirty = true;
        }
        String authUrl = apiRoot + "/authserver";
        if (!authUrl.equals(WawelPingPayload.normalizeUrl(provider.getAuthServerUrl()))) {
            provider.setAuthServerUrl(authUrl);
            dirty = true;
        }
        String sessionUrl = apiRoot + "/sessionserver";
        if (!sessionUrl.equals(WawelPingPayload.normalizeUrl(provider.getSessionServerUrl()))) {
            provider.setSessionServerUrl(sessionUrl);
            dirty = true;
        }
        String servicesUrl = apiRoot;
        if (!servicesUrl.equals(WawelPingPayload.normalizeUrl(provider.getServicesUrl()))) {
            provider.setServicesUrl(servicesUrl);
            dirty = true;
        }

        if (!fingerprint.equals(normalizeFingerprint(provider.getPublicKeyFingerprint()))) {
            provider.setPublicKeyFingerprint(fingerprint);
            dirty = true;
        }
        if (publicKeyBase64 != null && !publicKeyBase64.equals(normalizeString(provider.getPublicKeyBase64()))) {
            provider.setPublicKeyBase64(publicKeyBase64);
            dirty = true;
        }

        String skinDomainsJson = skinDomainsToJson(capabilities.getLocalAuthSkinDomains());
        if (!skinDomainsJson.equals(normalizeString(provider.getSkinDomains()))) {
            provider.setSkinDomains(skinDomainsJson);
            dirty = true;
        }

        if (dirty) {
            providerDAO.update(provider);
        }
        return provider;
    }

    public ClientProvider findExisting(ServerCapabilities capabilities) {
        if (capabilities == null || !capabilities.isLocalAuthSupported()) {
            return null;
        }

        String fingerprint = requiredFingerprint(capabilities);
        if (fingerprint == null) {
            return null;
        }

        String publicKeyBase64 = normalizeString(capabilities.getLocalAuthPublicKeyBase64());
        return findByPublicKey(publicKeyBase64, fingerprint);
    }

    private ClientProvider findByPublicKey(String publicKeyBase64, String fingerprint) {
        for (ClientProvider provider : providerDAO.listAll()) {
            if (publicKeyBase64 != null && publicKeyBase64.equals(normalizeString(provider.getPublicKeyBase64()))) {
                return provider;
            }
            if (fingerprint.equals(normalizeFingerprint(provider.getPublicKeyFingerprint()))) {
                return provider;
            }
        }
        return null;
    }

    private String resolveProviderName(String fingerprint) {
        String suffix = fingerprint.length() > 12 ? fingerprint.substring(0, 12) : fingerprint;
        String baseName = "LocalAuth-" + suffix;

        for (int i = 1; i < 200; i++) {
            String candidate = i == 1 ? baseName : baseName + "-" + i;
            if (providerDAO.findByName(candidate) == null) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not allocate local provider name for fingerprint.");
    }

    private static ClientProvider createProvider(String providerName, String apiRoot, String fingerprint,
        String publicKeyBase64, ServerCapabilities capabilities) {
        ClientProvider provider = new ClientProvider();
        provider.setName(providerName);
        provider.setType(ProviderType.CUSTOM);
        provider.setApiRoot(apiRoot);
        provider.setAuthServerUrl(apiRoot + "/authserver");
        provider.setSessionServerUrl(apiRoot + "/sessionserver");
        provider.setServicesUrl(apiRoot);
        provider.setSkinDomains(skinDomainsToJson(capabilities.getLocalAuthSkinDomains()));
        provider.setPublicKeyFingerprint(fingerprint);
        provider.setPublicKeyBase64(publicKeyBase64);
        provider.setCreatedAt(System.currentTimeMillis());
        return provider;
    }

    private static String skinDomainsToJson(List<String> domains) {
        JsonArray array = new JsonArray();
        if (domains != null) {
            for (String domain : domains) {
                String normalized = normalizeString(domain);
                if (normalized != null) {
                    array.add(new JsonPrimitive(normalized));
                }
            }
        }
        return array.toString();
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

    private static String requiredFingerprint(ServerCapabilities capabilities) {
        if (capabilities == null) {
            return null;
        }
        return normalizeFingerprint(capabilities.getLocalAuthPublicKeyFingerprint());
    }
}
