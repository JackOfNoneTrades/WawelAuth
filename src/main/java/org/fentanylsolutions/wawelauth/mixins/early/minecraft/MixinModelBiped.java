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
    @Shadow
    public ModelRenderer bipedEars;
    @Shadow
    public ModelRenderer bipedCloak;

    // -- Overlay layers --
    @Unique
    private ModelRenderer wawelauth$bodyWear;
    @Unique
    private ModelRenderer wawelauth$rightArmWear;
    @Unique
    private ModelRenderer wawelauth$leftArmWear;
    @Unique
    private ModelRenderer wawelauth$rightLegWear;
    @Unique
    private ModelRenderer wawelauth$leftLegWear;

    // -- Classic (4px) arm references --
    @Unique
    private ModelRenderer wawelauth$classicRightArm;
    @Unique
    private ModelRenderer wawelauth$classicLeftArm;
    @Unique
    private ModelRenderer wawelauth$classicRightArmWear;
    @Unique
    private ModelRenderer wawelauth$classicLeftArmWear;

    // -- Slim (3px) arm variants --
    @Unique
    private ModelRenderer wawelauth$slimRightArm;
    @Unique
    private ModelRenderer wawelauth$slimLeftArm;
    @Unique
    private ModelRenderer wawelauth$slimRightArmWear;
    @Unique
    private ModelRenderer wawelauth$slimLeftArmWear;

    // -- State --
    @Unique
    private boolean wawelauth$modernEnabled = false;
    @Unique
    private boolean wawelauth$currentSlim = false;

    // -- 3D skin layers state --
    @Unique
    private UUID wawelauth$currentRenderingPlayerUuid = null;
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
    public ModelRenderer wawelAuth$getBodyWear() {
        return this.wawelauth$bodyWear;
    }

    @Override
    public ModelRenderer wawelAuth$getRightArmWear() {
        return this.wawelauth$rightArmWear;
    }

    @Override
    public ModelRenderer wawelAuth$getLeftArmWear() {
        return this.wawelauth$leftArmWear;
    }

    @Override
    public ModelRenderer wawelAuth$getRightLegWear() {
        return this.wawelauth$rightLegWear;
    }

    @Override
    public ModelRenderer wawelAuth$getLeftLegWear() {
        return this.wawelauth$leftLegWear;
    }

    @Override
    public void wawelauth$initModern() {
        ModelBiped self = (ModelBiped) (Object) this;

        // Set texture dimensions to 64x64 BEFORE creating any new ModelRenderers
        // so they pick up the correct texture size in their constructor.
        self.textureWidth = 64;
        self.textureHeight = 64;

        float scale = 0.0F;

        // -- Rebuild all vanilla parts with 64x64 UV mapping --
        // Head (0,0)
        this.bipedHead = new ModelRenderer(self, 0, 0);
        this.bipedHead.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, scale);
        this.bipedHead.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Hat overlay (32,0)
        this.bipedHeadwear = new ModelRenderer(self, 32, 0);
        this.bipedHeadwear.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, scale + 0.5F);
        this.bipedHeadwear.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Body (16,16)
        this.bipedBody = new ModelRenderer(self, 16, 16);
        this.bipedBody.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale);
        this.bipedBody.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Right arm: classic 4px (40,16)
        this.bipedRightArm = new ModelRenderer(self, 40, 16);
        this.bipedRightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale);
        this.bipedRightArm.setRotationPoint(-5.0F, 2.0F, 0.0F);

        // Left arm: dedicated UV, NOT mirrored (32,48)
        this.bipedLeftArm = new ModelRenderer(self, 32, 48);
        this.bipedLeftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale);
        this.bipedLeftArm.setRotationPoint(5.0F, 2.0F, 0.0F);

        // Right leg (0,16)
        this.bipedRightLeg = new ModelRenderer(self, 0, 16);
        this.bipedRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale);
        this.bipedRightLeg.setRotationPoint(-1.9F, 12.0F, 0.0F);

        // Left leg: dedicated UV, NOT mirrored (16,48)
        this.bipedLeftLeg = new ModelRenderer(self, 16, 48);
        this.bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale);
        this.bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);

        // Cloak (0,0): same as vanilla
        this.bipedCloak = new ModelRenderer(self, 0, 0);
        // Cape textures are 64x32, not 64x64.
        this.bipedCloak.setTextureSize(64, 32);
        this.bipedCloak.addBox(-5.0F, 0.0F, -1.0F, 10, 16, 1, scale);

        // Ears (24,0): same as vanilla
        this.bipedEars = new ModelRenderer(self, 24, 0);
        this.bipedEars.addBox(-3.0F, -6.0F, -1.0F, 6, 6, 1, scale);

        // -- Create 5 overlay layers (all with +0.25F expansion) --
        float overlay = 0.25F;

        // Body wear (16,32)
        this.wawelauth$bodyWear = new ModelRenderer(self, 16, 32);
        this.wawelauth$bodyWear.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale + overlay);
        this.wawelauth$bodyWear.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Right arm wear (40,32)
        ModelRenderer rightArmWear = new ModelRenderer(self, 40, 32);
        rightArmWear.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);
        rightArmWear.setRotationPoint(-5.0F, 2.0F, 0.0F);
        this.wawelauth$rightArmWear = rightArmWear;

        // Left arm wear (48,48)
        ModelRenderer leftArmWear = new ModelRenderer(self, 48, 48);
        leftArmWear.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);
        leftArmWear.setRotationPoint(5.0F, 2.0F, 0.0F);
        this.wawelauth$leftArmWear = leftArmWear;

        // Right leg wear (0,32)
        this.wawelauth$rightLegWear = new ModelRenderer(self, 0, 32);
        this.wawelauth$rightLegWear.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);
        this.wawelauth$rightLegWear.setRotationPoint(-1.9F, 12.0F, 0.0F);

        // Left leg wear (0,48)
        this.wawelauth$leftLegWear = new ModelRenderer(self, 0, 48);
        this.wawelauth$leftLegWear.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);
        this.wawelauth$leftLegWear.setRotationPoint(1.9F, 12.0F, 0.0F);

        // -- Save classic arm references --
        this.wawelauth$classicRightArm = this.bipedRightArm;
        this.wawelauth$classicLeftArm = this.bipedLeftArm;
        this.wawelauth$classicRightArmWear = this.wawelauth$rightArmWear;
        this.wawelauth$classicLeftArmWear = this.wawelauth$leftArmWear;

        // -- Create slim (3px) arm variants --
        // Slim right arm (40,16): box width 3 instead of 4
        this.wawelauth$slimRightArm = new ModelRenderer(self, 40, 16);
        this.wawelauth$slimRightArm.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.wawelauth$slimRightArm.setRotationPoint(-5.0F, 2.5F, 0.0F);

        // Slim left arm (32,48): box width 3
        this.wawelauth$slimLeftArm = new ModelRenderer(self, 32, 48);
        this.wawelauth$slimLeftArm.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.wawelauth$slimLeftArm.setRotationPoint(5.0F, 2.5F, 0.0F);

        // Slim right arm wear (40,32)
        this.wawelauth$slimRightArmWear = new ModelRenderer(self, 40, 32);
        this.wawelauth$slimRightArmWear.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);
        this.wawelauth$slimRightArmWear.setRotationPoint(-5.0F, 2.5F, 0.0F);

        // Slim left arm wear (48,48)
        this.wawelauth$slimLeftArmWear = new ModelRenderer(self, 48, 48);
        this.wawelauth$slimLeftArmWear.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);
        this.wawelauth$slimLeftArmWear.setRotationPoint(5.0F, 2.5F, 0.0F);

        this.wawelauth$modernEnabled = true;
        this.wawelauth$currentSlim = false;
    }

    @Override
    public void wawelauth$setSlim(boolean slim) {
        if (!this.wawelauth$modernEnabled) return;
        if (slim == this.wawelauth$currentSlim) return;

        if (slim) {
            this.bipedRightArm = this.wawelauth$slimRightArm;
            this.bipedLeftArm = this.wawelauth$slimLeftArm;
            this.wawelauth$rightArmWear = this.wawelauth$slimRightArmWear;
            this.wawelauth$leftArmWear = this.wawelauth$slimLeftArmWear;
        } else {
            this.bipedRightArm = this.wawelauth$classicRightArm;
            this.bipedLeftArm = this.wawelauth$classicLeftArm;
            this.wawelauth$rightArmWear = this.wawelauth$classicRightArmWear;
            this.wawelauth$leftArmWear = this.wawelauth$classicLeftArmWear;
        }

        this.wawelauth$currentSlim = slim;
    }

    @Override
    public boolean wawelauth$isModern() {
        return this.wawelauth$modernEnabled;
    }

    @Override
    public void wawelauth$setCurrentPlayerUuid(UUID uuid) {
        this.wawelauth$currentRenderingPlayerUuid = uuid;
    }

    @Override
    public void wawelauth$renderRightArmWear(float scale) {
        if (!this.wawelauth$modernEnabled || !SkinLayers3DConfig.modernSkinSupport) return;
        if (this.bipedRightArm != null && this.wawelauth$rightArmWear != null) {
            wawelauth$copyAngles(this.bipedRightArm, this.wawelauth$rightArmWear);
        }

        SkinLayers3DState state3d = SkinLayers3DSetup.getState(wawelauth$currentRenderingPlayerUuid);
        if (SkinLayers3DConfig.enabled3D && state3d != null && state3d.initialized) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            wawelauth$render3DOrFlat(
                state3d.rightSleeveMesh,
                this.wawelauth$rightArmWear,
                scale,
                SkinLayers3DConfig.enableRightSleeve3D,
                LAYER_PART_RIGHT_ARM);
        } else if (this.wawelauth$rightArmWear != null) {
            this.wawelauth$rightArmWear.render(scale);
        }
    }

    // Saved showModel state for bipedHeadwear when suppressed for 3D rendering
    @Unique
    private boolean wawelauth$savedHeadwearShowModel;

    /**
     * Before vanilla render: suppress bipedHeadwear when 3D hat is active,
     * since our overlay method will render the 3D version instead.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void wawelauth$preRender(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float netHeadYaw, float headPitch, float scaleFactor, CallbackInfo ci) {
        if (!this.wawelauth$modernEnabled || !SkinLayers3DConfig.modernSkinSupport) return;

        SkinLayers3DState state3d = SkinLayers3DSetup.getState(wawelauth$currentRenderingPlayerUuid);
        this.wawelauth$savedHeadwearShowModel = this.bipedHeadwear.showModel;
        if (SkinLayers3DConfig.enabled3D && state3d != null
            && state3d.initialized
            && state3d.hatMesh != null
            && SkinLayers3DConfig.enableHat3D) {
            // Hide vanilla flat hat: we'll render the 3D version in renderAllOverlays
            this.bipedHeadwear.showModel = false;
        }
    }

    /**
     * Render all overlay layers after the base model renders.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void wawelauth$renderOverlays(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float netHeadYaw, float headPitch, float scaleFactor, CallbackInfo ci) {
        if (!this.wawelauth$modernEnabled || !SkinLayers3DConfig.modernSkinSupport) return;

        // Restore bipedHeadwear showModel
        this.bipedHeadwear.showModel = this.wawelauth$savedHeadwearShowModel;

        if (this.isChild) {
            GL11.glPushMatrix();
            GL11.glScalef(1.0F / 2.0F, 1.0F / 2.0F, 1.0F / 2.0F);
            GL11.glTranslatef(0.0F, 24.0F * scaleFactor, 0.0F);
            wawelauth$renderAllOverlays(scaleFactor);
            GL11.glPopMatrix();
        } else {
            wawelauth$renderAllOverlays(scaleFactor);
        }
    }

    /**
     * Sync overlay layer rotation angles after vanilla setRotationAngles.
     */
    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void wawelauth$syncOverlayAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, Entity entity, CallbackInfo ci) {
        if (!this.wawelauth$modernEnabled || !SkinLayers3DConfig.modernSkinSupport) return;

        wawelauth$copyAngles(this.bipedBody, this.wawelauth$bodyWear);
        wawelauth$copyAngles(this.bipedRightArm, this.wawelauth$rightArmWear);
        wawelauth$copyAngles(this.bipedLeftArm, this.wawelauth$leftArmWear);
        wawelauth$copyAngles(this.bipedRightLeg, this.wawelauth$rightLegWear);
        wawelauth$copyAngles(this.bipedLeftLeg, this.wawelauth$leftLegWear);
    }

    @Unique
    private void wawelauth$renderAllOverlays(float scaleFactor) {
        SkinLayers3DState state3d = SkinLayers3DSetup.getState(wawelauth$currentRenderingPlayerUuid);
        if (SkinLayers3DConfig.enabled3D && state3d != null && state3d.initialized) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Render 3D meshes where available, fall back to 2D for disabled parts
            wawelauth$render3DOrFlat(
                state3d.hatMesh,
                this.bipedHeadwear,
                scaleFactor,
                SkinLayers3DConfig.enableHat3D,
                LAYER_PART_HAT);
            wawelauth$render3DOrFlat(
                state3d.jacketMesh,
                this.wawelauth$bodyWear,
                scaleFactor,
                SkinLayers3DConfig.enableJacket3D,
                LAYER_PART_BODY);
            wawelauth$render3DOrFlat(
                state3d.rightSleeveMesh,
                this.wawelauth$rightArmWear,
                scaleFactor,
                SkinLayers3DConfig.enableRightSleeve3D,
                LAYER_PART_RIGHT_ARM);
            wawelauth$render3DOrFlat(
                state3d.leftSleeveMesh,
                this.wawelauth$leftArmWear,
                scaleFactor,
                SkinLayers3DConfig.enableLeftSleeve3D,
                LAYER_PART_LEFT_ARM);
            wawelauth$render3DOrFlat(
                state3d.rightPantsMesh,
                this.wawelauth$rightLegWear,
                scaleFactor,
                SkinLayers3DConfig.enableRightPants3D,
                LAYER_PART_RIGHT_LEG);
            wawelauth$render3DOrFlat(
                state3d.leftPantsMesh,
                this.wawelauth$leftLegWear,
                scaleFactor,
                SkinLayers3DConfig.enableLeftPants3D,
                LAYER_PART_LEFT_LEG);
        } else {
            // Fall back to standard 2D overlay rendering.
            // Note: bipedHeadwear is NOT rendered here: vanilla ModelBiped.render() already handles it.
            if (this.wawelauth$bodyWear != null) this.wawelauth$bodyWear.render(scaleFactor);
            if (this.wawelauth$rightArmWear != null) this.wawelauth$rightArmWear.render(scaleFactor);
            if (this.wawelauth$leftArmWear != null) this.wawelauth$leftArmWear.render(scaleFactor);
            if (this.wawelauth$rightLegWear != null) this.wawelauth$rightLegWear.render(scaleFactor);
            if (this.wawelauth$leftLegWear != null) this.wawelauth$leftLegWear.render(scaleFactor);
        }
    }

    /**
     * Render a 3D mesh if available and enabled, otherwise fall back to the 2D flat overlay.
     * Copies rotation/position from the corresponding vanilla ModelRenderer to the mesh.
     */
    @Unique
    private void wawelauth$render3DOrFlat(SkinLayers3DMesh mesh, ModelRenderer fallback, float scaleFactor,
        boolean enabled, int part) {
        if (enabled && mesh != null && mesh.isCompiled() && fallback != null) {
            // Respect vanilla showModel toggles (hat, etc).
            if (!fallback.showModel) {
                return;
            }

            // Sync rotation and position from the corresponding vanilla ModelRenderer.
            mesh.setPosition(fallback.rotationPointX, fallback.rotationPointY, fallback.rotationPointZ);
            mesh.setRotation(fallback.rotateAngleX, fallback.rotateAngleY, fallback.rotateAngleZ);

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
                    offsetX = this.wawelauth$currentSlim ? -0.499F : -0.998F;
                    offsetY = -0.1F;
                    break;
                case LAYER_PART_LEFT_ARM:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetX = this.wawelauth$currentSlim ? 0.499F : 0.998F;
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
        } else if (fallback != null) {
            fallback.render(scaleFactor);
        }
    }

    @Unique
    private static void wawelauth$copyAngles(ModelRenderer source, ModelRenderer dest) {
        if (source == null || dest == null) return;
        dest.rotateAngleX = source.rotateAngleX;
        dest.rotateAngleY = source.rotateAngleY;
        dest.rotateAngleZ = source.rotateAngleZ;
        dest.rotationPointX = source.rotationPointX;
        dest.rotationPointY = source.rotationPointY;
        dest.rotationPointZ = source.rotationPointZ;
    }
}
