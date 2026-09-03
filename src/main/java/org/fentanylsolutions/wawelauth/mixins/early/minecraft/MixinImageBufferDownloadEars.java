package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.awt.image.BufferedImage;

import net.minecraft.client.renderer.ImageBufferDownload;

import org.fentanylsolutions.wawelauth.client.render.EarsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores Ears' skin processor hook after WawelAuth's ImageBufferDownload overwrite. */
@Mixin(value = ImageBufferDownload.class, priority = 900)
public abstract class MixinImageBufferDownloadEars {

    @Inject(method = "parseUserSkin", at = @At("HEAD"), cancellable = true)
    private void wawelauth$processEarsSkin(BufferedImage image, CallbackInfoReturnable<BufferedImage> cir) {
        BufferedImage processed = EarsCompat.processSkin((ImageBufferDownload) (Object) this, image);
        if (processed != null) cir.setReturnValue(processed);
    }
}
