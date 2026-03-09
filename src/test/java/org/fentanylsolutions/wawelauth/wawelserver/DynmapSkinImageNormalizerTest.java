package org.fentanylsolutions.wawelauth.wawelserver;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

public class DynmapSkinImageNormalizerTest {

    @Test
    public void normalizeForDynmapBuildsCanonicalHdSkin() {
        BufferedImage skin = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 16, 16, 32, 32, 0xFFFF0000);
        fill(skin, 80, 16, 96, 32, 0xFF00FF00);
        fill(skin, 40, 40, 56, 64, 0xFF0000FF);
        fill(skin, 8, 40, 16, 64, 0xFFFFFF00);
        fill(skin, 88, 40, 96, 64, 0xFFFF00FF);

        BufferedImage normalized = DynmapSkinImageNormalizer.normalizeForDynmap(skin);

        Assert.assertEquals(64, normalized.getWidth());
        Assert.assertEquals(64, normalized.getHeight());
        Assert.assertEquals(0xFF00FF00, normalized.getRGB(8, 8));
        Assert.assertEquals(0, normalized.getRGB(40, 8));
        Assert.assertEquals(0xFF0000FF, normalized.getRGB(20, 20));
        Assert.assertEquals(0xFFFFFF00, normalized.getRGB(4, 20));
        Assert.assertEquals(0xFFFF00FF, normalized.getRGB(44, 20));
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                image.setRGB(x, y, argb);
            }
        }
    }
}
