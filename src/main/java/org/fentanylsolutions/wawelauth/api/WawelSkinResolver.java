package org.fentanylsolutions.wawelauth.api;

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
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Unified skin resolution API for WawelAuth.
 *
 * Given a player UUID (and optional context hints), resolves a skin
 * {@link ResourceLocation} ready for rendering. Manages the full pipeline:
 * provider selection, profile fetch, texture download, and caching.
 *
 * All {@code getSkin} methods are safe to call from the render thread.
 * They return a placeholder immediately and fetch asynchronously.
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * 
 * {
 *     &#64;code
 *     WawelSkinResolver resolver = WawelClient.instance()
 *         .getSkinResolver();
 *
 *     // Auto-resolve (uses ping context, local accounts, Mojang fallback)
 *     ResourceLocation skin = resolver.getSkin(uuid, name, SkinRequest.DEFAULT);
 *
 *     // Resolve for a specific server entry (uses server's advertised auth)
 *     ResourceLocation skin = resolver.getSkin(uuid, name, serverData, SkinRequest.DEFAULT);
 *
 *     // Resolve with an explicit provider key
 *     ResourceLocation skin = resolver.getSkin(uuid, name, publicKey, sessionBase, SkinRequest.DEFAULT);
 * }
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class WawelSkinResolver {

    public static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");

    // =========================================================================
    // Static face drawing API
    // =========================================================================

    /**
     * Draw an 8x8 player face from a skin {@link ResourceLocation}.
     *
     * Handles both legacy (64x32) and modern (64x64) skin formats,
     * as well as HD textures of any resolution. Draws the base face
     * layer and the hat/overlay layer on top.
     *
     * The caller is responsible for binding GL state (blend, lighting, etc.)
     * before calling this method.
     *
     * @param skin  the skin texture ResourceLocation
     * @param x     screen x position
     * @param y     screen y position
     * @param alpha opacity (0.0 = transparent, 1.0 = opaque)
     */
    public static void drawFace(ResourceLocation skin, float x, float y, float alpha) {
        drawFace(skin, x, y, 8, 8, alpha);
    }

    /**
     * Draw a player face at arbitrary dimensions.
     *
     * Same as {@link #drawFace(ResourceLocation, float, float, float)} but
     * the output is scaled to {@code width x height} pixels instead of 8x8.
     *
     * @param skin   the skin texture ResourceLocation
     * @param x      screen x position
     * @param y      screen y position
     * @param width  output width in pixels
     * @param height output height in pixels
     * @param alpha  opacity (0.0 = transparent, 1.0 = opaque)
     */
    public static void drawFace(ResourceLocation skin, float x, float y, int width, int height, float alpha) {
        if (skin == null) skin = STEVE;

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(skin);

        int texWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int texHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        if (texWidth <= 0 || texHeight <= 0) {
            texWidth = 64;
            texHeight = 64;
        }

        boolean legacyLayout = texWidth == texHeight * 2;
        float uScale = texWidth / 64.0F;
        float vScale = texHeight / (legacyLayout ? 32.0F : 64.0F);

        int sampleW = Math.max(1, Math.round(8.0F * uScale));
        int sampleH = Math.max(1, Math.round(8.0F * vScale));

        float baseU = 8.0F * uScale;
        float baseV = 8.0F * vScale;
        drawTexQuad(x, y, baseU, baseV, sampleW, sampleH, width, height, texWidth, texHeight, alpha);

        if (!legacyLayout) {
            float hatU = 40.0F * uScale;
            float hatV = 8.0F * vScale;
            if (hatU + sampleW <= texWidth && hatV + sampleH <= texHeight) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                drawTexQuad(x, y, hatU, hatV, sampleW, sampleH, width, height, texWidth, texHeight, alpha);
            }
        }
    }

    /**
     * Draw a textured quad with precise float UV coordinates.
     * Used internally by {@link #drawFace}.
     */
    private static void drawTexQuad(float x, float y, float u, float v, int uWidth, int vHeight, int width, int height,
        float tileWidth, float tileHeight, float alpha) {
        float uNorm = 1.0F / tileWidth;
        float vNorm = 1.0F / tileHeight;
        float u0 = u * uNorm;
        float u1 = (u + uWidth) * uNorm;
        float v0 = v * vNorm;
        float v1 = (v + vHeight) * vNorm;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, u0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0, u0, v0);
        tessellator.draw();
    }

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = { 2_000L, 8_000L, 30_000L };
    private static final long SKIN_TTL_MS = 20 * 60 * 1_000L;
    private static final long FAILED_RETRY_MS = 60_000L;

    private final SessionBridge sessionBridge;
    private final ConcurrentHashMap<String, SkinEntry> entries = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    public WawelSkinResolver(SessionBridge sessionBridge) {
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
     *
     * Resolution order:
     * <ol>
     * <li>Cache hit</li>
     * <li>In-world: connection-scoped provider</li>
     * <li>Menu: ping-advertised server auth for this UUID</li>
     * <li>Menu: locally stored account provider for this UUID</li>
     * <li>Mojang fallback (if {@link SkinRequest#allowVanillaFallback()})</li>
     * <li>Placeholder (steve) while async fetch runs</li>
     * </ol>
     *
     * @param profileId   player UUID
     * @param displayName player display name (used as fallback identifier)
     * @param request     caller flags
     * @return a {@link ResourceLocation} ready for rendering, never null
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, SkinRequest request) {
        if (profileId == null) return STEVE;
        return getSkinInternal(new LookupHint(buildCacheKey("auto", profileId), null), profileId, displayName, request);
    }

    /**
     * Resolve a player skin using a specific configured provider.
     *
     * This is provider-scoped: it will not silently switch to another local
     * provider that happens to share the same UUID.
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, String providerName, SkinRequest request) {
        if (profileId == null) return STEVE;
        SessionBridge.LookupContext lookupContext = sessionBridge.createProviderLookupContext(providerName, false);
        return getSkinInternal(
            new LookupHint(buildCacheKey(buildProviderScope(providerName), profileId), lookupContext),
            profileId,
            displayName,
            request);
    }

    /**
     * Resolve a player skin for a specific server entry.
     *
     * Uses the server's advertised auth capabilities (from its ping response)
     * to determine which provider to query for the profile and textures.
     *
     * @param profileId   player UUID
     * @param displayName player display name
     * @param serverEntry the {@link ServerData} whose capabilities to use
     * @param request     caller flags
     * @return a {@link ResourceLocation} ready for rendering, never null
     */
    public ResourceLocation getSkin(UUID profileId, String displayName, ServerData serverEntry, SkinRequest request) {
        if (profileId == null) return STEVE;

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
     *
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
        SkinRequest request) {
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
        Iterable<String> skinDomains, SkinRequest request) {
        if (profileId == null) return STEVE;

        ClientProvider explicitProvider = buildEphemeralProvider(providerKey, sessionServerBase, skinDomains);
        SessionBridge.LookupContext lookupContext = sessionBridge.createProviderLookupContext(explicitProvider, false);
        return getSkinInternal(
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
     * The next {@code getSkin} call will re-fetch from scratch.
     */
    public void invalidate(UUID profileId) {
        if (profileId == null) return;
        String suffix = profileId.toString();
        entries.keySet()
            .removeIf(k -> k.equals(suffix) || k.endsWith("|" + suffix));
        sessionBridge.invalidateProfileCache(profileId);
    }

    /** Invalidate all cached skin entries. */
    public void invalidateAll() {
        entries.clear();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Sweep expired resolved entries. Call once per client tick. */
    public void tick() {
        long now = System.currentTimeMillis();
        entries.values()
            .removeIf(
                entry -> entry.state == FetchState.RESOLVED && entry.resolvedAtMs > 0
                    && now - entry.resolvedAtMs > SKIN_TTL_MS);
    }

    /** Shut down the worker pool and clear all state. */
    public void shutdown() {
        executor.shutdownNow();
        entries.clear();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private ResourceLocation getSkinInternal(LookupHint hint, UUID profileId, String displayName, SkinRequest request) {
        SkinEntry entry = entries.get(hint.cacheKey);

        if (entry != null) {
            entry.lookupContext = hint.lookupContext;
            switch (entry.state) {
                case RESOLVED:
                    if (!entry.isExpired()) {
                        return entry.skinLocation;
                    }
                    entry.state = FetchState.PENDING;
                    break;
                case FETCHING:
                    return entry.skinLocation != null ? entry.skinLocation : STEVE;
                case PLACEHOLDER:
                case FAILED:
                    if (entry.shouldRetry()) {
                        entry.state = FetchState.PENDING;
                    } else {
                        return STEVE;
                    }
                    break;
                default:
                    break;
            }
        }

        if (entry == null) {
            entry = new SkinEntry(profileId, displayName, request, hint.lookupContext);
            SkinEntry existing = entries.putIfAbsent(hint.cacheKey, entry);
            if (existing != null) {
                entry = existing;
                entry.lookupContext = hint.lookupContext;
            }
        }

        if (entry.state == FetchState.PENDING) {
            submitFetch(entry);
        }

        return entry.skinLocation != null ? entry.skinLocation : STEVE;
    }

    private void submitFetch(SkinEntry entry) {
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

    private void doFetch(SkinEntry entry, UUID profileId, String displayName, boolean requireSecure,
        boolean allowVanilla) {

        MinecraftSessionService sessionService = Minecraft.getMinecraft()
            .func_152347_ac();
        SessionBridge.LookupContext lookupContext = entry.lookupContext;

        if (!allowVanilla && !sessionBridge.hasProviderForProfile(profileId, lookupContext)) {
            entry.state = FetchState.PLACEHOLDER;
            entry.skinLocation = STEVE;
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

        if (textures == null || !textures.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            // Profile exists but has no skin texture
            entry.state = FetchState.PLACEHOLDER;
            entry.skinLocation = STEVE;
            entry.lastAttemptMs = System.currentTimeMillis();
            return;
        }

        MinecraftProfileTexture skinTexture = textures.get(MinecraftProfileTexture.Type.SKIN);
        final MinecraftProfileTexture finalTexture = skinTexture;
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                    SkinManager skinManager = Minecraft.getMinecraft()
                        .func_152342_ad();
                    skinManager.func_152792_a(finalTexture, MinecraftProfileTexture.Type.SKIN);
                    entry.skinLocation = new ResourceLocation("skins/" + finalTexture.getHash());
                    entry.state = FetchState.RESOLVED;
                    entry.resolvedAtMs = System.currentTimeMillis();
                    entry.retryCount = 0;
                } catch (Exception e) {
                    WawelAuth.debug("Failed to register skin texture for " + profileId + ": " + e.getMessage());
                    handleFetchFailure(entry);
                }
            });
    }

    private static void handleFetchFailure(SkinEntry entry) {
        entry.retryCount++;
        entry.lastAttemptMs = System.currentTimeMillis();
        entry.state = entry.retryCount >= MAX_RETRIES ? FetchState.FAILED : FetchState.PLACEHOLDER;
        if (entry.skinLocation == null) {
            entry.skinLocation = STEVE;
        }
    }

    private <T> T runWithLookupContext(SessionBridge.LookupContext lookupContext, Supplier<T> action) {
        if (lookupContext == null) {
            return action.get();
        }
        return sessionBridge.withLookupContext(lookupContext, action);
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

    private static final class SkinEntry {

        final UUID profileId;
        final String displayName;
        final SkinRequest request;
        final AtomicBoolean fetchInFlight = new AtomicBoolean(false);

        volatile SessionBridge.LookupContext lookupContext;
        volatile FetchState state = FetchState.PENDING;
        volatile ResourceLocation skinLocation;
        volatile long resolvedAtMs;
        volatile long lastAttemptMs;
        volatile int retryCount;

        SkinEntry(UUID profileId, String displayName, SkinRequest request, SessionBridge.LookupContext lookupContext) {
            this.profileId = profileId;
            this.displayName = displayName;
            this.request = request;
            this.lookupContext = lookupContext;
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

    private static final class LookupHint {

        private final String cacheKey;
        private final SessionBridge.LookupContext lookupContext;

        private LookupHint(String cacheKey, SessionBridge.LookupContext lookupContext) {
            this.cacheKey = cacheKey;
            this.lookupContext = lookupContext;
        }
    }
}
