package org.fentanylsolutions.wawelauth;

import java.io.File;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayersConfig;
import org.fentanylsolutions.wawelauth.packet.PacketHandler;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelserver.CommandWawelAuth;
import org.fentanylsolutions.wawelauth.wawelserver.WawelPingServerHooks;
import org.fentanylsolutions.wawelauth.wawelserver.WawelServer;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.loadAll(event.getModConfigurationDirectory());

        try {
            ConfigurationManager.registerConfig(ClientConfig.class);
            ConfigurationManager.registerConfig(SkinLayers3DConfig.class);
            ConfigurationManager.registerConfig(SkinLayersConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }

        PacketHandler.init();
        WawelAuth.LOG.info("I am Wawel Auth at version {}", Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        if (Config.server() != null && Config.server()
            .isWawelAuthEnabled()) {
            if (!event.getServer()
                .isServerInOnlineMode()) {
                WawelAuth.LOG.warn("============================================================");
                WawelAuth.LOG.warn("Wawel Auth server module is ENABLED in config, but the server is");
                WawelAuth.LOG.warn("running with online-mode=false (offline mode).");
                WawelAuth.LOG.warn("Wawel Auth server features are DISABLED for this run.");
                WawelAuth.LOG.warn("Disabled features:");
                WawelAuth.LOG.warn(" - Local Yggdrasil endpoints");
                WawelAuth.LOG.warn(" - /wawelauth command registration");
                WawelAuth.LOG.warn(" - Wawel Auth ping capability advertisement");
                WawelAuth.LOG.warn("To enable Wawel Auth server module, set in server.properties:");
                WawelAuth.LOG.warn("  online-mode=true");
                WawelAuth.LOG.warn("Then restart the server.");
                WawelAuth.LOG.warn("============================================================");
                return;
            }

            WawelPingServerHooks.register();
            File stateDir = new File(Config.getConfigDir(), "data");
            WawelServer.start(stateDir);
            event.registerServerCommand(new CommandWawelAuth());
        }
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        WawelServer.stop();
    }

    public void onConfigReload() {}
}
