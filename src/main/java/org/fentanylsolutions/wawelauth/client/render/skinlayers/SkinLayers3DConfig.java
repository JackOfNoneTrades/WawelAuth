package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import java.io.File;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.JsonConfigIO;

/**
 * Configuration for client-side skin rendering extras.
 * <p>
 * Loaded from skinlayers.json in WawelAuth's local instance config directory.
 * <p>
 * Field defaults match 3d-Skin-Layers upstream where applicable.
 */
public class SkinLayers3DConfig {

    private static final String CONFIG_FILE = "skinlayers.json";

    // Master toggle for all modern skin support: 64x64, slim arms, and HD pass-through.
    public static boolean modernSkinSupport = true;

    // Master toggle for all 3D skin rendering (players + skulls).
    public static boolean enabled = true;

    // Per-part toggles
    public static boolean enableHat = true;
    public static boolean enableJacket = true;
    public static boolean enableLeftSleeve = true;
    public static boolean enableRightSleeve = true;
    public static boolean enableLeftPants = true;
    public static boolean enableRightPants = true;

    // Voxel sizes (slightly larger than base model)
    public static float baseVoxelSize = 1.15f;
    public static float bodyVoxelWidthSize = 1.05f;
    public static float headVoxelSize = 1.18f;
    public static float skullVoxelSize = 1.1f;

    // LOD: beyond this distance (in blocks) fall back to flat 2D overlays
    public static int renderDistanceLOD = 14;

    // Skull rendering
    public static boolean enableSkulls = true;

    // Fast render: front faces rendered as a single vanilla box, voxels only for sides
    public static boolean fastRender = true;

    /**
     * Load config from disk. Call during ClientProxy.init().
     *
     * @param configDir the wawelauth config directory (config/wawelauth/)
     */
    public static void load(File configDir) {
        File file = new File(configDir, CONFIG_FILE);
        SkinLayers3DConfigData data = JsonConfigIO.load(file, SkinLayers3DConfigData.class);
        applyFrom(data);
        WawelAuth.LOG.info("Loaded 3D skin layers config from {}", file.getAbsolutePath());
    }

    private static void applyFrom(SkinLayers3DConfigData data) {
        modernSkinSupport = data.modernSkinSupport;
        enabled = data.enabled;
        enableHat = data.enableHat;
        enableJacket = data.enableJacket;
        enableLeftSleeve = data.enableLeftSleeve;
        enableRightSleeve = data.enableRightSleeve;
        enableLeftPants = data.enableLeftPants;
        enableRightPants = data.enableRightPants;
        baseVoxelSize = data.baseVoxelSize;
        bodyVoxelWidthSize = data.bodyVoxelWidthSize;
        headVoxelSize = data.headVoxelSize;
        skullVoxelSize = data.skullVoxelSize;
        renderDistanceLOD = data.renderDistanceLOD;
        enableSkulls = data.enableSkulls;
        fastRender = data.fastRender;
    }

    /**
     * GSON-friendly data holder with defaults. Fields are non-static so GSON can
     * deserialize them. Must have a public no-arg constructor.
     */
    public static class SkinLayers3DConfigData {

        public boolean modernSkinSupport = true;
        public boolean enabled = true;
        public boolean enableHat = true;
        public boolean enableJacket = true;
        public boolean enableLeftSleeve = true;
        public boolean enableRightSleeve = true;
        public boolean enableLeftPants = true;
        public boolean enableRightPants = true;
        public float baseVoxelSize = 1.15f;
        public float bodyVoxelWidthSize = 1.05f;
        public float headVoxelSize = 1.18f;
        public float skullVoxelSize = 1.1f;
        public int renderDistanceLOD = 14;
        public boolean enableSkulls = true;
        public boolean fastRender = true;
    }
}
