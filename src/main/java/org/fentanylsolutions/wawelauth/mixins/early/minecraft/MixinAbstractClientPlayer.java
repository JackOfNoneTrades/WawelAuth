package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.UUID;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.api.internal.TextureRequest;
import org.fentanylsolutions.wawelauth.client.render.IProviderAwareSkinManager;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.client.render.animatedcape.AnimatedCapeTexture;
import org.fentanylsolutions.wawelauth.client.render.animatedcape.AnimatedCapeTracker;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

/**
 * Two injection points:
 * <p>
 * 1. Static init: replace default Steve skin with 64x64 version
 * <p>
 * 2. getLocationCape: override with animated cape texture if registered
 */
@Mixin(value = AbstractClientPlayer.class, priority = 999)
public class MixinAbstractClientPlayer {

    @Shadow
    @Final
    @Mutable
    public static ResourceLocation locationStevePng;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wawelauth$useModernSteve(CallbackInfo ci) {
        if (!SkinLayers3DConfig.modernSkinSupport) {
            return;
        }
        locationStevePng = new ResourceLocation("wawelauth", "textures/steve_64.png");
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/SkinManager;func_152790_a(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resources/SkinManager$SkinAvailableCallback;Z)V"))
    private void wawelauth$loadPlayerTexturesWithProvider(SkinManager skinManager, GameProfile profile,
        SkinManager.SkinAvailableCallback callback, boolean requireSecure) {
        ClientProvider provider = null;
        WawelClient client = WawelClient.instance();
        if (client != null) {
            provider = client.getSessionBridge()
                .resolveTextureDownloadProvider(profile != null ? profile.getId() : null);
        }

        if (skinManager instanceof IProviderAwareSkinManager) {
            ((IProviderAwareSkinManager) skinManager)
                .wawelauth$loadProfileTextures(profile, callback, requireSecure, provider);
            return;
        }

        skinManager.func_152790_a(profile, callback, requireSecure);
    }

    @Inject(method = "getLocationCape", at = @At("RETURN"), cancellable = true)
    private void wawelauth$overrideGetCape(CallbackInfoReturnable<ResourceLocation> cir) {

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getUniqueID();
        if (uuid == null) return;

        AnimatedCapeTexture animated = AnimatedCapeTracker.get(uuid);
        if (animated != null) {
            cir.setReturnValue(animated.getResourceLocation());
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }

        ResourceLocation resolved = client.getTextureResolver()
            .getCape(uuid, self.getCommandSenderName(), TextureRequest.NO_FALLBACK);
        if (resolved != null) {
            cir.setReturnValue(resolved);
        }

    }

    @Inject(method = "func_152122_n", at = @At("RETURN"), cancellable = true)
    private void wawelauth$reportCape(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getUniqueID();
        if (uuid == null) {
            return;
        }

        ResourceLocation resolved = client.getTextureResolver()
            .getCape(uuid, self.getCommandSenderName(), TextureRequest.NO_FALLBACK);

        if (resolved != null) {
            cir.setReturnValue(true);
            return;
        }

        SessionBridge.OfflineLocalSkin local = client.getSessionBridge()
            .resolveOfflineLocalSkin(uuid);
        if (local == null || local.getCapePath() == null) {
            return;
        }

        if (LocalTextureLoader.getOfflineGIFCape(uuid, local.getCapePath()) != null) {
            cir.setReturnValue(true);
        }

    }

    @Inject(method = "getLocationSkin", at = @At("RETURN"), cancellable = true)
    private void wawelauth$overrideSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getUniqueID();
        if (uuid == null) {
            return;
        }

        // Check WawelTextureResolver for a resolved skin. This handles both
        // online skin changes (invalidated via packet) and offline local skins.
        // Only override when the resolver has a real (non-placeholder) skin to
        // avoid replacing vanilla's valid result with Steve during async fetch.
        ResourceLocation resolved = client.getTextureResolver()
            .getSkin(uuid, self.getCommandSenderName(), TextureRequest.DEFAULT);
        if (resolved != null && !resolved.equals(WawelTextureResolver.getDefaultSkin())
            && !resolved.equals(WawelTextureResolver.getLegacyDefaultSkin())) {
            cir.setReturnValue(resolved);
            return;
        }

        // Resolver hasn't resolved yet or returned placeholder — fall back to
        // vanilla's result if it has a valid skin.
        ResourceLocation current = cir.getReturnValue();
        if (current != null && !current.equals(locationStevePng)) {
            return;
        }

        // Last resort: check for offline local skin with explicit provider scope
        SessionBridge.OfflineLocalSkin local = client.getSessionBridge()
            .resolveOfflineLocalSkin(uuid);
        if (local == null || local.getSkinPath() == null) {
            return;
        }

        ResourceLocation offlineResolved = client.getTextureResolver()
            .getSkin(
                uuid,
                self.getCommandSenderName(),
                BuiltinProviders.OFFLINE_PROVIDER_NAME,
                TextureRequest.NO_FALLBACK);
        if (offlineResolved != null) {
            cir.setReturnValue(offlineResolved);
        }
    }

}
