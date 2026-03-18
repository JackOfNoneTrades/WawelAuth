package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

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
    private void wawelauth$suppressVanillaSkinLoading(SkinManager skinManager, GameProfile profile,
        SkinManager.SkinAvailableCallback callback, boolean requireSecure) {
        // All skin loading goes through WawelTextureResolver. Suppress vanilla loading.
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
        if (client == null) return;

        ClientProvider provider = wawelauth$resolveProvider(client, uuid);
        if (provider == null) return;

        ResourceLocation resolved = client.getTextureResolver()
            .getCape(uuid, self.getCommandSenderName(), provider, false);
        if (resolved != null) {
            cir.setReturnValue(resolved);
        }
    }

    @Inject(method = "func_152122_n", at = @At("RETURN"), cancellable = true)
    private void wawelauth$reportCape(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getUniqueID();
        if (uuid == null) return;

        ClientProvider provider = wawelauth$resolveProvider(client, uuid);
        if (provider == null) return;

        ResourceLocation resolved = client.getTextureResolver()
            .getCape(uuid, self.getCommandSenderName(), provider, false);
        if (resolved != null) {
            cir.setReturnValue(true);
            return;
        }

        // Check for animated GIF cape from offline local skin
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            SessionBridge.OfflineLocalSkin local = client.getSessionBridge()
                .resolveOfflineLocalSkin(uuid, provider);
            if (local != null && local.getCapePath() != null) {
                if (LocalTextureLoader.getOfflineGIFCape(uuid, local.getCapePath()) != null) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "getLocationSkin", at = @At("RETURN"), cancellable = true)
    private void wawelauth$overrideSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getUniqueID();
        if (uuid == null) return;

        ClientProvider provider = wawelauth$resolveProvider(client, uuid);
        if (provider == null) return;

        ResourceLocation resolved = client.getTextureResolver()
            .getSkin(uuid, self.getCommandSenderName(), provider, false);
        if (resolved != null && !resolved.equals(WawelTextureResolver.getDefaultSkin())
            && !resolved.equals(WawelTextureResolver.getLegacyDefaultSkin())) {
            cir.setReturnValue(resolved);
        }
    }

    /** Resolve provider for this player, or null (caller keeps Steve). */
    @Unique
    private static ClientProvider wawelauth$resolveProvider(WawelClient client, UUID uuid) {
        ClientProvider provider = client.resolvePlayerProvider(uuid);
        if (provider != null) {
            return provider;
        }
        // Singleplayer with no explicit account: Mojang for local player
        if (Minecraft.getMinecraft()
            .isSingleplayer() && wawelauth$isLocalPlayer(uuid)) {
            return client.getMojangProvider();
        }
        return null;
    }

    @Unique
    private static boolean wawelauth$isLocalPlayer(UUID uuid) {
        if (uuid == null) return false;
        try {
            GameProfile sessionProfile = Minecraft.getMinecraft()
                .getSession()
                .func_148256_e();
            return sessionProfile != null && uuid.equals(sessionProfile.getId());
        } catch (Exception e) {
            return false;
        }
    }
}
