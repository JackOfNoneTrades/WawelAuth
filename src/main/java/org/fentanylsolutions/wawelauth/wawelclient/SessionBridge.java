package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.IOException;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.mixins.early.minecraft.AccessorMinecraft;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilRequestException;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.properties.Property;

/**
 * Bridge between WawelAuth account system and vanilla auth/session.
 * Session swap, joinServer redirect, texture verification, domain whitelisting,
 * profile fetching from custom providers, launcher session auto-import.
 */
public class SessionBridge {

    private static final String MOJANG_PROVIDER_NAME = BuiltinProviders.MOJANG_PROVIDER_NAME;
    private static final String[] VANILLA_SKIN_DOMAINS = { ".minecraft.net", ".mojang.com" };
    private final YggdrasilHttpClient httpClient;
    private final ClientProviderDAO providerDAO;
    private final ClientAccountDAO accountDAO;
    private final AccountManager accountManager;
    private final ExecutorService profileFetchExecutor;

    /** Launcher's original session, captured at construction. */
    private final Session launcherSession;

    private volatile ClientAccount activeAccount;
    private volatile ClientProvider activeProvider;
    private volatile String lastActivationError;
    private volatile String connectedSessionServerBase;
    private volatile List<ClientProvider> trustedProviders = Collections.emptyList();
    private volatile List<PublicKey> trustedKeys = Collections.emptyList();
    private final ConcurrentHashMap<String, GameProfile> profileCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> profileFetchInFlight = new ConcurrentHashMap<>();
    private final ThreadLocal<ClientProvider> activeProviderContext = new ThreadLocal<>();

    public SessionBridge(YggdrasilHttpClient httpClient, ClientProviderDAO providerDAO, ClientAccountDAO accountDAO,
        AccountManager accountManager) {
        this(
            httpClient,
            providerDAO,
            accountDAO,
            accountManager,
            Minecraft.getMinecraft()
                .getSession());
    }

    SessionBridge(YggdrasilHttpClient httpClient, ClientProviderDAO providerDAO, ClientAccountDAO accountDAO,
        AccountManager accountManager, Session launcherSession) {
        this.httpClient = httpClient;
        this.providerDAO = providerDAO;
        this.accountDAO = accountDAO;
        this.accountManager = accountManager;
        this.launcherSession = launcherSession;
        this.profileFetchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WawelAuth-ProfileFetch");
            t.setDaemon(true);
            return t;
        });
    }

    public void setActiveProviderContext(ClientProvider provider) {
        if (provider == null) {
            activeProviderContext.remove();
        } else {
            activeProviderContext.set(provider);
        }
    }

    public void clearActiveProviderContext() {
        activeProviderContext.remove();
    }

    public ClientProvider getActiveProviderContext() {
        return activeProviderContext.get();
    }

    // =========================================================================
    // Account activation / session swap
    // =========================================================================

    /** Swap the MC session to this account and build trusted provider list. */
    public void activateAccount(long accountId) {
        clearActiveAccount();

        ClientAccount account = accountDAO.findById(accountId);
        if (account == null) {
            WawelAuth.LOG.warn("Cannot activate account {}: not found", accountId);
            this.lastActivationError = "Account not found";
            return;
        }
        if (account.getStatus() == AccountStatus.EXPIRED) {
            WawelAuth.LOG.warn(
                "Cannot activate account {} ({}): token expired, re-authentication required",
                accountId,
                account.getProfileName());
            this.lastActivationError = "Account '" + account.getProfileName()
                + "' has expired, re-authentication required";
            return;
        }

        ClientProvider provider = providerDAO.findByName(account.getProviderName());
        if (provider == null) {
            WawelAuth.LOG
                .warn("Cannot activate account {}: provider '{}' not found", accountId, account.getProviderName());
            this.lastActivationError = "Provider '" + account.getProviderName() + "' not found";
            return;
        }

        if (account.getProfileUuid() == null || account.getProfileName() == null) {
            WawelAuth.LOG.warn("Cannot activate account {}: no profile bound", accountId);
            this.lastActivationError = "Account has no profile bound";
            return;
        }

        String token = accountManager.getUsableAccessToken(account);
        boolean offlineAccount = BuiltinProviders.isOfflineProvider(provider.getName());

        // Build new session
        Session newSession = new Session(
            account.getProfileName(),
            account.getProfileUuid()
                .toString(),
            token,
            offlineAccount ? "legacy" : "mojang");

        // Swap session
        ((AccessorMinecraft) Minecraft.getMinecraft()).wawelauth$setSession(newSession);

        this.activeAccount = account;
        this.activeProvider = provider;
        this.lastActivationError = null;

        // Provisional trust set until live capabilities arrive
        buildTrustedProviders(provider);

        WawelAuth
            .debug("Activated account '" + account.getProfileName() + "' on provider '" + provider.getName() + "'");
    }

    /** Clear active account and restore launcher session. */
    public void clearActiveAccount() {
        this.activeAccount = null;
        this.activeProvider = null;
        this.lastActivationError = null;
        this.connectedSessionServerBase = null;
        this.trustedProviders = Collections.emptyList();
        this.trustedKeys = Collections.emptyList();
        this.profileCache.clear();
        this.profileFetchInFlight.clear();
        activeProviderContext.remove();
        ((AccessorMinecraft) Minecraft.getMinecraft()).wawelauth$setSession(launcherSession);
    }

    /** Clear cached profiles for this UUID so next fetch is fresh. */
    public void invalidateProfileCache(UUID profileId) {
        if (profileId == null) return;
        String fragment = "|" + UuidUtil.toUnsigned(profileId) + "|";
        profileCache.keySet()
            .removeIf(key -> key.contains(fragment));
    }

    /** Stop background threads. */
    public void shutdown() {
        profileFetchExecutor.shutdownNow();
    }

    public boolean hasActiveAccount() {
        return activeAccount != null && activeProvider != null;
    }

    /** Last activation error, or null. */
    public String getLastActivationError() {
        return lastActivationError;
    }

    public ClientProvider getActiveProvider() {
        return activeProvider;
    }

    public ClientAccount getActiveAccount() {
        return activeAccount;
    }

    /** Set trusted providers for the current connection. */
    public void setTrustedProviders(List<ClientProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            this.trustedProviders = Collections.emptyList();
            this.trustedKeys = Collections.emptyList();
            return;
        }

        this.trustedProviders = new ArrayList<>(providers);
        this.trustedKeys = parsePublicKeys(this.trustedProviders);
    }

    /**
     * Build trusted providers from live server capabilities.
     * Vanilla/unadvertised: active + Mojang. WA servers: only explicitly matched providers.
     */
    public void applyServerCapabilities(ServerCapabilities capabilities) {
        ClientProvider active = this.activeProvider;
        this.connectedSessionServerBase = resolveConnectedSessionServerBase(capabilities);
        setTrustedProviders(buildConnectionTrustedProviders(active, capabilities));
    }

    /**
     * Find a single local account matching server capabilities.
     * Returns account id if exactly one matches, -1 otherwise.
     */
    public long findSingleMatchingAccountId(ServerCapabilities capabilities) {
        if (capabilities == null) return -1L;

        String acceptedFingerprint = normalizeFingerprint(capabilities.getLocalAuthPublicKeyFingerprint());

        Set<String> acceptedAuthUrls = new HashSet<>();
        for (String url : capabilities.getAcceptedAuthServerUrls()) {
            String normalized = WawelPingPayload.normalizeUrl(url);
            if (normalized != null) {
                acceptedAuthUrls.add(normalized);
            }
        }

        Set<String> acceptedProviderNames = new HashSet<>();
        for (String providerName : capabilities.getAcceptedProviderNames()) {
            if (providerName != null && !providerName.trim()
                .isEmpty()) {
                acceptedProviderNames.add(
                    providerName.trim()
                        .toLowerCase());
            }
        }

        if (acceptedFingerprint == null && acceptedAuthUrls.isEmpty() && acceptedProviderNames.isEmpty()) {
            return -1L;
        }

        long selectedId = -1L;
        int matches = 0;

        for (ClientAccount account : accountDAO.listAll()) {
            // EXPIRED needs user action
            if (account.getStatus() == AccountStatus.EXPIRED) {
                continue;
            }

            ClientProvider provider = providerDAO.findByName(account.getProviderName());
            if (provider == null) {
                continue;
            }

            boolean matched = false;
            String providerAuthUrl = WawelPingPayload.normalizeUrl(provider.getAuthServerUrl());
            String providerFingerprint = normalizeFingerprint(provider.getPublicKeyFingerprint());

            if (acceptedFingerprint != null && acceptedFingerprint.equals(providerFingerprint)) {
                matched = true;
            } else if (providerAuthUrl != null && acceptedAuthUrls.contains(providerAuthUrl)) {
                matched = true;
            } else if (!acceptedProviderNames.isEmpty()) {
                // Compat for older payloads that only have names
                String providerName = provider.getName();
                if (providerName != null && acceptedProviderNames.contains(
                    providerName.trim()
                        .toLowerCase())) {
                    matched = true;
                }
            }

            if (!matched) {
                continue;
            }

            matches++;
            if (matches == 1) {
                selectedId = account.getId();
            } else {
                return -1L;
            }
        }

        return matches == 1 ? selectedId : -1L;
    }

    // =========================================================================
    // joinServer: redirect to active provider
    // =========================================================================

    /**
     * POST joinServer to the active provider's session URL.
     * Throws authlib exceptions matching vanilla's catch blocks.
     */
    public void joinServer(GameProfile profile, String token, String serverId) throws AuthenticationException {
        ClientProvider provider = this.activeProvider;
        if (provider == null) {
            throw new AuthenticationException("No active WawelAuth provider");
        }
        if (isOfflineProvider(provider)) {
            WawelAuth
                .debug("Offline account selected: skipping joinServer authentication for profile " + profile.getId());
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", token);
        body.addProperty("selectedProfile", UuidUtil.toUnsigned(profile.getId()));
        body.addProperty("serverId", serverId);

        try {
            httpClient.postJson(provider, provider.sessionUrl("/session/minecraft/join"), body);
            WawelAuth.debug("joinServer succeeded for provider '" + provider.getName() + "'");
        } catch (YggdrasilRequestException e) {
            // Local provider with disabled/unavailable API, let server-side verification handle it
            if (isLikelyLocalProvider(provider) && isLikelyDisabledLocalApiStatus(e.getHttpStatus())) {
                WawelAuth.debug(
                    "Local provider join endpoint returned HTTP " + e.getHttpStatus()
                        + ", continuing to server-side verification");
                return;
            }
            if (e.getHttpStatus() == 403) {
                throw new InvalidCredentialsException(e.getErrorMessage());
            }
            throw new AuthenticationException(e.getErrorMessage());
        } catch (IOException e) {
            if (isLikelyLocalProvider(provider)) {
                WawelAuth.debug(
                    "Local provider join endpoint unavailable (" + e.getClass()
                        .getSimpleName() + "), continuing to server-side verification");
                return;
            }
            throw new AuthenticationUnavailableException(e);
        }
    }

    // =========================================================================
    // Texture verification: connection-scoped
    // =========================================================================

    /** Trusted providers for the current connection. */
    public List<ClientProvider> getTrustedProviders() {
        return trustedProviders;
    }

    /** All trusted public keys for the current connection. */
    public List<PublicKey> getTrustedPublicKeys() {
        return trustedKeys;
    }

    /**
     * Keys for texture signature verification.
     * Provider context key + connection-trusted keys (WA servers often proxy Mojang textures).
     */
    public List<PublicKey> getTextureVerificationKeys() {
        ClientProvider provider = activeProviderContext.get();
        if (provider != null) {
            List<PublicKey> providerKeys = parsePublicKeys(Collections.singletonList(provider));
            if (trustedKeys.isEmpty()) {
                return providerKeys;
            }
            List<PublicKey> allKeys = new ArrayList<>(providerKeys);
            for (PublicKey key : trustedKeys) {
                if (!allKeys.contains(key)) {
                    allKeys.add(key);
                }
            }
            return allKeys;
        }
        return trustedKeys;
    }

    /** Always true, WA servers commonly proxy Mojang-signed textures. */
    public boolean isVanillaTextureTrustAllowed() {
        return true;
    }

    /** Check if a URL's domain is whitelisted by any trusted provider. */
    public boolean isWhitelistedDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return false;

            // Always allow vanilla Mojang domains
            for (String domain : VANILLA_SKIN_DOMAINS) {
                if (host.endsWith(domain)) return true;
            }

            // Check the active provider context
            ClientProvider provider = activeProviderContext.get();
            if (provider != null && isHostInProviderDomains(host, provider)) {
                return true;
            }

            // Check connection-trusted providers
            for (ClientProvider trusted : trustedProviders) {
                if (isHostInProviderDomains(host, trusted)) return true;
            }
        } catch (Exception e) {
            WawelAuth.debug("Error checking whitelisted domain for URL '" + url + "': " + e.getMessage());
        }
        return false;
    }

    private static boolean isHostInProviderDomains(String host, ClientProvider provider) {
        List<String> domains = provider.getSkinDomainList();
        for (String domain : domains) {
            if (domain.startsWith(".")) {
                if (host.endsWith(domain)) return true;
            } else {
                if (host.equals(domain)) return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Profile property fetching: active provider context
    // =========================================================================

    public GameProfile fillProfileFromProvider(ClientProvider provider, GameProfile profile, boolean requireSecure) {
        if (profile == null || profile.getId() == null || provider == null) {
            return null;
        }
        if (isOfflineProvider(provider)) {
            return profile;
        }
        return lookupProfileFromProvider(provider, profile, requireSecure);
    }

    public OfflineLocalSkin resolveOfflineLocalSkin(UUID profileId, ClientProvider provider) {
        if (profileId == null || accountDAO == null || provider == null) {
            return null;
        }
        if (!isOfflineProvider(provider)) {
            return null;
        }
        return toOfflineLocalSkin(
            accountDAO.findByProviderAndProfile(BuiltinProviders.OFFLINE_PROVIDER_NAME, profileId));
    }

    public org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel resolveOfflineLocalSkinModel(UUID profileId) {
        ClientProvider offlineProvider = null;
        if (accountDAO != null) {
            // Check if there's an offline account for this profile
            var account = accountDAO.findByProviderAndProfile(BuiltinProviders.OFFLINE_PROVIDER_NAME, profileId);
            if (account != null) {
                offlineProvider = new ClientProvider();
                offlineProvider.setName(BuiltinProviders.OFFLINE_PROVIDER_NAME);
            }
        }
        if (offlineProvider == null) return null;
        OfflineLocalSkin localSkin = resolveOfflineLocalSkin(profileId, offlineProvider);
        return localSkin != null && localSkin.getSkinPath() != null ? localSkin.getSkinModel() : null;
    }

    // =========================================================================
    // Auto-import launcher session
    // =========================================================================

    /** Auto-import launcher session as Mojang account if token is valid. Called once on start. */
    public void tryImportLauncherSession() {
        try {
            Session session = Minecraft.getMinecraft()
                .getSession();
            String token = session.getToken();

            // Skip invalid/dev sessions
            if (!isUsableLauncherSession(session)) {
                WawelAuth.debug("Launcher session token is not valid, skipping auto-import");
                return;
            }

            ClientProvider mojang = providerDAO.findByName(MOJANG_PROVIDER_NAME);
            if (mojang == null) {
                WawelAuth.debug("Mojang provider not found, skipping launcher session import");
                return;
            }

            // Validate token against Mojang
            JsonObject validateBody = new JsonObject();
            validateBody.addProperty("accessToken", token);

            try {
                httpClient.postJson(mojang, mojang.authUrl("/validate"), validateBody);
            } catch (YggdrasilRequestException e) {
                WawelAuth.debug("Launcher session token validation failed: " + e.getMessage());
                return;
            } catch (IOException e) {
                WawelAuth.debug("Could not reach Mojang for session validation: " + e.getMessage());
                return;
            }

            // Token is valid: import as account
            String playerIdStr = session.getPlayerID();
            UUID profileUuid;
            try {
                profileUuid = UUID.fromString(playerIdStr);
            } catch (IllegalArgumentException e) {
                // playerID might be unsigned
                profileUuid = UuidUtil.fromUnsigned(playerIdStr);
            }

            String username = session.getUsername();

            // Check if account already exists
            ClientAccount existing = accountDAO.findByProviderAndProfile(mojang.getName(), profileUuid);
            if (existing != null) {
                WawelAuth.debug("Mojang account '" + username + "' already exists, skipping import");
                return;
            }

            // Create new account
            long now = System.currentTimeMillis();
            ClientAccount account = new ClientAccount();
            account.setProviderName(mojang.getName());
            account.setUserUuid(UuidUtil.toUnsigned(profileUuid));
            account.setProfileUuid(profileUuid);
            account.setProfileName(username);
            account.setAccessToken(token);
            account.setClientToken(null);
            account.setStatus(AccountStatus.VALID);
            account.setConsecutiveFailures(0);
            account.setCreatedAt(now);
            account.setLastValidatedAt(now);
            account.setTokenIssuedAt(now);

            long id = accountDAO.create(account);
            account.setId(id);
            accountManager.cacheStatus(id, AccountStatus.VALID);

            WawelAuth.LOG.info("Auto-imported launcher session as Mojang account: {}", username);

        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to auto-import launcher session: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private void buildTrustedProviders(ClientProvider activeProvider) {
        List<ClientProvider> providers = new ArrayList<>();

        // Pre-capability: keep vanilla trust so main menu and normal servers work
        if (!isMojangProvider(activeProvider) && !isOfflineProvider(activeProvider)) {
            ClientProvider mojang = providerDAO.findByName(MOJANG_PROVIDER_NAME);
            if (mojang != null) {
                providers.add(mojang);
            }
        }

        providers.add(activeProvider);

        this.trustedProviders = providers;
        this.trustedKeys = parsePublicKeys(providers);
    }

    List<ClientProvider> buildConnectionTrustedProviders(ClientProvider activeProvider,
        ServerCapabilities capabilities) {
        List<ClientProvider> resolved = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        if (capabilities == null || !capabilities.isWawelAuthAdvertised()) {
            if (!isOfflineProvider(activeProvider)) {
                addTrustedProvider(resolved, seen, providerDAO.findByName(MOJANG_PROVIDER_NAME));
            }
            addTrustedProvider(resolved, seen, activeProvider);
        }

        for (ClientProvider provider : resolveTrustedProvidersFromCapabilities(capabilities)) {
            addTrustedProvider(resolved, seen, provider);
        }

        return resolved;
    }

    private static void addTrustedProvider(List<ClientProvider> providers, Set<String> seen, ClientProvider provider) {
        if (provider == null) return;

        String identity = providerIdentity(provider);
        if (!seen.add(identity)) {
            return;
        }
        providers.add(provider);
    }

    private static String providerIdentity(ClientProvider provider) {
        String name = provider.getName();
        if (name != null && !name.trim()
            .isEmpty()) {
            return "name:" + name.trim()
                .toLowerCase();
        }

        String authUrl = WawelPingPayload.normalizeUrl(provider.getAuthServerUrl());
        if (authUrl != null) {
            return "auth:" + authUrl.toLowerCase();
        }

        String fingerprint = normalizeFingerprint(provider.getPublicKeyFingerprint());
        if (fingerprint != null) {
            return "fp:" + fingerprint;
        }

        return "anon:" + System.identityHashCode(provider);
    }

    private List<ClientProvider> resolveTrustedProvidersFromCapabilities(ServerCapabilities capabilities) {
        if (capabilities == null) {
            return Collections.emptyList();
        }

        List<ClientProvider> resolved = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        Set<String> acceptedAuthUrls = new HashSet<>();
        Set<String> acceptedAuthHosts = new HashSet<>();
        for (String url : capabilities.getAcceptedAuthServerUrls()) {
            String normalized = WawelPingPayload.normalizeUrl(url);
            if (normalized != null) {
                acceptedAuthUrls.add(normalized);
                String host = extractHost(normalized);
                if (host != null) {
                    acceptedAuthHosts.add(host);
                }
            }
        }

        Set<String> acceptedProviderNames = new HashSet<>();
        for (String providerName : capabilities.getAcceptedProviderNames()) {
            if (providerName != null && !providerName.trim()
                .isEmpty()) {
                acceptedProviderNames.add(
                    providerName.trim()
                        .toLowerCase());
            }
        }

        String localFingerprint = normalizeFingerprint(capabilities.getLocalAuthPublicKeyFingerprint());

        if (providerDAO != null) {
            for (ClientProvider provider : providerDAO.listAll()) {
                String providerAuthUrl = WawelPingPayload.normalizeUrl(provider.getAuthServerUrl());
                String providerFingerprint = normalizeFingerprint(provider.getPublicKeyFingerprint());
                String providerName = provider.getName();
                boolean matchedByHost = matchesAnyAcceptedHost(acceptedAuthHosts, extractHost(providerAuthUrl))
                    || matchesAnyAcceptedHost(acceptedAuthHosts, extractHost(provider.getSessionServerUrl()))
                    || matchesAnyAcceptedHost(acceptedAuthHosts, extractHost(provider.getApiRoot()));
                boolean matchedByName = providerName != null && acceptedProviderNames.contains(
                    providerName.trim()
                        .toLowerCase());

                if ((providerAuthUrl != null && acceptedAuthUrls.contains(providerAuthUrl)) || matchedByHost
                    || (localFingerprint != null && localFingerprint.equals(providerFingerprint))
                    || matchedByName) {
                    addTrustedProvider(resolved, seen, provider);
                }
            }
        }

        addTrustedProvider(resolved, seen, buildEphemeralLocalProvider(capabilities));
        for (ClientProvider provider : buildEphemeralAcceptedProviders(capabilities)) {
            addTrustedProvider(resolved, seen, provider);
        }
        return resolved.isEmpty() ? Collections.emptyList() : resolved;
    }

    private static String resolveConnectedSessionServerBase(ServerCapabilities capabilities) {
        if (capabilities == null || !capabilities.isWawelAuthAdvertised() || !capabilities.isLocalAuthSupported()) {
            return null;
        }
        String apiRoot = WawelPingPayload.normalizeUrl(capabilities.getLocalAuthApiRoot());
        if (apiRoot == null) {
            return null;
        }
        return apiRoot + "/sessionserver";
    }

    private static String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.trim()
            .isEmpty()) return null;
        try {
            String host = new URI(rawUrl).getHost();
            if (host == null) return null;
            return host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean matchesAnyAcceptedHost(Set<String> acceptedHosts, String candidateHost) {
        if (candidateHost == null || acceptedHosts == null || acceptedHosts.isEmpty()) {
            return false;
        }
        for (String accepted : acceptedHosts) {
            if (hostsEquivalent(accepted, candidateHost)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hostsEquivalent(String leftHost, String rightHost) {
        if (leftHost == null || rightHost == null) return false;
        String left = leftHost.toLowerCase();
        String right = rightHost.toLowerCase();
        if (left.equals(right)) return true;

        // For IP literals require exact match.
        if (isIpLiteral(left) || isIpLiteral(right)) {
            return false;
        }

        String leftDomain = registrableDomain(left);
        String rightDomain = registrableDomain(right);
        return leftDomain != null && leftDomain.equals(rightDomain);
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("^[0-9.]+$") || host.contains(":");
    }

    private static String registrableDomain(String host) {
        String[] parts = host.split("\\.");
        if (parts.length < 2) {
            return host;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static ClientProvider buildEphemeralLocalProvider(ServerCapabilities capabilities) {
        if (capabilities == null || !capabilities.isLocalAuthSupported()) {
            return null;
        }

        String apiRoot = WawelPingPayload.normalizeUrl(capabilities.getLocalAuthApiRoot());
        if (apiRoot == null) {
            return null;
        }

        ClientProvider provider = new ClientProvider();
        String fingerprint = normalizeFingerprint(capabilities.getLocalAuthPublicKeyFingerprint());
        provider.setName("local@" + (fingerprint == null ? "runtime" : fingerprint));
        provider.setApiRoot(apiRoot);
        provider.setAuthServerUrl(apiRoot + "/authserver");
        provider.setSessionServerUrl(apiRoot + "/sessionserver");
        provider.setServicesUrl(apiRoot);
        provider.setPublicKeyFingerprint(fingerprint);

        String publicKeyBase64 = capabilities.getLocalAuthPublicKeyBase64();
        if (publicKeyBase64 != null) {
            String trimmed = publicKeyBase64.trim();
            if (!trimmed.isEmpty()) {
                provider.setPublicKeyBase64(trimmed);
            }
        }

        List<String> skinDomains = capabilities.getLocalAuthSkinDomains();
        if (skinDomains != null && !skinDomains.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String domain : skinDomains) {
                if (domain != null) {
                    String trimmed = domain.trim();
                    if (!trimmed.isEmpty()) {
                        arr.add(new JsonPrimitive(trimmed));
                    }
                }
            }
            provider.setSkinDomains(arr.toString());
        }

        return provider;
    }

    private static List<ClientProvider> buildEphemeralAcceptedProviders(ServerCapabilities capabilities) {
        if (capabilities == null || capabilities.getAcceptedProviders()
            .isEmpty()) {
            return Collections.emptyList();
        }

        List<ClientProvider> providers = new ArrayList<>();
        String localAuthUrl = null;
        String localApiRoot = WawelPingPayload.normalizeUrl(capabilities.getLocalAuthApiRoot());
        if (localApiRoot != null) {
            localAuthUrl = localApiRoot + "/authserver";
        }

        for (ServerCapabilities.AcceptedProviderDescriptor descriptor : capabilities.getAcceptedProviders()) {
            if (descriptor == null) {
                continue;
            }

            String authUrl = WawelPingPayload.normalizeUrl(descriptor.getAuthServerUrl());
            if (authUrl != null && authUrl.equals(localAuthUrl)) {
                continue;
            }

            ClientProvider provider = new ClientProvider();
            provider.setApiRoot(WawelPingPayload.normalizeUrl(descriptor.getApiRoot()));
            provider.setAuthServerUrl(authUrl);
            provider.setSessionServerUrl(WawelPingPayload.normalizeUrl(descriptor.getSessionServerUrl()));
            provider.setServicesUrl(WawelPingPayload.normalizeUrl(descriptor.getServicesUrl()));
            String publicKey = descriptor.getSignaturePublicKeyBase64();
            if (publicKey != null && !publicKey.trim()
                .isEmpty()) {
                provider.setPublicKeyBase64(publicKey.trim());
            }
            provider.setManualEntry(false);

            List<String> skinDomains = descriptor.getSkinDomains();
            if (skinDomains != null && !skinDomains.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String domain : skinDomains) {
                    if (domain == null) {
                        continue;
                    }
                    String trimmed = domain.trim();
                    if (!trimmed.isEmpty()) {
                        arr.add(new JsonPrimitive(trimmed));
                    }
                }
                if (arr.size() > 0) {
                    provider.setSkinDomains(arr.toString());
                }
            }

            providers.add(provider);
        }

        return providers;
    }

    private static List<PublicKey> parsePublicKeys(List<ClientProvider> providers) {
        List<PublicKey> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ClientProvider provider : providers) {
            String keyBase64 = provider.getPublicKeyBase64();
            if (keyBase64 == null || keyBase64.isEmpty()) continue;
            if (!seen.add(keyBase64)) continue;
            try {
                byte[] keyBytes = Base64.getDecoder()
                    .decode(keyBase64);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                keys.add(kf.generatePublic(spec));
            } catch (Exception e) {
                WawelAuth
                    .debug("Failed to parse public key for provider '" + provider.getName() + "': " + e.getMessage());
            }
        }
        return keys;
    }

    private static boolean isLikelyDisabledLocalApiStatus(int status) {
        return status == 400 || status == 404
            || status == 405
            || status == 500
            || status == 501
            || status == 502
            || status == 503;
    }

    private static boolean isLikelyLocalProvider(ClientProvider provider) {
        if (provider == null || provider.getApiRoot() == null) {
            return false;
        }
        try {
            URI uri = new URI(provider.getApiRoot());
            String host = uri.getHost();
            if (host == null) return false;
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                return true;
            }
            // RFC1918 / loopback ranges
            if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("127.")) {
                return true;
            }
            if (host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isMojangProvider(ClientProvider provider) {
        return provider != null && BuiltinProviders.isMojangProvider(provider.getName());
    }

    private static boolean isOfflineProvider(ClientProvider provider) {
        return provider != null && BuiltinProviders.isOfflineProvider(provider.getName());
    }

    private static OfflineLocalSkin toOfflineLocalSkin(ClientAccount account) {
        if (account == null || !BuiltinProviders.isOfflineProvider(account.getProviderName())) {
            return null;
        }

        String skinPath = trimToNull(account.getLocalSkinPath());
        String capePath = trimToNull(account.getLocalCapePath());
        if (skinPath == null && capePath == null) {
            return null;
        }

        return new OfflineLocalSkin(
            skinPath,
            account.getLocalSkinModel() != null ? account.getLocalSkinModel()
                : org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel.CLASSIC,
            capePath);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isUsableProfileProvider(ClientProvider provider) {
        return provider != null && !isOfflineProvider(provider);
    }

    private static boolean hasUsableProfileProvider(List<ClientProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return false;
        }
        for (ClientProvider provider : providers) {
            if (isUsableProfileProvider(provider)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMojangProvider(List<ClientProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return false;
        }
        for (ClientProvider provider : providers) {
            if (isMojangProvider(provider)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClientWorldLoaded() {
        try {
            return Minecraft.getMinecraft().theWorld != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isUsableLauncherSession(Session session) {
        if (session == null) {
            return false;
        }

        String token = session.getToken();
        return token != null && !token.isEmpty()
            && !"NotValid".equals(token)
            && !"0".equals(token)
            && !"FML".equals(token);
    }

    private static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null) return null;
        String trimmed = fingerprint.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private GameProfile lookupProfileFromSessionBase(String sessionBase, GameProfile profile, boolean requireSecure) {
        String cacheKey = buildProfileCacheKey("server:" + sessionBase, profile.getId(), requireSecure);
        GameProfile cached = profileCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (Minecraft.getMinecraft()
            .func_152345_ab()) {
            queueProfileFetchFromSessionBase(sessionBase, profile, requireSecure, cacheKey);
            return profile;
        }

        GameProfile fetched = fetchProfileFromSessionServer(sessionBase, profile, requireSecure);
        if (hasProperties(fetched)) {
            String altKey = buildProfileCacheKey("server:" + sessionBase, profile.getId(), !requireSecure);
            cacheFetchedProfile(cacheKey, altKey, fetched, requireSecure);
            return fetched;
        }

        return null;
    }

    private GameProfile lookupProfileFromProvider(ClientProvider provider, GameProfile profile, boolean requireSecure) {
        if (isOfflineProvider(provider)) {
            return profile;
        }
        String cacheKey = buildProfileCacheKey(provider, profile.getId(), requireSecure);
        GameProfile cached = profileCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (Minecraft.getMinecraft()
            .func_152345_ab()) { // Minecraft.isCallingFromMinecraftThread
            queueProfileFetch(provider, profile, requireSecure, cacheKey);
            return profile;
        }

        GameProfile fetched = fetchProfileFromProvider(provider, profile, requireSecure);
        if (hasProperties(fetched)) {
            String altKey = buildProfileCacheKey(provider, profile.getId(), !requireSecure);
            cacheFetchedProfile(cacheKey, altKey, fetched, requireSecure);
        }
        return fetched;
    }

    private void queueProfileFetch(ClientProvider provider, GameProfile profile, boolean requireSecure,
        String cacheKey) {
        if (profileFetchInFlight.putIfAbsent(cacheKey, Boolean.TRUE) != null) {
            return;
        }

        final UUID profileId = profile.getId();
        final String profileName = profile.getName();

        profileFetchExecutor.submit(() -> {
            try {
                GameProfile probe = new GameProfile(profileId, profileName);
                GameProfile fetched = fetchProfileFromProvider(provider, probe, requireSecure);
                if (hasProperties(fetched)) {
                    String altKey = buildProfileCacheKey(provider, profileId, !requireSecure);
                    cacheFetchedProfile(cacheKey, altKey, fetched, requireSecure);
                }
            } catch (Exception e) {
                WawelAuth.debug("Background profile fetch failed: " + e.getMessage());
            } finally {
                profileFetchInFlight.remove(cacheKey);
            }
        });
    }

    private void queueProfileFetchFromSessionBase(String sessionBase, GameProfile profile, boolean requireSecure,
        String cacheKey) {
        if (profileFetchInFlight.putIfAbsent(cacheKey, Boolean.TRUE) != null) {
            return;
        }

        final String sessionBaseSnapshot = sessionBase;
        final UUID profileId = profile.getId();
        final String profileName = profile.getName();

        profileFetchExecutor.submit(() -> {
            try {
                GameProfile probe = new GameProfile(profileId, profileName);
                GameProfile fetched = fetchProfileFromSessionServer(sessionBaseSnapshot, probe, requireSecure);
                if (hasProperties(fetched)) {
                    String altKey = buildProfileCacheKey("server:" + sessionBaseSnapshot, profileId, !requireSecure);
                    cacheFetchedProfile(cacheKey, altKey, fetched, requireSecure);
                }
            } catch (Exception e) {
                WawelAuth.debug("Background server profile fetch failed: " + e.getMessage());
            } finally {
                profileFetchInFlight.remove(cacheKey);
            }
        });
    }

    private static String buildProfileCacheKey(ClientProvider provider, UUID profileId, boolean requireSecure) {
        String providerKey = provider.getName();
        if (providerKey == null || providerKey.trim()
            .isEmpty()) {
            providerKey = WawelPingPayload.normalizeUrl(provider.getSessionServerUrl());
        }
        if (providerKey == null || providerKey.trim()
            .isEmpty()) {
            providerKey = "provider@" + System.identityHashCode(provider);
        }
        return buildProfileCacheKey("provider:" + providerKey, profileId, requireSecure);
    }

    private static String buildProfileCacheKey(String sourceKey, UUID profileId, boolean requireSecure) {
        return sourceKey + "|" + UuidUtil.toUnsigned(profileId) + "|" + (requireSecure ? "S" : "U");
    }

    static void putFetchedProfileInCache(java.util.Map<String, GameProfile> cache, String cacheKey, String altKey,
        GameProfile fetched, boolean requireSecure) {
        if (cache == null || cacheKey == null || !hasProperties(fetched)) {
            return;
        }

        cache.put(cacheKey, fetched);

        // Signed can satisfy unsigned lookups, not the other way around
        if (requireSecure && altKey != null) {
            cache.putIfAbsent(altKey, fetched);
        }
    }

    private void cacheFetchedProfile(String cacheKey, String altKey, GameProfile fetched, boolean requireSecure) {
        putFetchedProfileInCache(profileCache, cacheKey, altKey, fetched, requireSecure);
    }

    private static boolean hasProperties(GameProfile profile) {
        return profile != null && !profile.getProperties()
            .isEmpty();
    }

    public static final class OfflineLocalSkin {

        private final String skinPath;
        private final org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel skinModel;
        private final String capePath;

        private OfflineLocalSkin(String skinPath, org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel skinModel,
            String capePath) {
            this.skinPath = skinPath;
            this.skinModel = skinModel;
            this.capePath = capePath;
        }

        public String getSkinPath() {
            return skinPath;
        }

        public org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel getSkinModel() {
            return skinModel;
        }

        public String getCapePath() {
            return capePath;
        }
    }

    private GameProfile fetchProfileFromProvider(ClientProvider provider, GameProfile profile, boolean requireSecure) {
        if (isOfflineProvider(provider)) {
            return profile;
        }
        try {
            String uuid = UuidUtil.toUnsigned(profile.getId());
            String url = provider.sessionUrl("/session/minecraft/profile/" + uuid);
            if (requireSecure) {
                url += "?unsigned=false";
            }

            WawelAuth.debug(
                "Fetching profile properties for " + profile.getId()
                    + " from provider '"
                    + provider.getName()
                    + "' using proxy "
                    + org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport
                        .describeProxySettings(provider.getProxySettings()));
            JsonObject response = httpClient.getJson(provider, url);
            return buildProfileFromJson(profile, response);
        } catch (Exception e) {
            WawelAuth.debug(
                "Failed to fill profile properties from provider '" + provider.getName() + "': " + e.getMessage());
            if (MOJANG_PROVIDER_NAME.equals(provider.getName())) {
                return null; // Mojang: fall through to vanilla (same endpoint)
            }
            // Non-Mojang: re-throw so mixin sees failure and callers retry
            throw new RuntimeException("Provider profile fetch failed: " + provider.getName(), e);
        }
    }

    private GameProfile fetchProfileFromSessionServer(String sessionBase, GameProfile profile, boolean requireSecure) {
        try {
            String uuid = UuidUtil.toUnsigned(profile.getId());
            String base = WawelPingPayload.normalizeUrl(sessionBase);
            if (base == null) return null;

            String url = base + "/session/minecraft/profile/" + uuid;
            url += requireSecure ? "?unsigned=false&wawelauth_client=1" : "?wawelauth_client=1";

            JsonObject response = httpClient.getJson(url);
            return buildProfileFromJson(profile, response);
        } catch (Exception e) {
            WawelAuth.debug("Failed to fill profile properties from connected server: " + e.getMessage());
            return null;
        }
    }

    private static GameProfile buildProfileFromJson(GameProfile original, JsonObject response) {
        if (original == null || response == null) return null;

        UUID profileId = original.getId();
        String name = response.has("name") ? response.get("name")
            .getAsString() : original.getName();
        GameProfile filled = new GameProfile(profileId, name);

        if (response.has("properties") && response.get("properties")
            .isJsonArray()) {
            JsonArray props = response.getAsJsonArray("properties");
            for (JsonElement elem : props) {
                JsonObject prop = elem.getAsJsonObject();
                String propName = prop.get("name")
                    .getAsString();
                String propValue = prop.get("value")
                    .getAsString();
                if (prop.has("signature") && !prop.get("signature")
                    .isJsonNull()) {
                    filled.getProperties()
                        .put(
                            propName,
                            new Property(
                                propName,
                                propValue,
                                prop.get("signature")
                                    .getAsString()));
                } else {
                    filled.getProperties()
                        .put(propName, new Property(propName, propValue));
                }
            }
        }

        return filled;
    }
}
