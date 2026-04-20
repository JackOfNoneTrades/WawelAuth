package org.fentanylsolutions.wawelauth.client.render;

import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureUtil;

/**
 * Helpers for distinguishing a real registered skin texture from Minecraft's
 * cached missing-texture sentinel.
 */
public final class SkinTextureState {

    private SkinTextureState() {}

    public static boolean isUsable(ITextureObject textureObject) {
        return isUsable(textureObject, TextureUtil.missingTexture);
    }

    /**
     * Returns true only when a texture can be safely bound for immediate
     * rendering. Provider-routed skins may be registered before their
     * decoded image is available, so they need a stricter readiness check.
     */
    public static boolean isReadyForRender(ITextureObject textureObject) {
        return isReadyForRender(textureObject, TextureUtil.missingTexture);
    }

    static boolean isUsable(ITextureObject textureObject, ITextureObject missingTextureMarker) {
        return textureObject != null && textureObject != missingTextureMarker;
    }

    static boolean isReadyForRender(ITextureObject textureObject, ITextureObject missingTextureMarker) {
        if (!isUsable(textureObject, missingTextureMarker)) {
            return false;
        }
        if (textureObject instanceof ProviderThreadDownloadImageData) {
            return ((ProviderThreadDownloadImageData) textureObject).bufferedImage != null;
        }
        return true;
    }
}
