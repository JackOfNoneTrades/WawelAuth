package org.fentanylsolutions.wawelauth.mixins.late.dynmap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.imageio.ImageIO;

import org.fentanylsolutions.wawelauth.wawelserver.DynmapSkinImageNormalizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.dynmap.PlayerFaces$LoadPlayerImages", remap = false)
public abstract class MixinDynmapLoadPlayerImages {

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Ljavax/imageio/ImageIO;read(Ljava/net/URL;)Ljava/awt/image/BufferedImage;",
            remap = false),
        remap = false)
    private BufferedImage wawelauth$normalizeLoadedSkin(URL url) throws IOException {
        return DynmapSkinImageNormalizer.normalizeForDynmap(ImageIO.read(url));
    }

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Ljavax/imageio/ImageIO;read(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;",
            remap = false),
        remap = false)
    private BufferedImage wawelauth$normalizeDefaultSkin(InputStream input) throws IOException {
        return DynmapSkinImageNormalizer.normalizeForDynmap(ImageIO.read(input));
    }
}
