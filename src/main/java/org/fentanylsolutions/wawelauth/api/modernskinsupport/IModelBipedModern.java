package org.fentanylsolutions.wawelauth.api.modernskinsupport;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper.LayerState;
import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper.SkinLayer;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

/**
 * <insert that one wall of text here>
 *
 * @see org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport.MixinModelBiped
 * @author kotmatross
 */
public interface IModelBipedModern {

    // ========================================
    // I N T E R F A C E
    // ========================================

    void init(float scale);

    boolean isInitialized();

    boolean isBehindTranslucent();

    void renderAllLayers(EntityPlayer player, float scale, RenderPassLayer pass);

    void renderLayer(SkinLayer layer, LayerState state, SkinLayers3DState state3d, float scale);

    void renderLayerSide(int glCullFace, SkinLayer layer, LayerState state, SkinLayers3DMesh mesh,
        ModelRenderer overlay, float scale);

    void renderMesh(SkinLayer layer, SkinLayers3DMesh mesh, ModelRenderer source, float scale);

    SkinModel getSkinModel();

    void setSkinModel(SkinModel state);

    UUID getRenderPlayerUUID();

    void setRenderPlayerUUID(UUID uuid);

    ModelRenderer rendererFromLayer(SkinLayer layer);

    ModelRenderer baseRendererFromLayer(SkinLayer layer);

    SkinLayer layerFromRenderer(ModelRenderer renderer);

    default void hideLayer(SkinLayer layer, boolean value) {
        this.rendererFromLayer(layer).showModel = !value;
    }

    // ========================================
    // S T A T I C
    // ========================================

    static void copyModelFields(ModelRenderer source, ModelRenderer dest) {
        dest.showModel = source.showModel;
        dest.offsetX = source.offsetX;
        dest.offsetY = source.offsetY;
        dest.offsetZ = source.offsetZ;
        dest.rotateAngleX = source.rotateAngleX;
        dest.rotateAngleY = source.rotateAngleY;
        dest.rotateAngleZ = source.rotateAngleZ;
        dest.rotationPointX = source.rotationPointX;
        dest.rotationPointY = source.rotationPointY;
        dest.rotationPointZ = source.rotationPointZ;
    }

    static boolean isBehindTranslucent(World world, double x, double y, double z) {
        if (world == null) return false;

        EntityLivingBase camera = Minecraft.getMinecraft().renderViewEntity;
        if (camera == null) return false;

        // todo: config
        int maxDistance = 64;

        double camX = camera.posX;
        double camY = camera.posY + camera.getEyeHeight();
        double camZ = camera.posZ;

        double dx = x - camX;
        double dy = y - camY;
        double dz = z - camZ;

        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq <= 0.0) return false;

        if (distanceSq > 4096.0) {
            double length = Math.sqrt(distanceSq);
            double factor = maxDistance / length;
            dx *= factor;
            dy *= factor;
            dz *= factor;
            x = camX + dx;
            y = camY + dy;
            z = camZ + dz;
        }

        int currentBlockX = MathHelper.floor_double(camX);
        int currentBlockY = MathHelper.floor_double(camY);
        int currentBlockZ = MathHelper.floor_double(camZ);

        int endBlockX = MathHelper.floor_double(x);
        int endBlockY = MathHelper.floor_double(y);
        int endBlockZ = MathHelper.floor_double(z);

        int stepX = dx > 1.0E-4 ? 1 : (dx < -1.0E-4 ? -1 : 0);
        int stepY = dy > 1.0E-4 ? 1 : (dy < -1.0E-4 ? -1 : 0);
        int stepZ = dz > 1.0E-4 ? 1 : (dz < -1.0E-4 ? -1 : 0);

        double deltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double deltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double deltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;

        double maxX = stepX > 0 ? (currentBlockX + 1.0 - camX) / dx
            : (stepX < 0 ? (camX - currentBlockX) / -dx : Double.MAX_VALUE);
        double maxY = stepY > 0 ? (currentBlockY + 1.0 - camY) / dy
            : (stepY < 0 ? (camY - currentBlockY) / -dy : Double.MAX_VALUE);
        double maxZ = stepZ > 0 ? (currentBlockZ + 1.0 - camZ) / dz
            : (stepZ < 0 ? (camZ - currentBlockZ) / -dz : Double.MAX_VALUE);

        int maxBlocks = Math.abs(endBlockX - currentBlockX) + Math.abs(endBlockY - currentBlockY)
            + Math.abs(endBlockZ - currentBlockZ)
            + 1;
        int maxSteps = Math.min(maxBlocks, maxDistance);

        for (int i = 0; i < maxSteps; i++) {
            if (currentBlockY >= 0 && currentBlockY < 256) {
                Block currentBlock = world.getBlock(currentBlockX, currentBlockY, currentBlockZ);
                if (currentBlock != Blocks.air && currentBlock.getRenderBlockPass() == 1) {
                    return true;
                }
            }

            if (currentBlockX == endBlockX && currentBlockY == endBlockY && currentBlockZ == endBlockZ) {
                break;
            }

            if (maxX < maxY) {
                if (maxX < maxZ) {
                    currentBlockX += stepX;
                    maxX += deltaX;
                } else {
                    currentBlockZ += stepZ;
                    maxZ += deltaZ;
                }
            } else {
                if (maxY < maxZ) {
                    currentBlockY += stepY;
                    maxY += deltaY;
                } else {
                    currentBlockZ += stepZ;
                    maxZ += deltaZ;
                }
            }
        }
        return false;
    }

}
