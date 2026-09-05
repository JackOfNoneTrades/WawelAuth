package org.fentanylsolutions.wawelauth.api;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Shared skin image utilities used by both the server texture pipeline
 * and the client-side skin loader.
 */
public final class SkinImageUtil {

    private SkinImageUtil() {}

    /**
     * Converts a legacy 64x32-ratio skin to 64x64 by mirroring the right
     * leg/arm regions to the left side (matching vanilla Minecraft's conversion).
     * Supports HD skins (128x64, etc.) via scale factor.
     * Returns the image unchanged if it is not a 2:1 legacy skin.
     */
    public static BufferedImage convertLegacySkin(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (h * 2 != w) return image;

        int scale = w / 64;
        BufferedImage converted = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = converted.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }

        // Mirror right leg to left leg
        copyMirrored(image, converted, scale, 4, 16, 4, 4, 20, 48);
        copyMirrored(image, converted, scale, 8, 16, 4, 4, 24, 48);
        copyMirrored(image, converted, scale, 0, 20, 4, 12, 24, 52);
        copyMirrored(image, converted, scale, 4, 20, 4, 12, 20, 52);
        copyMirrored(image, converted, scale, 8, 20, 4, 12, 16, 52);
        copyMirrored(image, converted, scale, 12, 20, 4, 12, 28, 52);

        // Mirror right arm to left arm
        copyMirrored(image, converted, scale, 44, 16, 4, 4, 36, 48);
        copyMirrored(image, converted, scale, 48, 16, 4, 4, 40, 48);
        copyMirrored(image, converted, scale, 40, 20, 4, 12, 40, 52);
        copyMirrored(image, converted, scale, 44, 20, 4, 12, 36, 52);
        copyMirrored(image, converted, scale, 48, 20, 4, 12, 32, 52);
        copyMirrored(image, converted, scale, 52, 20, 4, 12, 44, 52);

        return converted;
    }

    /**
     * Copies a rectangular region from src to dst, flipping horizontally.
     * All coordinates are in base 64x64 units, scaled by the given factor.
     */
    private static void copyMirrored(BufferedImage src, BufferedImage dst, int scale, int srcX, int srcY, int w, int h,
        int dstX, int dstY) {
        int sx = srcX * scale, sy = srcY * scale;
        int sw = w * scale, sh = h * scale;
        int dx = dstX * scale, dy = dstY * scale;

        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                dst.setRGB(dx + sw - 1 - x, dy + y, src.getRGB(sx + x, sy + y));
            }
        }
    }
}
