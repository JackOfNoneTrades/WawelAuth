package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import java.awt.image.BufferedImage;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelSurfaceBuilder;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelTexture;

/**
 * VoxelTexture implementation wrapping a BufferedImage (the 64x64 skin from ImageBufferDownload).
 * <p>
 * Reads pixel ARGB data for presence/solidity checks.
 */
public class SkinLayers3DSkinData implements VoxelTexture {

    private final BufferedImage image;

    public SkinLayers3DSkinData(BufferedImage image) {
        this.image = image;
    }

    @Override
    public boolean isPresent(VoxelSurfaceBuilder.UV uv) {
        if (uv.u < 0 || uv.v < 0 || uv.u >= image.getWidth() || uv.v >= image.getHeight()) {
            return false;
        }
        int argb = image.getRGB(uv.u, uv.v);
        int alpha = (argb >>> 24) & 0xFF;
        return alpha > 0;
    }

    @Override
    public boolean isSolid(VoxelSurfaceBuilder.UV uv) {
        if (uv.u < 0 || uv.v < 0 || uv.u >= image.getWidth() || uv.v >= image.getHeight()) {
            return false;
        }
        int argb = image.getRGB(uv.u, uv.v);
        int alpha = (argb >>> 24) & 0xFF;
        return alpha == 255;
    }

    @Override
    public int getWidth() {
        return image.getWidth();
    }

    @Override
    public int getHeight() {
        return image.getHeight();
    }
}
