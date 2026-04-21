package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.UUID;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import org.fentanylsolutions.wawelauth.client.render.IModelBipedModernExt;
import org.fentanylsolutions.wawelauth.client.render.SkinModelHelper;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.common.ISkinLayerExtender;
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
            ((IModelBipedModernExt) this.modelBipedMain).initModern();
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
        UUID uuid = player.getUniqueID();
        ext.setCurrentPlayerUuid(uuid);

        if (!SkinLayers3DConfig.modernSkinSupport) {
            ext.setSlim(false);
            SkinLayers3DSetup.updateState(uuid, null);
            return;
        }
        if (!ext.isModern()) {
            ext.initModern();
        }

        SkinModel model = SkinModelHelper.getSkinModel(player);
        boolean slim = model == SkinModel.SLIM;
        ext.setSlim(slim);

        if (!SkinLayers3DConfig.enabled3D) {
            SkinLayers3DSetup.updateState(uuid, null);
            return;
        }

        // 3D skin layers: check LOD distance
        double distSq = x * x + y * y + z * z;
        int lodDist = SkinLayers3DConfig.renderDistanceLOD;
        if (distSq < (double) lodDist * lodDist) {
            SkinLayers3DState existing = SkinLayers3DSetup.getState(uuid);
            SkinLayers3DState state = SkinLayers3DSetup.createOrUpdate(player, existing, slim);
            SkinLayers3DSetup.updateState(uuid, state);
        } else {
            // Beyond LOD range: fall back to 2D overlays
            SkinLayers3DSetup.updateState(uuid, null);
        }
    }

    /**
     * Set slim/classic before first-person arm rendering.
     */
    @Inject(method = "renderFirstPersonArm", at = @At("HEAD"))
    private void wawelauth$setSlimFirstPersonArm(EntityPlayer player, CallbackInfo ci) {
        IModelBipedModernExt ext = (IModelBipedModernExt) this.modelBipedMain;
        UUID uuid = player.getUniqueID();
        ext.setCurrentPlayerUuid(uuid);
        SkinLayers3DState state = null;

        if (!SkinLayers3DConfig.modernSkinSupport) {
            ext.setSlim(false);
            SkinLayers3DSetup.updateState(uuid, null);
            return;
        }
        if (!ext.isModern()) {
            ext.initModern();
        }
        if (player instanceof AbstractClientPlayer clientPlayer) {
            SkinModel model = SkinModelHelper.getSkinModel(clientPlayer);
            boolean slim = model == SkinModel.SLIM;
            ext.setSlim(slim);

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

        // Apply right sleeve visibility for first-person arm
        if (((ISkinLayerExtender) player).wawelAuth$getHideRightSleeve()) {
            ext.getRightArmWear().showModel = false;
        }

        // TODO: find a better solution instead of disabling
        if (SkinLayers3DConfig.hideOverlayArmor) {
            ItemStack chest = player.inventory.armorInventory[2];
            if (chest != null && chest.getItem() instanceof ItemArmor) {
                boolean allowFirstPerson3DWithArmor = SkinLayers3DConfig.showFirstPerson3DLayersWithArmor
                    && SkinLayers3DConfig.enabled3D
                    && SkinLayers3DConfig.enableRightSleeve3D
                    && state != null
                    && state.initialized
                    && state.rightSleeveMesh != null
                    && state.rightSleeveMesh.isCompiled();
                if (!allowFirstPerson3DWithArmor) {
                    ext.getRightArmWear().showModel = false;
                }
            }
        }

    }

    /**
     * Renders right arm overlay layer before first-person arm base renders
     */
    @Inject(
        method = "renderFirstPersonArm",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V",
            shift = At.Shift.BEFORE))
    private void wawelauth$renderFirstPersonArmWearPre(EntityPlayer player, CallbackInfo ci) {
        IModelBipedModernExt ext = (IModelBipedModernExt) this.modelBipedMain;
        ext.render3DRightArmWear(0.0625F);
    }

    /**
     * Returns right arm overlay layer visibility after first-person arm base renders
     */
    @Inject(method = "renderFirstPersonArm", at = @At(value = "TAIL"))
    private void wawelauth$renderFirstPersonArmWearPost(EntityPlayer player, CallbackInfo ci) {
        IModelBipedModernExt ext = (IModelBipedModernExt) this.modelBipedMain;
        ext.getRightArmWear().showModel = true;
    }
}
