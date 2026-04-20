package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import com.gtnewhorizon.gtnhlib.config.Config;

/**
 * Configuration for client-side 3D skin rendering.
 * <p>
 * Field defaults match 3d-Skin-Layers upstream where applicable.
 */
@Config(modid = "wawelauth", category = "skinlayers3D")
public class SkinLayers3DConfig {

    @Config.Comment("Enable modern skin support (64x64, slim arms, HD pass-through).")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean modernSkinSupport = true;

    @Config.Comment("Disable skin overlay rendering when armor is equipped.")
    @Config.DefaultBoolean(true)
    public static boolean hideOverlayArmor = true;

    @Config.Comment("Allow first-person 3D sleeve layers to render even when armor would normally hide overlays.")
    @Config.DefaultBoolean(false)
    public static boolean showFirstPerson3DLayersWithArmor = false;

    @Config.Comment("Master toggle for all 3D skin layer rendering (players and skulls).")
    @Config.DefaultBoolean(true)
    public static boolean enabled3D = true;

    @Config.Comment("Render hat layer as 3D voxels.")
    @Config.DefaultBoolean(true)
    public static boolean enableHat3D = true;

    @Config.Comment("Render jacket layer as 3D voxels.")
    @Config.DefaultBoolean(true)
    public static boolean enableJacket3D = true;

    @Config.Comment("Render left sleeve layer as 3D voxels.")
    @Config.DefaultBoolean(true)
    public static boolean enableLeftSleeve3D = true;

    @Config.Comment("Render right sleeve layer as 3D voxels.")
    @Config.DefaultBoolean(true)
    public static boolean enableRightSleeve3D = true;

    @Config.Comment("Render left pants layer as 3D voxels.")
    @Config.DefaultBoolean(true)
    public static boolean enableLeftPants3D = true;

    @Config.Comment("Render right pants layer as 3D voxels.")
    @Config.DefaultBoolean(true)
    public static boolean enableRightPants3D = true;

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
    @Config.DefaultInt(14)
    @Config.RangeInt(min = 1, max = 64)
    public static int renderDistanceLOD = 14;

    @Config.Comment("Enable 3D voxel rendering for player skulls.")
    @Config.DefaultBoolean(true)
    public static boolean enableSkulls = true;

    @Config.Comment("Render front faces as vanilla boxes, voxels for sides only (faster).")
    @Config.DefaultBoolean(true)
    public static boolean fastRender = true;
}
