package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;

/**
 * Skull skin resolution + 3D hat layer.
 * Uses HEAD to capture args, ModifyVariable for model swap,
 * RETURN to re-render with resolved texture + 3D hat.
 */
@Mixin(TileEntitySkullRenderer.class)
public class MixinTileEntitySkullRenderer {

    @Shadow
    private ModelSkeletonHead field_147533_g;

    @Shadow
    private ModelSkeletonHead field_147538_h;

    /** Swap to 64x64 UV model for player skulls when modern skin support is on. */
    @ModifyVariable(method = "func_152674_a", at = @At("STORE"), ordinal = 0)
    private ModelSkeletonHead wawelauth$replaceSkullModel(ModelSkeletonHead original,
        @Local(index = 6, argsOnly = true) int skullType) {
        if (SkinLayers3DConfig.modernSkinSupport && skullType == 3) {
            return field_147538_h;
        }
        return original;
    }

    /** After vanilla renders (with Steve), re-render with our resolved skin + 3D hat. */
    @Inject(method = "func_152674_a", at = @At("RETURN"))
    private void wawelauth$renderWithResolvedSkin(float x, float y, float z, int facing, float rotation, int skullType,
        GameProfile profile, CallbackInfo ci) {
        if (skullType != 3 || profile == null) return;

        ResourceLocation skin = wawelauth$resolveSkin(profile);
        if (skin == null || skin.equals(WawelTextureResolver.getDefaultSkin())
            || skin.equals(WawelTextureResolver.getLegacyDefaultSkin())) {
            return;
        }

        ModelSkeletonHead model = SkinLayers3DConfig.modernSkinSupport ? field_147538_h : field_147533_g;

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(skin);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_CULL_FACE);

        if (facing != 1) {
            switch (facing) {
                case 2:
                    GL11.glTranslatef(x + 0.5F, y + 0.25F, z + 0.74F);
                    break;
                case 3:
                    GL11.glTranslatef(x + 0.5F, y + 0.25F, z + 0.26F);
                    rotation = 180.0F;
                    break;
                case 4:
                    GL11.glTranslatef(x + 0.74F, y + 0.25F, z + 0.5F);
                    rotation = 270.0F;
                    break;
                case 5:
                default:
                    GL11.glTranslatef(x + 0.26F, y + 0.25F, z + 0.5F);
                    rotation = 90.0F;
                    break;
            }
        } else {
            GL11.glTranslatef(x + 0.5F, y, z + 0.5F);
        }

        float scale = 0.0625F;
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(-1.0F, -1.0F, 1.0F);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        model.render(null, 0.0F, 0.0F, 0.0F, rotation, 0.0F, scale);

        // 3D hat overlay
        if (SkinLayers3DConfig.enabled && SkinLayers3DConfig.enableSkulls) {
            SkinLayers3DMesh hatMesh = SkinLayers3DSetup.getOrCreateSkullMesh(profile, skin);
            if (hatMesh != null) {
                float voxelSize = SkinLayers3DConfig.skullVoxelSize;
                GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                hatMesh.setPosition(0, 0, 0);
                hatMesh.setRotation(
                    model.skeletonHead.rotateAngleX,
                    model.skeletonHead.rotateAngleY,
                    model.skeletonHead.rotateAngleZ);
                hatMesh.render(scale, voxelSize);

                GL11.glPopAttrib();
            }
        }

        GL11.glPopMatrix();
    }

    @Unique
    private static ResourceLocation wawelauth$resolveSkin(GameProfile profile) {
        if (profile == null || profile.getId() == null) return null;

        WawelClient client = WawelClient.instance();
        if (client == null) return null;

        // Skulls can belong to any provider, try all trusted ones
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
