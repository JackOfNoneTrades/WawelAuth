package org.fentanylsolutions.wawelauth.api;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

public class WawelFaceRendererServerTest {

    @Test
    public void renderFaceUsesHdCoordinatesAndOverlay() {
        BufferedImage skin = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 16, 16, 32, 32, 0xFFFF0000);
        fill(skin, 80, 16, 96, 32, 0xFF00FF00);

        BufferedImage rendered = WawelFaceRendererServer.renderFace(skin, 8);

        Assert.assertEquals(0xFF00FF00, rendered.getRGB(0, 0));
        Assert.assertEquals(0xFF00FF00, rendered.getRGB(7, 7));
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                image.setRGB(x, y, argb);
            }
        }
    }
}
