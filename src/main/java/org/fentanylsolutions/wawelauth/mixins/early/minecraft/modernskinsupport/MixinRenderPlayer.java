package org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import org.fentanylsolutions.wawelauth.api.ModelPlayer;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.render.SkinModelHelper;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.config.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.RIGHT_SLEEVE;

/**
 * Replaces legacy 64x32 model with modern 64x64,
 * handles swap between slim/classic arms per player, render first-person arm overlay,
 * and manages 3D skin layer state per player.
 */
@Mixin(RenderPlayer.class)
public class MixinRenderPlayer {

    @Shadow
    public ModelBiped modelBipedMain;

    /// Copied from OfflineAuth (fork); replace with MixinExtras?
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/model/ModelBiped", ordinal = 0))
    private static ModelBiped init(float p_i1148_1_) {
        return new ModelPlayer(p_i1148_1_);
    }

    /**
     * Before rendering each player, detect their skin model type from the
     * GameProfile textures property and swap arm ModelRenderers accordingly.
     * Also set up 3D skin layer meshes if the player is within LOD range.
     */
    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("HEAD"))
    private void wawelauth$setSlimPerPlayer(AbstractClientPlayer player, double x, double y, double z, float yaw,
        float partialTicks, CallbackInfo ci) {

        ModelPlayer bipedPlayer = (ModelPlayer) this.modelBipedMain;
        UUID uuid = player.getUniqueID();
        bipedPlayer.setRenderPlayerUUID(uuid);

        SkinModel model = SkinModelHelper.getSkinModel(player);
        boolean slim = model == SkinModel.SLIM;
        bipedPlayer.setSlim(slim);

        if (!SkinLayers3DConfig.enabled3D) {
            SkinLayers3DSetup.updateState(uuid, null);
            return;
        }

        double distSq = x * x + y * y + z * z;
        int lodDist = SkinLayers3DConfig.renderDistanceLOD;
        if (distSq < (double) lodDist * lodDist) {
            SkinLayers3DState existing = SkinLayers3DSetup.getState(uuid);
            SkinLayers3DState state = SkinLayers3DSetup.createOrUpdate(player, existing, slim);
            SkinLayers3DSetup.updateState(uuid, state);
        } else {
            SkinLayers3DSetup.updateState(uuid, null);
        }
    }

    /**
     * Set slim/classic before first-person arm rendering.
     */
    @Inject(method = "renderFirstPersonArm", at = @At("HEAD"))
    private void wawelauth$setSlimFirstPersonArm(EntityPlayer player, CallbackInfo ci) {
        ModelPlayer bipedPlayer = (ModelPlayer) this.modelBipedMain;
        UUID uuid = player.getUniqueID();
        bipedPlayer.setRenderPlayerUUID(uuid);
        SkinLayers3DState state;

        if (player instanceof AbstractClientPlayer clientPlayer) {
            SkinModel model = SkinModelHelper.getSkinModel(clientPlayer);
            boolean slim = model == SkinModel.SLIM;
            bipedPlayer.setSlim(slim);

            if (SkinLayers3DConfig.enabled3D) {
                SkinLayers3DState existing = SkinLayers3DSetup.getState(uuid);
                state = SkinLayers3DSetup.createOrUpdate(clientPlayer, existing, slim);
                SkinLayers3DSetup.updateState(uuid, state);
            } else {
                SkinLayers3DSetup.updateState(uuid, null);
            }
        } else {
            SkinLayers3DSetup.updateState(uuid, null);
        }
    }

    /**
     * Renders right arm overlay
     */
    @Inject(method = "renderFirstPersonArm", at = @At(value = "TAIL"))
    private void wawelauth$renderFirstPersonSleevePost(EntityPlayer player, CallbackInfo ci) {
        ModelPlayer bipedPlayer = (ModelPlayer) this.modelBipedMain;
        SkinLayersHelper.PartState state = SkinLayersHelper.getSkinLayerState(player, RIGHT_SLEEVE);
        UUID uuid = player.getUniqueID();
        SkinLayers3DState state3d = SkinLayers3DSetup.getState(uuid);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            bipedPlayer.renderLayer(RIGHT_SLEEVE, state, state3d, 0.0625F, false, true);
        } finally {
            GL11.glPopAttrib();
        }
    }

}
