package org.fentanylsolutions.wawelauth.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.fentlib.util.ClientUtil;
import org.fentanylsolutions.wawelauth.client.render.SkinTextureState;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WawelFaceRendererClient {

    /**
     * Draw a 8x8 player face from a skin {@link ResourceLocation}.
     * <p>
     * Handles both legacy (64x32) and modern (64x64) skin formats,
     * as well as HD textures of any resolution. Draws the base face
     * layer and the hat/overlay layer on top.
     * <p>
     * The caller is responsible for binding GL state (blend, lighting, etc.)
     * before calling this method.
     *
     * @param skin  the skin texture ResourceLocation
     * @param x     screen x position
     * @param y     screen y position
     * @param alpha opacity (0.0 = transparent, 1.0 = opaque)
     */
    public static void drawFace(ResourceLocation skin, float x, float y, float alpha) {
        drawFace(skin, x, y, 8, 8, alpha);
    }

    /**
     * Draw a player face at arbitrary dimensions.
     * <p>
     * Same as {@link WawelFaceRendererClient#drawFace(ResourceLocation, float, float, float)} but
     * the output is scaled to {@code width x height} pixels instead of 8x8.
     *
     * @param skin   the skin texture ResourceLocation
     * @param x      screen x position
     * @param y      screen y position
     * @param width  output width in pixels
     * @param height output height in pixels
     * @param alpha  opacity (0.0 = transparent, 1.0 = opaque)
     */
    public static void drawFace(ResourceLocation skin, float x, float y, int width, int height, float alpha) {
        skin = resolveBindableSkin(skin);
        if (skin == null) {
            return;
        }

        ClientUtil.drawPlayerFace(skin, WawelTextureResolver.getDefaultSkin(), x, y, width, height, alpha);
    }

    private static ResourceLocation resolveBindableSkin(ResourceLocation skin) {
        ResourceLocation fallback = WawelTextureResolver.getDefaultSkin();
        if (skin == null) {
            return fallback;
        }
        if (skin.equals(fallback) || skin.equals(WawelTextureResolver.getLegacyDefaultSkin())) {
            return skin;
        }

        ITextureObject textureObject = Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(skin);
        return SkinTextureState.isReadyForRender(textureObject) ? skin : fallback;
    }

}
