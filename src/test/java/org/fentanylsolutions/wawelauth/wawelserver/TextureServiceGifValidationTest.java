package org.fentanylsolutions.wawelauth.wawelserver;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;
import org.junit.Assert;
import org.junit.Test;

public class TextureServiceGifValidationTest {

    @Test
    public void acceptsValidAnimatedCapeGif() throws Exception {
        int[] info = validate(gifFrames(64, 32, 2), new ServerConfig.Textures());

        Assert.assertArrayEquals(new int[] { 64, 32, 2 }, info);
    }

    @Test
    public void acceptsOptimizedGifSubFramesInsideLogicalCape() throws Exception {
        int[] info = validate(
            gifFrames(new int[][] { { 64, 32 }, { 10, 10 }, { 22, 17 } }),
            new ServerConfig.Textures());

        Assert.assertArrayEquals(new int[] { 64, 32, 3 }, info);
    }

    @Test(expected = NetException.class)
    public void rejectsSingleFrameGif() throws Exception {
        validate(gifFrames(64, 32, 1), new ServerConfig.Textures());
    }

    @Test(expected = NetException.class)
    public void rejectsTooManyFramesWithoutCountingUnboundedGif() throws Exception {
        ServerConfig.Textures textures = new ServerConfig.Textures();
        textures.setMaxCapeFrameCount(2);

        validate(gifFrames(64, 32, 3), textures);
    }

    @Test(expected = NetException.class)
    public void rejectsFrameThatExceedsCapeDimensions() throws Exception {
        validate(gifFrames(128, 64, 2), new ServerConfig.Textures());
    }

    private static int[] validate(byte[] gif, ServerConfig.Textures textures) throws Exception {
        Method method = TextureService.class
            .getDeclaredMethod("validateGifCape", byte[].class, ServerConfig.Textures.class);
        method.setAccessible(true);
        try {
            return (int[]) method.invoke(null, gif, textures);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new AssertionError(cause);
        }
    }

    private static byte[] gifFrames(int width, int height, int frameCount) throws IOException {
        int[][] frames = new int[frameCount][2];
        for (int i = 0; i < frameCount; i++) {
            frames[i][0] = width;
            frames[i][1] = height;
        }
        return gifFrames(frames);
    }

    private static byte[] gifFrames(int[][] frameSizes) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif")
            .next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            if (ios == null) {
                throw new IOException("No ImageOutputStream for GIF test output.");
            }
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frameSizes.length; i++) {
                writer.writeToSequence(new IIOImage(frame(frameSizes[i][0], frameSizes[i][1], i), null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage frame(int width, int height, int index) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color((index * 80) % 255, 64, 128, 255));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
