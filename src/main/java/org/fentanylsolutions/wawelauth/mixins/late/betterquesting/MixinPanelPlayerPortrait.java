package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import betterquesting.api2.client.gui.panels.content.PanelPlayerPortrait;

@Mixin(value = PanelPlayerPortrait.class, priority = 999, remap = false)
public abstract class MixinPanelPlayerPortrait {

    @Redirect(
        method = "<init>(Lbetterquesting/api2/client/gui/misc/IGuiRect;Lnet/minecraft/client/entity/AbstractClientPlayer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getDownloadImageSkin(Lnet/minecraft/util/ResourceLocation;Ljava/lang/String;)Lnet/minecraft/client/renderer/ThreadDownloadImageData;",
            remap = true),
        remap = true)
    private ThreadDownloadImageData wawelauth$skipVanillaDownload(ResourceLocation resourceLocationIn,
        String username) {
        if (WawelClient.instance() == null) {
            return AbstractClientPlayer.getDownloadImageSkin(resourceLocationIn, username);
        }
        return null;
    }
}
