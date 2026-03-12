package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.client.render.IModelBipedModernExt;
import org.fentanylsolutions.wawelauth.client.render.SkinModelHelper;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into RenderPlayer to initialize the modern 64x64 model,
 * swap between slim/classic arms per player, render first-person arm overlay,
 * and manage 3D skin layer state per player.
 */
@Mixin(RenderPlayer.class)
public class MixinRenderPlayer {

    @Shadow
    public ModelBiped modelBipedMain;

    /**
     * Initialize the main player model for modern 64x64 rendering.
     * Called once during RenderPlayer construction. Only affects modelBipedMain,
     * not armor models (modelArmorChestplate, modelArmor).
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void wawelauth$initModernModel(CallbackInfo ci) {
        if (SkinLayers3DConfig.modernSkinSupport) {
            ((IModelBipedModernExt) this.modelBipedMain).wawelauth$initModern();
        }
    }

    /**
     * Before rendering each player, detect their skin model type from the
     * GameProfile textures property and swap arm ModelRenderers accordingly.
     * Also set up 3D skin layer meshes if the player is within LOD range.
     */
    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("HEAD"))
    private void wawelauth$setSlimPerPlayer(AbstractClientPlayer player, double x, double y, double z, float yaw,
        float partialTicks, CallbackInfo ci) {
        IModelBipedModernExt ext = (IModelBipedModernExt) this.modelBipedMain;
        if (!SkinLayers3DConfig.modernSkinSupport) {
            ext.wawelauth$setSlim(false);
            ext.wawelauth$setSkinLayers3D(null);
            return;
        }
        if (!ext.wawelauth$isModern()) {
            ext.wawelauth$initModern();
        }

        SkinModel model = SkinModelHelper.getSkinModel(player);
        boolean slim = model == SkinModel.SLIM;
        ext.wawelauth$setSlim(slim);

        if (!SkinLayers3DConfig.enabled) {
            ext.wawelauth$setSkinLayers3D(null);
            return;
        }

        // 3D skin layers: check LOD distance
        double distSq = x * x + y * y + z * z;
        int lodDist = SkinLayers3DConfig.renderDistanceLOD;
        if (distSq < (double) lodDist * lodDist) {
            // Within LOD range: set up or reuse 3D meshes
            SkinLayers3DState existing = ext.wawelauth$getSkinLayers3D();
            SkinLayers3DState state = SkinLayers3DSetup.createOrUpdate(player, existing, slim);
            ext.wawelauth$setSkinLayers3D(state);
        } else {
            // Beyond LOD range: fall back to 2D overlays
            ext.wawelauth$setSkinLayers3D(null);
        }
    }

    /**
     * Set slim/classic before first-person arm rendering.
     */
    @Inject(method = "renderFirstPersonArm", at = @At("HEAD"))
    private void wawelauth$setSlimFirstPersonArm(EntityPlayer player, CallbackInfo ci) {
        IModelBipedModernExt ext = (IModelBipedModernExt) this.modelBipedMain;
        if (!SkinLayers3DConfig.modernSkinSupport) {
            ext.wawelauth$setSlim(false);
            ext.wawelauth$setSkinLayers3D(null);
            return;
        }
        if (!ext.wawelauth$isModern()) {
            ext.wawelauth$initModern();
        }
        if (player instanceof AbstractClientPlayer clientPlayer) {
			SkinModel model = SkinModelHelper.getSkinModel(clientPlayer);
            boolean slim = model == SkinModel.SLIM;
            ext.wawelauth$setSlim(slim);

            if (SkinLayers3DConfig.enabled) {
                SkinLayers3DState existing = ext.wawelauth$getSkinLayers3D();
                SkinLayers3DState state = SkinLayers3DSetup.createOrUpdate(clientPlayer, existing, slim);
                ext.wawelauth$setSkinLayers3D(state);
            } else {
                ext.wawelauth$setSkinLayers3D(null);
            }
        } else {
            ext.wawelauth$setSkinLayers3D(null);
        }
    }

    /**
     * Render the right arm overlay layer after the first-person arm base renders.
     */
    @Inject(method = "renderFirstPersonArm", at = @At("TAIL"))
    private void wawelauth$renderFirstPersonArmWear(EntityPlayer player, CallbackInfo ci) {
        ((IModelBipedModernExt) this.modelBipedMain).wawelauth$renderRightArmWear(0.0625F);
    }
}
