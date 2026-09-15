package org.fentanylsolutions.wawelauth.api;

import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.CAPE;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.config.SkinLayers3DConfig;
import org.lwjgl.opengl.GL11;

/**
 * Backport of ModelPlayer from 1.8+
 * Adds support for 2D and 3D overlays, as well as correct sorting for translucent ones.
 * <p>
 * Usage:
 *
 * <pre>
 * {@code
 *     private void someMethod(RenderPlayer renderer) {
 *         ModelPlayer model = (ModelPlayer) renderer.modelBipedMain;
 *         model.anyAPIMethod(...);
 *     }
 * }
 * </pre>
 *
 * @author Kotmatross
 */
public class ModelPlayer extends ModelBiped {

    // ========================================
    // F I E L D S
    // ========================================

    public static final int CLASSIC = 0;
    public static final int SLIM = 1;

    public ModelRenderer[] rightArm = new ModelRenderer[2];
    public ModelRenderer[] leftArm = new ModelRenderer[2];

    public ModelRenderer jacket;
    public ModelRenderer rightPants;
    public ModelRenderer leftPants;
    public ModelRenderer[] rightSleeve = new ModelRenderer[2];
    public ModelRenderer[] leftSleeve = new ModelRenderer[2];

    public boolean isSlim = false;
    public boolean behindTranslucent;
    public UUID currentRenderingPlayerUuid = null;

    // ========================================
    // V A N I L L A O V E R R I D E S
    // ========================================

    public ModelPlayer(float scale) {
        super(scale, 0, 64, 64);

        this.textureWidth = 64;
        this.textureHeight = 32;
        // TODO: our own cape renderer, with proper translucent sorting?
        this.bipedCloak = new ModelRenderer(this, 0, 0);
        this.bipedCloak.addBox(-5.0F, 0.0F, -1.0F, 10, 16, 1, scale);

        float overlay = 0.25F;
        this.textureWidth = 64;
        this.textureHeight = 64;

        this.bipedHead = new ModelRenderer(this, 0, 0);
        this.bipedHead.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, scale);
        this.bipedHead.setRotationPoint(0.0F, 0.0F, 0.0F);

        this.bipedHeadwear = new ModelRenderer(this, 32, 0);
        this.bipedHeadwear.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, scale + 0.5F);

        this.bipedBody = new ModelRenderer(this, 16, 16);
        this.bipedBody.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale);
        this.bipedBody.setRotationPoint(0.0F, 0.0F, 0.0F);

        this.jacket = new ModelRenderer(this, 16, 32);
        this.jacket.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale + overlay);

        this.rightArm[CLASSIC] = new ModelRenderer(this, 40, 16);
        this.rightArm[CLASSIC].addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale);
        this.rightArm[CLASSIC].setRotationPoint(-5.0F, 2.0F, 0.0F);
        this.rightArm[SLIM] = new ModelRenderer(this, 40, 16);
        this.rightArm[SLIM].addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.rightArm[SLIM].setRotationPoint(-5.0F, 2.5F, 0.0F);

        this.rightSleeve[CLASSIC] = new ModelRenderer(this, 40, 32);
        this.rightSleeve[CLASSIC].addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);
        this.rightSleeve[SLIM] = new ModelRenderer(this, 40, 32);
        this.rightSleeve[SLIM].addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);

        this.leftArm[CLASSIC] = new ModelRenderer(this, 32, 48);
        this.leftArm[CLASSIC].addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale);
        this.leftArm[CLASSIC].setRotationPoint(5.0F, 2.0F, 0.0F);
        this.leftArm[SLIM] = new ModelRenderer(this, 32, 48);
        this.leftArm[SLIM].addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.leftArm[SLIM].setRotationPoint(5.0F, 2.5F, 0.0F);

        this.leftSleeve[CLASSIC] = new ModelRenderer(this, 48, 48);
        this.leftSleeve[CLASSIC].addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);
        this.leftSleeve[SLIM] = new ModelRenderer(this, 48, 48);
        this.leftSleeve[SLIM].addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);

        this.bipedRightLeg = new ModelRenderer(this, 0, 16);
        this.bipedRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale);
        this.bipedRightLeg.setRotationPoint(-1.9F, 12.0F, 0.0F);

        this.rightPants = new ModelRenderer(this, 0, 32);
        this.rightPants.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);

        this.bipedLeftLeg = new ModelRenderer(this, 16, 48);
        this.bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale);
        this.bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);

        this.leftPants = new ModelRenderer(this, 0, 48);
        this.leftPants.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);

        this.bipedRightArm = this.rightArm[CLASSIC];
        this.bipedLeftArm = this.leftArm[CLASSIC];
    }

    /**
     * Copies all transformations from base models to overlays
     */
    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, Entity entity) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);
        for (SkinLayersHelper.EnumPlayerModelParts part : SkinLayersHelper.EnumPlayerModelParts.VALUES) {
            copyModelFields(baseRendererFromPart(part), rendererFromPart(part));
        }
    }

    /**
     * Main model renderer (opaque) + overlays (with translucent sorting)
     */
    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor) {
        if (!(entity instanceof EntityPlayer player)) return;
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);

        int pass = MinecraftForgeClient.getRenderPass();
        this.behindTranslucent = (pass == 0 || pass == 1)
            && isBehindTranslucent(player.worldObj, player.posX, player.posY, player.posZ);

        /// TRANSLUCENT PASS
        if (pass == 1) {
            renderAllLayers(player, scaleFactor, false, false);
        }
        /// MAIN PASS
        else if (pass == 0) {
            renderBaseModel(scaleFactor);
            renderAllLayers(player, scaleFactor, true, false);
        }
        /// OTHER PASS (GUI)
        else {
            renderBaseModel(scaleFactor);
            renderAllLayers(player, scaleFactor, true, true);
        }
    }

    // ========================================
    // A P I
    // ========================================

    public void renderBaseModel(float scaleFactor) {
        this.bipedHead.render(scaleFactor);
        this.bipedBody.render(scaleFactor);
        this.bipedRightArm.render(scaleFactor);
        this.bipedLeftArm.render(scaleFactor);
        this.bipedRightLeg.render(scaleFactor);
        this.bipedLeftLeg.render(scaleFactor);
    }

    /**
     * Swaps between slim (3px) and classic (4px) arm ModelRenderers
     */
    public void setSlim(boolean slim) {
        this.isSlim = slim;
        this.bipedRightArm = this.rightArm[slim ? SLIM : CLASSIC];
        this.bipedLeftArm = this.leftArm[slim ? SLIM : CLASSIC];
    }

    /**
     * Sets the hash map key for rendering 3D layers
     */
    public void setRenderPlayerUUID(UUID uuid) {
        this.currentRenderingPlayerUuid = uuid;
    }

    /**
     * Renders all player overlays
     */
    public void renderAllLayers(EntityPlayer player, float scale, boolean mainPass, boolean bypassTranslucent) {
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);

            SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
            for (SkinLayersHelper.EnumPlayerModelParts part : SkinLayersHelper.EnumPlayerModelParts.VALUES) {
                if (part == CAPE) continue;
                SkinLayersHelper.PartState state = SkinLayersHelper.getSkinLayerState(player, part);
                renderLayer(part, state, state3d, scale, mainPass, bypassTranslucent);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Renders an individual overlay
     */
    public void renderLayer(SkinLayersHelper.EnumPlayerModelParts part, SkinLayersHelper.PartState state,
        SkinLayers3DState state3d, float scale, boolean mainPass, boolean bypassTranslucent) {

        ModelRenderer overlay = rendererFromPart(part);

        if (state.isDisabled() || !overlay.showModel) return;
        if (!bypassTranslucent && (this.behindTranslucent != mainPass)) return;

        SkinLayers3DMesh mesh = null;
        if (state3d != null && state3d.initialized) mesh = state3d.meshFromPart(part);
        if (mesh == null || !mesh.isCompiled()) state = SkinLayersHelper.PartState.FLAT;

        renderLayerSide(GL11.GL_FRONT, part, state, mesh, overlay, scale);
        renderLayerSide(GL11.GL_BACK, part, state, mesh, overlay, scale);
    }

    /**
     * Renders an individual overlay side
     */
    private void renderLayerSide(int cullFace, SkinLayersHelper.EnumPlayerModelParts part,
        SkinLayersHelper.PartState state, SkinLayers3DMesh mesh, ModelRenderer overlay, float scale) {
        GL11.glCullFace(cullFace);
        if (state == SkinLayersHelper.PartState.VOLUMETRIC) {
            renderMesh(part, mesh, overlay, scale);
        } else {
            overlay.render(scale);
        }
    }

    /**
     * Renders 3D mesh overlay
     */
    private void renderMesh(SkinLayersHelper.EnumPlayerModelParts part, SkinLayers3DMesh mesh, ModelRenderer source,
        float scale) {
        mesh.setPosition(source.rotationPointX, source.rotationPointY, source.rotationPointZ);
        mesh.setOffset(source.offsetX, source.offsetY, source.offsetZ);
        mesh.setRotation(source.rotateAngleX, source.rotateAngleY, source.rotateAngleZ);

        final float baseVoxel = SkinLayers3DConfig.baseVoxelSize;
        float scaleX = baseVoxel;
        float scaleY = baseVoxel;
        float scaleZ = baseVoxel;
        float offsetX = 0.0F;
        float offsetY = 0.0F;
        float offsetZ = 0.0F;

        switch (part) {
            case HAT:
                final float headVoxel = SkinLayers3DConfig.headVoxelSize;
                scaleX = headVoxel;
                scaleY = headVoxel;
                scaleZ = headVoxel;
                break;
            case JACKET:
                scaleX = SkinLayers3DConfig.bodyVoxelWidthSize;
                scaleY = 1.035F;
                offsetY = -0.2F;
                break;
            case RIGHT_SLEEVE:
                scaleY = 1.035F;
                offsetX = this.isSlim ? -0.499F : -0.998F;
                offsetY = -0.1F;
                break;
            case LEFT_SLEEVE:
                scaleY = 1.035F;
                offsetX = this.isSlim ? 0.499F : 0.998F;
                offsetY = -0.1F;
                break;
            case RIGHT_PANTS:
            case LEFT_PANTS:
                scaleY = 1.035F;
                offsetY = -0.2F;
                break;
            default:
                break;
        }

        mesh.render(scale, scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ);
    }

    /**
     * Checks whether position is behind translucent block from renderViewEntity perspective.
     */
    public boolean isBehindTranslucent(World world, double x, double y, double z) {
        if (world == null) return false;

        EntityLivingBase camera = Minecraft.getMinecraft().renderViewEntity;
        if (camera == null) return false;

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

    /**
     * Copies all parameters from one ModelRenderer to another
     */
    public static void copyModelFields(ModelRenderer source, ModelRenderer dest) {
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

    /**
     * Hides part of the model
     */
    public void hidePart(SkinLayersHelper.EnumPlayerModelParts part, boolean value) {
        this.rendererFromPart(part).showModel = !value;
    }

    /**
     * Helper method to get overlay ModelRenderer from EnumPlayerModelParts.
     */
    public ModelRenderer rendererFromPart(SkinLayersHelper.EnumPlayerModelParts part) {
        return switch (part) {
            case CAPE -> this.bipedCloak;
            case JACKET -> this.jacket;
            case LEFT_SLEEVE -> this.leftSleeve[this.isSlim ? SLIM : CLASSIC];
            case RIGHT_SLEEVE -> this.rightSleeve[this.isSlim ? SLIM : CLASSIC];
            case LEFT_PANTS -> this.leftPants;
            case RIGHT_PANTS -> this.rightPants;
            case HAT -> this.bipedHeadwear;
        };
    }

    /**
     * Helper method to get base ModelRenderer from EnumPlayerModelParts.
     */
    public ModelRenderer baseRendererFromPart(SkinLayersHelper.EnumPlayerModelParts part) {
        return switch (part) {
            case CAPE -> this.bipedCloak;
            case JACKET -> this.bipedBody;
            case LEFT_SLEEVE -> this.bipedLeftArm;
            case RIGHT_SLEEVE -> this.bipedRightArm;
            case LEFT_PANTS -> this.bipedLeftLeg;
            case RIGHT_PANTS -> this.bipedRightLeg;
            case HAT -> this.bipedHead;
        };
    }

    /**
     * Helper method to get EnumPlayerModelParts from overlay ModelRenderer.
     */
    public SkinLayersHelper.EnumPlayerModelParts partFromRenderer(ModelRenderer renderer) {
        if (renderer == this.bipedCloak) return SkinLayersHelper.EnumPlayerModelParts.CAPE;
        if (renderer == this.jacket) return SkinLayersHelper.EnumPlayerModelParts.JACKET;

        ModelRenderer[] leftSleeveArr = this.leftSleeve;
        if (renderer == leftSleeveArr[SLIM] || renderer == leftSleeveArr[CLASSIC])
            return SkinLayersHelper.EnumPlayerModelParts.LEFT_SLEEVE;

        ModelRenderer[] rightSleeveArr = this.rightSleeve;
        if (renderer == rightSleeveArr[SLIM] || renderer == rightSleeveArr[CLASSIC])
            return SkinLayersHelper.EnumPlayerModelParts.RIGHT_SLEEVE;

        if (renderer == this.leftPants) return SkinLayersHelper.EnumPlayerModelParts.LEFT_PANTS;
        if (renderer == this.rightPants) return SkinLayersHelper.EnumPlayerModelParts.RIGHT_PANTS;
        if (renderer == this.bipedHeadwear) return SkinLayersHelper.EnumPlayerModelParts.HAT;
        return null;
    }

}
