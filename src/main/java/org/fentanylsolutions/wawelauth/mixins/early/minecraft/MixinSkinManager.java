package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.io.File;
import java.util.concurrent.ExecutorService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.client.render.IProviderAwareSkinManager;
import org.fentanylsolutions.wawelauth.client.render.ProviderThreadDownloadImageData;
import org.fentanylsolutions.wawelauth.client.render.SkinManagerCompatImageBuffer;
import org.fentanylsolutions.wawelauth.client.render.SkinTextureState;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;

/**
 * Bypass SkinManager's anonymous IImageBuffer callback class so skin downloads
 * keep working even when another mod fails to transform SkinManager$2.
 */
@Mixin(SkinManager.class)
public abstract class MixinSkinManager implements IProviderAwareSkinManager {

    @Shadow
    @Final
    private static ExecutorService field_152794_b;

    @Shadow
    @Final
    public static ResourceLocation field_152793_a;

    @Shadow
    @Final
    private TextureManager field_152795_c;

    @Shadow
    @Final
    private File field_152796_d;

    @Shadow
    @Final
    private MinecraftSessionService field_152797_e;

    @Inject(method = "func_152789_a", at = @At("HEAD"), cancellable = true)
    private void wawelauth$registerTextureWithoutAnonymousCallback(MinecraftProfileTexture texture, Type textureType,
        SkinManager.SkinAvailableCallback callback, CallbackInfoReturnable<ResourceLocation> cir) {
        cir.setReturnValue(wawelauth$loadTexture(texture, textureType, callback, null));
    }

    @Override
    public ResourceLocation wawelauth$loadTexture(MinecraftProfileTexture texture, Type textureType,
        SkinManager.SkinAvailableCallback callback, ClientProvider provider) {
        ResourceLocation resourceLocation = new ResourceLocation("skins/" + texture.getHash());
        ITextureObject existingTexture = field_152795_c.getTexture(resourceLocation);

        if (SkinTextureState.isUsable(existingTexture)) {
            if (callback != null) {
                callback.func_152121_a(textureType, resourceLocation);
            }
            return resourceLocation;
        }

        File cacheDir = new File(
            field_152796_d,
            texture.getHash()
                .substring(0, 2));
        File cacheFile = new File(cacheDir, texture.getHash());
        IImageBuffer delegate = textureType == Type.SKIN ? new ImageBufferDownload() : null;
        ProviderThreadDownloadImageData download = new ProviderThreadDownloadImageData(
            cacheFile,
            texture.getUrl(),
            textureType == Type.SKIN ? field_152793_a : WawelTextureResolver.getDefaultCape(),
            new SkinManagerCompatImageBuffer(delegate, callback, textureType, resourceLocation),
            provider);
        field_152795_c.loadTexture(resourceLocation, download);
        return resourceLocation;
    }

    @Override
    public void wawelauth$loadProfileTextures(final GameProfile profile,
        final SkinManager.SkinAvailableCallback callback, final boolean requireSecure, final ClientProvider provider) {
        field_152794_b.submit(() -> {
            final java.util.HashMap<Type, MinecraftProfileTexture> textures = new java.util.HashMap<>();

            try {
                textures.putAll(field_152797_e.getTextures(profile, requireSecure));
            } catch (InsecureTextureException ignored) {}

            if (textures.isEmpty() && profile.getId()
                .equals(
                    Minecraft.getMinecraft()
                        .getSession()
                        .func_148256_e()
                        .getId())) {
                textures
                    .putAll(field_152797_e.getTextures(field_152797_e.fillProfileProperties(profile, false), false));
            }

            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (textures.containsKey(Type.SKIN)) {
                        wawelauth$loadTexture(textures.get(Type.SKIN), Type.SKIN, callback, provider);
                    }

                    if (textures.containsKey(Type.CAPE)) {
                        wawelauth$loadTexture(textures.get(Type.CAPE), Type.CAPE, callback, provider);
                    }
                });
        });
    }
}
