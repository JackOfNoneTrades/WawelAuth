package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import cpw.mods.fml.common.Loader;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
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

import java.util.UUID;

import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.CAPE;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.HAT;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.JACKET;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.LEFT_PANTS;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.LEFT_SLEEVE;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.RIGHT_PANTS;
import static org.fentanylsolutions.wawelauth.api.SkinLayersHelper.EnumPlayerModelParts.RIGHT_SLEEVE;


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
    @Shadow public ModelRenderer bipedHead;
    @Shadow public ModelRenderer bipedHeadwear;
    @Shadow public ModelRenderer bipedBody;
    @Shadow public ModelRenderer bipedRightArm;
    @Shadow public ModelRenderer bipedLeftArm;
    @Shadow public ModelRenderer bipedRightLeg;
    @Shadow public ModelRenderer bipedLeftLeg;
    @Shadow public ModelRenderer bipedCloak;

    // -- Overlay layers --
    @Unique private ModelRenderer jacket;
    @Unique private ModelRenderer rightPants;
    @Unique private ModelRenderer leftPants;

    // -- Classic (4px) arm variants --
    @Unique private ModelRenderer classicRightArm;
    @Unique private ModelRenderer classicLeftArm;
    @Unique private ModelRenderer classicRightSleeve;
    @Unique private ModelRenderer classicLeftSleeve;

    // -- Slim (3px) arm variants --
    @Unique private ModelRenderer slimRightArm;
    @Unique private ModelRenderer slimLeftArm;
    @Unique private ModelRenderer slimRightSleeve;
    @Unique private ModelRenderer slimLeftSleeve;

    // -- State --
    @Unique private boolean modernEnabled = false;
    @Unique private boolean currentSlim = false;

    // -- 3D skin layers state --
    @Unique private UUID currentRenderingPlayerUuid = null;

    @Override
    public void initModern() {
        ModelBiped self = (ModelBiped)(Object) this;

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
        this.bipedRightArm.addBox(0.0F, 0.0F, 0.0F, 1, 1, 1, 0.0F);
        this.classicRightArm = new ModelRenderer(self, 40, 16);
        this.classicRightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale);
        this.slimRightArm = new ModelRenderer(self, 40, 16);
        this.slimRightArm.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale);
        this.slimRightArm.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.remapUV(bipedLeftArm, 32, 48);
        this.bipedLeftArm.addBox(0.0F, 0.0F, 0.0F, 1, 1, 1, 0.0F);
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

        this.jacket = new ModelRenderer(self, 16, 32);
        this.jacket.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, scale + overlay);

        this.classicRightSleeve = new ModelRenderer(self, 40, 32);
        this.classicRightSleeve.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);
        this.slimRightSleeve = new ModelRenderer(self, 40, 32);
        this.slimRightSleeve.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);
        this.slimRightSleeve.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.classicLeftSleeve = new ModelRenderer(self, 48, 48);
        this.classicLeftSleeve.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, scale + overlay);
        this.slimLeftSleeve = new ModelRenderer(self, 48, 48);
        this.slimLeftSleeve.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, scale + overlay);
        this.slimLeftSleeve.setRotationPoint(0.0F, 0.5F, 0.0F);

        this.rightPants = new ModelRenderer(self, 0, 32);
        this.rightPants.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);

        this.leftPants = new ModelRenderer(self, 0, 48);
        this.leftPants.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, scale + overlay);

        /* Kindergarten */

        this.bipedBody.addChild(jacket);

        this.classicRightArm.addChild(classicRightSleeve);
        this.classicLeftArm.addChild(classicLeftSleeve);
        this.slimRightArm.addChild(slimRightSleeve);
        this.slimLeftArm.addChild(slimLeftSleeve);

        this.bipedRightArm.addChild(classicRightArm);
        this.bipedRightArm.addChild(slimRightArm);
        this.bipedLeftArm.addChild(classicLeftArm);
        this.bipedLeftArm.addChild(slimLeftArm);

        this.bipedRightLeg.addChild(rightPants);
        this.bipedLeftLeg.addChild(leftPants);

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
        renderer.mirror = false;
        renderer.cubeList.clear();
        renderer.setTextureOffset(texOffsetX, texOffsetY);
    }

    @Unique
    private boolean is3DEnabled() {
        return SkinLayers3DConfig.enabled3D && this.modernEnabled
            && SkinLayers3DConfig.modernSkinSupport
            && !Loader.isModLoaded("SmartMoving");
    }

    @Unique boolean[] partsEnabled = new boolean[7];
    // boots[0] | legs[1] | chest[2] | helmet[3]
    @Unique boolean[] armorEquipped = new boolean[4];

    @Override
    public void renderPart3D(SkinLayersHelper.EnumPlayerModelParts part, float scale) {
        if (!is3DEnabled()) return;

        boolean partSaved = this.rendererFromPart(part).showModel;
        this.hidePart(part, true);

        SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
        if (state3d != null && state3d.initialized) {
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            try {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                renderMesh(
                    state3d.meshFromPart(part),
                    this.baseRendererFromPart(part),
                    scale,
                    partSaved,
                    part);
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, CallbackInfo ci) {
        if (!is3DEnabled()) return;

        //TODO: render 3d layers with armor, somehow

        if (entity instanceof EntityPlayer player) {
            ItemStack[] armor = player.inventory.armorInventory;
            for (int i = 0; i < 4; i++) {
                armorEquipped[i] = (armor[i] != null);
            }
        }

        /// Save 2d overlay state before hiding
        partsEnabled[CAPE.id()] = this.rendererFromPart(CAPE).showModel;
        partsEnabled[JACKET.id()] = this.rendererFromPart(JACKET).showModel;
        partsEnabled[LEFT_SLEEVE.id()] = this.rendererFromPart(LEFT_SLEEVE).showModel;
        partsEnabled[RIGHT_SLEEVE.id()] = this.rendererFromPart(RIGHT_SLEEVE).showModel;
        partsEnabled[LEFT_PANTS.id()] = this.rendererFromPart(LEFT_PANTS).showModel;
        partsEnabled[RIGHT_PANTS.id()] = this.rendererFromPart(RIGHT_PANTS).showModel;
        partsEnabled[HAT.id()] = this.rendererFromPart(HAT).showModel;

        /// Hide 2d overlay before 3d renderer (if corresponding armor isn't equipped)
        SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
        if (state3d != null && state3d.initialized) {
            if (state3d.hatMesh != null && SkinLayers3DConfig.enableHat3D && !armorEquipped[3])
                this.hidePart(HAT, true);
            if (state3d.jacketMesh != null && SkinLayers3DConfig.enableJacket3D && !armorEquipped[2])
                this.hidePart(JACKET, true);
            if (state3d.rightSleeveMesh != null && SkinLayers3DConfig.enableRightSleeve3D && !armorEquipped[2])
                this.hidePart(RIGHT_SLEEVE, true);
            if (state3d.leftSleeveMesh != null && SkinLayers3DConfig.enableLeftSleeve3D && !armorEquipped[2])
                this.hidePart(LEFT_SLEEVE, true);
            if (state3d.rightPantsMesh != null && SkinLayers3DConfig.enableRightPants3D
                && (!armorEquipped[1] || !armorEquipped[0]))
                this.hidePart(RIGHT_PANTS, true);
            if (state3d.leftPantsMesh != null && SkinLayers3DConfig.enableLeftPants3D
                && (!armorEquipped[1] || !armorEquipped[0]))
                this.hidePart(LEFT_PANTS, true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void postRender(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, CallbackInfo ci) {
        if (!is3DEnabled()) return;

        if (armorEquipped[3]) partsEnabled[HAT.id()] = false;
        if (armorEquipped[2]) {
            partsEnabled[JACKET.id()] = false;
            partsEnabled[LEFT_SLEEVE.id()] = false;
            partsEnabled[RIGHT_SLEEVE.id()] = false;
        }
        if (armorEquipped[1] || armorEquipped[0]) {
            partsEnabled[LEFT_PANTS.id()] = false;
            partsEnabled[RIGHT_PANTS.id()] = false;
        }

        boolean child = this.isChild;

        if(child) {
            GL11.glPushMatrix();
            GL11.glScalef(1.0F / 2.0F, 1.0F / 2.0F, 1.0F / 2.0F);
            GL11.glTranslatef(0.0F, 24.0F * scaleFactor, 0.0F);
        }
        renderAll3DLayers(scaleFactor, partsEnabled);
        if(child) GL11.glPopMatrix();
    }

    @Unique
    private void renderAll3DLayers(float scaleFactor, boolean[] partsEnabled) {
        SkinLayers3DState state3d = SkinLayers3DSetup.getState(currentRenderingPlayerUuid);
        if (state3d != null && state3d.initialized) {
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            try {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                renderMesh(
                    state3d.hatMesh,
                    this.bipedHead,
                    scaleFactor,
                    SkinLayers3DConfig.enableHat3D && partsEnabled[HAT.id()],
                    HAT);
                renderMesh(
                    state3d.jacketMesh,
                    this.bipedBody,
                    scaleFactor,
                    SkinLayers3DConfig.enableJacket3D && partsEnabled[JACKET.id()],
                    JACKET);
                renderMesh(
                    state3d.rightSleeveMesh,
                    this.bipedRightArm,
                    scaleFactor,
                    SkinLayers3DConfig.enableRightSleeve3D && partsEnabled[RIGHT_SLEEVE.id()],
                    RIGHT_SLEEVE);
                renderMesh(
                    state3d.leftSleeveMesh,
                    this.bipedLeftArm,
                    scaleFactor,
                    SkinLayers3DConfig.enableLeftSleeve3D && partsEnabled[LEFT_SLEEVE.id()],
                    LEFT_SLEEVE);
                renderMesh(
                    state3d.rightPantsMesh,
                    this.bipedRightLeg,
                    scaleFactor,
                    SkinLayers3DConfig.enableRightPants3D && partsEnabled[RIGHT_PANTS.id()],
                    RIGHT_PANTS);
                renderMesh(
                    state3d.leftPantsMesh,
                    this.bipedLeftLeg,
                    scaleFactor,
                    SkinLayers3DConfig.enableLeftPants3D && partsEnabled[LEFT_PANTS.id()],
                    LEFT_PANTS);

            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    /**
     * Render a 3D mesh if available and enabled.
     */
    @Unique
    private void renderMesh(SkinLayers3DMesh mesh, ModelRenderer source, float scaleFactor, boolean enabled, SkinLayersHelper.EnumPlayerModelParts part) {
        if (enabled && mesh != null && mesh.isCompiled() && source != null) {
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
                case HAT:
                    scaleX = SkinLayers3DConfig.headVoxelSize;
                    scaleY = SkinLayers3DConfig.headVoxelSize;
                    scaleZ = SkinLayers3DConfig.headVoxelSize;
                    break;
                case JACKET:
                    scaleX = SkinLayers3DConfig.bodyVoxelWidthSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetY = -0.2F;
                    break;
                case RIGHT_SLEEVE:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetX = this.currentSlim ? -0.499F : -0.998F;
                    offsetY = -0.1F;
                    break;
                case LEFT_SLEEVE:
                    scaleX = SkinLayers3DConfig.baseVoxelSize;
                    scaleY = 1.035F;
                    scaleZ = SkinLayers3DConfig.baseVoxelSize;
                    offsetX = this.currentSlim ? 0.499F : 0.998F;
                    offsetY = -0.1F;
                    break;
                case RIGHT_PANTS:
                case LEFT_PANTS:
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
    public ModelRenderer rendererFromPart(SkinLayersHelper.EnumPlayerModelParts part) {
        return switch (part) {
            case CAPE -> this.bipedCloak;
            case JACKET -> this.jacket;
            case LEFT_SLEEVE -> this.currentSlim ? this.slimLeftSleeve : this.classicLeftSleeve;
            case RIGHT_SLEEVE -> this.currentSlim ? this.slimRightSleeve : this.classicRightSleeve;
            case LEFT_PANTS -> this.leftPants;
            case RIGHT_PANTS -> this.rightPants;
            case HAT -> this.bipedHeadwear;
        };
    }

    @Override
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

    @Override
    public SkinLayersHelper.EnumPlayerModelParts partFromRenderer(ModelRenderer renderer) {
        if(renderer == this.bipedCloak) return CAPE;
        if(renderer == this.jacket) return JACKET;
        if(renderer == this.slimLeftSleeve || renderer == this.classicLeftSleeve) return LEFT_SLEEVE;
        if(renderer == this.slimRightSleeve || renderer == this.classicRightSleeve) return RIGHT_SLEEVE;
        if(renderer == this.leftPants) return LEFT_PANTS;
        if(renderer == this.rightPants) return RIGHT_PANTS;
        if(renderer == this.bipedHeadwear) return HAT;
        return null;
    }

}
