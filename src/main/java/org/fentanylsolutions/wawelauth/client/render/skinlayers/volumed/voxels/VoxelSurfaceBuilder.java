package org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels;

import java.util.HashSet;
import java.util.Set;

/**
 * MIT-derived from 3d-Skin-Layers snapshot (commit 4d5b1dd0bfb6d4b928958bcddae4a38142c8432d), then adapted for:
 * <ul>
 * <li>local VoxelTexture abstraction</li>
 * <li>generic face dimensions</li>
 * <li>custom VoxelBuilder target</li>
 * </ul>
 */
public class VoxelSurfaceBuilder {

    private static final float PIXEL_SIZE = 1.0F;

    public static final class UV {

        public final int u;
        public final int v;

        public UV(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    private static final class Dimensions {

        private final int width;
        private final int height;
        private final int depth;

        private Dimensions(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    private static final class Position {

        private final float x;
        private final float y;
        private final float z;

        private Position(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class VoxelPosition {

        private final int x;
        private final int y;
        private final int z;

        private VoxelPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static VoxelBuilder wrapBox(VoxelBuilder builder, VoxelTexture textureData, int width, int height, int depth,
        int textureU, int textureV, boolean topPivot, float rotationOffset) {
        builder.textureSize(textureData.getWidth(), textureData.getHeight());
        float staticXOffset = -width / 2.0F;
        float staticYOffset = topPivot ? +rotationOffset : -height + rotationOffset;
        float staticZOffset = -depth / 2.0F;

        Position staticOffset = new Position(staticXOffset, staticYOffset, staticZOffset);
        Dimensions dimensions = new Dimensions(width, height, depth);
        UV textureUV = new UV(textureU, textureV);

        for (Direction face : Direction.values()) {
            UV sizeUV = getSizeUV(dimensions, face);
            for (int u = 0; u < sizeUV.u; u++) {
                for (int v = 0; v < sizeUV.v; v++) {
                    addPixel(textureData, builder, staticOffset, face, dimensions, new UV(u, v), textureUV, sizeUV);
                }
            }
        }

        return builder;
    }

    private static UV getSizeUV(Dimensions dimensions, Direction face) {
        if (face == Direction.UP || face == Direction.DOWN) {
            return new UV(dimensions.width, dimensions.depth);
        }
        if (face == Direction.NORTH || face == Direction.SOUTH) {
            return new UV(dimensions.width, dimensions.height);
        }
        return new UV(dimensions.depth, dimensions.height);
    }

    private static UV getOnTextureUV(UV textureUV, UV onFaceUV, Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN) {
            return new UV(textureUV.u + dimensions.depth + onFaceUV.u, textureUV.v + onFaceUV.v);
        }
        if (face == Direction.UP) {
            return new UV(textureUV.u + dimensions.width + dimensions.depth + onFaceUV.u, textureUV.v + onFaceUV.v);
        }
        if (face == Direction.NORTH) {
            return new UV(textureUV.u + dimensions.depth + onFaceUV.u, textureUV.v + dimensions.depth + onFaceUV.v);
        }
        if (face == Direction.SOUTH) {
            return new UV(
                textureUV.u + dimensions.depth + dimensions.width + dimensions.depth + onFaceUV.u,
                textureUV.v + dimensions.depth + onFaceUV.v);
        }
        if (face == Direction.WEST) {
            return new UV(textureUV.u + onFaceUV.u, textureUV.v + dimensions.depth + onFaceUV.v);
        }
        return new UV(
            textureUV.u + dimensions.depth + dimensions.width + onFaceUV.u,
            textureUV.v + dimensions.depth + onFaceUV.v);
    }

    private static VoxelPosition uvToXYZ(UV onFaceUV, Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN) {
            return new VoxelPosition(onFaceUV.u, 0, dimensions.depth - 1 - onFaceUV.v);
        }
        if (face == Direction.UP) {
            return new VoxelPosition(onFaceUV.u, dimensions.height - 1, dimensions.depth - 1 - onFaceUV.v);
        }
        if (face == Direction.NORTH) {
            return new VoxelPosition(onFaceUV.u, onFaceUV.v, 0);
        }
        if (face == Direction.SOUTH) {
            return new VoxelPosition(dimensions.width - 1 - onFaceUV.u, onFaceUV.v, dimensions.depth - 1);
        }
        if (face == Direction.WEST) {
            return new VoxelPosition(0, onFaceUV.v, dimensions.depth - 1 - onFaceUV.u);
        }
        return new VoxelPosition(dimensions.width - 1, onFaceUV.v, onFaceUV.u);
    }

    private static UV xyzToUV(VoxelPosition voxelPosition, Dimensions dimensions, Direction face) {
        if (face == Direction.DOWN || face == Direction.UP) {
            return new UV(voxelPosition.x, dimensions.depth - 1 - voxelPosition.z);
        }
        if (face == Direction.NORTH) {
            return new UV(voxelPosition.x, voxelPosition.y);
        }
        if (face == Direction.SOUTH) {
            return new UV(dimensions.width - 1 - voxelPosition.x, voxelPosition.y);
        }
        if (face == Direction.WEST) {
            return new UV(dimensions.depth - 1 - voxelPosition.z, voxelPosition.y);
        }
        return new UV(voxelPosition.z, voxelPosition.y);
    }

    private static void addPixel(VoxelTexture textureData, VoxelBuilder builder, Position staticOffset, Direction face,
        Dimensions dimensions, UV onFaceUV, UV textureUV, UV sizeUV) {
        UV onTextureUV = getOnTextureUV(textureUV, onFaceUV, dimensions, face);
        if (!isPixelPresent(textureData, onTextureUV)) {
            return;
        }

        VoxelPosition voxelPosition = uvToXYZ(onFaceUV, dimensions, face);
        Position position = new Position(
            staticOffset.x + voxelPosition.x,
            staticOffset.y + voxelPosition.y,
            staticOffset.z + voxelPosition.z);
        boolean solidPixel = textureData.isSolid(onTextureUV);

        Set<Direction> hide = new HashSet<>();
        Set<Direction[]> corners = new HashSet<>();

        boolean onBorder = false;
        boolean backsideOverlaps = false;
        for (Direction neighborFace : Direction.values()) {
            if (neighborFace.getAxis() == face.getAxis()) {
                continue;
            }

            VoxelPosition neighborVoxel = new VoxelPosition(
                voxelPosition.x + neighborFace.getStepX(),
                voxelPosition.y + neighborFace.getStepY(),
                voxelPosition.z + neighborFace.getStepZ());
            UV neighborOnFaceUV = xyzToUV(neighborVoxel, dimensions, face);
            if (isOnFace(neighborOnFaceUV, sizeUV)) {
                UV neighborOnTextureUV = getOnTextureUV(textureUV, neighborOnFaceUV, dimensions, face);
                if (isPixelPresent(textureData, neighborOnTextureUV)) {
                    if (!(solidPixel && !textureData.isSolid(neighborOnTextureUV))) {
                        hide.add(neighborFace);
                    }
                } else {
                    VoxelPosition farNeighborVoxel = new VoxelPosition(
                        neighborVoxel.x + neighborFace.getStepX(),
                        neighborVoxel.y + neighborFace.getStepY(),
                        neighborVoxel.z + neighborFace.getStepZ());
                    UV farNeighborOnFaceUV = xyzToUV(farNeighborVoxel, dimensions, face);
                    if (!isOnFace(farNeighborOnFaceUV, sizeUV)) {
                        farNeighborOnFaceUV = xyzToUV(farNeighborVoxel, dimensions, neighborFace);
                        UV farNeighborOnTextureUV = getOnTextureUV(
                            textureUV,
                            farNeighborOnFaceUV,
                            dimensions,
                            neighborFace);
                        if (isPixelPresent(textureData, farNeighborOnTextureUV)) {
                            if (!(solidPixel && !textureData.isSolid(farNeighborOnTextureUV))) {
                                hide.add(neighborFace);
                            }
                        }
                    }
                }
            } else {
                onBorder = true;
                neighborOnFaceUV = xyzToUV(voxelPosition, dimensions, neighborFace);
                UV neighborOnTextureUV = getOnTextureUV(textureUV, neighborOnFaceUV, dimensions, neighborFace);
                if (isPixelPresent(textureData, neighborOnTextureUV)) {
                    backsideOverlaps = true;
                    hide.add(neighborFace);
                    corners.add(new Direction[] { face.getOpposite(), neighborFace });
                } else {
                    UV downNeighborOnFaceUV = xyzToUV(
                        new VoxelPosition(
                            voxelPosition.x - face.getStepX(),
                            voxelPosition.y - face.getStepY(),
                            voxelPosition.z - face.getStepZ()),
                        dimensions,
                        neighborFace);
                    UV downNeighborOnTextureUV = getOnTextureUV(
                        textureUV,
                        downNeighborOnFaceUV,
                        dimensions,
                        neighborFace);
                    if (isPixelPresent(textureData, downNeighborOnTextureUV)) {
                        backsideOverlaps = true;
                    }
                }
            }
        }

        if (!onBorder || backsideOverlaps) {
            hide.add(face.getOpposite());
        }

        builder.uv(onTextureUV.u - 2, onTextureUV.v - 1)
            .addBox(
                position.x,
                position.y,
                position.z,
                PIXEL_SIZE,
                hide.toArray(new Direction[0]),
                corners.toArray(new Direction[0][0]));
    }

    private static boolean isOnFace(UV onFaceUV, UV sizeUV) {
        return onFaceUV.u >= 0 && onFaceUV.u < sizeUV.u && onFaceUV.v >= 0 && onFaceUV.v < sizeUV.v;
    }

    private static boolean isPixelPresent(VoxelTexture textureData, UV uv) {
        if (uv.u < 0 || uv.v < 0 || uv.u >= textureData.getWidth() || uv.v >= textureData.getHeight()) {
            return false;
        }
        return textureData.isPresent(uv);
    }
}
