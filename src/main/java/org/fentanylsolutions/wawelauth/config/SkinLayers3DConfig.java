package org.fentanylsolutions.wawelauth.config;

import com.gtnewhorizon.gtnhlib.config.Config;

/**
 * Configuration for client-side 3D skin rendering.
 * <p>
 * Field defaults match 3d-Skin-Layers upstream where applicable.
 */
@Config(modid = "wawelauth", category = "skinlayers3D", configSubDirectory = "wawelauth")
public class SkinLayers3DConfig {

    @Config.Comment("Master toggle for all 3D skin layer rendering (players and skulls). Not compatible with SmartMoving.")
    @Config.DefaultBoolean(true)
    public static boolean enabled3D = true;

    @Config.Comment("Enable 3D voxel rendering for player skulls.")
    @Config.DefaultBoolean(true)
    public static boolean enableSkulls3D = true;

    @Config.Comment("Base voxel size for limbs and body.")
    @Config.DefaultFloat(1.15f)
    @Config.RangeFloat(min = 0.5f, max = 2.0f)
    public static float baseVoxelSize = 1.15f;

    @Config.Comment("Body voxel width scale.")
    @Config.DefaultFloat(1.05f)
    @Config.RangeFloat(min = 0.5f, max = 2.0f)
    public static float bodyVoxelWidthSize = 1.05f;

    @Config.Comment("Head voxel size scale.")
    @Config.DefaultFloat(1.18f)
    @Config.RangeFloat(min = 0.5f, max = 2.0f)
    public static float headVoxelSize = 1.18f;

    @Config.Comment("Skull voxel size scale.")
    @Config.DefaultFloat(1.1f)
    @Config.RangeFloat(min = 0.5f, max = 2.0f)
    public static float skullVoxelSize = 1.1f;

    @Config.Comment("Distance in blocks before falling back to flat 2D overlays.")
    @Config.DefaultInt(16)
    @Config.RangeInt(min = 1, max = 64)
    public static int renderDistanceLOD = 16;

}
