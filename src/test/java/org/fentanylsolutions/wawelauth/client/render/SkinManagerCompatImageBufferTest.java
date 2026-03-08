package org.fentanylsolutions.wawelauth.client.render;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;

public class SkinManagerCompatImageBufferTest {

    @Test
    public void parseUserSkinDelegatesAndCompletionInvokesCallback() {
        AtomicBoolean delegateCompleted = new AtomicBoolean(false);
        AtomicReference<MinecraftProfileTexture.Type> callbackType = new AtomicReference<>();
        AtomicReference<ResourceLocation> callbackLocation = new AtomicReference<>();
        ResourceLocation expectedLocation = new ResourceLocation("skins/examplehash");
        BufferedImage expectedImage = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        IImageBuffer delegate = new IImageBuffer() {

            @Override
            public BufferedImage parseUserSkin(BufferedImage image) {
                return expectedImage;
            }

            @Override
            public void func_152634_a() {
                delegateCompleted.set(true);
            }
        };

        SkinManager.SkinAvailableCallback callback = new SkinManager.SkinAvailableCallback() {

            @Override
            public void func_152121_a(MinecraftProfileTexture.Type textureType, ResourceLocation resourceLocation) {
                callbackType.set(textureType);
                callbackLocation.set(resourceLocation);
                Assert.assertTrue(delegateCompleted.get());
            }
        };

        SkinManagerCompatImageBuffer buffer = new SkinManagerCompatImageBuffer(
            delegate,
            callback,
            MinecraftProfileTexture.Type.SKIN,
            expectedLocation);

        Assert.assertSame(expectedImage, buffer.parseUserSkin(null));

        buffer.func_152634_a();

        Assert.assertTrue(delegateCompleted.get());
        Assert.assertEquals(MinecraftProfileTexture.Type.SKIN, callbackType.get());
        Assert.assertEquals(expectedLocation, callbackLocation.get());
    }

    @Test
    public void parseUserSkinFallsBackToInputWhenNoDelegateExists() {
        BufferedImage input = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        SkinManagerCompatImageBuffer buffer = new SkinManagerCompatImageBuffer(
            null,
            null,
            MinecraftProfileTexture.Type.CAPE,
            new ResourceLocation("skins/capehash"));

        Assert.assertSame(input, buffer.parseUserSkin(input));
        buffer.func_152634_a();
    }
}
