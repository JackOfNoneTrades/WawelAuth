package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.client.render.ProviderThreadDownloadImageData;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelBuilder;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelCube;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.voxels.VoxelSurfaceBuilder;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import com.mojang.authlib.GameProfile;

/**
 * Static utility that generates 3D meshes from a player's skin BufferedImage.
 * <p>
 * Handles caching and change detection via ResourceLocation comparison.
 */
public class SkinLayers3DSetup {

    /** Cache for skull meshes keyed by GameProfile. */
    private static final Map<UUID, SkullMeshCache> skullCache = new ConcurrentHashMap<>();

    public static void clearSkullCache() {
        skullCache.values()
            .forEach(cached -> { if (cached.mesh != null) cached.mesh.cleanup(); });
        skullCache.clear();
    }

    public static void updateSkullCache(UUID uuid, SkullMeshCache newCache) {
        if (uuid == null) return;
        SkullMeshCache oldCache = (newCache == null) ? skullCache.remove(uuid) : skullCache.put(uuid, newCache);
        if (oldCache != null && oldCache.mesh != null && oldCache != newCache) {
            oldCache.mesh.cleanup();
        }
    }

    public static SkullMeshCache getSkullCache(UUID uuid) {
        if (uuid == null) return null;
        return skullCache.get(uuid);
    }

    /** Cache for player 3d state keyed by GameProfile. */
    private static final Map<UUID, SkinLayers3DState> skinLayersStateCache = new ConcurrentHashMap<>();

    public static void clearState() {
        skinLayersStateCache.values()
            .forEach(state -> { if (state != null) state.cleanup(); });
        skinLayersStateCache.clear();
    }

    public static void updateState(UUID uuid, SkinLayers3DState newState) {
        if (uuid == null) return;
        SkinLayers3DState oldState = (newState == null) ? skinLayersStateCache.remove(uuid)
            : skinLayersStateCache.put(uuid, newState);
        if (oldState != null && oldState != newState) {
            oldState.cleanup();
        }
    }

    public static SkinLayers3DState getState(UUID uuid) {
        if (uuid == null) return null;
        return skinLayersStateCache.get(uuid);
    }

    /**
     * Create or update 3D skin layer meshes for a player.
     *
     * @param player   the player to generate meshes for
     * @param existing existing state (may be null)
     * @param slim     whether the player uses slim arms
     * @return the updated state, or null if mesh generation failed
     */
    public static SkinLayers3DState createOrUpdate(AbstractClientPlayer player, SkinLayers3DState existing,
        boolean slim) {

        WawelClient client = WawelClient.instance();
        if (client == null) return null;

        ResourceLocation skinLocation = client.getTextureResolver()
            .getSkin(
                player.getUniqueID(),
                player.getDisplayName(),
                client.resolvePlayerProvider(player.getUniqueID()),
                false);
        if (skinLocation == null) return null;

        // Check if skin hasn't changed and slim matches
        if (existing != null && existing.initialized
            && skinLocation.equals(existing.lastSkinLocation)
            && slim == existing.slim) {
            return existing;
        }

        // Get the BufferedImage from the skin texture
        BufferedImage skinImage = getSkinImage(skinLocation);
        if (skinImage == null) {
            return null;
        }

        // Validate: must be 64x64 (skip HD skins per README FAQ)
        if (skinImage.getWidth() != 64 || skinImage.getHeight() != 64) {
            return null;
        }

        // Cleanup old state
        if (existing != null) {
            existing.cleanup();
        }

        SkinLayers3DState state = new SkinLayers3DState();
        state.lastSkinLocation = skinLocation;
        state.slim = slim;

        try {
            SkinLayers3DSkinData skinData = new SkinLayers3DSkinData(skinImage);

            // Hat: 8x8x8, UV (32,0), topPivot=false, offset=0.6
            if (SkinLayers3DConfig.enableHat) {
                state.hatMesh = buildMesh(skinData, 8, 8, 8, 32, 0, false, 0.6f);
            }

            // Jacket: 8x12x4, UV (16,32), topPivot=true, offset=0
            if (SkinLayers3DConfig.enableJacket) {
                state.jacketMesh = buildMesh(skinData, 8, 12, 4, 16, 32, true, 0f);
            }

            // Right sleeve
            if (SkinLayers3DConfig.enableRightSleeve) {
                int armWidth = slim ? 3 : 4;
                state.rightSleeveMesh = buildMesh(skinData, armWidth, 12, 4, 40, 32, true, -2f);
            }

            // Left sleeve
            if (SkinLayers3DConfig.enableLeftSleeve) {
                int armWidth = slim ? 3 : 4;
                state.leftSleeveMesh = buildMesh(skinData, armWidth, 12, 4, 48, 48, true, -2f);
            }

            // Right pants: 4x12x4, UV (0,32), topPivot=true, offset=0
            if (SkinLayers3DConfig.enableRightPants) {
                state.rightPantsMesh = buildMesh(skinData, 4, 12, 4, 0, 32, true, 0f);
            }

            // Left pants: 4x12x4, UV (0,48), topPivot=true, offset=0
            if (SkinLayers3DConfig.enableLeftPants) {
                state.leftPantsMesh = buildMesh(skinData, 4, 12, 4, 0, 48, true, 0f);
            }

            state.initialized = true;
            return state;
        } catch (Exception e) {
            WawelAuth.LOG.error("Failed to build 3D skin layer meshes", e);
            state.cleanup();
            return null;
        }
    }

    /**
     * Get or create a 3D hat mesh for a player skull.
     *
     * @param profile      the skull's game profile
     * @param skinLocation the skull's skin texture location
     * @return the hat mesh, or null if generation failed
     */
    public static SkinLayers3DMesh getOrCreateSkullMesh(GameProfile profile, ResourceLocation skinLocation) {
        if (profile == null || skinLocation == null) return null;

        SkullMeshCache cached = getSkullCache(profile.getId());
        if (cached != null) {
            if (skinLocation.equals(cached.skinLocation)) {
                return cached.mesh;
            }
            // Skin changed, cleanup old
            if (cached.mesh != null) {
                cached.mesh.cleanup();
            }
        }

        BufferedImage skinImage = getSkinImage(skinLocation);
        if (skinImage == null || skinImage.getWidth() != 64 || skinImage.getHeight() != 64) {
            return null;
        }

        try {
            SkinLayers3DSkinData skinData = new SkinLayers3DSkinData(skinImage);
            SkinLayers3DMesh mesh = buildMesh(skinData, 8, 8, 8, 32, 0, false, 0.6f);
            updateSkullCache(profile.getId(), new SkullMeshCache(skinLocation, mesh));
            return mesh;
        } catch (Exception e) {
            WawelAuth.LOG.error("Failed to build 3D skull hat mesh", e);
            return null;
        }
    }

    private static SkinLayers3DMesh buildMesh(SkinLayers3DSkinData skinData, int width, int height, int depth,
        int textureU, int textureV, boolean topPivot, float rotationOffset) {
        SkinLayers3DModelBuilder builder = new SkinLayers3DModelBuilder();
        VoxelBuilder result = VoxelSurfaceBuilder
            .wrapBox(builder, skinData, width, height, depth, textureU, textureV, topPivot, rotationOffset);
        if (result == null || builder.isEmpty()) {
            return null;
        }
        List<VoxelCube> cubes = builder.getCubes();
        SkinLayers3DMesh mesh = new SkinLayers3DMesh(cubes);
        mesh.compileDisplayList();
        return mesh;
    }

    /**
     * Extract the BufferedImage from a skin ResourceLocation via the texture
     * manager and the accessor mixin.
     */
    private static BufferedImage getSkinImage(ResourceLocation skinLocation) {
        ITextureObject texture = Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(skinLocation);
        if (texture instanceof ProviderThreadDownloadImageData providerImageData) {
            return providerImageData.bufferedImage;
        }
        // Offline/local skins are registered as DynamicTexture via LocalTextureLoader
        return LocalTextureLoader.getCachedImage(skinLocation);
    }

    public static class SkullMeshCache {

        final ResourceLocation skinLocation;
        final SkinLayers3DMesh mesh;

        SkullMeshCache(ResourceLocation skinLocation, SkinLayers3DMesh mesh) {
            this.skinLocation = skinLocation;
            this.mesh = mesh;
        }
    }
}
