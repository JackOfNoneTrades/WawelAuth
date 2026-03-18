package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import net.minecraft.client.renderer.ImageBufferDownload;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.SkinLayers3DConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Replace ImageBufferDownload.parseUserSkin with a pure-raster implementation
 * that outputs 64x64 for all standard skins.
 * <p>
 * On some macOS/JDK setups, BufferedImage.getGraphics() can hang during join-time
 * skin processing. Vanilla parseUserSkin uses Graphics.drawImage.
 * This overwrite reproduces behavior without AWT Graphics usage.
 * <p>
 * For legacy 64x32 skins, the right arm and right leg are mirrored to their
 * dedicated left-limb UV positions in the bottom half of the texture.
 * Modern 64x64 skins are copied through directly.
 */
@Mixin(ImageBufferDownload.class)
public abstract class MixinImageBufferDownload {

    @Shadow
    private int[] imageData;

    @Shadow
    private int imageWidth;

    @Shadow
    private int imageHeight;

    @Shadow
    private void setAreaTransparent(int x1, int y1, int x2, int y2) {}

    @Shadow
    private void setAreaOpaque(int x1, int y1, int x2, int y2) {}

    /**
     * @author WawelAuth
     * @reason Output 64x64 for all standard skins to support modern skin rendering.
     *         Legacy 64x32 skins get mirrored left limbs. Modern 64x64 skins are
     *         copied directly. HD skins (>64px wide) are passed through for HD skin mods.
     */
    @Overwrite
    public BufferedImage parseUserSkin(BufferedImage image) {
        if (image == null) return null;

        if (!SkinLayers3DConfig.modernSkinSupport) {
            return wawelauth$parseLegacySkin(image);
        }

        int srcW = image.getWidth();
        int srcH = image.getHeight();

        // HD skin (wider than 64px): pass through for HD skin mods.
        if (srcW > 64) {
            if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
                return image;
            }
            BufferedImage converted = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = new int[srcW * srcH];
            image.getRGB(0, 0, srcW, srcH, pixels, 0, srcW);
            converted.setRGB(0, 0, srcW, srcH, pixels, 0, srcW);
            return converted;
        }

        // Standard skin: output as 64x64.
        this.imageWidth = 64;
        this.imageHeight = 64;

        BufferedImage out = new BufferedImage(this.imageWidth, this.imageHeight, BufferedImage.TYPE_INT_ARGB);
        this.imageData = ((DataBufferInt) out.getRaster()
            .getDataBuffer()).getData();

        boolean legacy = srcH <= 32;
        if (legacy) {
            // Legacy 64x32 skin: copy top 32 rows, then mirror right limbs to left limb positions.
            copyRegion(image, this.imageData, this.imageWidth, 0, 0, srcW, srcH, 0, 0);

            // Mirror right arm → left arm (per-face flip, not bulk row flip)
            mirrorLimb(this.imageData, this.imageWidth, 40, 16, 32, 48, 4, 4, 12);

            // Mirror right leg → left leg (per-face flip)
            mirrorLimb(this.imageData, this.imageWidth, 0, 16, 16, 48, 4, 4, 12);
        } else {
            // Modern 64x64 skin: copy all rows directly.
            int copyH = Math.min(srcH, 64);
            copyRegion(image, this.imageData, this.imageWidth, 0, 0, Math.min(srcW, 64), copyH, 0, 0);
        }

        // Alpha fixups: base parts forced opaque.
        // Matches modern behavior: notch transparency hack only for legacy skins.
        this.setAreaOpaque(0, 0, 32, 16); // Head
        if (legacy) {
            this.setAreaTransparent(32, 0, 64, 32); // Notch hack for legacy hat area
        }
        this.setAreaOpaque(0, 16, 64, 32); // Body + arms + legs row
        this.setAreaOpaque(16, 48, 48, 64); // Left arm + left leg bases

        return out;
    }

    private BufferedImage wawelauth$parseLegacySkin(BufferedImage image) {
        int srcW = image.getWidth();
        int srcH = image.getHeight();

        this.imageWidth = 64;
        this.imageHeight = 32;

        BufferedImage out = new BufferedImage(this.imageWidth, this.imageHeight, BufferedImage.TYPE_INT_ARGB);
        this.imageData = ((DataBufferInt) out.getRaster()
            .getDataBuffer()).getData();

        copyRegion(image, this.imageData, this.imageWidth, 0, 0, Math.min(srcW, 64), Math.min(srcH, 32), 0, 0);
        this.setAreaOpaque(0, 0, 32, 16);
        this.setAreaTransparent(32, 0, 64, 32);
        this.setAreaOpaque(0, 16, 64, 32);
        return out;
    }

    /**
     * Copy a rectangular region from a source BufferedImage into a dest int[] pixel array.
     */
    private static void copyRegion(BufferedImage source, int[] dest, int destStride, int srcX, int srcY, int width,
        int height, int dstX, int dstY) {
        int copyWidth = Math.min(width, Math.min(source.getWidth() - srcX, destStride - dstX));
        int copyHeight = Math.min(height, source.getHeight() - srcY);
        if (copyWidth <= 0 || copyHeight <= 0) return;

        int[] row = new int[copyWidth];
        for (int y = 0; y < copyHeight; y++) {
            source.getRGB(srcX, srcY + y, copyWidth, 1, row, 0, copyWidth);
            System.arraycopy(row, 0, dest, (dstY + y) * destStride + dstX, copyWidth);
        }
    }

    /**
     * Mirror a limb's UV block from source to destination by flipping each face
     * individually AND swapping inner↔outer faces.
     * <p>
     * When mirroring a right limb to create a left limb, the inner face (facing
     * the body) geometrically becomes the outer face (facing away) and vice versa.
     * This matches modern MC's SkinTextureDownloader.processLegacySkin behavior.
     * <p>
     * Limb UV layout (w = limb width, d = limb depth, h = limb height):
     * Cap rows (d rows): [d pad] [w top] [w bottom] [d pad]
     * Face rows (h rows): [d inner] [w front] [d outer] [w back]
     * Total: (2d + 2w) wide, (d + h) tall
     * <p>
     * For standard arms/legs: w=4, d=4, h=12 → 16x16 block.
     */
    private static void mirrorLimb(int[] data, int stride, int srcX, int srcY, int dstX, int dstY, int limbWidth,
        int limbDepth, int limbHeight) {
        // Cap region: flip top and bottom caps (stay in same position)
        mirrorFace(data, stride, srcX + limbDepth, srcY, dstX + limbDepth, dstY, limbWidth, limbDepth);
        mirrorFace(
            data,
            stride,
            srcX + limbDepth + limbWidth,
            srcY,
            dstX + limbDepth + limbWidth,
            dstY,
            limbWidth,
            limbDepth);

        // Face region: flip each face AND swap inner↔outer
        int srcFaceY = srcY + limbDepth;
        int dstFaceY = dstY + limbDepth;

        // Source inner (offset 0) → Dest OUTER (offset d+w)
        mirrorFace(data, stride, srcX, srcFaceY, dstX + limbDepth + limbWidth, dstFaceY, limbDepth, limbHeight);
        // Front → Front (same position)
        mirrorFace(data, stride, srcX + limbDepth, srcFaceY, dstX + limbDepth, dstFaceY, limbWidth, limbHeight);
        // Source outer (offset d+w) → Dest INNER (offset 0)
        mirrorFace(data, stride, srcX + limbDepth + limbWidth, srcFaceY, dstX, dstFaceY, limbDepth, limbHeight);
        // Back → Back (same position)
        mirrorFace(
            data,
            stride,
            srcX + 2 * limbDepth + limbWidth,
            srcFaceY,
            dstX + 2 * limbDepth + limbWidth,
            dstFaceY,
            limbWidth,
            limbHeight);
    }

    /**
     * Copy a rectangular face region, flipping each row horizontally.
     */
    private static void mirrorFace(int[] data, int stride, int srcX, int srcY, int dstX, int dstY, int w, int h) {
        for (int y = 0; y < h; y++) {
            int srcRowBase = (srcY + y) * stride + srcX;
            int dstRowBase = (dstY + y) * stride + dstX;
            for (int x = 0; x < w; x++) {
                data[dstRowBase + x] = data[srcRowBase + (w - 1 - x)];
            }
        }
    }
}
