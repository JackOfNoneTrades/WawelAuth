package org.fentanylsolutions.wawelauth.client.render.skinlayers3d.voxels;

public interface VoxelTexture {

    boolean isPresent(VoxelSurfaceBuilder.UV onTextureUV);

    boolean isSolid(VoxelSurfaceBuilder.UV onTextureUV);

    int getWidth();

    int getHeight();

}
