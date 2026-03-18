package org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels;

import java.util.List;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels.VoxelCube.Polygon;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels.VoxelCube.Vertex;

/**
 * Cut down copy of the Vanilla ModelPart to bypass Optifine/Sodium screwing
 * with the VoxelCube class
 */
public abstract class VoxelModelPart {

    protected float x;
    protected float y;
    protected float z;
    protected float xRot;
    protected float yRot;
    protected float zRot;
    protected boolean visible = true;
    protected float[] polygonData = null;
    protected int polygonAmount = 0;
    protected final int polyDataSize = 23;

    public VoxelModelPart(List<VoxelCube> customCubes) {
        compactCubes(customCubes);
    }

    private void compactCubes(List<VoxelCube> customCubes) {
        for (VoxelCube cube : customCubes) {
            polygonAmount += cube.polygonCount;
        }
        polygonData = new float[polygonAmount * polyDataSize];
        int offset = 0;
        Polygon polygon;
        for (VoxelCube cube : customCubes) {
            for (int id = 0; id < cube.polygonCount; id++) {
                polygon = cube.polygons[id];
                Vector3 vector3f = polygon.normal;
                polygonData[offset + 0] = vector3f.x;
                polygonData[offset + 1] = vector3f.y;
                polygonData[offset + 2] = vector3f.z;
                for (int i = 0; i < 4; i++) {
                    Vertex vertex = polygon.vertices[i];
                    polygonData[offset + 3 + (i * 5) + 0] = vertex.scaledX;
                    polygonData[offset + 3 + (i * 5) + 1] = vertex.scaledY;
                    polygonData[offset + 3 + (i * 5) + 2] = vertex.scaledZ;
                    polygonData[offset + 3 + (i * 5) + 3] = vertex.u;
                    polygonData[offset + 3 + (i * 5) + 4] = vertex.v;
                }
                offset += polyDataSize;
            }
        }
    }

    public void setPosition(float f, float g, float h) {
        this.x = f;
        this.y = g;
        this.z = h;
    }

    public void setRotation(float f, float g, float h) {
        this.xRot = f;
        this.yRot = g;
        this.zRot = h;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

}
