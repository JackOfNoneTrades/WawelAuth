package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.UUID;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import org.fentanylsolutions.wawelauth.client.render.IModelBipedModernExt;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DMesh;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DState;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.Loader;

/**
 * Injects modern 64x64 skin support into vanilla ModelBiped.
 * <p>
 * Adds 5 overlay layers (body wear, arm wear, leg wear), dedicated left-limb UVs,
 * and slim (3px) arm variant support. The model instance remains vanilla ModelBiped:
 * only its internal ModelRenderer parts are rebuilt for 64x64 UV mapping.
 * <p>
 * Non-player ModelBiped instances (zombies, skeletons, armor) are unaffected:
 * modernEnabled stays false, and all injections are no-ops.
 */
@SuppressWarnings("AddedMixinMembersNamePattern") // Using @Unique already prevents collisions
@Mixin(ModelBiped.class)
public abstract class MixinModelBiped extends ModelBase implements IModelBipedModernExt {

    // -- Vanilla fields --
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

    // -- Overlay layers --
    @Unique
    private ModelRenderer bodyWear;
    @Unique
    private ModelRenderer rightLegWear;
    @Unique
    private ModelRenderer leftLegWear;

    // -- Classic (4px) arm variants --
    @Unique
    private ModelRenderer classicRightArm;
    @Unique
    private ModelRenderer classicLeftArm;
    @Unique
    private ModelRenderer classicRightArmWear;
    @Unique
    private ModelRenderer classicLeftArmWear;

    // -- Slim (3px) arm variants --
    @Unique
    private ModelRenderer slimRightArm;
    @Unique
    private ModelRenderer slimLeftArm;
    @Unique
    private ModelRenderer slimRightArmWear;
    @Unique
    private ModelRenderer slimLeftArmWear;

    // -- State --
    @Unique
    private boolean modernEnabled = false;
    @Unique
    private boolean currentSlim = false;

    // -- 3D skin layers state --
    @Unique
    private UUID currentRenderingPlayerUuid = null;
    @Unique
    private static final int LAYER_PART_HAT = 0;
    @Unique
    private static final int LAYER_PART_BODY = 1;
    @Unique
    private static final int LAYER_PART_RIGHT_ARM = 2;
    @Unique
    private static final int LAYER_PART_LEFT_ARM = 3;
    @Unique
    private static final int LAYER_PART_RIGHT_LEG = 4;
    @Unique
    private static final int LAYER_PART_LEFT_LEG = 5;

    @Override
    public void initModern() {
        ModelBiped self = (ModelBiped) (Object) this;

        float scale = 0.0F;
        float overlay = 0.25F;

        self.textureWidth = 64;
        self.textureHeight = 64;

        /* Main Skin */

        this.remapUV(bipedHead, 0, 0);
        this.bipedHead.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, scale);

        this.remapUV(bipedBody, 16, 16);
        this.bipedBody.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale);

        this.remapUV(bipedRightArm, 40, 16);

        this.classicRightArm = new ModelRenderer(self, 40, 16);
        this.classicRightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale);

        this.slimRightArm = new ModelRenderer(self, 40, 16);
        this.slimRightArm.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.slimRightArm.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.remapUV(bipedLeftArm, 32, 48);

        this.classicLeftArm = new ModelRenderer(self, 32, 48);
        this.classicLeftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale);

        this.slimLeftArm = new ModelRenderer(self, 32, 48);
        this.slimLeftArm.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.slimLeftArm.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.remapUV(bipedRightLeg, 0, 16);
        this.bipedRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale);

        this.remapUV(bipedLeftLeg, 16, 48);
        this.bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale);

        this.remapUV(bipedHeadwear, 32, 0);
        this.bipedHeadwear.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, scale + 0.5F);

        /* Skin Overlay */

        this.bodyWear = new ModelRenderer(self, 16, 32);
        this.bodyWear.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale + overlay);

        this.classicRightArmWear = new ModelRenderer(self, 40, 32);
        this.classicRightArmWear.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);

        this.slimRightArmWear = new ModelRenderer(self, 40, 32);
        this.slimRightArmWear.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);
        this.slimRightArmWear.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.classicLeftArmWear = new ModelRenderer(self, 48, 48);
        this.classicLeftArmWear.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);

        this.slimLeftArmWear = new ModelRenderer(self, 48, 48);
        this.slimLeftArmWear.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);
        this.slimLeftArmWear.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.rightLegWear = new ModelRenderer(self, 0, 32);
        this.rightLegWear.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);

        this.leftLegWear = new ModelRenderer(self, 0, 48);
        this.leftLegWear.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);

        /* Kindergarten */

        this.bipedBody.addChild(bodyWear);

        this.classicRightArm.addChild(classicRightArmWear);
        this.classicLeftArm.addChild(classicLeftArmWear);
        this.slimRightArm.addChild(slimRightArmWear);
        this.slimLeftArm.addChild(slimLeftArmWear);

        this.bipedRightArm.addChild(classicRightArm);
        this.bipedRightArm.addChild(slimRightArm);
        this.bipedLeftArm.addChild(classicLeftArm);
        this.bipedLeftArm.addChild(slimLeftArm);

        this.bipedRightLeg.addChild(rightLegWear);
        this.bipedLeftLeg.addChild(leftLegWear);

        this.modernEnabled = true;
        this.currentSlim = false;
    }

    @Override
    public void setSlim(boolean slim) {
        if (!this.modernEnabled) return;

        this.currentSlim = slim;

        this.classicRightArm.showModel = !slim;
        this.classicLeftArm.showModel = !slim;

        this.slimRightArm.showModel = slim;
        this.slimLeftArm.showModel = slim;
    }

    @Unique
    private void remapUV(ModelRenderer renderer, int texOffsetX, int texOffsetY) {
        renderer.setTextureSize(64, 64);
        renderer.cubeList.clear();
        renderer.setTextureOffset(texOffsetX, texOffsetY);
    }

    @Unique
    private boolean is3DEnabled() {
        return SkinLayers3DConfig.enabled3D && this.modernEnabled
            && SkinLayers3DConfig.modernSkinSupport
            && !Loader.isModLoaded("SmartMoving");
    }

    @Unique
    boolean headWearSaved;
    @Unique
    boolean bodyWearSaved;
    @Unique
    boolean rightArmWearSaved;
    @Unique
    boolean leftArmWearSaved;
    @Unique
    boolean rightLegWearSaved;
    @Unique
    boolean leftLegWearSaved;

    @Override
    public void render3DRightArmWear(float scale) {
        if (!is3DEnabled()) return;

        ModelRenderer rightArmWear = this.getRightArmWear();
        boolean rightArmWearSaved = rightArmWear.showModel;
        rightArmWear.showModel = false;

        SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
        if (state3d != null && state3d.initialized) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            renderMesh(
                state3d.rightSleeveMesh,
                this.bipedRightArm,
                scale,
                SkinLayers3DConfig.enableRightSleeve3D && rightArmWearSaved,
                LAYER_PART_RIGHT_ARM);
        }
    }

    /**
     * Before vanilla render: suppress overlay rendering when 3D is active.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, CallbackInfo ci) {
        if (!is3DEnabled()) return;

        ModelRenderer rightArmWear = this.getRightArmWear();
        ModelRenderer leftArmWear = this.getLeftArmWear();

        headWearSaved = this.bipedHeadwear.showModel;
        bodyWearSaved = this.bodyWear.showModel;
        rightArmWearSaved = rightArmWear.showModel;
        leftArmWearSaved = leftArmWear.showModel;
        rightLegWearSaved = this.rightLegWear.showModel;
        leftLegWearSaved = this.leftLegWear.showModel;

        SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
        if (state3d != null && state3d.initialized) {
            if (state3d.hatMesh != null && SkinLayers3DConfig.enableHat3D) {
                this.bipedHeadwear.showModel = false;
            }
            if (state3d.jacketMesh != null && SkinLayers3DConfig.enableJacket3D) {
                this.bodyWear.showModel = false;
            }
            if (state3d.rightSleeveMesh != null && SkinLayers3DConfig.enableRightSleeve3D) {
                rightArmWear.showModel = false;
            }
            if (state3d.leftSleeveMesh != null && SkinLayers3DConfig.enableLeftSleeve3D) {
                leftArmWear.showModel = false;
            }
            if (state3d.rightPantsMesh != null && SkinLayers3DConfig.enableRightPants3D) {
                this.rightLegWear.showModel = false;
            }
            if (state3d.leftPantsMesh != null && SkinLayers3DConfig.enableLeftPants3D) {
                this.leftLegWear.showModel = false;
            }
        }
    }

    /**
     * Render all 3D overlay layers after the base model renders
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void render3DOverlays(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float netHeadYaw, float headPitch, float scaleFactor, CallbackInfo ci) {
        if (!is3DEnabled()) return;

        if (this.isChild) {
            GL11.glPushMatrix();
            GL11.glScalef(1.0F / 2.0F, 1.0F / 2.0F, 1.0F / 2.0F);
            GL11.glTranslatef(0.0F, 24.0F * scaleFactor, 0.0F);
            render3D(
                scaleFactor,
                headWearSaved,
                bodyWearSaved,
                rightArmWearSaved,
                leftArmWearSaved,
                rightLegWearSaved,
                leftLegWearSaved);
            GL11.glPopMatrix();
        } else {
            render3D(
                scaleFactor,
                headWearSaved,
                bodyWearSaved,
                rightArmWearSaved,
                leftArmWearSaved,
                rightLegWearSaved,
                leftLegWearSaved);
        }
    }

    @Unique
    private void render3D(float scaleFactor, boolean headWear, boolean bodyWear, boolean rightArmWear,
        boolean leftArmWear, boolean rightLegWear, boolean leftLegWear) {
        SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
        if (state3d != null && state3d.initialized) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            renderMesh(
                state3d.hatMesh,
                this.bipedHead,
                scaleFactor,
                SkinLayers3DConfig.enableHat3D && headWear,
                LAYER_PART_HAT);
            renderMesh(
                state3d.jacketMesh,
                this.bipedBody,
                scaleFactor,
                SkinLayers3DConfig.enableJacket3D && bodyWear,
                LAYER_PART_BODY);
            renderMesh(
                state3d.rightSleeveMesh,
                this.bipedRightArm,
                scaleFactor,
                SkinLayers3DConfig.enableRightSleeve3D && rightArmWear,
                LAYER_PART_RIGHT_ARM);
            renderMesh(
                state3d.leftSleeveMesh,
                this.bipedLeftArm,
                scaleFactor,
                SkinLayers3DConfig.enableLeftSleeve3D && leftArmWear,
                LAYER_PART_LEFT_ARM);
            renderMesh(
                state3d.rightPantsMesh,
                this.bipedRightLeg,
                scaleFactor,
                SkinLayers3DConfig.enableRightPants3D && rightLegWear,
                LAYER_PART_RIGHT_LEG);
            renderMesh(
                state3d.leftPantsMesh,
                this.bipedLeftLeg,
                scaleFactor,
                SkinLayers3DConfig.enableLeftPants3D && leftLegWear,
                LAYER_PART_LEFT_LEG);
        }
    }

    /**
     * Render a 3D mesh if available and enabled.
     */
    @Unique
    private void renderMesh(SkinLayers3DMesh mesh, ModelRenderer source, float scaleFactor, boolean enabled, int part) {
        if (enabled && mesh != null && mesh.isCompiled() && source != null) {

            // Sync position / offset / rotation from the corresponding vanilla ModelRenderer
            mesh.setPosition(source.rotationPointX, source.rotationPointY, source.rotationPointZ);
            mesh.setOffset(source.offsetX, source.offsetY, source.offsetZ);
            mesh.setRotation(source.rotateAngleX, source.rotateAngleY, source.rotateAngleZ);

            float scaleX;
            float scaleY;
            float scaleZ;
            float offsetX = 0.0F;
            float offsetY = 0.0F;
            float offsetZ = 0.0F;

            switch (part) {
                case LAYER_PART_HAT:
                    scaleX = SkinLayers3DConfig.headVoxelSize;
                    scaleY = SkinLayers3DConfig.headVoxelSize;
                    scaleZ = SkinLayers3DConfig.headVoxelSize;
                    break;
                case LAYER_PART_BODY:
                    scaleX = SkinLayers3DConfig.bodyVoxelWidthSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetY = -0.2F;
                    break;
                case LAYER_PART_RIGHT_ARM:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetX = this.currentSlim ? -0.499F : -0.998F;
                    offsetY = -0.1F;
                    break;
                case LAYER_PART_LEFT_ARM:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetX = this.currentSlim ? 0.499F : 0.998F;
                    offsetY = -0.1F;
                    break;
                case LAYER_PART_RIGHT_LEG:
                case LAYER_PART_LEFT_LEG:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetY = -0.2F;
                    break;
                default:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = SkinLayers3DConfig.baseVoxelSize;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    break;
            }

            mesh.render(scaleFactor, scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ);
        }
    }

    @Override
    public boolean isModern() {
        return this.modernEnabled;
    }

    @Override
    public void setCurrentPlayerUuid(UUID uuid) {
        this.currentRenderingPlayerUuid = uuid;
    }

    @Override
    public ModelRenderer getBodyWear() {
        return this.bodyWear;
    }

    @Override
    public ModelRenderer getRightArmWear() {
        return this.currentSlim ? this.slimRightArmWear : this.classicRightArmWear;
    }

    @Override
    public ModelRenderer getLeftArmWear() {
        return this.currentSlim ? this.slimLeftArmWear : this.classicLeftArmWear;
    }

    @Override
    public ModelRenderer getRightLegWear() {
        return this.rightLegWear;
    }

    @Override
    public ModelRenderer getLeftLegWear() {
        return this.leftLegWear;
    }

}
