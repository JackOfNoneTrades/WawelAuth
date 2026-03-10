package org.fentanylsolutions.wawelauth.client.gui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.WawelSkinResolver;
import org.fentanylsolutions.wawelauth.client.render.ISkinModelOverride;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.cleanroommc.modularui.utils.fakeworld.DummyWorld;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PlayerPreviewEntity extends EntityOtherPlayerMP implements ISkinModelOverride {

    private static final ResourceLocation STEVE_SKIN = WawelSkinResolver.STEVE;
    private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);
    private static final AtomicLong ENTITY_COUNTER = new AtomicLong(0);

    private ResourceLocation customSkin;
    private ResourceLocation customCape;
    private boolean capeVisible = true;
    private final String texturePrefix;
    private ResourceLocation currentSkinLocation;
    private ResourceLocation currentCapeLocation;
    private long currentRequestId;
    private SkinModel forcedSkinModel;
    private AnimatedCapeTexture animatedCape;

    public PlayerPreviewEntity(GameProfile profile) {
        super(DummyWorld.INSTANCE, profile);
        this.texturePrefix = "preview/" + ENTITY_COUNTER.incrementAndGet();
    }

    @Override
    public ResourceLocation getLocationSkin() {
        return customSkin != null ? customSkin : super.getLocationSkin();
    }

    @Override
    public ResourceLocation getLocationCape() {
        if (!capeVisible) return null;
        return customCape != null ? customCape : super.getLocationCape();
    }

    @Override
    public boolean func_152122_n() { // AbstractClientPlayer.hasCape
        if (!capeVisible) return false;
        return customCape != null || super.func_152122_n(); // hasCape
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        // Preview entity lives in DummyWorld; avoid querying chunk lighting.
        return 0x00F000F0;
    }

    @Override
    public float getBrightness(float partialTicks) {
        return 1.0F;
    }

    public void setCustomSkin(ResourceLocation skin) {
        this.customSkin = skin;
    }

    public void setCustomCape(ResourceLocation cape) {
        this.customCape = cape;
    }

    public void setCapeVisible(boolean capeVisible) {
        this.capeVisible = capeVisible;
    }

    public void setForcedSkinModel(SkinModel model) {
        this.forcedSkinModel = model;
    }

    @Override
    public SkinModel wawelauth$getForcedSkinModel() {
        return forcedSkinModel;
    }

    public long newRequestId() {
        long id = REQUEST_COUNTER.incrementAndGet();
        this.currentRequestId = id;
        return id;
    }

    public boolean isRequestStale(long requestId) {
        return requestId != this.currentRequestId;
    }

    public void setSkinFromUrl(String url, UUID profileUuid, long requestId) {
        if (isRequestStale(requestId)) return;

        ResourceLocation location = makeSkinLocation(profileUuid);
        loadTexture(location, url, true);

        if (!isRequestStale(requestId)) {
            unloadOldSkin(location);
            this.customSkin = location;
        }
    }

    public void setSkinFromExisting(ResourceLocation location, long requestId) {
        if (isRequestStale(requestId)) return;

        unloadOldSkin(location);
        if (!isRequestStale(requestId)) {
            this.customSkin = location;
        }
    }

    public void setCapeFromUrl(String url, UUID profileUuid, long requestId) {
        if (isRequestStale(requestId)) return;

        ResourceLocation location = makeCapeLocation(profileUuid);
        loadTexture(location, url, false);

        if (!isRequestStale(requestId)) {
            unloadOldCape(location);
            this.customCape = location;
        }
    }

    public void setCapeFromExisting(ResourceLocation location, long requestId) {
        if (isRequestStale(requestId)) return;

        unloadOldCape(location);
        if (!isRequestStale(requestId)) {
            this.customCape = location;
            this.animatedCape = null;
        }
    }

    /**
     * Downloads a GIF from the given URL asynchronously and creates an
     * AnimatedCapeTexture for preview rendering.
     */
    public void setCapeAnimated(String url, UUID profileUuid, long requestId) {
        setCapeAnimated(url, profileUuid, requestId, null);
    }

    public void setCapeAnimated(String url, UUID profileUuid, long requestId, Runnable onReady) {
        if (isRequestStale(requestId)) return;

        final String locationPath = "capes/" + texturePrefix + "/anim/" + UuidUtil.toUnsigned(profileUuid);

        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] gifBytes = downloadBytes(url);
                // Decode on background thread (CPU only, no OpenGL)
                return AnimatedCapeTexture.decodeGif(gifBytes);
            } catch (Exception e) {
                WawelAuth.debug("Failed to download animated cape: " + e.getMessage());
                return null;
            }
        })
            .whenComplete((decoded, err) -> {
                if (decoded == null || isRequestStale(requestId)) return;
                // Create OpenGL texture on main thread
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (isRequestStale(requestId)) return;
                        AnimatedCapeTexture tex = AnimatedCapeTexture.createFromDecoded(decoded, locationPath);
                        if (tex == null) return;
                        unloadOldCape(tex.getResourceLocation());
                        this.animatedCape = tex;
                        this.customCape = tex.getResourceLocation();
                        if (onReady != null) {
                            onReady.run();
                        }
                    });
            });
    }

    /** Ticks the animated cape if present. Call each frame/tick. */
    public void tickAnimatedCape() {
        if (animatedCape != null) {
            animatedCape.tick();
        }
    }

    public void clearTextures() {
        newRequestId();
        unloadOldSkin(null);
        unloadOldCape(null);
        this.customSkin = null;
        this.customCape = null;
        this.forcedSkinModel = null;
        this.animatedCape = null;
    }

    public ResourceLocation getCustomSkinLocation() {
        return customSkin;
    }

    public ResourceLocation getCustomCapeLocation() {
        return customCape;
    }

    /**
     * Preview entities are effectively static. Keep vanilla cloak dynamics at rest
     * so the cape does not start tilted from uninitialized motion history.
     */
    public void stabilizeCapePhysics() {
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.posX = 0.0D;
        this.posY = 0.0D;
        this.posZ = 0.0D;
        this.prevPosX = 0.0D;
        this.prevPosY = 0.0D;
        this.prevPosZ = 0.0D;
        this.lastTickPosX = 0.0D;
        this.lastTickPosY = 0.0D;
        this.lastTickPosZ = 0.0D;
        this.field_71091_bM = 0.0D; // prevChasingPosX (cape physics)
        this.field_71096_bN = 0.0D; // prevChasingPosY
        this.field_71097_bO = 0.0D; // prevChasingPosZ
        this.field_71094_bP = 0.0D; // chasingPosX
        this.field_71095_bQ = 0.0D; // chasingPosY
        this.field_71085_bR = 0.0D; // chasingPosZ
        this.cameraYaw = 0.0F;
        this.prevCameraYaw = 0.0F;
        this.distanceWalkedModified = 0.0F;
        this.prevDistanceWalkedModified = 0.0F;
        this.setSneaking(false);
    }

    private void unloadOldSkin(ResourceLocation newLocation) {
        if (currentSkinLocation != null && !currentSkinLocation.equals(newLocation)) {
            Minecraft.getMinecraft().renderEngine.deleteTexture(currentSkinLocation);
        }
        currentSkinLocation = newLocation;
    }

    private void unloadOldCape(ResourceLocation newLocation) {
        if (currentCapeLocation != null && !currentCapeLocation.equals(newLocation)) {
            Minecraft.getMinecraft().renderEngine.deleteTexture(currentCapeLocation);
        }
        currentCapeLocation = newLocation;
    }

    private ResourceLocation makeSkinLocation(UUID uuid) {
        return new ResourceLocation("wawelauth", "skins/" + texturePrefix + "/" + UuidUtil.toUnsigned(uuid));
    }

    private ResourceLocation makeCapeLocation(UUID uuid) {
        return new ResourceLocation("wawelauth", "capes/" + texturePrefix + "/" + UuidUtil.toUnsigned(uuid));
    }

    private static void loadTexture(ResourceLocation location, String url, boolean useSkinBuffer) {
        TextureManager texMgr = Minecraft.getMinecraft().renderEngine;

        ITextureObject existing = texMgr.getTexture(location);
        if (existing != null) {
            texMgr.deleteTexture(location);
        }

        IImageBuffer buffer = useSkinBuffer ? new ImageBufferDownload() : null;
        ThreadDownloadImageData texture = new ThreadDownloadImageData(null, url, STEVE_SKIN, buffer);

        texMgr.loadTexture(location, texture);
        WawelAuth.debug("Loading texture from " + url + " at " + location);
    }

    private static byte[] downloadBytes(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "WawelAuth");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code + " from " + urlStr);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                return baos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }
}
