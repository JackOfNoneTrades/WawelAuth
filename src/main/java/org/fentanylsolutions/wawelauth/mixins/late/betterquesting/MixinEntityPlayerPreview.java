package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import betterquesting.api2.utils.EntityPlayerPreview;

@Mixin(value = EntityPlayerPreview.class, priority = 999, remap = false)
public abstract class MixinEntityPlayerPreview {

    @Inject(method = "func_110306_p", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$useResolvedSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }

        GameProfile profile = ((AbstractClientPlayer) (Object) this).getGameProfile();
        if (profile == null || profile.getId() == null) {
            return;
        }

        // BQ only gives a UUID, try all trusted providers
        java.util.List<ClientProvider> trusted = client.getSessionBridge()
            .getTrustedProviders();
        if (!trusted.isEmpty()) {
            cir.setReturnValue(
                client.getTextureResolver()
                    .getSkinFromAnyProvider(profile.getId(), profile.getName(), trusted));
            return;
        }

        ClientProvider provider = client.resolvePlayerProvider(profile.getId());
        if (provider != null) {
            cir.setReturnValue(
                client.getTextureResolver()
                    .getSkin(profile.getId(), profile.getName(), provider, false));
        }
    }
}
