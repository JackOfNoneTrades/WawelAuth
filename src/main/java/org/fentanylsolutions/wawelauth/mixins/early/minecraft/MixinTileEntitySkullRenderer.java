package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;

/**
 * Skull skin resolution + 3D hat layer.
 * Redirects player skull bindTexture (case 3) to use our resolver.
 */
@Mixin(TileEntitySkullRenderer.class)
public class MixinTileEntitySkullRenderer {

    @Shadow
    private ModelSkeletonHead field_147538_h = new ModelSkeletonHead(0, 0, 64, 64);

    /** Current skull's profile for the bindTexture redirect. */
    @Unique
    private GameProfile wawelauth$currentProfile;

    @ModifyVariable(method = "func_152674_a", at = @At("STORE"), ordinal = 0)
    private ModelSkeletonHead wawelauth$replaceSkullModel(ModelSkeletonHead original,
        @Local(index = 6, argsOnly = true) int p_152674_6_) {
        if (SkinLayers3DConfig.modernSkinSupport && p_152674_6_ == 3) {
            return field_147538_h;
        }
        return original;
    }

    /** Stash profile at HEAD for the bindTexture redirect. */
    @Inject(method = "func_152674_a", at = @At("HEAD"))
    private void wawelauth$captureProfile(float x, float y, float z, int facing, float rotation, int skullType,
        GameProfile profile, CallbackInfo ci) {
        this.wawelauth$currentProfile = profile;
    }

    /** Redirect 4th bindTexture (player skull, ordinal 3) to our resolved texture. */
    @Redirect(
        method = "func_152674_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySkullRenderer;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 3))
    private void wawelauth$bindResolvedSkullTexture(TileEntitySkullRenderer self, ResourceLocation vanillaLocation) {
        GameProfile profile = this.wawelauth$currentProfile;
        ResourceLocation skin = wawelauth$resolveSkin(profile);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(skin != null ? skin : vanillaLocation);
    }

    /**
     * 3D hat overlay after skull render.
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
        if (skullType != 3 || profile == null) return;

        ResourceLocation skinLocation = wawelauth$resolveSkin(profile);
        if (skinLocation == null) return;

        SkinLayers3DMesh hatMesh = SkinLayers3DSetup.getOrCreateSkullMesh(profile, skinLocation);
        if (hatMesh == null) return;

        ModelRenderer head = modelskeletonhead.skeletonHead;

        float scale = 1.0F / 16.0F;
        float voxelSize = SkinLayers3DConfig.skullVoxelSize;

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        hatMesh.setPosition(0, 0, 0);
        hatMesh.setRotation(head.rotateAngleX, head.rotateAngleY, head.rotateAngleZ);
        hatMesh.render(scale, voxelSize);

        GL11.glPopAttrib();
    }

    @Unique
    private static ResourceLocation wawelauth$resolveSkin(GameProfile profile) {
        if (profile == null || profile.getId() == null) return null;

        WawelClient client = WawelClient.instance();
        if (client == null) return null;

        // Skulls can belong to any provider. Try all trusted providers.
        java.util.List<ClientProvider> trusted = client.getSessionBridge()
            .getTrustedProviders();
        if (!trusted.isEmpty()) {
            return client.getTextureResolver()
                .getSkinFromAnyProvider(profile.getId(), profile.getName(), trusted);
        }

        // Singleplayer fallback
        if (Minecraft.getMinecraft()
            .isSingleplayer()) {
            ClientProvider mojang = client.getMojangProvider();
            if (mojang != null) {
                return client.getTextureResolver()
                    .getSkin(profile.getId(), profile.getName(), mojang, false);
            }
        }

        return null;
    }
}
