package org.fentanylsolutions.wawelauth.api;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.fentanylsolutions.wawelauth.wawelnet.NetException;

/**
 * Server-safe face cutout renderer for vanilla and HD skins.
 */
public final class WawelFaceRenderer {

    private WawelFaceRenderer() {}

    public static byte[] renderFacePng(byte[] skinBytes, int outputSize) {
        try {
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinBytes));
            BufferedImage rendered = renderFace(skin, outputSize);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rendered, "png", out);
            return out.toByteArray();
        } catch (NetException e) {
            throw e;
        } catch (Exception e) {
            throw NetException.notFound("Failed to render avatar.");
        }
    }

    public static BufferedImage renderFace(BufferedImage skin, int outputSize) {
        if (skin == null) {
            throw NetException.notFound("Invalid skin image.");
        }
        if (outputSize < 1) {
            throw NetException.notFound("Invalid avatar size.");
        }

        int width = skin.getWidth();
        int height = skin.getHeight();
        if (width < 16 || height < 16) {
            throw NetException.notFound("Skin image is too small.");
        }

        boolean legacyLayout = isLegacySkinLayout(width, height);
        float uScale = width / 64.0F;
        float vScale = height / (legacyLayout ? 32.0F : 64.0F);

        int faceX0 = scaleCoord(8, uScale, width);
        int faceY0 = scaleCoord(8, vScale, height);
        int faceX1 = scaleCoord(16, uScale, width);
        int faceY1 = scaleCoord(16, vScale, height);
        if (!hasArea(faceX0, faceY0, faceX1, faceY1, width, height)) {
            throw NetException.notFound("Skin image missing face region.");
        }

        int hatX0 = scaleCoord(40, uScale, width);
        int hatY0 = faceY0;
        int hatX1 = scaleCoord(48, uScale, width);
        int hatY1 = faceY1;
        boolean hasHatLayer = hasArea(hatX0, hatY0, hatX1, hatY1, width, height);

        BufferedImage out = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(skin, 0, 0, outputSize, outputSize, faceX0, faceY0, faceX1, faceY1, null);
            if (hasHatLayer) {
                g.drawImage(skin, 0, 0, outputSize, outputSize, hatX0, hatY0, hatX1, hatY1, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    static boolean isLegacySkinLayout(int width, int height) {
        return width == height * 2;
    }

    static int scaleCoord(int uv, float scale, int limit) {
        int scaled = Math.round(uv * scale);
        if (scaled < 0) {
            return 0;
        }
        return Math.min(limit, scaled);
    }

    static boolean hasArea(int x0, int y0, int x1, int y1, int maxWidth, int maxHeight) {
        if (x0 < 0 || y0 < 0 || x1 > maxWidth || y1 > maxHeight) {
            return false;
        }
        return x1 > x0 && y1 > y0;
    }
}
