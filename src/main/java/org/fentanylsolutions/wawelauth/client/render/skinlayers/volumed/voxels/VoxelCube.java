package org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels;

import java.util.HashMap;
import java.util.Map;

/**
 * MIT-derived from 3d-Skin-Layers snapshot
 * (commit 4d5b1dd0bfb6d4b928958bcddae4a38142c8432d), adapted for 1.7.10.
 * <p>
 * Supports per-face hiding and per-corner clipping for seam overlap handling.
 */
public class VoxelCube {

    private final Direction[] hidden;
    protected final Polygon[] polygons;
    protected int polygonCount = 0;
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;

    public VoxelCube(int u, int v, float x, float y, float z, float sizeX, float sizeY, float sizeZ, float extraX,
        float extraY, float extraZ, boolean mirror, float textureWidth, float textureHeight, Direction[] hide,
        Direction[][] hideCorners) {
        this.hidden = hide != null ? hide : new Direction[0];
        this.minX = x;
        this.minY = y;
        this.minZ = z;
        this.maxX = x + sizeX;
        this.maxY = y + sizeY;
        this.maxZ = z + sizeZ;
        this.polygons = new Polygon[6];

        float pX = x + sizeX;
        float pY = y + sizeY;
        float pZ = z + sizeZ;
        x -= extraX;
        y -= extraY;
        z -= extraZ;
        pX += extraX;
        pY += extraY;
        pZ += extraZ;
        if (mirror) {
            float tmp = pX;
            pX = x;
            x = tmp;
        }

        Vertex nnn = new Vertex(x, y, z, 0.0F, 0.0F);
        Vertex pnn = new Vertex(pX, y, z, 0.0F, 8.0F);
        Vertex ppn = new Vertex(pX, pY, z, 8.0F, 8.0F);
        Vertex npn = new Vertex(x, pY, z, 8.0F, 0.0F);
        Vertex nnp = new Vertex(x, y, pZ, 0.0F, 0.0F);
        Vertex pnp = new Vertex(pX, y, pZ, 0.0F, 8.0F);
        Vertex ppp = new Vertex(pX, pY, pZ, 8.0F, 8.0F);
        Vertex npp = new Vertex(x, pY, pZ, 8.0F, 0.0F);

        Map<Direction.Axis, Direction[]> axisToCorner = new HashMap<>();
        if (hideCorners != null) {
            nextCorner: for (Direction[] corner : hideCorners) {
                if (corner == null || corner.length == 0) {
                    continue;
                }
                nextAxis: for (Direction.Axis axis : Direction.Axis.VALUES) {
                    for (Direction dir : corner) {
                        if (dir == null || dir.getAxis() == axis) {
                            continue nextAxis;
                        }
                    }
                    axisToCorner.put(axis, corner);
                    continue nextCorner;
                }
            }
        }

        float minU = u + sizeZ + sizeX;
        float maxU = u + sizeZ + sizeX + sizeZ;
        float minV = v + sizeZ;
        float maxV = v + sizeZ + sizeY;

        addFace(
            Direction.DOWN,
            removeCornerVertex(new Vertex[] { pnp, nnp, nnn, pnn }, axisToCorner.get(Direction.Axis.Y)),
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror);
        addFace(
            Direction.UP,
            removeCornerVertex(new Vertex[] { ppn, npn, npp, ppp }, axisToCorner.get(Direction.Axis.Y)),
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror);
        addFace(
            Direction.NORTH,
            removeCornerVertex(new Vertex[] { pnn, nnn, npn, ppn }, axisToCorner.get(Direction.Axis.Z)),
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror);
        addFace(
            Direction.SOUTH,
            removeCornerVertex(new Vertex[] { nnp, pnp, ppp, npp }, axisToCorner.get(Direction.Axis.Z)),
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror);
        addFace(
            Direction.WEST,
            removeCornerVertex(new Vertex[] { nnn, nnp, npp, npn }, axisToCorner.get(Direction.Axis.X)),
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror);
        addFace(
            Direction.EAST,
            removeCornerVertex(new Vertex[] { pnp, pnn, ppn, ppp }, axisToCorner.get(Direction.Axis.X)),
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror);
    }

    private void addFace(Direction face, Vertex[] vertices, float minU, float minV, float maxU, float maxV,
        float textureWidth, float textureHeight, boolean mirror) {
        if (!visibleFace(face)) {
            return;
        }
        this.polygons[this.polygonCount++] = new Polygon(
            vertices,
            minU,
            minV,
            maxU,
            maxV,
            textureWidth,
            textureHeight,
            mirror,
            face);
    }

    private boolean visibleFace(Direction face) {
        for (Direction dir : hidden) {
            if (dir == face) {
                return false;
            }
        }
        return true;
    }

    private static Vertex[] removeCornerVertex(Vertex[] vertices, Direction[] corner) {
        if (corner == null) {
            return vertices;
        }

        Vertex except = vertices[0];
        for (int i = 1; i < 4; i++) {
            except = compareVertices(except, vertices[i], corner);
        }

        int index = 0;
        for (int i = 0; i < 4; i++) {
            if (vertices[i] == except) {
                continue;
            }
            vertices[index++] = vertices[i];
        }
        vertices[3] = vertices[2];

        return vertices;
    }

    private static Vertex compareVertices(Vertex first, Vertex second, Direction[] corner) {
        for (Direction dir : corner) {
            if (dir == null) {
                continue;
            }
            double diff = dir.getAxis()
                .choose(first.pos.x - second.pos.x, first.pos.y - second.pos.y, first.pos.z - second.pos.z)
                * dir.getDirStep();
            if (diff > 0) {
                return first;
            } else if (diff < 0) {
                return second;
            }
        }
        return first;
    }

    protected static class Polygon {

        public final Vertex[] vertices;
        public final Vector3 normal;

        public Polygon(Vertex[] vertices, float minU, float minV, float maxU, float maxV, float textureWidth,
            float textureHeight, boolean mirror, Direction direction) {
            this.vertices = vertices;
            float zeroWidth = 0.0F / textureWidth;
            float zeroHeight = 0.0F / textureHeight;
            vertices[0] = vertices[0].remap(maxU / textureWidth - zeroWidth, minV / textureHeight + zeroHeight);
            vertices[1] = vertices[1].remap(minU / textureWidth + zeroWidth, minV / textureHeight + zeroHeight);
            vertices[2] = vertices[2].remap(minU / textureWidth + zeroWidth, maxV / textureHeight - zeroHeight);
            vertices[3] = vertices[3].remap(maxU / textureWidth - zeroWidth, maxV / textureHeight - zeroHeight);
            if (mirror) {
                int vertexCount = vertices.length;
                for (int i = 0; i < vertexCount / 2; i++) {
                    Vertex tmp = vertices[i];
                    vertices[i] = vertices[vertexCount - 1 - i];
                    vertices[vertexCount - 1 - i] = tmp;
                }
            }
            this.normal = new Vector3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            if (mirror) {
                this.normal.mul(-1.0F, 1.0F, 1.0F);
            }
        }
    }

    protected static class Vertex {

        public final Vector3 pos;
        public final float u;
        public final float v;
        public final float scaledX;
        public final float scaledY;
        public final float scaledZ;

        public Vertex(float x, float y, float z, float u, float v) {
            this(new Vector3(x, y, z), u, v);
        }

        public Vertex remap(float u, float v) {
            return new Vertex(this.pos, u, v);
        }

        public Vertex(Vector3 pos, float u, float v) {
            this.pos = pos;
            this.u = u;
            this.v = v;
            this.scaledX = pos.x / 16.0F;
            this.scaledY = pos.y / 16.0F;
            this.scaledZ = pos.z / 16.0F;
        }
    }
}
