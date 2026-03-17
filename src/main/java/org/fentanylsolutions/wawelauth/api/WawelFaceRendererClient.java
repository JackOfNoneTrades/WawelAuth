package org.fentanylsolutions.wawelauth.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

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
        if (skin == null) skin = WawelTextureResolver.getDefaultSkin();

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(skin);

        int texWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int texHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        if (texWidth <= 0 || texHeight <= 0) {
            texWidth = 64;
            texHeight = 64;
        }

        boolean legacyLayout = texWidth == texHeight * 2;
        float uScale = texWidth / 64.0F;
        float vScale = texHeight / (legacyLayout ? 32.0F : 64.0F);

        int sampleW = Math.max(1, Math.round(8.0F * uScale));
        int sampleH = Math.max(1, Math.round(8.0F * vScale));

        float baseU = 8.0F * uScale;
        float baseV = 8.0F * vScale;
        drawTexQuad(x, y, baseU, baseV, sampleW, sampleH, width, height, texWidth, texHeight, alpha);

        // Hat/overlay layer at (40,8) in both legacy and modern formats
        float hatU = 40.0F * uScale;
        float hatV = 8.0F * vScale;
        if (hatU + sampleW <= texWidth && hatV + sampleH <= texHeight) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            drawTexQuad(x, y, hatU, hatV, sampleW, sampleH, width, height, texWidth, texHeight, alpha);
        }
    }

    /**
     * Draw a textured quad with precise float UV coordinates.
     * Used internally by {@link WawelFaceRendererClient#drawFace}.
     */
    private static void drawTexQuad(float x, float y, float u, float v, int uWidth, int vHeight, int width, int height,
        float tileWidth, float tileHeight, float alpha) {
        float uNorm = 1.0F / tileWidth;
        float vNorm = 1.0F / tileHeight;
        float u0 = u * uNorm;
        float u1 = (u + uWidth) * uNorm;
        float v0 = v * vNorm;
        float v1 = (v + vHeight) * vNorm;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, u0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0, u0, v0);
        tessellator.draw();
    }
}
