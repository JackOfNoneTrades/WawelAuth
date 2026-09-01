package org.fentanylsolutions.wawelauth.client.render;

import java.util.UUID;

import net.minecraft.client.model.ModelRenderer;

/**
 * Duck interface injected into {@link net.minecraft.client.model.ModelBiped} via mixin.
 * <p>
 * Cast any ModelBiped to this interface to access modern 64x64 skin features.
 * Non-player ModelBiped instances (zombies, skeletons, armor) have
 * {@link #isModern()} → false; all methods are no-ops for them.
 */
public interface IModelBipedModernExt {

    /**
     * Initialize this ModelBiped for modern 64x64 skin rendering.
     * Sets textureWidth/Height to 64x64, rebuilds all vanilla parts with correct UVs,
     * creates 5 overlay layers and slim arm variants.
     * Called once from MixinRenderPlayer's constructor.
     */
    void initModern();

    /**
     * Swap between slim (3px) and classic (4px) arm ModelRenderers.
     * No-op if the current state already matches the requested mode.
     */
    void setSlim(boolean slim);

    /**
     * Returns true if this model has been initialized for modern 64x64 rendering.
     */
    boolean isModern();

    /**
     * Renders the 3d right arm overlay mesh (for first-person arm rendering).
     */
    void render3DRightArmWear(float scale);

    void setCurrentPlayerUuid(UUID uuid);

    ModelRenderer getBodyWear();

    ModelRenderer getRightArmWear();

    ModelRenderer getLeftArmWear();

    ModelRenderer getRightLegWear();

    ModelRenderer getLeftLegWear();
}
