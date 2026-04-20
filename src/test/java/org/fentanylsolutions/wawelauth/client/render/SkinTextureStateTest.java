package org.fentanylsolutions.wawelauth.client.render;

import java.io.IOException;

import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

public class SkinTextureStateTest {

    @Test
    public void usableRejectsNullAndMissingTextureMarker() {
        ITextureObject sentinel = new StubTextureObject();
        ITextureObject realTexture = new StubTextureObject();

        Assert.assertFalse(SkinTextureState.isUsable(null, sentinel));
        Assert.assertFalse(SkinTextureState.isUsable(sentinel, sentinel));
        Assert.assertTrue(SkinTextureState.isUsable(realTexture, sentinel));
    }

    @Test
    public void readyForRenderRejectsProviderTextureWithoutImage() {
        ITextureObject sentinel = new StubTextureObject();
        ProviderThreadDownloadImageData texture = new ProviderThreadDownloadImageData(
            null,
            "https://example.invalid/skin.png",
            new ResourceLocation("textures/entity/steve.png"),
            null);

        Assert.assertFalse(SkinTextureState.isReadyForRender(texture, sentinel));

        texture.bufferedImage = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Assert.assertTrue(SkinTextureState.isReadyForRender(texture, sentinel));
    }

    private static final class StubTextureObject implements ITextureObject {

        @Override
        public void loadTexture(IResourceManager resourceManager) throws IOException {}

        @Override
        public int getGlTextureId() {
            return 0;
        }
    }
}
