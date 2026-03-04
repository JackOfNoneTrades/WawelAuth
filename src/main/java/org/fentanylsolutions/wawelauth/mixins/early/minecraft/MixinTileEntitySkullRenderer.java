package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;

/**
 * Renders 3D hat layer on player skull blocks/items.
 *
 * In 1.7.10, TileEntitySkullRenderer.func_152674_a handles skull rendering.
 * Player skulls have skullType == 3 and a non-null GameProfile.
 */
@Mixin(TileEntitySkullRenderer.class)
public class MixinTileEntitySkullRenderer {

    // todo: replace 64x32 with 64x64 if SkinLayers3DConfig.modernSkinSupport
    @Shadow
    private ModelSkeletonHead field_147533_g = new ModelSkeletonHead(0, 0, 64, 32);

    /**
     * Inject after skull rendering to add 3D hat overlay for player skulls.
     * func_152674_a is the MCP deobfuscated name for the skull render method.
     */
    @Inject(
        method = "func_152674_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelSkeletonHead;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
            shift = At.Shift.AFTER))
    private void wawelauth$renderSkull3DHat(float x, float y, float z, int facing, float rotation, int skullType,
        GameProfile profile, CallbackInfo ci) {
        if (!SkinLayers3DConfig.enabled || !SkinLayers3DConfig.enableSkulls) return;
        if (skullType != 3 || profile == null) return; // 3 = player skull

        // Get the player's skin texture location (same lookup vanilla uses)
        ResourceLocation skinLocation = wawelauth$getSkinForProfile(profile);
        if (skinLocation == null) return;

        SkinLayers3DMesh hatMesh = SkinLayers3DSetup.getOrCreateSkullMesh(profile, skinLocation);
        if (hatMesh == null) return;

        ModelRenderer head = this.field_147533_g.skeletonHead;

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

    /**
     * Resolve the skin ResourceLocation for a GameProfile, matching the vanilla
     * skull renderer's lookup path through the SkinManager.
     */
    @Unique
    @SuppressWarnings("unchecked")
    private static ResourceLocation wawelauth$getSkinForProfile(GameProfile profile) {
        SkinManager skinManager = Minecraft.getMinecraft()
            .func_152342_ad();
        Map<Type, MinecraftProfileTexture> textures = skinManager.func_152788_a(profile);
        if (textures.containsKey(Type.SKIN)) {
            return skinManager.func_152792_a(textures.get(Type.SKIN), Type.SKIN);
        }
        return null;
    }
}
