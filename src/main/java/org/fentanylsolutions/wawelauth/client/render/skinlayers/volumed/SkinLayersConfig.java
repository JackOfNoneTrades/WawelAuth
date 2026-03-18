package org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "wawelauth", category = "skinlayers")
public class SkinLayersConfig {

    // todo: todo

    @Config.Comment("Render cape.")
    @Config.DefaultBoolean(true)
    public static boolean enableCape = true;

    @Config.Comment("Render jacket layer.")
    @Config.DefaultBoolean(true)
    public static boolean enableJacket = true;

    @Config.Comment("Render left sleeve layer.")
    @Config.DefaultBoolean(true)
    public static boolean enableLeftSleeve = true;

    @Config.Comment("Render right sleeve layer.")
    @Config.DefaultBoolean(true)
    public static boolean enableRightSleeve = true;

    @Config.Comment("Render left pants layer.")
    @Config.DefaultBoolean(true)
    public static boolean enableLeftPants = true;

    @Config.Comment("Render right pants layer.")
    @Config.DefaultBoolean(true)
    public static boolean enableRightPants = true;

    @Config.Comment("Render hat layer.")
    @Config.DefaultBoolean(true)
    public static boolean enableHat = true;

}
