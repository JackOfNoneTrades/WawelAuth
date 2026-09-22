package org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport;

import static org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper.SkinLayer.CAPE;

import java.util.UUID;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.fentanylsolutions.wawelauth.api.modernskinsupport.IModelBipedModern;
import org.fentanylsolutions.wawelauth.api.modernskinsupport.RenderPassLayer;
import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DState;
import org.fentanylsolutions.wawelauth.config.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * @see org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport.MixinRenderPlayer
 */
@Mixin(ModelBiped.class)
public class MixinModelBiped extends ModelBase implements IModelBipedModern {

    // ========================================
    // F I E L D S | V A N I L L A
    // ========================================

    @Shadow
    public ModelRenderer bipedHead;
    @Shadow
    public ModelRenderer bipedHeadwear;
    @Shadow
    public ModelRenderer bipedBody;
    @Shadow
    public ModelRenderer bipedRightArm;
    @Shadow
    public ModelRenderer bipedLeftArm;
    @Shadow
    public ModelRenderer bipedRightLeg;
    @Shadow
    public ModelRenderer bipedLeftLeg;
    @Shadow
    public ModelRenderer bipedCloak;

    // ========================================
    // F I E L D S | C U S T O M
    // ========================================

    @Unique
    private static final int CLASSIC = 0;
    @Unique
    private static final int SLIM = 1;

    @Unique
    ModelRenderer[] rightArm = new ModelRenderer[2];
    @Unique
    ModelRenderer[] leftArm = new ModelRenderer[2];

    @Unique
    ModelRenderer jacket;
    @Unique
    ModelRenderer rightPants;
    @Unique
    ModelRenderer leftPants;
    @Unique
    ModelRenderer[] rightSleeve = new ModelRenderer[2];
    @Unique
    ModelRenderer[] leftSleeve = new ModelRenderer[2];

    // ========================================
    // F I E L D S | S T A T E S
    // ========================================

    @Unique
    boolean initialized = false;

    public boolean isInitialized() {
        return this.initialized;
    }

    @Unique
    SkinModel skinModel = SkinModel.CLASSIC;

    @Override
    public SkinModel getSkinModel() {
        return this.skinModel;
    }

    @Override
    public void setSkinModel(SkinModel state) {
        if (!this.initialized) return;
        this.skinModel = state;
        this.bipedRightArm = this.rightArm[state.ordinal()];
        this.bipedLeftArm = this.leftArm[state.ordinal()];
    }

    @Unique
    boolean behindTranslucent;

    @Override
    public boolean isBehindTranslucent() {
        return this.behindTranslucent;
    }

    @Unique
    UUID currentRenderingPlayerUUID = null;

    @Override
    public UUID getRenderPlayerUUID() {
        return this.currentRenderingPlayerUUID;
    }

    @Override
    public void setRenderPlayerUUID(UUID uuid) {
        if (!this.initialized) return;
        this.currentRenderingPlayerUUID = uuid;
    }

    // ========================================
    // I N J E C T S
    // ========================================

    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void setRotationAngles_Post(CallbackInfo ci) {
        if (!this.initialized) return;
        for (SkinLayersHelper.SkinLayer layer : SkinLayersHelper.SkinLayer.VALUES) {
            IModelBipedModern.copyModelFields(baseRendererFromLayer(layer), rendererFromLayer(layer));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render_Pre(CallbackInfo ci, @Local(argsOnly = true, name = "p_78088_1_") Entity p_78088_1_,
        @Local(argsOnly = true, name = "p_78088_7_") float p_78088_7_) {
        if (!this.initialized || !(p_78088_1_ instanceof EntityPlayer player)) return;
        RenderPassLayer pass = RenderPassLayer.getCurrent();
        if (pass != RenderPassLayer.GUI) this.behindTranslucent = IModelBipedModern
            .isBehindTranslucent(player.worldObj, player.posX, player.posY, player.posZ);
        else this.behindTranslucent = false;
    }

    @WrapWithCondition(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V"))
    private boolean hijackBaseRenderer(ModelRenderer instance, float p_78785_1_) {
        RenderPassLayer pass = RenderPassLayer.getCurrent();
        boolean isHeadwear = instance == this.bipedHeadwear;
        /// skip headwear as it's layer (rendered later),
        /// don't render base parts during 1 pass
        return !isHeadwear && pass != RenderPassLayer.TRANSLUCENT;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render_Post(CallbackInfo ci, @Local(argsOnly = true, name = "p_78088_1_") Entity p_78088_1_,
        @Local(argsOnly = true, name = "p_78088_7_") float p_78088_7_) {
        if (!this.initialized || !(p_78088_1_ instanceof EntityPlayer player)) return;
        RenderPassLayer pass = RenderPassLayer.getCurrent();
        renderAllLayers(player, p_78088_7_, pass);
    }

    // ========================================
    // O V E R R I D E S
    // ========================================

    @Override
    public void init(float scale) {
        this.textureWidth = 64;
        this.textureHeight = 32;
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
        this.initialized = true;
    }

    @Override
    public void renderAllLayers(EntityPlayer player, float scale, RenderPassLayer pass) {
        if (!this.initialized || !pass.shouldRender(this.behindTranslucent)) return;

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);

            SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUUID);
            for (SkinLayersHelper.SkinLayer layer : SkinLayersHelper.SkinLayer.VALUES) {
                if (layer == CAPE) continue;
                SkinLayersHelper.LayerState state = SkinLayersHelper.getSkinLayerState(player, layer);
                renderLayer(layer, state, state3d, scale);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void renderLayer(SkinLayersHelper.SkinLayer layer, SkinLayersHelper.LayerState state,
        SkinLayers3DState state3d, float scale) {
        if (!this.initialized) return;

        ModelRenderer overlay = rendererFromLayer(layer);

        if (state.isDisabled() || !overlay.showModel) return;

        SkinLayers3DMesh mesh = null;
        if (state3d != null && state3d.initialized) mesh = state3d.meshFromPart(layer);
        if (mesh == null || !mesh.isCompiled()) state = SkinLayersHelper.LayerState.FLAT;

        renderLayerSide(GL11.GL_FRONT, layer, state, mesh, overlay, scale);
        renderLayerSide(GL11.GL_BACK, layer, state, mesh, overlay, scale);
    }

    @Override
    public void renderLayerSide(int glCullFace, SkinLayersHelper.SkinLayer layer, SkinLayersHelper.LayerState state,
        SkinLayers3DMesh mesh, ModelRenderer overlay, float scale) {
        GL11.glCullFace(glCullFace);
        if (state == SkinLayersHelper.LayerState.VOLUMETRIC) {
            renderMesh(layer, mesh, overlay, scale);
        } else {
            overlay.render(scale);
        }
    }

    @Override
    public void renderMesh(SkinLayersHelper.SkinLayer layer, SkinLayers3DMesh mesh, ModelRenderer source, float scale) {
        if (!this.initialized) return;

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

        switch (layer) {
            case HAT -> {
                final float headVoxel = SkinLayers3DConfig.headVoxelSize;
                scaleX = headVoxel;
                scaleY = headVoxel;
                scaleZ = headVoxel;
            }
            case JACKET -> {
                scaleX = SkinLayers3DConfig.bodyVoxelWidthSize;
                scaleY = 1.035F;
                offsetY = -0.2F;
            }
            case RIGHT_SLEEVE -> {
                scaleY = 1.035F;
                offsetX = this.skinModel.isSlim() ? -0.499F : -0.998F;
                offsetY = -0.1F;
            }
            case LEFT_SLEEVE -> {
                scaleY = 1.035F;
                offsetX = this.skinModel.isSlim() ? 0.499F : 0.998F;
                offsetY = -0.1F;
            }

            case RIGHT_PANTS, LEFT_PANTS -> {
                scaleY = 1.035F;
                offsetY = -0.2F;
            }
            default -> {}
        }

        mesh.render(scale, scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ);
    }

    @Override
    public ModelRenderer rendererFromLayer(SkinLayersHelper.SkinLayer layer) {
        return switch (layer) {
            case CAPE -> this.bipedCloak;
            case JACKET -> this.jacket;
            case LEFT_SLEEVE -> this.leftSleeve[this.skinModel.ordinal()];
            case RIGHT_SLEEVE -> this.rightSleeve[this.skinModel.ordinal()];
            case LEFT_PANTS -> this.leftPants;
            case RIGHT_PANTS -> this.rightPants;
            case HAT -> this.bipedHeadwear;
        };
    }

    @Override
    public ModelRenderer baseRendererFromLayer(SkinLayersHelper.SkinLayer layer) {
        return switch (layer) {
            case CAPE -> this.bipedCloak;
            case JACKET -> this.bipedBody;
            case LEFT_SLEEVE -> this.bipedLeftArm;
            case RIGHT_SLEEVE -> this.bipedRightArm;
            case LEFT_PANTS -> this.bipedLeftLeg;
            case RIGHT_PANTS -> this.bipedRightLeg;
            case HAT -> this.bipedHead;
        };
    }

    @Override
    public SkinLayersHelper.SkinLayer layerFromRenderer(ModelRenderer renderer) {
        if (renderer == this.bipedCloak) return SkinLayersHelper.SkinLayer.CAPE;
        if (renderer == this.jacket) return SkinLayersHelper.SkinLayer.JACKET;

        ModelRenderer[] leftSleeveArr = this.leftSleeve;
        if (renderer == leftSleeveArr[SLIM] || renderer == leftSleeveArr[CLASSIC])
            return SkinLayersHelper.SkinLayer.LEFT_SLEEVE;

        ModelRenderer[] rightSleeveArr = this.rightSleeve;
        if (renderer == rightSleeveArr[SLIM] || renderer == rightSleeveArr[CLASSIC])
            return SkinLayersHelper.SkinLayer.RIGHT_SLEEVE;

        if (renderer == this.leftPants) return SkinLayersHelper.SkinLayer.LEFT_PANTS;
        if (renderer == this.rightPants) return SkinLayersHelper.SkinLayer.RIGHT_PANTS;
        if (renderer == this.bipedHeadwear) return SkinLayersHelper.SkinLayer.HAT;
        return null;
    }
}
