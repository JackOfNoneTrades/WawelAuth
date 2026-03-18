package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import java.util.ArrayList;
import java.util.List;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.Direction;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelBuilder;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelCube;

/**
 * 1.7.10 cube collector used by the voxel wrapper pipeline.
 * <p>
 * This builder follows the MIT snapshot wiring style from 3d-Skin-Layers and
 * adapts it to our custom-cube target.
 */
public class SkinLayers3DModelBuilder implements VoxelBuilder {

    private final List<VoxelCube> cubes = new ArrayList<>();
    private int u;
    private int v;
    private boolean mirror;
    private int textureWidth = 64;
    private int textureHeight = 64;

    @Override
    public VoxelBuilder textureSize(int width, int height) {
        this.textureWidth = width;
        this.textureHeight = height;
        return this;
    }

    @Override
    public VoxelBuilder uv(int u, int v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VoxelBuilder mirror(boolean bl) {
        this.mirror = bl;
        return this;
    }

    @Override
    public VoxelBuilder addBox(float x, float y, float z, float pixelSize, Direction[] hide, Direction[][] corners) {
        Direction[] hidden = hide != null ? hide : new Direction[0];
        Direction[][] hiddenCorners = corners != null ? corners : new Direction[0][0];
        this.cubes.add(
            new VoxelCube(
                this.u,
                this.v,
                x,
                y,
                z,
                pixelSize,
                pixelSize,
                pixelSize,
                0,
                0,
                0,
                this.mirror,
                textureWidth,
                textureHeight,
                hidden,
                hiddenCorners));
        return this;
    }

    @Override
    public VoxelBuilder addVanillaBox(float x, float y, float z, float width, float height, float depth) {
        // Maintained for interface compatibility; current wrapper path uses addBox.
        this.cubes.add(
            new VoxelCube(
                this.u,
                this.v,
                x,
                y,
                z,
                width,
                height,
                depth,
                0,
                0,
                0,
                this.mirror,
                textureWidth,
                textureHeight,
                new Direction[0],
                new Direction[0][0]));
        return this;
    }

    @Override
    public boolean isEmpty() {
        return cubes.isEmpty();
    }

    public List<VoxelCube> getCubes() {
        return cubes;
    }
}
