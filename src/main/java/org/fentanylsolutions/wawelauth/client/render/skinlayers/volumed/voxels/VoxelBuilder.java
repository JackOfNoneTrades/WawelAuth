package org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels;

public interface VoxelBuilder {

    VoxelBuilder textureSize(int width, int height);

    VoxelBuilder uv(int u, int v);

    VoxelBuilder mirror(boolean bl);

    VoxelBuilder addBox(float x, float y, float z, float pixelSize, Direction[] hide, Direction[][] corners);

    VoxelBuilder addVanillaBox(float x, float y, float z, float width, float height, float depth);

    boolean isEmpty();

}
