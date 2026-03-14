package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.internal.TextureRequest;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
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

        cir.setReturnValue(
            client.getTextureResolver()
                .getSkin(profile.getId(), profile.getName(), TextureRequest.DEFAULT));
    }
}
