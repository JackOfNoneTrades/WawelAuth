package org.fentanylsolutions.wawelauth.client.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinImageUtil;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.client.render.animatedcape.AnimatedCapeTexture;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Owns the client texture used by one skin/cape upload dialog. */
@SideOnly(Side.CLIENT)
final class TextureUploadPreview implements AutoCloseable {

    private final ResourceLocation location;
    private final AnimatedCapeTexture animatedCape;
    private boolean closed;

    private TextureUploadPreview(ResourceLocation location, AnimatedCapeTexture animatedCape) {
        this.location = location;
        this.animatedCape = animatedCape;
    }

    static TextureUploadPreview create(File file, TextureType textureType) throws IOException {
        if (file == null) {
            throw new IOException("Texture file is required.");
        }

        String path = "upload_preview/" + textureType.getApiName() + "/" + System.nanoTime();
        if (textureType == TextureType.CAPE) {
            byte[] data = Files.readAllBytes(file.toPath());
            if (isGif(data)) {
                AnimatedCapeTexture.DecodedGif decoded = AnimatedCapeTexture.decodeGif(data);
                if (decoded == null) {
                    throw new IOException("Failed to decode animated cape.");
                }
                AnimatedCapeTexture animated = AnimatedCapeTexture.createFromDecoded(decoded, path);
                if (animated == null) {
                    throw new IOException("Failed to create animated cape preview.");
                }
                return new TextureUploadPreview(animated.getResourceLocation(), animated);
            }
        }

        BufferedImage image = LocalTextureLoader.readImage(file);
        if (textureType == TextureType.SKIN) {
            image = SkinImageUtil.convertLegacySkin(image);
        }
        ResourceLocation location = new ResourceLocation("wawelauth", path);
        LocalTextureLoader.registerBufferedImage(location, image);
        return new TextureUploadPreview(location, null);
    }

    static boolean isGif(byte[] data) {
        if (data == null || data.length < 6) {
            return false;
        }
        return data[0] == 'G' && data[1] == 'I'
            && data[2] == 'F'
            && data[3] == '8'
            && (data[4] == '7' || data[4] == '9')
            && data[5] == 'a';
    }

    ResourceLocation getLocation() {
        return location;
    }

    void tick() {
        if (!closed && animatedCape != null) {
            animatedCape.tick();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (animatedCape != null) {
            animatedCape.delete();
        } else {
            LocalTextureLoader.unregisterBufferedImage(location);
        }
    }
}
