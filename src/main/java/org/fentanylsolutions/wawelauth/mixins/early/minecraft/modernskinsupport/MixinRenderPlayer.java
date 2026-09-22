package org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport;

import static org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper.SkinLayer.RIGHT_SLEEVE;

import java.util.UUID;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.api.modernskinsupport.IModelBipedModern;
import org.fentanylsolutions.wawelauth.api.modernskinsupport.RenderPassLayer;
import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.render.SkinModelHelper;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.config.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

/**
 * Replaces legacy 64x32 model with modern 64x64,
 * handles swap between slim/classic arms per player, render first-person arm overlay,
 * and manages 3D skin layer state per player.
 */
@Mixin(RenderPlayer.class)
public class MixinRenderPlayer {

    @Shadow
    public ModelBiped modelBipedMain;

    // ========================================
    // I N J E C T S
    // ========================================

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initModernModel(CallbackInfo ci) {
        ((IModelBipedModern) this.modelBipedMain).init(0.0F);
    }

    @WrapWithCondition(
        method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBiped;renderCloak(F)V"))
    private boolean capeTranslucentSort(ModelBiped instance, float scale) {
        RenderPassLayer pass = RenderPassLayer.getCurrent();
        return pass.shouldRender(((IModelBipedModern) instance).isBehindTranslucent());
    }

    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("HEAD"))
    private void doRender_Pre(AbstractClientPlayer player, double x, double y, double z, float yaw, float partialTicks,
        CallbackInfo ci) {
        UUID uuid = player.getUniqueID();
        SkinModel model = SkinModelHelper.getSkinModel(player);
        this.setSlimPerPlayer(uuid, model);
        this.update3DState(uuid, model, player, x, y, z);
    }

    @Inject(method = "renderFirstPersonArm", at = @At("HEAD"))
    private void renderFirstPersonArm_Pre(EntityPlayer player, CallbackInfo ci) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) return;

        UUID uuid = player.getUniqueID();
        SkinModel model = SkinModelHelper.getSkinModel(clientPlayer);
        this.setSlimPerPlayer(uuid, model);
        this.update3DStateNoLOD(uuid, model, clientPlayer);
    }

    @Inject(method = "renderFirstPersonArm", at = @At(value = "TAIL"))
    private void renderFirstPersonArm_Post(EntityPlayer player, CallbackInfo ci) {
        IModelBipedModern bipedModern = (IModelBipedModern) this.modelBipedMain;
        SkinLayersHelper.LayerState state = SkinLayersHelper.getSkinLayerState(player, RIGHT_SLEEVE);
        UUID uuid = player.getUniqueID();
        SkinLayers3DState state3d = SkinLayers3DSetup.getState(uuid);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            bipedModern.renderLayer(RIGHT_SLEEVE, state, state3d, 0.0625F);
        } finally {
            GL11.glPopAttrib();
        }
    }

    // ========================================
    // M E T H O D S | C U S T O M
    // ========================================

    @Unique
    private void setSlimPerPlayer(UUID uuid, SkinModel model) {
        IModelBipedModern bipedModern = (IModelBipedModern) this.modelBipedMain;
        bipedModern.setRenderPlayerUUID(uuid);
        bipedModern.setSkinModel(model);
    }

    @Unique
    private void update3DState(UUID uuid, SkinModel model, AbstractClientPlayer player, double x, double y, double z) {
        if (!SkinLayers3DConfig.enabled3D) {
            SkinLayers3DSetup.updateState(uuid, null);
            return;
        }

        double distSq = x * x + y * y + z * z;
        int lodDist = SkinLayers3DConfig.renderDistanceLOD;
        if (distSq < (double) lodDist * lodDist) {
            update3DStateNoLOD(uuid, model, player);
        } else {
            SkinLayers3DSetup.updateState(uuid, null);
        }
    }

    @Unique
    private void update3DStateNoLOD(UUID uuid, SkinModel model, AbstractClientPlayer player) {
        if (!SkinLayers3DConfig.enabled3D) {
            SkinLayers3DSetup.updateState(uuid, null);
            return;
        }

        SkinLayers3DState existing = SkinLayers3DSetup.getState(uuid);
        SkinLayers3DState state = SkinLayers3DSetup.createOrUpdate(player, existing, model.isSlim());
        SkinLayers3DSetup.updateState(uuid, state);
    }

}
