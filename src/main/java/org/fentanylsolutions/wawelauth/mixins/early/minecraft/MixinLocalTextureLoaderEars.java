package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.awt.image.BufferedImage;

import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.EarsCompat;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Upgrades local offline and upload-preview skins after their normal WawelAuth registration. */
@Mixin(value = LocalTextureLoader.class, remap = false)
public abstract class MixinLocalTextureLoaderEars {

    @Inject(method = "registerBufferedImage", at = @At("RETURN"), remap = false)
    private static void wawelauth$registerEarsSkin(ResourceLocation location, BufferedImage image,
        CallbackInfoReturnable<ResourceLocation> cir) {
        try {
            EarsCompat.replaceLocalSkinTexture(location, image);
        } catch (LinkageError e) {
            WawelAuth.LOG.error("Could not adapt local skin texture for Ears", e);
        }
    }
}
