package org.fentanylsolutions.wawelauth.client.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.gui.AnimatedCapeTexture;
import org.fentanylsolutions.wawelauth.client.gui.AnimatedCapeTracker;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class LocalTextureLoader {

    private static final Set<UUID> OFFLINE_CAPE_LOADS = Collections
        .newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private static final Map<ResourceLocation, BufferedImage> imageCache = new ConcurrentHashMap<>();

    private LocalTextureLoader() {}

    public static BufferedImage readImage(File file) throws IOException {
        if (file == null) {
            throw new IOException("Image file is required.");
        }
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Failed to decode image: " + file.getAbsolutePath());
        }
        return image;
    }

    public static ResourceLocation registerBufferedImage(ResourceLocation location, BufferedImage image) {
        if (location == null) {
            throw new IllegalArgumentException("Texture location is required.");
        }
        if (image == null) {
            throw new IllegalArgumentException("Image is required.");
        }

        TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();
        ITextureObject existing = textureManager.getTexture(location);
        if (existing != null) {
            textureManager.deleteTexture(location);
        }

        textureManager.loadTexture(location, new DynamicTexture(image));
        imageCache.put(location, image);
        return location;
    }

    /**
     * Returns the BufferedImage previously registered for the given location,
     * or null if not found. Used by SkinLayers3D to read pixel data from
     * offline/local skins that are stored as DynamicTexture.
     */
    public static BufferedImage getCachedImage(ResourceLocation location) {
        return location != null ? imageCache.get(location) : null;
    }

    public static boolean isGifPath(String path) {
        return path != null && path.toLowerCase()
            .endsWith(".gif");
    }

    public static ResourceLocation getOfflineCape(UUID profileId, String capePath) {
        if (profileId == null || capePath == null
            || capePath.trim()
                .isEmpty()) {
            return null;
        }

        if (isGifPath(capePath)) {
            AnimatedCapeTexture animated = AnimatedCapeTracker.get(profileId);
            if (animated != null) {
                return animated.getResourceLocation();
            }
            ensureOfflineAnimatedCapeLoaded(profileId, capePath);
            return null;
        }

        File file = new File(capePath);
        if (!file.isFile()) {
            return null;
        }

        ResourceLocation location = offlineCapeLocation(profileId);
        TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();
        if (textureManager.getTexture(location) != null) {
            return location;
        }

        try {
            return registerBufferedImage(location, readImage(file));
        } catch (IOException e) {
            WawelAuth.debug("Failed to load local offline cape for " + profileId + ": " + e.getMessage());
            return null;
        }
    }

    public static void invalidateOfflineCape(UUID profileId) {
        if (profileId == null) {
            return;
        }
        OFFLINE_CAPE_LOADS.remove(profileId);
        AnimatedCapeTracker.remove(profileId);
        TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();
        ResourceLocation location = offlineCapeLocation(profileId);
        if (textureManager.getTexture(location) != null) {
            textureManager.deleteTexture(location);
        }
    }

    private static void ensureOfflineAnimatedCapeLoaded(UUID profileId, String capePath) {
        if (!OFFLINE_CAPE_LOADS.add(profileId)) {
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return AnimatedCapeTexture.decodeGif(Files.readAllBytes(new File(capePath).toPath()));
            } catch (Exception e) {
                WawelAuth
                    .debug("Failed to decode local offline animated cape for " + profileId + ": " + e.getMessage());
                return null;
            }
        })
            .whenComplete(
                (decoded, err) -> Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        try {
                            if (decoded == null) {
                                return;
                            }
                            AnimatedCapeTexture texture = AnimatedCapeTexture
                                .createFromDecoded(decoded, "offline_capes/anim/" + UuidUtil.toUnsigned(profileId));
                            if (texture != null) {
                                AnimatedCapeTracker.register(profileId, texture);
                            }
                        } finally {
                            OFFLINE_CAPE_LOADS.remove(profileId);
                        }
                    }));
    }

    private static ResourceLocation offlineCapeLocation(UUID profileId) {
        return new ResourceLocation("wawelauth", "offline_capes/" + UuidUtil.toUnsigned(profileId));
    }
}
