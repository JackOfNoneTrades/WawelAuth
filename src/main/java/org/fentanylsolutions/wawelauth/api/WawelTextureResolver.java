package org.fentanylsolutions.wawelauth.api;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;

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

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = { 2_000L, 8_000L, 30_000L };
    private static final long SKIN_TTL_MS = 20 * 60 * 1_000L;
    private static final long FAILED_RETRY_MS = 60_000L;

    private final SessionBridge sessionBridge;
    private final ConcurrentHashMap<String, SkinEntry> skinEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CapeEntry> capeEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkinModel> resolvedSkinModels = new ConcurrentHashMap<>();
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

    /** Resolve a player skin. Never returns null. */
    public ResourceLocation getSkin(UUID profileId, String displayName, ClientProvider provider,
        boolean allowUnsigned) {
        if (profileId == null || provider == null) return getDefaultSkin();
        String cacheKey = buildCacheKey(provider, profileId);
        return getSkinInternal(cacheKey, profileId, displayName, provider, allowUnsigned);
    }

    /** Retrieve the skin model resolved during texture fetch, or null if not yet fetched. */
    public SkinModel getResolvedSkinModel(UUID profileId, ClientProvider provider) {
        if (profileId == null || provider == null) return null;
        return resolvedSkinModels.get(buildCacheKey(provider, profileId));
    }

    /** Resolve a player cape, or null if none. */
    public ResourceLocation getCape(UUID profileId, String displayName, ClientProvider provider,
        boolean allowUnsigned) {
        if (profileId == null || provider == null) return null;
        String cacheKey = buildCacheKey(provider, profileId);
        return getCapeInternal(cacheKey, profileId, displayName, provider, allowUnsigned);
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
        if (profileId == null) return;
        String suffix = "|" + profileId.toString();
        skinEntries.keySet()
            .removeIf(k -> k.endsWith(suffix));
        capeEntries.keySet()
            .removeIf(k -> k.endsWith(suffix));
        resolvedSkinModels.keySet()
            .removeIf(k -> k.endsWith(suffix));
        sessionBridge.invalidateProfileCache(profileId);
    }

    /**
     * Invalidate all cached entries.
     */
    public void invalidateAll() {
        skinEntries.clear();
        capeEntries.clear();
        resolvedSkinModels.clear();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Sweep expired entries. Call once per client tick. */
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

    /** Shut down worker pool and clear state. */
    public void shutdown() {
        executor.shutdownNow();
        skinEntries.clear();
        capeEntries.clear();
        resolvedSkinModels.clear();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private ResourceLocation getSkinInternal(String cacheKey, UUID profileId, String displayName,
        ClientProvider provider, boolean allowUnsigned) {

        SkinEntry entry = skinEntries.get(cacheKey);

        if (entry != null) {
            switch (entry.state) {
                case RESOLVED:
                    if (!entry.isExpired() && hasUsableRegisteredTexture(entry.texLocation)) {
                        return entry.texLocation;
                    }
                    entry.texLocation = getDefaultSkin();
                    entry.state = FetchState.PENDING;
                    break;
                case FETCHING:
                    return getDefaultSkin();
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
            entry = new SkinEntry(profileId, displayName, provider, allowUnsigned);
            SkinEntry existing = skinEntries.putIfAbsent(cacheKey, entry);
            if (existing != null) {
                entry = existing;
            }
        }

        if (entry.state == FetchState.PENDING) {
            submitFetch(entry);
        }

        return getDefaultSkin();
    }

    private ResourceLocation getCapeInternal(String cacheKey, UUID profileId, String displayName,
        ClientProvider provider, boolean allowUnsigned) {

        CapeEntry entry = capeEntries.get(cacheKey);

        if (entry != null) {
            switch (entry.state) {
                case RESOLVED:
                    if (entry.isExpired()) {
                        entry.texLocation = null;
                        entry.state = FetchState.PENDING;
                        break;
                    }
                    // null texture = no cape, not an error
                    if (entry.texLocation != null && !hasUsableRegisteredTexture(entry.texLocation)) {
                        entry.texLocation = null;
                        entry.state = FetchState.PENDING;
                        break;
                    }
                    return entry.texLocation;
                case FETCHING:
                    return null;
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
            entry = new CapeEntry(profileId, displayName, provider, allowUnsigned);
            CapeEntry existing = capeEntries.putIfAbsent(cacheKey, entry);
            if (existing != null) {
                entry = existing;
            }
        }

        if (entry.state == FetchState.PENDING) {
            submitFetch(entry);
        }

        return null;
    }

    private void submitFetch(TextureEntry entry) {
        if (!entry.fetchInFlight.compareAndSet(false, true)) {
            return;
        }
        entry.state = FetchState.FETCHING;

        final UUID profileId = entry.profileId;
        final String displayName = entry.displayName;
        final ClientProvider provider = entry.provider;
        String type = entry instanceof SkinEntry ? "skin" : "cape";
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

        executor.submit(() -> {
            try {
                doFetch(entry, profileId, displayName, provider, requireSecure);
            } catch (Exception e) {
                WawelAuth.debug("Skin fetch failed for " + profileId + ": " + e.getMessage());
                handleFetchFailure(entry);
            } finally {
                entry.fetchInFlight.set(false);
            }
        });
    }

    private void doFetch(TextureEntry entry, UUID profileId, String displayName, ClientProvider provider,
        boolean requireSecure) {

        final boolean isSkin = entry instanceof SkinEntry;
        final boolean isOffline = BuiltinProviders.isOfflineProvider(provider.getName());

        // Offline provider: resolve local skin files
        if (isOffline) {
            SessionBridge.OfflineLocalSkin offlineLocalSkin = sessionBridge
                .resolveOfflineLocalSkin(profileId, provider);
            if (offlineLocalSkin != null
                && (isSkin ? offlineLocalSkin.getSkinPath() != null : offlineLocalSkin.getCapePath() != null)) {
                resolveOfflineLocalTexture(entry, isSkin, profileId, offlineLocalSkin);
                return;
            }
            // No local skin file, not transient, don't retry
            entry.state = FetchState.RESOLVED;
            entry.resolvedAtMs = System.currentTimeMillis();
            return;
        }

        // Fetch profile from the provider's session server
        GameProfile requestedProfile = new GameProfile(profileId, displayName);
        GameProfile profile;
        try {
            profile = sessionBridge.fillProfileFromProvider(provider, requestedProfile, requireSecure);
        } catch (Exception e) {
            WawelAuth.debug("fillProfileFromProvider failed for " + profileId + ": " + e.getMessage());
            handleFetchFailure(entry);
            return;
        }

        if (profile == null || profile.getProperties()
            .isEmpty()) {
            handleFetchFailure(entry);
            return;
        }

        if (isSkin) {
            SkinModel model = extractSkinModelFromProfile(profile);
            if (model != null) {
                resolvedSkinModels.put(buildCacheKey(provider, profileId), model);
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

        final MinecraftProfileTexture.Type finalType = isSkin ? MinecraftProfileTexture.Type.SKIN
            : MinecraftProfileTexture.Type.CAPE;

        if (textures == null || !textures.containsKey(finalType)) {
            // Profile has no skin/cape, not transient, don't retry
            if (isSkin) {
                entry.texLocation = getDefaultSkin();
            }
            entry.state = FetchState.RESOLVED;
            entry.resolvedAtMs = System.currentTimeMillis();
            return;
        }

        final MinecraftProfileTexture finalTexture = textures.get(finalType);
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                    SkinManager skinManager = Minecraft.getMinecraft()
                        .func_152342_ad();
                    ResourceLocation registeredLocation = skinManager instanceof IProviderAwareSkinManager
                        ? ((IProviderAwareSkinManager) skinManager)
                            .wawelauth$loadTexture(finalTexture, finalType, null, provider)
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

    private static SkinModel extractSkinModelFromProfile(GameProfile profile) {
        try {
            Collection<Property> textures = profile.getProperties()
                .get("textures");
            if (textures == null || textures.isEmpty()) return null;

            for (Property property : textures) {
                if (property == null) continue;
                String value = property.getValue();
                if (value == null || value.isEmpty()) continue;

                String json = new String(org.apache.commons.codec.binary.Base64.decodeBase64(value), "UTF-8");
                JsonObject root = new JsonParser().parse(json)
                    .getAsJsonObject();
                JsonObject tex = root.getAsJsonObject("textures");
                if (tex == null) continue;
                JsonObject skin = tex.getAsJsonObject("SKIN");
                if (skin == null) continue;
                JsonObject metadata = skin.getAsJsonObject("metadata");
                if (metadata == null) return SkinModel.CLASSIC;
                JsonElement model = metadata.get("model");
                if (model == null || !model.isJsonPrimitive()) return SkinModel.CLASSIC;
                return SkinModel.fromYggdrasil(model.getAsString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    static String buildCacheKey(ClientProvider provider, UUID profileId) {
        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            providerName = "unknown";
        }
        return providerName.trim()
            .toLowerCase() + "|"
            + profileId.toString();
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
        final ClientProvider provider;
        final boolean allowUnsigned;
        final AtomicBoolean fetchInFlight = new AtomicBoolean(false);

        volatile FetchState state = FetchState.PENDING;
        volatile ResourceLocation texLocation;
        volatile long resolvedAtMs;
        volatile long lastAttemptMs;
        volatile int retryCount;

        TextureEntry(UUID profileId, String displayName, ClientProvider provider, boolean allowUnsigned) {
            this.profileId = profileId;
            this.displayName = displayName;
            this.provider = provider;
            this.allowUnsigned = allowUnsigned;
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

        SkinEntry(UUID profileId, String displayName, ClientProvider provider, boolean allowUnsigned) {
            super(profileId, displayName, provider, allowUnsigned);
        }
    }

    private static final class CapeEntry extends TextureEntry {

        CapeEntry(UUID profileId, String displayName, ClientProvider provider, boolean allowUnsigned) {
            super(profileId, displayName, provider, allowUnsigned);
        }
    }
}
