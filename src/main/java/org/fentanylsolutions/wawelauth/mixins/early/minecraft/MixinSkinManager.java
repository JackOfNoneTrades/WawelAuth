package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.io.File;

import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.client.render.SkinManagerCompatImageBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;

/**
 * Bypass SkinManager's anonymous IImageBuffer callback class so skin downloads
 * keep working even when another mod fails to transform SkinManager$2.
 */
@Mixin(SkinManager.class)
public abstract class MixinSkinManager {

    @Shadow
    @Final
    public static ResourceLocation field_152793_a;

    @Shadow
    @Final
    private TextureManager field_152795_c;

    @Shadow
    @Final
    private File field_152796_d;

    @Inject(method = "func_152789_a", at = @At("HEAD"), cancellable = true)
    private void wawelauth$registerTextureWithoutAnonymousCallback(MinecraftProfileTexture texture, Type textureType,
        SkinManager.SkinAvailableCallback callback, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation resourceLocation = new ResourceLocation("skins/" + texture.getHash());
        ITextureObject existingTexture = field_152795_c.getTexture(resourceLocation);

        if (existingTexture != null) {
            if (callback != null) {
                callback.func_152121_a(textureType, resourceLocation);
            }
            cir.setReturnValue(resourceLocation);
            return;
        }

        File cacheDir = new File(field_152796_d, texture.getHash().substring(0, 2));
        File cacheFile = new File(cacheDir, texture.getHash());
        IImageBuffer delegate = textureType == Type.SKIN ? new ImageBufferDownload() : null;
        ThreadDownloadImageData download = new ThreadDownloadImageData(
            cacheFile,
            texture.getUrl(),
            field_152793_a,
            new SkinManagerCompatImageBuffer(delegate, callback, textureType, resourceLocation));
        field_152795_c.loadTexture(resourceLocation, download);
        cir.setReturnValue(resourceLocation);
    }
}
