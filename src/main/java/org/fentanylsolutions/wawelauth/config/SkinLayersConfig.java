package org.fentanylsolutions.wawelauth.config;

import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "wawelauth", category = "skinlayers", configSubDirectory = "wawelauth")
public class SkinLayersConfig {

    @Config.Comment("Cape.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState cape;

    @Config.Comment("Jacket layer.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState jacket;

    @Config.Comment("Left sleeve layer.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState leftSleeve;

    @Config.Comment("Right sleeve layer.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState rightSleeve;

    @Config.Comment("Left pants layer.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState leftPants;

    @Config.Comment("Right pants layer.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState rightPants;

    @Config.Comment("Hat layer.")
    @Config.DefaultEnum("VOLUMETRIC")
    public static SkinLayersHelper.PartState hat;

    @Config.Comment("Allows the base parts of the skin to be semi-translucent/transparent instead of completely black. Note that invisible skins will also work with this option enabled.")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableBasePartTranslucency = false;

}
