package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.SkinRequest;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;

/**
 * Renders 3D hat layer on player skull blocks/items.
 *
 * In 1.7.10, TileEntitySkullRenderer.func_152674_a handles skull rendering.
 * Player skulls have skullType == 3 and a non-null GameProfile.
 */
@Mixin(TileEntitySkullRenderer.class)
public class MixinTileEntitySkullRenderer {

    @Shadow
    private ModelSkeletonHead field_147538_h = new ModelSkeletonHead(0, 0, 64, 64);

    @ModifyVariable(method = "func_152674_a", at = @At("STORE"), ordinal = 0)
    private ModelSkeletonHead wawelauth$replaceSkullModel(ModelSkeletonHead original,
        @Local(index = 6, argsOnly = true) int p_152674_6_) {
        if (SkinLayers3DConfig.modernSkinSupport && p_152674_6_ == 3) {
            return field_147538_h;
        }
        return original;
    }

    /**
     * Inject after skull rendering to add 3D hat overlay for player skulls.
     */
    @Inject(
        method = "func_152674_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelSkeletonHead;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
            shift = At.Shift.AFTER))
    private void wawelauth$renderSkull3DHat(float x, float y, float z, int facing, float rotation, int skullType,
        GameProfile profile, CallbackInfo ci, @Local ModelSkeletonHead modelskeletonhead) {
        if (!SkinLayers3DConfig.enabled || !SkinLayers3DConfig.enableSkulls) return;
        if (skullType != 3 || profile == null) return; // 3 = player skull

        // Get the player's skin texture location
        ResourceLocation skinLocation = wawelauth$getSkinForProfile(profile);
        if (skinLocation == null) return;

        SkinLayers3DMesh hatMesh = SkinLayers3DSetup.getOrCreateSkullMesh(profile, skinLocation);
        if (hatMesh == null) return;

        ModelRenderer head = modelskeletonhead.skeletonHead;

        // The skin texture is already bound by the vanilla skull renderer.
        // Render the 3D hat mesh.

        float scale = 1.0F / 16.0F;
        float voxelSize = SkinLayers3DConfig.skullVoxelSize;

        // Enable alpha blending for semi-transparent pixels
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        hatMesh.setPosition(0, 0, 0);
        hatMesh.setRotation(head.rotateAngleX, head.rotateAngleY, head.rotateAngleZ);
        hatMesh.render(scale, voxelSize);

        GL11.glDisable(GL11.GL_BLEND);
    }

    @Inject(
        method = "func_152674_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySkullRenderer;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 3,
            shift = At.Shift.BEFORE))
    private void wawelauth$replaceSkin(float x, float y, float z, int facing, float rotation, int skullType,
        GameProfile profile, CallbackInfo ci, @Local LocalRef<ResourceLocation> resourcelocation) {
        resourcelocation.set(wawelauth$getSkinForProfile(profile));
    }

    /**
     * Resolve the skin ResourceLocation for a GameProfile.
     */
    @Unique
    private static ResourceLocation wawelauth$getSkinForProfile(GameProfile profile) {
        WawelClient client = WawelClient.instance();
        if (client == null) return null;

        return client.getSkinResolver()
            .getSkin(profile.getId(), profile.getName(), SkinRequest.DEFAULT);
    }

}
