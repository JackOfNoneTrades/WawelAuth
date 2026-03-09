package org.fentanylsolutions.wawelauth.wawelserver;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.fentanylsolutions.wawelauth.api.WawelFaceRenderer;

/**
 * Normalizes non-standard skins into the minimal 64x64 layout Dynmap expects.
 */
public final class DynmapSkinImageNormalizer {

    private DynmapSkinImageNormalizer() {}

    public static BufferedImage normalizeForDynmap(BufferedImage skin) {
        if (skin == null) {
            return null;
        }

        int width = skin.getWidth();
        int height = skin.getHeight();
        if ((width == 64 && height == 32) || (width == 64 && height == 64)) {
            return skin;
        }
        if (width < 16 || height < 16) {
            return skin;
        }

        boolean legacyLayout = width == height * 2;
        float uScale = width / 64.0F;
        float vScale = height / (legacyLayout ? 32.0F : 64.0F);

        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            BufferedImage face = WawelFaceRenderer.renderFace(skin, 8);
            g.drawImage(face, 8, 8, null);

            drawScaledRegion(g, skin, 20, 20, 28, 32, 20, 20, 28, 32, uScale, vScale);
            drawScaledRegion(g, skin, 4, 20, 8, 32, 4, 20, 8, 32, uScale, vScale);
            drawScaledRegion(g, skin, 44, 20, 48, 32, 44, 20, 48, 32, uScale, vScale);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void drawScaledRegion(Graphics2D g, BufferedImage skin, int dstX0, int dstY0, int dstX1, int dstY1,
        int srcU0, int srcV0, int srcU1, int srcV1, float uScale, float vScale) {
        int srcX0 = scaleCoord(srcU0, uScale, skin.getWidth());
        int srcY0 = scaleCoord(srcV0, vScale, skin.getHeight());
        int srcX1 = scaleCoord(srcU1, uScale, skin.getWidth());
        int srcY1 = scaleCoord(srcV1, vScale, skin.getHeight());
        if (!hasArea(srcX0, srcY0, srcX1, srcY1, skin.getWidth(), skin.getHeight())) {
            return;
        }
        g.drawImage(skin, dstX0, dstY0, dstX1, dstY1, srcX0, srcY0, srcX1, srcY1, null);
    }

    private static int scaleCoord(int uv, float scale, int limit) {
        int scaled = Math.round(uv * scale);
        if (scaled < 0) {
            return 0;
        }
        return Math.min(limit, scaled);
    }

    private static boolean hasArea(int x0, int y0, int x1, int y1, int maxWidth, int maxHeight) {
        if (x0 < 0 || y0 < 0 || x1 > maxWidth || y1 > maxHeight) {
            return false;
        }
        return x1 > x0 && y1 > y0;
    }
}
