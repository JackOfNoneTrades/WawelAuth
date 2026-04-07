package org.fentanylsolutions.wawelauth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fentanylsolutions.wawelauth.test.SqliteSmokeTest;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = WawelAuth.MODID,
    version = Tags.VERSION,
    name = "Wawel Auth",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    guiFactory = "org.fentanylsolutions.wawelauth.client.gui.GuiFactory",
    customProperties = { @Mod.CustomProperty(k = "license", v = "LGPLv3+SNEED"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/JackOfNoneTrades/WawelAuth/issues"),
        @Mod.CustomProperty(k = "iconFile", v = "assets/wawelauth/Logo_Dragon_Outline.png"),
        @Mod.CustomProperty(k = "backgroundFile", v = "assets/wawelauth/background.png") })
public class WawelAuth {

    public static final String MODID = "wawelauth";
    public static final String MODGROUP = "org.fentanylsolutions";
    public static final Logger LOG = LogManager.getLogger(MODID);

    private static boolean DEBUG_MODE;

    @SidedProxy(
        clientSide = MODGROUP + "." + MODID + ".ClientProxy",
        serverSide = MODGROUP + "." + MODID + ".CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        String debugVar = System.getenv("MCMODDING_DEBUG_MODE");
        DEBUG_MODE = debugVar != null;
        LOG.info("MCMODDING_DEBUG_MODE env var: {}", DEBUG_MODE);

        proxy.preInit(event);

        // SQLite smoke test
        if (isDebugMode()) {
            SqliteSmokeTest.run(event.getModConfigurationDirectory());
        }

        LocalConfig local = Config.local();
        boolean configuredDebug = local != null && local.isDebugMode();
        LOG.info("debugMode config option (local): {}", configuredDebug);
        LOG.info("isDebugMode: {}", isDebugMode());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        proxy.serverAboutToStart(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }

    public static boolean isDebugMode() {
        LocalConfig local = Config.local();
        boolean configuredDebug = local != null && local.isDebugMode();
        return DEBUG_MODE || configuredDebug;
    }

    public static void debug(String message) {
        if (isDebugMode()) {
            LOG.info("DEBUG: {}", message);
        }
    }
}
