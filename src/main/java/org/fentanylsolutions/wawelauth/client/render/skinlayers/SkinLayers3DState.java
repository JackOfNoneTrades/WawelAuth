package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import net.minecraft.util.ResourceLocation;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;

/**
 * Per-player state holding 3D meshes for each body part overlay.
 * <p>
 * Stored on the ModelBiped via IModelBipedModernExt duck interface.
 */
public class SkinLayers3DState {

    public SkinLayers3DMesh hatMesh;
    public SkinLayers3DMesh jacketMesh;
    public SkinLayers3DMesh leftSleeveMesh;
    public SkinLayers3DMesh rightSleeveMesh;
    public SkinLayers3DMesh leftPantsMesh;
    public SkinLayers3DMesh rightPantsMesh;

    /** Track the skin to detect changes (triggers mesh rebuild). */
    public ResourceLocation lastSkinLocation;

    /** Whether meshes have been initialized. */
    public boolean initialized;

    /** Whether this state was built for slim arms. */
    public boolean slim;

    public SkinLayers3DMesh meshFromPart(SkinLayersHelper.EnumPlayerModelParts part) {
        return switch (part) {
            case CAPE -> null;
            case JACKET -> jacketMesh;
            case LEFT_SLEEVE -> leftSleeveMesh;
            case RIGHT_SLEEVE -> rightSleeveMesh;
            case LEFT_PANTS -> leftPantsMesh;
            case RIGHT_PANTS -> rightPantsMesh;
            case HAT -> hatMesh;
        };
    }

    /**
     * Delete all GL display lists. Call when the player leaves render distance
     * or the skin changes.
     */
    public void cleanup() {
        if (hatMesh != null) {
            hatMesh.cleanup();
            hatMesh = null;
        }
        if (jacketMesh != null) {
            jacketMesh.cleanup();
            jacketMesh = null;
        }
        if (leftSleeveMesh != null) {
            leftSleeveMesh.cleanup();
            leftSleeveMesh = null;
        }
        if (rightSleeveMesh != null) {
            rightSleeveMesh.cleanup();
            rightSleeveMesh = null;
        }
        if (leftPantsMesh != null) {
            leftPantsMesh.cleanup();
            leftPantsMesh = null;
        }
        if (rightPantsMesh != null) {
            rightPantsMesh.cleanup();
            rightPantsMesh = null;
        }
        initialized = false;
    }
}
