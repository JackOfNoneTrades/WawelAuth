package org.fentanylsolutions.wawelauth.api;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.internal.TextureRequest;
import org.fentanylsolutions.wawelauth.client.render.IProviderAwareSkinManager;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.client.render.SkinTextureState;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Unified skin / cape resolution API for WawelAuth.
 * <p>
 * Given a player UUID (and optional context hints), resolves a skin / cape
 * {@link ResourceLocation} ready for rendering. Manages the full pipeline:
 * provider selection, profile fetch, texture download, and caching.
 * <p>
 * All {@code getSkin} / {@code getCape} methods are safe to call from the render thread.
 * They return a placeholder immediately and fetch asynchronously.
 *
 * <h3>Usage</h3>
 *
 * <pre>
 *
 * {
 *     &#64;code
 *     WawelTextureResolver resolver = WawelClient.instance()
 *         .getTextureResolver();
 *
 *     // Auto-resolve (uses ping context, local accounts, Mojang fallback)
 *     ResourceLocation skin = resolver.getSkin(uuid, name, TextureRequest.DEFAULT);
 *     ResourceLocation cape = resolver.getCape(uuid, name, TextureRequest.DEFAULT);
 *
 *     // Resolve for a specific server entry (uses server's advertised auth)
 *     ResourceLocation skin = resolver.getSkin(uuid, name, serverData, TextureRequest.DEFAULT);
 *     ResourceLocation cape = resolver.getCape(uuid, name, serverData, TextureRequest.DEFAULT);
 *
 *     // Resolve with an explicit provider key
 *     ResourceLocation skin = resolver.getSkin(uuid, name, publicKey, sessionBase, TextureRequest.DEFAULT);
 *     ResourceLocation cape = resolver.getCape(uuid, name, publicKey, sessionBase, TextureRequest.DEFAULT);
 * }
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class WawelTextureResolver {

    private static final ResourceLocation LEGACY_STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation MODERN_STEVE = new ResourceLocation("wawelauth", "textures/steve_64.png");
    private static final ResourceLocation DEFAULT_CAPE = new ResourceLocation("wawelauth", "textures/capeFallback.png");

    public static ResourceLocation getDefaultSkin() {
        return SkinLayers3DConfig.modernSkinSupport ? MODERN_STEVE : LEGACY_STEVE;
    }

    public static ResourceLocation getDefaultCape() {
        return DEFAULT_CAPE;
    }

    // =========================================================================
    // Static face drawing API
    // =========================================================================

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = { 2_000L, 8_000L, 30_000L };
    private static final long SKIN_TTL_MS = 20 * 60 * 1_000L;
    private static final long FAILED_RETRY_MS = 60_000L;

    private final SessionBridge sessionBridge;
    private final ConcurrentHashMap<String, SkinEntry> skinEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CapeEntry> capeEntries = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    public WawelTextureResolver(SessionBridge sessionBridge) {
        this.sessionBridge = sessionBridge;
        this.executor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "WawelAuth-SkinResolver-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolve a player skin using WawelAuth's own context.
     * <p>
     * Resolution order:
     * <ol>
     * <li>Cache hit</li>
     * <li>In-world: connection-scoped provider</li>
     * <li>Menu: ping-advertised server auth for this UUID</li>
     * <li>Menu: locally stored account provider for this UUID</li>
     * <li>Mojang fallback (if {@link TextureRequest#allowVanillaFallback()})</li>
     * <li>Placeholder (steve) while async fetch runs</li>
     * </ol>
     *
     * @param profileId   player UUID
     * @param displayName player display name (used as fallback identifier)
     * @param request     caller flags
     * @return a {@link ResourceLocation} ready for rendering, never null
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, TextureRequest request) {
        if (profileId == null) return getDefaultSkin();
        return getSkinInternal(new LookupHint(buildCacheKey("auto", profileId), null), profileId, displayName, request);
    }

    /**
     * Resolve a player skin using a specific configured provider.
     * <p>
     * This is provider-scoped: it will not silently switch to another local
     * provider that happens to share the same UUID.
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, String providerName, TextureRequest request) {
        if (profileId == null) return getDefaultSkin();
        SessionBridge.LookupContext lookupContext = sessionBridge.createProviderLookupContext(providerName, false);
        return getSkinInternal(
            new LookupHint(buildCacheKey(buildProviderScope(providerName), profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    /**
     * Resolve a player skin for a specific server entry.
     * <p>
     * Uses the server's advertised auth capabilities (from its ping response)
     * to determine which provider to query for the profile and textures.
     *
     * @param profileId   player UUID
     * @param displayName player display name
     * @param serverEntry the {@link ServerData} whose capabilities to use
     * @param request     caller flags
     * @return a {@link ResourceLocation} ready for rendering, never null
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, ServerData serverEntry,
        TextureRequest request) {
        if (profileId == null) return getDefaultSkin();

        ServerCapabilities caps = null;
        if (serverEntry instanceof IServerDataExt) {
            caps = ((IServerDataExt) serverEntry).getWawelCapabilities();
            GameProfile profile = new GameProfile(profileId, displayName);
            sessionBridge.rememberPingProfiles(
                caps != null ? caps : ServerCapabilities.unadvertised(System.currentTimeMillis()),
                new GameProfile[] { profile });
        }

        String scope = buildServerScope(serverEntry != null ? serverEntry.serverIP : null);
        SessionBridge.LookupContext lookupContext = sessionBridge
            .createServerLookupContext(caps, request.allowVanillaFallback());
        return getSkinInternal(
            new LookupHint(buildCacheKey(scope, profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    /**
     * Resolve a player skin using an explicit provider public key.
     * <p>
     * Use this when the caller already knows the auth provider's key and
     * session server, bypassing WawelAuth's automatic provider resolution.
     *
     * @param profileId         player UUID
     * @param displayName       player display name
     * @param providerKey       the provider's public key (for texture signature verification)
     * @param sessionServerBase base URL of the provider's session server
     * @param request           caller flags
     * @return a {@link ResourceLocation} ready for rendering, never null
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, PublicKey providerKey, String sessionServerBase,
        TextureRequest request) {
        return getSkin(
            profileId,
            displayName,
            providerKey,
            sessionServerBase,
            Collections.<String>emptyList(),
            request);
    }

    /**
     * Resolve a player skin using an explicit provider key and trusted skin domains.
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, PublicKey providerKey, String sessionServerBase,
        Iterable<String> skinDomains, TextureRequest request) {
        if (profileId == null) return getDefaultSkin();

        ClientProvider explicitProvider = buildEphemeralProvider(providerKey, sessionServerBase, skinDomains);
        SessionBridge.LookupContext lookupContext = sessionBridge.createProviderLookupContext(explicitProvider, false);
        return getSkinInternal(
            new LookupHint(buildCacheKey(buildExplicitScope(sessionServerBase), profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    /**
     * Resolve a player cape using WawelAuth's own context.
     * <p>
     * Resolution order:
     * <ol>
     * <li>Cache hit</li>
     * <li>In-world: connection-scoped provider</li>
     * <li>Menu: ping-advertised server auth for this UUID</li>
     * <li>Menu: locally stored account provider for this UUID</li>
     * <li>Mojang fallback (if {@link TextureRequest#allowVanillaFallback()})</li>
     * <li>null while async fetch runs</li>
     * </ol>
     *
     * @param profileId   player UUID
     * @param displayName player display name (used as fallback identifier)
     * @param request     caller flags
     * @return a {@link ResourceLocation} ready for rendering, can be null
     */
    public ResourceLocation getCape(UUID profileId, String displayName, TextureRequest request) {
        if (profileId == null) return null;
        return getCapeInternal(new LookupHint(buildCacheKey("auto", profileId), null), profileId, displayName, request);
    }

    /**
     * Resolve a player cape using a specific configured provider.
     * <p>
     * This is provider-scoped: it will not silently switch to another local
     * provider that happens to share the same UUID.
     */
    public ResourceLocation getCape(UUID profileId, String displayName, String providerName, TextureRequest request) {
        if (profileId == null) return null;
        SessionBridge.LookupContext lookupContext = sessionBridge.createProviderLookupContext(providerName, false);
        return getCapeInternal(
            new LookupHint(buildCacheKey(buildProviderScope(providerName), profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    /**
     * Resolve a player cape for a specific server entry.
     * <p>
     * Uses the server's advertised auth capabilities (from its ping response)
     * to determine which provider to query for the profile and textures.
     *
     * @param profileId   player UUID
     * @param displayName player display name
     * @param serverEntry the {@link ServerData} whose capabilities to use
     * @param request     caller flags
     * @return a {@link ResourceLocation} ready for rendering, can be null
     */
    public ResourceLocation getCape(UUID profileId, String displayName, ServerData serverEntry,
        TextureRequest request) {
        if (profileId == null) return null;

        ServerCapabilities caps = null;
        if (serverEntry instanceof IServerDataExt) {
            caps = ((IServerDataExt) serverEntry).getWawelCapabilities();
            GameProfile profile = new GameProfile(profileId, displayName);
            sessionBridge.rememberPingProfiles(
                caps != null ? caps : ServerCapabilities.unadvertised(System.currentTimeMillis()),
                new GameProfile[] { profile });
        }

        String scope = buildServerScope(serverEntry != null ? serverEntry.serverIP : null);
        SessionBridge.LookupContext lookupContext = sessionBridge
            .createServerLookupContext(caps, request.allowVanillaFallback());
        return getCapeInternal(
            new LookupHint(buildCacheKey(scope, profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    /**
     * Resolve a player cape using an explicit provider public key.
     * <p>
     * Use this when the caller already knows the auth provider's key and
     * session server, bypassing WawelAuth's automatic provider resolution.
     *
     * @param profileId         player UUID
     * @param displayName       player display name
     * @param providerKey       the provider's public key (for texture signature verification)
     * @param sessionServerBase base URL of the provider's session server
     * @param request           caller flags
     * @return a {@link ResourceLocation} ready for rendering, can be null
     */
    public ResourceLocation getCape(UUID profileId, String displayName, PublicKey providerKey, String sessionServerBase,
        TextureRequest request) {
        return getCape(
            profileId,
            displayName,
            providerKey,
            sessionServerBase,
            Collections.<String>emptyList(),
            request);
    }

    /**
     * Resolve a player cape using an explicit provider key and trusted skin domains.
     */
    public ResourceLocation getCape(UUID profileId, String displayName, PublicKey providerKey, String sessionServerBase,
        Iterable<String> skinDomains, TextureRequest request) {
        if (profileId == null) return null;

        ClientProvider explicitProvider = buildEphemeralProvider(providerKey, sessionServerBase, skinDomains);
        SessionBridge.LookupContext lookupContext = sessionBridge.createProviderLookupContext(explicitProvider, false);
        return getCapeInternal(
            new LookupHint(buildCacheKey(buildExplicitScope(sessionServerBase), profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    /**
     * Invalidate all cached entries for a player UUID.
     * The next {@code getSkin} / {@code getCape} call will re-fetch from scratch.
     */
    public void invalidate(UUID profileId) {
        if (profileId == null) return;
        String suffix = profileId.toString();
        skinEntries.keySet()
            .removeIf(k -> k.equals(suffix) || k.endsWith("|" + suffix));
        capeEntries.keySet()
            .removeIf(k -> k.equals(suffix) || k.endsWith("|" + suffix));
        sessionBridge.invalidateProfileCache(profileId);
    }

    /**
     * Invalidate all cached entries.
     */
    public void invalidateAll() {
        skinEntries.clear();
        capeEntries.clear();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Sweep expired resolved entries. Call once per client tick.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        skinEntries.values()
            .removeIf(
                entry -> entry.state == FetchState.RESOLVED && entry.resolvedAtMs > 0
                    && now - entry.resolvedAtMs > SKIN_TTL_MS);
        capeEntries.values()
            .removeIf(
                entry -> entry.state == FetchState.RESOLVED && entry.resolvedAtMs > 0
                    && now - entry.resolvedAtMs > SKIN_TTL_MS);
    }

    /**
     * Shut down the worker pool and clear all state.
     */
    public void shutdown() {
        executor.shutdownNow();
        skinEntries.clear();
        capeEntries.clear();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private ResourceLocation getSkinInternal(LookupHint hint, UUID profileId, String displayName,
        TextureRequest request) {
        SkinEntry entry = skinEntries.get(hint.cacheKey);

        if (entry != null) {
            entry.lookupContext = hint.lookupContext;
            switch (entry.state) {
                case RESOLVED:
                    if (!entry.isExpired() && hasUsableRegisteredTexture(entry.texLocation)) {
                        return entry.texLocation;
                    }
                    entry.texLocation = getDefaultSkin();
                    entry.state = FetchState.PENDING;
                    break;
                case FETCHING:
                    return entry.texLocation != null ? entry.texLocation : getDefaultSkin();
                case PLACEHOLDER:
                case FAILED:
                    if (entry.shouldRetry()) {
                        entry.state = FetchState.PENDING;
                    } else {
                        return getDefaultSkin();
                    }
                    break;
                default:
                    break;
            }
        }

        if (entry == null) {
            entry = new SkinEntry(profileId, displayName, request, hint.lookupContext);
            SkinEntry existing = skinEntries.putIfAbsent(hint.cacheKey, entry);
            if (existing != null) {
                entry = existing;
                entry.lookupContext = hint.lookupContext;
            }
        }

        if (entry.state == FetchState.PENDING) {
            submitFetch(entry);
        }

        return entry.texLocation != null ? entry.texLocation : getDefaultSkin();
    }

    private ResourceLocation getCapeInternal(LookupHint hint, UUID profileId, String displayName,
        TextureRequest request) {
        CapeEntry entry = capeEntries.get(hint.cacheKey);

        if (entry != null) {
            entry.lookupContext = hint.lookupContext;
            switch (entry.state) {
                case RESOLVED:
                    if (!entry.isExpired() && hasUsableRegisteredTexture(entry.texLocation)) {
                        return entry.texLocation;
                    }
                    entry.texLocation = null;
                    entry.state = FetchState.PENDING;
                    break;
                case FETCHING:
                    return entry.texLocation != null ? entry.texLocation : null;
                case PLACEHOLDER:
                case FAILED:
                    if (entry.shouldRetry()) {
                        entry.state = FetchState.PENDING;
                    } else {
                        return null;
                    }
                    break;
                default:
                    break;
            }
        }

        if (entry == null) {
            entry = new CapeEntry(profileId, displayName, request, hint.lookupContext);
            CapeEntry existing = capeEntries.putIfAbsent(hint.cacheKey, entry);
            if (existing != null) {
                entry = existing;
                entry.lookupContext = hint.lookupContext;
            }
        }

        if (entry.state == FetchState.PENDING) {
            submitFetch(entry);
        }

        return entry.texLocation != null ? entry.texLocation : null;
    }

    private void submitFetch(TextureEntry entry) {
        if (!entry.fetchInFlight.compareAndSet(false, true)) {
            return;
        }
        entry.state = FetchState.FETCHING;

        final UUID profileId = entry.profileId;
        final String displayName = entry.displayName;
        final boolean requireSecure = entry.request.requireSigned();
        final boolean allowVanilla = entry.request.allowVanillaFallback();

        executor.submit(() -> {
            try {
                doFetch(entry, profileId, displayName, requireSecure, allowVanilla);
            } catch (Exception e) {
                WawelAuth.debug("Skin fetch failed for " + profileId + ": " + e.getMessage());
                handleFetchFailure(entry);
            } finally {
                entry.fetchInFlight.set(false);
            }
        });
    }

    private void doFetch(TextureEntry entry, UUID profileId, String displayName, boolean requireSecure,
        boolean allowVanilla) {

        MinecraftSessionService sessionService = Minecraft.getMinecraft()
            .func_152347_ac();
        SessionBridge.LookupContext lookupContext = entry.lookupContext;
        SessionBridge.OfflineLocalSkin offlineLocalSkin = sessionBridge
            .resolveOfflineLocalSkin(profileId, lookupContext);

        final boolean isSkin = entry instanceof SkinEntry;

        if (offlineLocalSkin != null
            && (isSkin ? offlineLocalSkin.getSkinPath() != null : offlineLocalSkin.getCapePath() != null)) {
            resolveOfflineLocalTexture(entry, isSkin, profileId, offlineLocalSkin);
            return;
        }

        if (!allowVanilla && !sessionBridge.hasProviderForProfile(profileId, lookupContext)) {
            entry.state = FetchState.PLACEHOLDER;
            entry.texLocation = entry.getDefaultTexture();
            entry.lastAttemptMs = System.currentTimeMillis();
            return;
        }

        // fillProfileProperties goes through the authlib mixin which routes
        // to SessionBridge. SessionBridge resolves the right provider from
        // connection context, ping context, or local accounts.
        GameProfile requestedProfile = new GameProfile(profileId, displayName);
        GameProfile profile;
        try {
            profile = runWithLookupContext(
                lookupContext,
                () -> sessionService.fillProfileProperties(requestedProfile, requireSecure));
        } catch (Exception e) {
            WawelAuth.debug("fillProfileProperties failed for " + profileId + ": " + e.getMessage());
            handleFetchFailure(entry);
            return;
        }

        if (profile.getProperties()
            .isEmpty()) {
            handleFetchFailure(entry);
            return;
        }

        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures;
        try {
            textures = runWithLookupContext(lookupContext, () -> sessionService.getTextures(profile, requireSecure));
        } catch (InsecureTextureException e) {
            WawelAuth.debug(
                "Insecure texture for " + profileId + " with requireSecure=" + requireSecure + ": " + e.getMessage());
            textures = Collections.emptyMap();
        }

        final MinecraftProfileTexture.Type finalType = isSkin ? MinecraftProfileTexture.Type.SKIN
            : MinecraftProfileTexture.Type.CAPE;

        if (textures == null || !textures.containsKey(finalType)) {
            // Profile exists but has no skin texture
            entry.state = FetchState.PLACEHOLDER;
            entry.texLocation = entry.getDefaultTexture();
            entry.lastAttemptMs = System.currentTimeMillis();
            return;
        }

        final MinecraftProfileTexture finalTexture = textures.get(finalType);
        final ClientProvider downloadProvider = sessionBridge.resolveTextureDownloadProvider(profileId, lookupContext);
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                    SkinManager skinManager = Minecraft.getMinecraft()
                        .func_152342_ad();
                    ResourceLocation registeredLocation = skinManager instanceof IProviderAwareSkinManager
                        ? ((IProviderAwareSkinManager) skinManager)
                            .wawelauth$loadTexture(finalTexture, finalType, null, downloadProvider)
                        : skinManager.func_152792_a(finalTexture, finalType);
                    if (!hasUsableRegisteredTexture(registeredLocation)) {
                        WawelAuth.debug("Texture registration was not usable yet for " + profileId);
                        handleFetchFailure(entry);
                        return;
                    }
                    entry.texLocation = registeredLocation;
                    entry.state = FetchState.RESOLVED;
                    entry.resolvedAtMs = System.currentTimeMillis();
                    entry.retryCount = 0;
                } catch (Exception e) {
                    WawelAuth.debug("Failed to register texture for " + profileId + ": " + e.getMessage());
                    handleFetchFailure(entry);
                }
            });
    }

    private void resolveOfflineLocalTexture(TextureEntry entry, boolean isSkin, UUID profileId,
        SessionBridge.OfflineLocalSkin offlineLocalSkin) {
        final BufferedImage texImage;
        try {
            if (isSkin) {
                texImage = SkinImageUtil
                    .convertLegacySkin(LocalTextureLoader.readImage(new File(offlineLocalSkin.getSkinPath())));
            } else {
                texImage = LocalTextureLoader.readImage(new File(offlineLocalSkin.getCapePath()));
            }
        } catch (Exception e) {
            WawelAuth.debug("Failed to load local offline texture for " + profileId + ": " + e.getMessage());
            handleFetchFailure(entry);
            return;
        }

        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {

                    ResourceLocation location;

                    if (isSkin) {
                        location = new ResourceLocation("wawelauth", "offline_skins/" + UuidUtil.toUnsigned(profileId));
                    } else {
                        location = new ResourceLocation("wawelauth", "offline_capes/" + UuidUtil.toUnsigned(profileId));
                    }

                    ResourceLocation registeredLocation = LocalTextureLoader.registerBufferedImage(location, texImage);
                    if (!hasUsableRegisteredTexture(registeredLocation)) {
                        WawelAuth.debug("Local offline texture registration was not usable yet for " + profileId);
                        handleFetchFailure(entry);
                        return;
                    }
                    entry.texLocation = registeredLocation;
                    entry.state = FetchState.RESOLVED;
                    entry.resolvedAtMs = System.currentTimeMillis();
                    entry.retryCount = 0;
                } catch (Exception e) {
                    WawelAuth
                        .debug("Failed to register local offline texture for " + profileId + ": " + e.getMessage());
                    handleFetchFailure(entry);
                }
            });
    }

    private static void handleFetchFailure(TextureEntry entry) {
        entry.retryCount++;
        entry.lastAttemptMs = System.currentTimeMillis();
        entry.state = entry.retryCount >= MAX_RETRIES ? FetchState.FAILED : FetchState.PLACEHOLDER;
        if (entry.texLocation == null) {
            entry.texLocation = entry.getDefaultTexture();
        }
    }

    private <T> T runWithLookupContext(SessionBridge.LookupContext lookupContext, Supplier<T> action) {
        if (lookupContext == null) {
            return action.get();
        }
        return sessionBridge.withLookupContext(lookupContext, action);
    }

    private static boolean hasUsableRegisteredTexture(ResourceLocation texLocation) {
        if (texLocation == null) {
            return false;
        }
        ITextureObject textureObject = Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(texLocation);
        return SkinTextureState.isUsable(textureObject);
    }

    private static ClientProvider buildEphemeralProvider(PublicKey key, String sessionServerBase,
        Iterable<String> skinDomains) {
        ClientProvider provider = new ClientProvider();
        provider.setName("explicit@" + sessionServerBase);
        provider.setSessionServerUrl(sessionServerBase);
        if (key != null) {
            provider.setPublicKeyBase64(
                Base64.getEncoder()
                    .encodeToString(key.getEncoded()));
        }

        List<String> normalizedSkinDomains = normalizeSkinDomains(sessionServerBase, skinDomains);
        if (!normalizedSkinDomains.isEmpty()) {
            JsonArray skinDomainsJson = new JsonArray();
            for (String skinDomain : normalizedSkinDomains) {
                skinDomainsJson.add(new JsonPrimitive(skinDomain));
            }
            provider.setSkinDomains(skinDomainsJson.toString());
        }
        return provider;
    }

    static String buildCacheKey(String scope, UUID profileId) {
        return normalizeScopePart(scope, "auto") + "|" + profileId.toString();
    }

    static String buildProviderScope(String providerName) {
        return "provider:" + normalizeScopePart(providerName, "unknown");
    }

    static String buildServerScope(String serverAddress) {
        return "server:" + normalizeScopePart(serverAddress, "unknown");
    }

    static String buildExplicitScope(String sessionServerBase) {
        return "explicit:" + normalizeScopePart(sessionServerBase, "unknown");
    }

    static List<String> normalizeSkinDomains(String sessionServerBase, Iterable<String> skinDomains) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        String sessionHost = extractHost(sessionServerBase);
        if (sessionHost != null) {
            normalized.add(sessionHost);
        }

        if (skinDomains != null) {
            for (String skinDomain : skinDomains) {
                String normalizedDomain = normalizeSkinDomain(skinDomain);
                if (normalizedDomain != null) {
                    normalized.add(normalizedDomain);
                }
            }
        }

        return new ArrayList<>(normalized);
    }

    private static String normalizeScopePart(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim()
            .toLowerCase();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String normalizeSkinDomain(String domain) {
        if (domain == null) {
            return null;
        }

        String trimmed = domain.trim()
            .toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith(".")) {
            return trimmed;
        }

        if (trimmed.contains("://")) {
            String host = extractHost(trimmed);
            return host != null ? host : null;
        }

        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }

        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            trimmed = trimmed.substring(0, colon);
        }

        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.trim()
            .isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl);
            String host = uri.getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // State machine
    // =========================================================================

    private enum FetchState {

        PENDING,
        FETCHING,
        RESOLVED,
        PLACEHOLDER,
        FAILED
    }

    private static class TextureEntry {

        final UUID profileId;
        final String displayName;
        final TextureRequest request;
        final AtomicBoolean fetchInFlight = new AtomicBoolean(false);

        volatile SessionBridge.LookupContext lookupContext;
        volatile FetchState state = FetchState.PENDING;
        volatile ResourceLocation texLocation;
        volatile long resolvedAtMs;
        volatile long lastAttemptMs;
        volatile int retryCount;

        TextureEntry(UUID profileId, String displayName, TextureRequest request,
            SessionBridge.LookupContext lookupContext) {
            this.profileId = profileId;
            this.displayName = displayName;
            this.request = request;
            this.lookupContext = lookupContext;
        }

        ResourceLocation getDefaultTexture() {
            return null;
        }

        boolean isExpired() {
            return resolvedAtMs > 0 && System.currentTimeMillis() - resolvedAtMs > SKIN_TTL_MS;
        }

        boolean shouldRetry() {
            if (state == FetchState.FAILED) {
                return System.currentTimeMillis() - lastAttemptMs > FAILED_RETRY_MS;
            }
            if (state == FetchState.PLACEHOLDER) {
                long delay = retryCount < RETRY_DELAYS_MS.length ? RETRY_DELAYS_MS[retryCount]
                    : RETRY_DELAYS_MS[RETRY_DELAYS_MS.length - 1];
                return System.currentTimeMillis() - lastAttemptMs > delay;
            }
            return false;
        }
    }

    private static final class SkinEntry extends TextureEntry {

        @Override
        ResourceLocation getDefaultTexture() {
            return getDefaultSkin();
        }

        SkinEntry(UUID profileId, String displayName, TextureRequest request,
            SessionBridge.LookupContext lookupContext) {
            super(profileId, displayName, request, lookupContext);
        }
    }

    private static final class CapeEntry extends TextureEntry {

        CapeEntry(UUID profileId, String displayName, TextureRequest request,
            SessionBridge.LookupContext lookupContext) {
            super(profileId, displayName, request, lookupContext);
        }
    }

    private static final class LookupHint {

        private final String cacheKey;
        private final SessionBridge.LookupContext lookupContext;

        private LookupHint(String cacheKey, SessionBridge.LookupContext lookupContext) {
            this.cacheKey = cacheKey;
            this.lookupContext = lookupContext;
        }
    }
}
