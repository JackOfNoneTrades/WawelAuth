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
import java.util.function.Supplier;

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
 * Coordinates between the WawelAuth client account system and
 * vanilla Minecraft's authentication/session infrastructure.
 * <p>
 * Handles: session swapping, joinServer redirection, connection-scoped
 * texture signature verification, skin domain whitelisting, profile
 * property fetching from custom providers, and auto-import of the
 * launcher session.
 */
public class SessionBridge {

    private static final String MOJANG_PROVIDER_NAME = BuiltinProviders.MOJANG_PROVIDER_NAME;
    private static final String[] VANILLA_SKIN_DOMAINS = { ".minecraft.net", ".mojang.com" };
    private static final long PING_PROFILE_CONTEXT_TTL_MS = 30_000L;
    private final YggdrasilHttpClient httpClient;
    private final ClientProviderDAO providerDAO;
    private final ClientAccountDAO accountDAO;
    private final AccountManager accountManager;
    private final ExecutorService profileFetchExecutor;

    /**
     * The launcher's original session, captured at construction.
     */
    private final Session launcherSession;

    private volatile ClientAccount activeAccount;
    private volatile ClientProvider activeProvider;
    private volatile String lastActivationError;
    private volatile String connectedSessionServerBase;
    private volatile List<ClientProvider> trustedProviders = Collections.emptyList();
    private volatile List<PublicKey> trustedKeys = Collections.emptyList();
    private final ConcurrentHashMap<String, GameProfile> profileCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> profileFetchInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PingProfileContext> pingProfileContexts = new ConcurrentHashMap<>();
    private final ThreadLocal<LookupContext> activeLookupContext = new ThreadLocal<>();

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

    // =========================================================================
    // Account activation / session swap
    // =========================================================================

    /**
     * Swap the Minecraft session to the given account.
     * Builds the trusted provider list for the current connection.
     */
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

        // Build a provisional trust set until live server capabilities arrive.
        buildTrustedProviders(provider);

        WawelAuth
            .debug("Activated account '" + account.getProfileName() + "' on provider '" + provider.getName() + "'");
    }

    /**
     * Clear active account state and restore the launcher's original session.
     */
    public void clearActiveAccount() {
        this.activeAccount = null;
        this.activeProvider = null;
        this.lastActivationError = null;
        this.connectedSessionServerBase = null;
        this.trustedProviders = Collections.emptyList();
        this.trustedKeys = Collections.emptyList();
        this.profileCache.clear();
        this.profileFetchInFlight.clear();
        activeLookupContext.remove();
        ((AccessorMinecraft) Minecraft.getMinecraft()).wawelauth$setSession(launcherSession);
    }

    /**
     * Remove all cached profile entries for the given UUID so the next
     * {@link #fillProfileFromProvider} call fetches a fresh profile from
     * the server. Useful after skin/cape uploads.
     */
    public void invalidateProfileCache(UUID profileId) {
        if (profileId == null) return;
        String fragment = "|" + UuidUtil.toUnsigned(profileId) + "|";
        profileCache.keySet()
            .removeIf(key -> key.contains(fragment));
    }

    /**
     * Track ping-sampled profiles so menu-time lookups can use the hovered
     * server's advertised auth context instead of global provider state.
     */
    public void rememberPingProfiles(ServerCapabilities capabilities, GameProfile[] profiles) {
        if (profiles == null || profiles.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        PingProfileContext context = new PingProfileContext(capabilities, now);
        for (GameProfile profile : profiles) {
            if (profile == null || profile.getId() == null) {
                continue;
            }
            pingProfileContexts.put(profile.getId(), context);
        }
        purgeExpiredPingProfileContexts(now);
    }

    /**
     * Build a request-scoped lookup context for one concrete provider.
     */
    public LookupContext createProviderLookupContext(ClientProvider provider, boolean allowVanillaFallback) {
        if (provider == null) {
            return new LookupContext(null, null, Collections.emptyList(), allowVanillaFallback);
        }
        if (isOfflineProvider(provider)) {
            return new LookupContext(null, provider, Collections.singletonList(provider), false);
        }
        return new LookupContext(null, provider, Collections.singletonList(provider), allowVanillaFallback);
    }

    /**
     * Build a request-scoped lookup context for a configured provider by name.
     */
    public LookupContext createProviderLookupContext(String providerName, boolean allowVanillaFallback) {
        if (providerName == null || providerName.trim()
            .isEmpty() || providerDAO == null) {
            return new LookupContext(null, null, Collections.emptyList(), allowVanillaFallback);
        }
        return createProviderLookupContext(providerDAO.findByName(providerName), allowVanillaFallback);
    }

    /**
     * Build a request-scoped lookup context from one server entry's capabilities.
     */
    public LookupContext createServerLookupContext(ServerCapabilities capabilities) {
        return createLookupContextFromCapabilities(capabilities, true);
    }

    /**
     * Build a request-scoped lookup context from one server entry's capabilities,
     * optionally allowing Mojang fallback when the server auth is unknown.
     */
    public LookupContext createServerLookupContext(ServerCapabilities capabilities, boolean allowVanillaFallback) {
        return createLookupContextFromCapabilities(capabilities, allowVanillaFallback);
    }

    /**
     * Run a lookup with a request-scoped provider/session context.
     */
    public <T> T withLookupContext(LookupContext context, Supplier<T> action) {
        LookupContext previous = activeLookupContext.get();
        if (context == null) {
            activeLookupContext.remove();
        } else {
            activeLookupContext.set(context);
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                activeLookupContext.remove();
            } else {
                activeLookupContext.set(previous);
            }
        }
    }

    /**
     * Check whether any WawelAuth provider can resolve this profile.
     * Returns true if: connected in-world, or ping context exists,
     * or a locally stored account matches.
     */
    public boolean hasProviderForProfile(UUID profileId) {
        return hasProviderForProfile(profileId, null);
    }

    /**
     * Check whether any WawelAuth provider can resolve this profile in the
     * given request context.
     */
    public boolean hasProviderForProfile(UUID profileId, LookupContext context) {
        if (profileId == null) return false;

        if (context != null) {
            return context.sessionBase != null || isUsableProfileProvider(context.provider)
                || hasUsableProfileProvider(context.trustedProviders);
        }

        if (Minecraft.getMinecraft().theWorld != null) {
            return connectedSessionServerBase != null || isUsableProfileProvider(activeProvider);
        }

        if (resolvePingProfileContext(profileId) != null) {
            return true;
        }

        return isUsableProfileProvider(resolveProviderForProfile(profileId));
    }

    boolean hasFreshPingProfileContext(UUID profileId, long nowMs) {
        return resolvePingProfileContext(profileId, nowMs) != null;
    }

    /**
     * Stop background resources used by session/profile bridging.
     */
    public void shutdown() {
        profileFetchExecutor.shutdownNow();
    }

    public boolean hasActiveAccount() {
        return activeAccount != null && activeProvider != null;
    }

    /**
     * Returns the reason the last {@link #activateAccount} call failed, or null.
     */
    public String getLastActivationError() {
        return lastActivationError;
    }

    public ClientProvider getActiveProvider() {
        return activeProvider;
    }

    public ClientAccount getActiveAccount() {
        return activeAccount;
    }

    /**
     * Set trusted providers for the current connection.
     * Called by capability detection (Step 6/7) with server-accepted providers.
     */
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
     * Build connection-trusted providers from live server capabilities.
     * <p>
     * For unadvertised/vanilla servers, keep the pre-capability trust model:
     * active provider plus Mojang. For advertised WawelAuth servers, trust only
     * providers explicitly matched by the server's advertised policy.
     */
    public void applyServerCapabilities(ServerCapabilities capabilities) {
        ClientProvider active = this.activeProvider;
        this.connectedSessionServerBase = resolveConnectedSessionServerBase(capabilities);
        setTrustedProviders(buildConnectionTrustedProviders(active, capabilities));
    }

    /**
     * Resolve a single account candidate based on runtime server capabilities.
     * <p>
     * Returns:
     * - account id, if exactly one local account matches server-accepted auth endpoints
     * - -1, if none or multiple match
     * <p>
     * Does not persist anything to ServerData/NBT.
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
            // EXPIRED always requires user action.
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
                // Backward-compat fallback for older payloads that only expose names.
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
     * Throws authlib exception types matching vanilla's catch blocks in
     * NetHandlerLoginClient.handleEncryptionRequest.
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
            // If the selected provider is local and its API module is disabled/unavailable,
            // don't surface "auth servers down" from the client path. Continue so the
            // dedicated server performs its normal username verification and rejects with
            // "Failed to verify username!".
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

    /**
     * Get all trusted public keys for the current connection.
     */
    public List<PublicKey> getTrustedPublicKeys() {
        return trustedKeys;
    }

    /**
     * Public keys used for texture signature verification.
     * <p>
     * During an active server session, uses connection-trusted keys only.
     * Outside session context (menus/tooltips), uses a profile-scoped trust
     * source instead of trusting every configured provider globally.
     */
    public List<PublicKey> getTextureVerificationKeys() {
        return getTextureVerificationKeys(null);
    }

    /**
     * Public keys used for texture signature verification for one profile.
     * <p>
     * In-world: use connection-scoped keys.
     * Menus/UI: use only the profile's trusted source (ping-advertised auth or
     * locally known account provider), not all configured providers globally.
     */
    public List<PublicKey> getTextureVerificationKeys(GameProfile profile) {
        List<ClientProvider> providers = resolveTextureProviders(profile != null ? profile.getId() : null);
        if (providers == null || providers.isEmpty()) {
            return Collections.emptyList();
        }
        return parsePublicKeys(providers);
    }

    public boolean isVanillaTextureTrustAllowed(GameProfile profile) {
        return isVanillaTextureTrustAllowed(profile != null ? profile.getId() : null);
    }

    boolean isVanillaTextureTrustAllowed(UUID profileId) {
        if (isClientWorldLoaded()) {
            return containsMojangProvider(trustedProviders);
        }

        LookupContext requestContext = activeLookupContext.get();
        if (requestContext != null) {
            return requestContext.allowVanillaFallback && containsMojangProvider(requestContext.trustedProviders);
        }

        MenuProfileLookupContext menuLookup = resolveMenuProfileLookup(profileId);
        if (menuLookup != null) {
            return menuLookup.isVanillaFallbackAllowed() && containsMojangProvider(menuLookup.getTrustedProviders());
        }

        ClientProvider provider = resolveProviderForProfile(profileId);
        if (provider != null) {
            return isMojangProvider(provider);
        }

        return true;
    }

    /**
     * Check if a URL's domain is whitelisted by any trusted provider
     * for the current connection. Vanilla Mojang domains are accepted only
     * when the current lookup context allows vanilla fallback.
     */
    public boolean isWhitelistedDomain(String url) {
        return isWhitelistedDomain(url, null);
    }

    /**
     * Check if a URL's domain is whitelisted for one profile's texture context.
     */
    public boolean isWhitelistedDomain(String url, GameProfile profile) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return false;

            if (isVanillaTextureTrustAllowed(profile != null ? profile.getId() : null)) {
                for (String domain : VANILLA_SKIN_DOMAINS) {
                    if (host.endsWith(domain)) return true;
                }
            }

            List<ClientProvider> providers = resolveTextureProviders(profile != null ? profile.getId() : null);
            if (providers == null || providers.isEmpty()) {
                return false;
            }

            // Check provider skin domains scoped to the current lookup context.
            for (ClientProvider provider : providers) {
                List<String> domains = provider.getSkinDomainList();
                for (String domain : domains) {
                    if (domain.startsWith(".")) {
                        if (host.endsWith(domain)) return true;
                    } else {
                        if (host.equals(domain)) return true;
                    }
                }
            }
        } catch (Exception e) {
            WawelAuth.debug("Error checking whitelisted domain for URL '" + url + "': " + e.getMessage());
        }
        return false;
    }

    // =========================================================================
    // Profile property fetching: active provider context
    // =========================================================================

    /**
     * Fetch profile properties from the active provider's session server.
     * Uses connection-scoped auth while in-world. In menus/UI, can also use
     * ping-advertised server auth for sampled player profiles before falling
     * back to locally known account providers.
     * <p>
     * Returns null only when no suitable provider can be determined
     * (caller should fall through to vanilla).
     */
    public GameProfile fillProfileFromProvider(GameProfile profile, boolean requireSecure) {
        if (profile == null || profile.getId() == null) {
            return null;
        }

        String connectedSessionBase = this.connectedSessionServerBase;
        if (connectedSessionBase != null && Minecraft.getMinecraft().theWorld != null) {
            GameProfile fetchedFromServer = lookupProfileFromSessionBase(connectedSessionBase, profile, requireSecure);
            if (fetchedFromServer != null) {
                return fetchedFromServer;
            }
        }

        if (Minecraft.getMinecraft().theWorld == null) {
            LookupContext requestContext = activeLookupContext.get();
            if (requestContext != null) {
                GameProfile requestedProfile = lookupProfileFromMenuContext(requestContext, profile, requireSecure);
                if (requestedProfile != null) {
                    return requestedProfile;
                }
                return requestContext.allowVanillaFallback ? null : profile;
            }

            MenuProfileLookupContext menuLookup = resolveMenuProfileLookup(profile.getId());
            if (menuLookup != null) {
                GameProfile menuProfile = lookupProfileFromMenuContext(menuLookup, profile, requireSecure);
                if (menuProfile != null) {
                    return menuProfile;
                }
                return menuLookup.isVanillaFallbackAllowed() ? null : profile;
            }
        }

        ClientProvider provider = this.activeProvider;
        // Only use activeProvider when actually connected to a server.
        // After disconnect, activeProvider may still be set (stale); fall
        // through to per-profile resolution so each account uses its own provider.
        if (provider != null && Minecraft.getMinecraft().theWorld == null) {
            provider = null;
        }
        if (provider == null) {
            provider = resolveProviderForProfile(profile != null ? profile.getId() : null);
        }
        if (provider == null) {
            return null;
        }
        if (isOfflineProvider(provider)) {
            return profile;
        }

        return lookupProfileFromProvider(provider, profile, requireSecure);
    }

    public GameProfile fillProfileFromProvider(ClientProvider provider, GameProfile profile, boolean requireSecure) {
        if (profile == null || profile.getId() == null || provider == null) {
            return null;
        }
        if (isOfflineProvider(provider)) {
            return profile;
        }
        return lookupProfileFromProvider(provider, profile, requireSecure);
    }

    public ClientProvider resolveTextureDownloadProvider(UUID profileId) {
        return resolveTextureDownloadProvider(profileId, activeLookupContext.get());
    }

    public ClientProvider resolveTextureDownloadProvider(UUID profileId, LookupContext lookupContext) {
        if (lookupContext != null) {
            ClientProvider direct = lookupContext.getProvider();
            if (isUsableProfileProvider(direct)) {
                return direct;
            }
            return selectSingleUsableProvider(lookupContext.getTrustedProviders());
        }

        if (isClientWorldLoaded()) {
            if (isUsableProfileProvider(activeProvider)) {
                return activeProvider;
            }
            return selectSingleUsableProvider(trustedProviders);
        }

        MenuProfileLookupContext menuLookup = resolveMenuProfileLookup(profileId);
        if (menuLookup != null) {
            ClientProvider direct = menuLookup.getProvider();
            if (isUsableProfileProvider(direct)) {
                return direct;
            }
            return selectSingleUsableProvider(menuLookup.getTrustedProviders());
        }

        return resolveUniqueLocalProviderForProfile(profileId);
    }

    public OfflineLocalSkin resolveOfflineLocalSkin(UUID profileId) {
        return resolveOfflineLocalSkin(profileId, activeLookupContext.get());
    }

    public OfflineLocalSkin resolveOfflineLocalSkin(UUID profileId, LookupContext lookupContext) {
        if (profileId == null || accountDAO == null) {
            return null;
        }

        if (lookupContext != null) {
            ClientProvider direct = lookupContext.getProvider();
            if (isOfflineProvider(direct)) {
                return toOfflineLocalSkin(
                    accountDAO.findByProviderAndProfile(BuiltinProviders.OFFLINE_PROVIDER_NAME, profileId));
            }
            return null;
        }

        if (isClientWorldLoaded()) {
            if (isOfflineProvider(activeProvider) && activeAccount != null
                && profileId.equals(activeAccount.getProfileUuid())) {
                return toOfflineLocalSkin(activeAccount);
            }
            return null;
        }

        return resolveUniqueOfflineLocalSkin(profileId);
    }

    public org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel resolveOfflineLocalSkinModel(UUID profileId) {
        OfflineLocalSkin localSkin = resolveOfflineLocalSkin(profileId);
        return localSkin != null && localSkin.getSkinPath() != null ? localSkin.getSkinModel() : null;
    }

    private ClientProvider resolveProviderForProfile(UUID profileId) {
        if (profileId == null || accountDAO == null || providerDAO == null) {
            return null;
        }

        List<ClientAccount> accounts = accountDAO.listAll();
        for (ClientAccount account : accounts) {
            if (account == null || account.getProfileUuid() == null) {
                continue;
            }
            if (!profileId.equals(account.getProfileUuid())) {
                continue;
            }
            String providerName = account.getProviderName();
            if (providerName == null || providerName.trim()
                .isEmpty()) {
                continue;
            }
            ClientProvider provider = providerDAO.findByName(providerName);
            if (provider != null) {
                return provider;
            }
        }
        return null;
    }

    private ClientProvider resolveUniqueLocalProviderForProfile(UUID profileId) {
        if (profileId == null || accountDAO == null || providerDAO == null) {
            return null;
        }

        ClientProvider resolved = null;
        String resolvedIdentity = null;

        for (ClientAccount account : accountDAO.listAll()) {
            if (account == null || account.getProfileUuid() == null || !profileId.equals(account.getProfileUuid())) {
                continue;
            }

            String providerName = account.getProviderName();
            if (providerName == null || providerName.trim()
                .isEmpty()) {
                continue;
            }

            ClientProvider provider = providerDAO.findByName(providerName);
            if (!isUsableProfileProvider(provider)) {
                continue;
            }

            String identity = providerIdentity(provider);
            if (resolved == null) {
                resolved = provider;
                resolvedIdentity = identity;
                continue;
            }

            if (!resolvedIdentity.equals(identity)) {
                return null;
            }
        }

        return resolved;
    }

    private OfflineLocalSkin resolveUniqueOfflineLocalSkin(UUID profileId) {
        OfflineLocalSkin resolved = null;

        for (ClientAccount account : accountDAO.listAll()) {
            if (account == null || account.getProfileUuid() == null || !profileId.equals(account.getProfileUuid())) {
                continue;
            }

            String providerName = account.getProviderName();
            if (providerName == null || providerName.trim()
                .isEmpty()) {
                return null;
            }

            if (!BuiltinProviders.isOfflineProvider(providerName)) {
                return null;
            }

            OfflineLocalSkin candidate = toOfflineLocalSkin(account);
            if (candidate == null) {
                return null;
            }

            if (resolved != null) {
                return null;
            }
            resolved = candidate;
        }

        return resolved;
    }

    private MenuProfileLookupContext resolveMenuProfileLookup(UUID profileId) {
        PingProfileContext pingContext = resolvePingProfileContext(profileId);
        if (pingContext == null) {
            return null;
        }

        return createLookupContextFromCapabilities(pingContext.capabilities);
    }

    private List<ClientProvider> resolveTextureProviders(UUID profileId) {
        if (Minecraft.getMinecraft().theWorld != null) {
            return trustedProviders != null ? trustedProviders : Collections.emptyList();
        }

        LookupContext requestContext = activeLookupContext.get();
        if (requestContext != null) {
            return requestContext.trustedProviders != null ? requestContext.trustedProviders : Collections.emptyList();
        }

        MenuProfileLookupContext menuLookup = resolveMenuProfileLookup(profileId);
        if (menuLookup != null && !menuLookup.getTrustedProviders()
            .isEmpty()) {
            return menuLookup.getTrustedProviders();
        }

        ClientProvider provider = resolveProviderForProfile(profileId);
        if (provider != null) {
            return Collections.singletonList(provider);
        }

        // Keep Mojang's verification context available even without a usable
        // launcher session. When TextureRequest allows vanilla fallback, authlib
        // can still resolve public profile textures from the client's default
        // session service, and those textures must pass signature/domain checks.
        ClientProvider mojang = providerDAO.findByName(MOJANG_PROVIDER_NAME);
        if (mojang != null) {
            return Collections.singletonList(mojang);
        }

        return Collections.emptyList();
    }

    private MenuProfileLookupContext createLookupContextFromCapabilities(ServerCapabilities capabilities) {
        return createLookupContextFromCapabilities(capabilities, true);
    }

    private MenuProfileLookupContext createLookupContextFromCapabilities(ServerCapabilities capabilities,
        boolean allowVanillaFallback) {
        List<ClientProvider> trusted = new ArrayList<>(resolveTrustedProvidersFromCapabilities(capabilities));
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ClientProvider provider : trusted) {
            seen.add(providerIdentity(provider));
        }

        String sessionBase = resolveConnectedSessionServerBase(capabilities);
        if (sessionBase != null) {
            return new MenuProfileLookupContext(sessionBase, null, trusted, false);
        }

        boolean allowVanilla = allowVanillaFallback && (capabilities == null || !capabilities.isWawelAuthAdvertised());
        if (allowVanilla && providerDAO != null) {
            addTrustedProvider(trusted, seen, providerDAO.findByName(MOJANG_PROVIDER_NAME));
        }

        if (trusted.size() == 1) {
            ClientProvider provider = trusted.get(0);
            boolean allowSingleProviderVanilla = allowVanilla && isMojangProvider(provider);
            return new MenuProfileLookupContext(null, provider, trusted, allowSingleProviderVanilla);
        }

        return new MenuProfileLookupContext(null, null, trusted, allowVanilla);
    }

    // =========================================================================
    // Auto-import launcher session
    // =========================================================================

    /**
     * Inspect the launcher's Minecraft session and auto-import it as a
     * WawelAuth account under the Mojang provider if the token is valid.
     * Called once from WawelClient.start().
     */
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

        // Before capability detection runs, keep vanilla-compatible trust so
        // normal servers and the main menu still behave like stock authlib.
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
            // RFC1918 / loopback v4 ranges often used for local/LAN auth hosts.
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

    private static ClientProvider selectSingleUsableProvider(List<ClientProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }

        ClientProvider resolved = null;
        String resolvedIdentity = null;
        for (ClientProvider provider : providers) {
            if (!isUsableProfileProvider(provider)) {
                continue;
            }

            String identity = providerIdentity(provider);
            if (resolved == null) {
                resolved = provider;
                resolvedIdentity = identity;
                continue;
            }

            if (!resolvedIdentity.equals(identity)) {
                return null;
            }
        }

        return resolved;
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

    private PingProfileContext resolvePingProfileContext(UUID profileId) {
        return resolvePingProfileContext(profileId, System.currentTimeMillis());
    }

    private PingProfileContext resolvePingProfileContext(UUID profileId, long nowMs) {
        if (profileId == null) {
            return null;
        }

        PingProfileContext context = pingProfileContexts.get(profileId);
        if (context == null) {
            return null;
        }
        if (nowMs - context.capturedAtMs > PING_PROFILE_CONTEXT_TTL_MS) {
            pingProfileContexts.remove(profileId, context);
            return null;
        }
        return context;
    }

    private void purgeExpiredPingProfileContexts(long nowMs) {
        pingProfileContexts.entrySet()
            .removeIf(entry -> nowMs - entry.getValue().capturedAtMs > PING_PROFILE_CONTEXT_TTL_MS);
    }

    private GameProfile lookupProfileFromMenuContext(LookupContext lookup, GameProfile profile, boolean requireSecure) {
        if (lookup == null) {
            return null;
        }

        if (lookup.sessionBase != null) {
            return lookupProfileFromSessionBase(lookup.sessionBase, profile, requireSecure);
        }

        if (lookup.provider != null) {
            return lookupProfileFromProvider(lookup.provider, profile, requireSecure);
        }

        return null;
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

        // Signed properties are safe to reuse for unsigned lookups.
        // Unsigned properties must never satisfy secure callers.
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

    private static final class PingProfileContext {

        private final ServerCapabilities capabilities;
        private final long capturedAtMs;

        private PingProfileContext(ServerCapabilities capabilities, long capturedAtMs) {
            this.capabilities = capabilities;
            this.capturedAtMs = capturedAtMs;
        }
    }

    public static class LookupContext {

        private final String sessionBase;
        private final ClientProvider provider;
        private final List<ClientProvider> trustedProviders;
        private final boolean allowVanillaFallback;

        private LookupContext(String sessionBase, ClientProvider provider, List<ClientProvider> trustedProviders,
            boolean allowVanillaFallback) {
            this.sessionBase = sessionBase;
            this.provider = provider;
            this.trustedProviders = trustedProviders != null ? trustedProviders : Collections.emptyList();
            this.allowVanillaFallback = allowVanillaFallback;
        }

        public String getSessionBase() {
            return sessionBase;
        }

        public ClientProvider getProvider() {
            return provider;
        }

        public List<ClientProvider> getTrustedProviders() {
            return trustedProviders;
        }

        public boolean isVanillaFallbackAllowed() {
            return allowVanillaFallback;
        }
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

    private static final class MenuProfileLookupContext extends LookupContext {

        private MenuProfileLookupContext(String sessionBase, ClientProvider provider,
            List<ClientProvider> trustedProviders, boolean allowVanillaFallback) {
            super(sessionBase, provider, trustedProviders, allowVanillaFallback);
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
            // Non-Mojang: re-throw so the exception propagates through the mixin
            // (prevents vanilla fallthrough) and callers see failure → retry.
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
