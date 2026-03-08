package org.fentanylsolutions.wawelauth.client.render;

import java.awt.image.BufferedImage;

import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;

/**
 * Named callback buffer used to avoid loading SkinManager's anonymous
 * IImageBuffer class, which some mixins target by synthetic field names.
 */
public final class SkinManagerCompatImageBuffer implements IImageBuffer {

    private final IImageBuffer delegate;
    private final SkinManager.SkinAvailableCallback callback;
    private final MinecraftProfileTexture.Type textureType;
    private final ResourceLocation resourceLocation;

    public SkinManagerCompatImageBuffer(IImageBuffer delegate, SkinManager.SkinAvailableCallback callback,
        MinecraftProfileTexture.Type textureType, ResourceLocation resourceLocation) {
        this.delegate = delegate;
        this.callback = callback;
        this.textureType = textureType;
        this.resourceLocation = resourceLocation;
    }

    @Override
    public BufferedImage parseUserSkin(BufferedImage image) {
        if (delegate != null) {
            return delegate.parseUserSkin(image);
        }
        return image;
    }

    @Override
    public void func_152634_a() {
        if (delegate != null) {
            delegate.func_152634_a();
        }
        if (callback != null) {
            callback.func_152121_a(textureType, resourceLocation);
        }
    }
}
