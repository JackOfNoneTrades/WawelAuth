package org.fentanylsolutions.wawelauth.mixins.late.aether_legacy;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.client.render.FirstPersonRenderState;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Renders Aether's legacy 64x32 glove texture independently of WawelAuth's modern arm hierarchy. */
@Pseudo
@Mixin(targets = "com.gildedgames.the_aether.client.renders.AetherItemRenderer", remap = false)
public abstract class MixinAetherItemRenderer {

    @Unique
    private final ModelBiped wawelauth$legacyGloveModel = new ModelBiped();

    /** Suppress WawelAuth's sleeve only in Aether's glove-present branch. */
    @Redirect(
        method = "renderFirstPersonArm",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderPlayer;renderFirstPersonArm(Lnet/minecraft/entity/player/EntityPlayer;)V",
            ordinal = 1,
            remap = true),
        require = 1,
        remap = false)
    private void wawelauth$renderArmWithoutSleeve(RenderPlayer renderer, EntityPlayer player) {
        FirstPersonRenderState.pushRightSleeveSuppression();
        try {
            renderer.renderFirstPersonArm(player);
        } finally {
            FirstPersonRenderState.popRightSleeveSuppression();
        }
    }

    @Redirect(
        method = "renderFirstPersonArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", remap = true),
        require = 1,
        remap = false)
    private void wawelauth$renderLegacyGloveArm(ModelRenderer playerArm, float scale) {
        ModelRenderer gloveArm = this.wawelauth$legacyGloveModel.bipedRightArm;
        gloveArm.rotateAngleX = playerArm.rotateAngleX;
        gloveArm.rotateAngleY = playerArm.rotateAngleY;
        gloveArm.rotateAngleZ = playerArm.rotateAngleZ;

        boolean polygonOffsetEnabled = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1.0F, -10.0F);
        try {
            gloveArm.render(scale);
        } finally {
            GL11.glPolygonOffset(0.0F, 0.0F);
            if (!polygonOffsetEnabled) {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
        }
    }
}
