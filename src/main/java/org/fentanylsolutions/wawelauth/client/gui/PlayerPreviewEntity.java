package org.fentanylsolutions.wawelauth.client.gui;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.client.render.ISkinModelOverride;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

import com.cleanroommc.modularui.utils.fakeworld.DummyWorld;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PlayerPreviewEntity extends EntityOtherPlayerMP implements ISkinModelOverride {

    private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);
    private boolean capeVisible = true;
    private SkinModel forcedSkinModel;
    private long currentRequestId;

    public PlayerPreviewEntity(GameProfile profile) {
        super(DummyWorld.INSTANCE, profile);
    }

    @Override
    public ResourceLocation getLocationSkin() {
        return forcedSkin != null ? forcedSkin : super.getLocationSkin();
    }

    @Override
    public ResourceLocation getLocationCape() {
        if (!capeVisible) return null;
        return forcedCape != null ? forcedCape : super.getLocationCape();
    }

    @Override
    public boolean func_152122_n() { // AbstractClientPlayer.hasCape
        if (!capeVisible) return false;
        return super.func_152122_n(); // hasCape
    }

    private ResourceLocation forcedSkin;
    private ResourceLocation forcedCape;

    public void prepareTextureUpload() {
        forcedSkin = null;
        forcedCape = null;
    }

    public void clearTextures() {
        this.forcedSkinModel = null;
        forcedSkin = WawelTextureResolver.getDefaultSkin();
        forcedCape = WawelTextureResolver.getDefaultCape();
    }

    public long newRequestId() {
        long id = REQUEST_COUNTER.incrementAndGet();
        this.currentRequestId = id;
        return id;
    }

    public boolean isRequestStale(long requestId) {
        return requestId != this.currentRequestId;
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

    @Override
    public int getBrightnessForRender(float partialTicks) {
        // Preview entity lives in DummyWorld; avoid querying chunk lighting.
        return 0x00F000F0;
    }

    @Override
    public float getBrightness(float partialTicks) {
        return 1.0F;
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

}
