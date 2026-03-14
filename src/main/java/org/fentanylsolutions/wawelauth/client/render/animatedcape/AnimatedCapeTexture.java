package org.fentanylsolutions.wawelauth.client.render.animatedcape;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.fentlib.util.GifUtil;
import org.fentanylsolutions.wawelauth.WawelAuth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wraps a {@link DynamicTexture} and cycles through GIF animation frames.
 * <p>
 * The GIF is decoded via FentLib's {@link GifUtil} which stitches all frames
 * into a single horizontal sprite sheet. Each tick, the appropriate frame's
 * pixels are copied into the dynamic texture.
 */
@SideOnly(Side.CLIENT)
public class AnimatedCapeTexture {

    private final DynamicTexture dynamicTexture;
    private final ResourceLocation resourceLocation;
    private final int[] spriteSheetPixels;
    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;
    private final int frameDelayMs;
    private int lastFrame = -1;

    private AnimatedCapeTexture(DynamicTexture dynamicTexture, ResourceLocation resourceLocation,
        int[] spriteSheetPixels, int frameWidth, int frameHeight, int frameCount, int frameDelayMs) {
        this.dynamicTexture = dynamicTexture;
        this.resourceLocation = resourceLocation;
        this.spriteSheetPixels = spriteSheetPixels;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;
        this.frameDelayMs = frameDelayMs > 0 ? frameDelayMs : 100;
    }

    /**
     * Advances the animation. Call once per tick (or per frame).
     * Only updates the texture when the current frame index changes.
     */
    public void tick() {
        int frame = (int) ((System.currentTimeMillis() / frameDelayMs) % frameCount);
        if (frame == lastFrame) return;
        lastFrame = frame;

        int[] texPixels = dynamicTexture.getTextureData();
        for (int y = 0; y < frameHeight; y++) {
            System.arraycopy(
                spriteSheetPixels,
                y * (frameWidth * frameCount) + frame * frameWidth,
                texPixels,
                y * frameWidth,
                frameWidth);
        }
        dynamicTexture.updateDynamicTexture();
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }

    /**
     * Intermediate result from decoding a GIF on a background thread.
     * Contains only CPU-side data: no OpenGL resources.
     * Must be finalized on the main thread via {@link #createFromDecoded}.
     */
    public static class DecodedGif {

        final int[] spriteSheetPixels;
        final int frameWidth;
        final int frameHeight;
        final int frameCount;
        final int frameDelayMs;

        DecodedGif(int[] spriteSheetPixels, int frameWidth, int frameHeight, int frameCount, int frameDelayMs) {
            this.spriteSheetPixels = spriteSheetPixels;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.frameCount = frameCount;
            this.frameDelayMs = frameDelayMs;
        }
    }

    /**
     * Decodes a GIF into a sprite sheet pixel array. Safe to call from any thread.
     * Does NOT create any OpenGL resources.
     *
     * @param gifBytes raw GIF file bytes
     * @return decoded data, or null if decoding failed
     */
    public static DecodedGif decodeGif(byte[] gifBytes) {
        try {
            // Read native GIF dimensions to preserve HD resolution
            int nativeW = 64, nativeH = 32;
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(gifBytes));
                    reader.setInput(iis);
                    nativeW = reader.getWidth(0);
                    nativeH = reader.getHeight(0);
                } finally {
                    reader.dispose();
                }
            }

            GifUtil.StitchedAnimationData stitched = GifUtil.stitchedFromBytes(gifBytes, nativeW, nativeH);
            if (stitched == null) {
                WawelAuth.LOG.warn("Failed to decode animated cape GIF: stitchedFromBytes returned null");
                return null;
            }

            // Convert stitched PNG bytes to pixel array
            BufferedImage img = GifUtil.byteArrayToBufferedImage(stitched.stichedData);
            if (img == null) {
                WawelAuth.LOG.warn("Failed to decode animated cape GIF: could not read stitched image");
                return null;
            }
            int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());

            return new DecodedGif(
                pixels,
                stitched.frameWidth,
                stitched.frameHeight,
                stitched.frameCount,
                stitched.frameDelayMs);
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to decode animated cape GIF: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates the OpenGL texture and registers it. MUST be called on the main (render) thread.
     *
     * @param decoded      the decoded GIF data from {@link #decodeGif}
     * @param locationPath resource location path (e.g. "capes/animated/uuid")
     * @return the AnimatedCapeTexture, or null on failure
     */
    public static AnimatedCapeTexture createFromDecoded(DecodedGif decoded, String locationPath) {
        if (decoded == null) return null;

        try {
            DynamicTexture frameTex = new DynamicTexture(decoded.frameWidth, decoded.frameHeight);
            ResourceLocation location = new ResourceLocation("wawelauth", locationPath);

            Minecraft mc = Minecraft.getMinecraft();
            ITextureObject existing = mc.renderEngine.getTexture(location);
            if (existing != null) {
                mc.renderEngine.deleteTexture(location);
            }
            mc.renderEngine.loadTexture(location, frameTex);

            AnimatedCapeTexture result = new AnimatedCapeTexture(
                frameTex,
                location,
                decoded.spriteSheetPixels,
                decoded.frameWidth,
                decoded.frameHeight,
                decoded.frameCount,
                decoded.frameDelayMs);

            // Force first frame render
            result.tick();

            WawelAuth.debug(
                "Loaded animated cape: " + decoded.frameCount
                    + " frames, "
                    + decoded.frameWidth
                    + "x"
                    + decoded.frameHeight
                    + ", delay="
                    + decoded.frameDelayMs
                    + "ms");

            return result;
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to create animated cape texture: {}", e.getMessage());
            return null;
        }
    }
}
