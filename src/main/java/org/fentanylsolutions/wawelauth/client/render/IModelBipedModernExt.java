package org.fentanylsolutions.wawelauth.client.render;

import net.minecraft.client.model.ModelRenderer;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;

import java.util.UUID;

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
     */
    void setSlim(boolean slim);

    /**
     * Returns true if this model has been initialized for modern 64x64 rendering.
     */
    boolean isModern();

    void setCurrentPlayerUuid(UUID uuid);

    void renderPart3D(SkinLayersHelper.EnumPlayerModelParts part, float scale);
    ModelRenderer rendererFromPart(SkinLayersHelper.EnumPlayerModelParts part);
    ModelRenderer baseRendererFromPart(SkinLayersHelper.EnumPlayerModelParts part);
    SkinLayersHelper.EnumPlayerModelParts partFromRenderer(ModelRenderer renderer);
    default void hidePart(SkinLayersHelper.EnumPlayerModelParts part, boolean value) {
        if (!this.isModern() && part.isModern()) return;
        this.rendererFromPart(part).showModel = !value;
    }

}
