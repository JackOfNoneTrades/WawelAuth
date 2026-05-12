package org.fentanylsolutions.wawelauth.api;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.IProviderAwareSkinManager;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.client.render.SkinTextureState;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Single source of truth for skin/cape resolution.
 * Render-thread safe, returns placeholder and fetches async.
 */
@SideOnly(Side.CLIENT)
public class WawelTextureResolver {

    private static final ResourceLocation LEGACY_STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation MODERN_STEVE = new ResourceLocation("wawelauth", "textures/steve_64.png");
    private static final ResourceLocation DEFAULT_CAPE = new ResourceLocation("wawelauth", "textures/capeFallback.png");

    public static ResourceLocation getDefaultSkin() {
        return SkinLayers3DConfig.modernSkinSupport ? MODERN_STEVE : LEGACY_STEVE;
    }

    public static ResourceLocation getLegacyDefaultSkin() {
        return LEGACY_STEVE;
    }

    public static ResourceLocation getDefaultCape() {
        return DEFAULT_CAPE;
    }

    private final SessionBridge sessionBridge;
    private final TextureResolverCache cache;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    public WawelTextureResolver(SessionBridge sessionBridge) {
        this.sessionBridge = sessionBridge;
        this.cache = new TextureResolverCache();
        this.executor = createDefaultExecutor();
    }

    WawelTextureResolver(SessionBridge sessionBridge, ThreadPoolExecutor executor) {
        this.sessionBridge = sessionBridge;
        this.cache = new TextureResolverCache();
        this.executor = executor;
    }

    private ThreadPoolExecutor createDefaultExecutor() {
        return new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "WawelAuth-SkinResolver-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Resolve a player skin. Never returns null. */
    public ResourceLocation getSkin(UUID profileId, String displayName, ClientProvider provider,
        boolean allowUnsigned) {
        if (profileId == null || provider == null) return getDefaultSkin();
        String cacheKey = buildCacheKey(provider, profileId);
        return getTextureInternal(TextureKind.SKIN, cacheKey, profileId, displayName, provider, allowUnsigned);
    }

    /** Retrieve the skin model resolved during texture fetch, or null if not yet fetched. */
    public SkinModel getResolvedSkinModel(UUID profileId, ClientProvider provider) {
        if (profileId == null || provider == null) return null;
        return cache.getResolvedSkinModel(buildCacheKey(provider, profileId));
    }

    /** Resolve a player cape, or null if none. */
    public ResourceLocation getCape(UUID profileId, String displayName, ClientProvider provider,
        boolean allowUnsigned) {
        if (profileId == null || provider == null) return null;
        String cacheKey = buildCacheKey(provider, profileId);
        return getTextureInternal(TextureKind.CAPE, cacheKey, profileId, displayName, provider, allowUnsigned);
    }

    /**
     * Try multiple providers and return the first resolved skin.
     * For when we don't know which provider a player belongs to.
     */
    public ResourceLocation getSkinFromAnyProvider(UUID profileId, String displayName,
        java.util.List<ClientProvider> providers) {
        if (profileId == null || providers == null || providers.isEmpty()) return getDefaultSkin();

        for (ClientProvider provider : providers) {
            if (provider == null) continue;
            ResourceLocation skin = getSkin(profileId, displayName, provider, false);
            if (skin != null && !skin.equals(getDefaultSkin()) && !skin.equals(getLegacyDefaultSkin())) {
                return skin;
            }
        }
        return getDefaultSkin();
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    /** Invalidate all cached entries for a player UUID across all providers. */
    public void invalidate(UUID profileId) {
        cache.invalidate(profileId);
        if (sessionBridge != null) {
            sessionBridge.invalidateProfileCache(profileId);
        }
    }

    /**
     * Invalidate all cached entries.
     */
    public void invalidateAll() {
        cache.clear();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Sweep expired entries. Call once per client tick. */
    public void tick() {
        cache.tick();
    }

    /** Shut down worker pool and clear state. */
    public void shutdown() {
        executor.shutdownNow();
        cache.clear();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private ResourceLocation getTextureInternal(TextureKind kind, String cacheKey, UUID profileId, String displayName,
        ClientProvider provider, boolean allowUnsigned) {

        ResourceLocation fallback = fallbackFor(kind);
        TextureEntry entry = cache.get(kind, cacheKey);
        if (entry != null) {
            switch (entry.state) {
                case RESOLVED:
                    if (isResolvedReady(entry)) {
                        return entry.texLocation;
                    }
                    entry.texLocation = fallback;
                    entry.state = TextureFetchState.PENDING;
                    break;
                case REGISTERED_LOADING:
                    // Main-thread only: render checks and queued texture registrations both run on the client thread.
                    if (entry.texLocation != null && hasUsableRegisteredTexture(entry.texLocation)) {
                        entry.markResolved(entry.texLocation);
                        return entry.texLocation;
                    }
                    if (entry.isRegisteredLoadingTimedOut()) {
                        entry.texLocation = fallback;
                        entry.markFailure();
                    }
                    return fallback;
                case FETCHING:
                    return fallback;
                case PLACEHOLDER:
                case FAILED:
                    if (entry.shouldRetry()) {
                        entry.state = TextureFetchState.PENDING;
                        break;
                    }
                    return fallback;
                default:
                    break;
            }
        }

        if (entry == null) {
            entry = cache.getOrCreate(kind, cacheKey, profileId, displayName, provider, allowUnsigned);
        }

        if (entry.state == TextureFetchState.PENDING) {
            submitFetch(entry);
        }

        return fallback;
    }

    private boolean isResolvedReady(TextureEntry entry) {
        if (entry.isExpired()) {
            return false;
        }
        if (!entry.kind.isSkin() && entry.texLocation == null) {
            return true;
        }
        return hasUsableRegisteredTexture(entry.texLocation);
    }

    private void submitFetch(TextureEntry entry) {
        if (!entry.fetchInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!cache.beginFetchIfCurrent(entry)) {
            entry.fetchInFlight.set(false);
            return;
        }

        final UUID profileId = entry.profileId;
        final String displayName = entry.displayName;
        final ClientProvider provider = entry.provider;
        final String type = entry.kind.label;
        WawelAuth.debug(
            "Fetching " + type
                + " for "
                + profileId
                + " ("
                + displayName
                + ") from provider '"
                + provider.getName()
                + "'");
        final boolean requireSecure = !entry.allowUnsigned;

        try {
            executor.submit(() -> {
                try {
                    if (!cache.isCurrent(entry)) {
                        return;
                    }
                    doFetch(entry, profileId, displayName, provider, requireSecure);
                } catch (Exception e) {
                    WawelAuth.debug("Failed to fetch " + type + " for " + profileId + ": " + e.getMessage());
                    cache.handleFetchFailureIfCurrent(entry);
                } finally {
                    entry.fetchInFlight.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            entry.fetchInFlight.set(false);
            WawelAuth.debug("Fetch submission rejected for " + type + " " + profileId + ": " + e.getMessage());
            cache.handleFetchFailureIfCurrent(entry);
        }
    }

    private void doFetch(TextureEntry entry, UUID profileId, String displayName, ClientProvider provider,
        boolean requireSecure) {
        if (!cache.isCurrent(entry)) {
            return;
        }

        final boolean isOffline = BuiltinProviders.isOfflineProvider(provider.getName());

        // Offline provider: resolve local skin files
        if (isOffline) {
            SessionBridge.OfflineLocalSkin offlineLocalSkin = sessionBridge
                .resolveOfflineLocalSkin(profileId, provider);
            if (offlineLocalSkin != null && (entry.kind.isSkin() ? offlineLocalSkin.getSkinPath() != null
                : offlineLocalSkin.getCapePath() != null)) {
                resolveOfflineLocalTexture(entry, profileId, offlineLocalSkin);
                return;
            }
            // No local skin file, not transient, don't retry
            cache.completeNoTextureIfCurrent(entry, fallbackFor(entry.kind));
            return;
        }

        // Fetch profile from the provider's session server
        GameProfile requestedProfile = new GameProfile(profileId, displayName);
        GameProfile profile;
        try {
            profile = sessionBridge.fillProfileFromProvider(provider, requestedProfile, requireSecure);
        } catch (Exception e) {
            WawelAuth.debug("fillProfileFromProvider failed for " + profileId + ": " + e.getMessage());
            cache.handleFetchFailureIfCurrent(entry);
            return;
        }

        if (profile == null || profile.getProperties()
            .isEmpty()) {
            cache.handleFetchFailureIfCurrent(entry);
            return;
        }

        if (entry.kind.isSkin()) {
            SkinModel model = YggdrasilTexturePayload.extractSkinModel(profile);
            if (model != null) {
                cache.putResolvedSkinModelIfCurrent(entry, model);
            }
        }

        // Parse textures via authlib (mixin handles signature/domain checks)
        MinecraftSessionService sessionService = Minecraft.getMinecraft()
            .func_152347_ac();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures;
        try {
            sessionBridge.setActiveProviderContext(provider);
            textures = sessionService.getTextures(profile, requireSecure);
        } catch (InsecureTextureException e) {
            WawelAuth.debug(
                "Insecure texture for " + profileId + " with requireSecure=" + requireSecure + ": " + e.getMessage());
            textures = Collections.emptyMap();
        } finally {
            sessionBridge.clearActiveProviderContext();
        }
        if (!cache.isCurrent(entry)) {
            return;
        }

        if (textures == null || !textures.containsKey(entry.kind.profileType)) {
            // Profile has no skin/cape, not transient, don't retry
            cache.completeNoTextureIfCurrent(entry, fallbackFor(entry.kind));
            return;
        }

        final MinecraftProfileTexture finalTexture = textures.get(entry.kind.profileType);
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                    TextureFetchState completedState = cache.completeRegisteredTextureIfCurrent(entry, () -> {
                        SkinManager skinManager = Minecraft.getMinecraft()
                            .func_152342_ad();
                        return skinManager instanceof IProviderAwareSkinManager
                            ? ((IProviderAwareSkinManager) skinManager)
                                .wawelauth$loadTexture(finalTexture, entry.kind.profileType, null, provider)
                            : skinManager.func_152792_a(finalTexture, entry.kind.profileType);
                    }, WawelTextureResolver::hasUsableRegisteredTexture);
                    if (completedState == TextureFetchState.REGISTERED_LOADING) {
                        WawelAuth.debug("Texture registered but still loading for " + profileId);
                    }
                } catch (Exception e) {
                    WawelAuth.debug("Failed to register texture for " + profileId + ": " + e.getMessage());
                    cache.handleFetchFailureIfCurrent(entry);
                }
            });
    }

    private void resolveOfflineLocalTexture(TextureEntry entry, UUID profileId,
        SessionBridge.OfflineLocalSkin offlineLocalSkin) {
        final BufferedImage texImage;
        try {
            if (entry.kind.isSkin()) {
                texImage = SkinImageUtil
                    .convertLegacySkin(LocalTextureLoader.readImage(new File(offlineLocalSkin.getSkinPath())));
            } else {
                texImage = LocalTextureLoader.readImage(new File(offlineLocalSkin.getCapePath()));
            }
        } catch (Exception e) {
            WawelAuth.debug("Failed to load local offline texture for " + profileId + ": " + e.getMessage());
            cache.handleFetchFailureIfCurrent(entry);
            return;
        }
        if (!cache.isCurrent(entry)) {
            return;
        }

        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                    TextureFetchState completedState = cache.completeReadyTextureIfCurrent(entry, () -> {
                        ResourceLocation location = new ResourceLocation(
                            "wawelauth",
                            entry.kind.offlinePathPrefix + "/" + UuidUtil.toUnsigned(profileId));

                        return LocalTextureLoader.registerBufferedImage(location, texImage);
                    }, WawelTextureResolver::hasUsableRegisteredTexture);
                    if (completedState != null && completedState != TextureFetchState.RESOLVED) {
                        WawelAuth.debug("Local offline texture registration was not usable yet for " + profileId);
                    }
                } catch (Exception e) {
                    WawelAuth
                        .debug("Failed to register local offline texture for " + profileId + ": " + e.getMessage());
                    cache.handleFetchFailureIfCurrent(entry);
                }
            });
    }

    private static boolean hasUsableRegisteredTexture(ResourceLocation texLocation) {
        if (texLocation == null) {
            return false;
        }
        ITextureObject textureObject = Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(texLocation);
        return SkinTextureState.isReadyForRender(textureObject);
    }

    private static ResourceLocation fallbackFor(TextureKind kind) {
        return kind.isSkin() ? getDefaultSkin() : null;
    }

    static String buildCacheKey(ClientProvider provider, UUID profileId) {
        return TextureResolverCache.buildCacheKey(provider, profileId);
    }

    TextureEntry cacheEntry(TextureEntry entry) {
        return cache.cacheEntry(entry);
    }

    TextureEntry getSkinEntry(String cacheKey) {
        return cache.get(TextureKind.SKIN, cacheKey);
    }

    TextureEntry getCapeEntry(String cacheKey) {
        return cache.get(TextureKind.CAPE, cacheKey);
    }

    boolean isCurrent(TextureEntry entry) {
        return cache.isCurrent(entry);
    }

    void putResolvedSkinModelIfCurrent(TextureEntry entry, SkinModel model) {
        cache.putResolvedSkinModelIfCurrent(entry, model);
    }

    void completeNoTextureIfCurrent(TextureEntry entry) {
        cache.completeNoTextureIfCurrent(entry, fallbackFor(entry.kind));
    }
}
