package org.fentanylsolutions.wawelauth;

import java.io.File;

import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayersConfig;
import org.fentanylsolutions.wawelauth.packet.PacketHandler;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelserver.CommandWawelAuth;
import org.fentanylsolutions.wawelauth.wawelserver.WawelPingServerHooks;
import org.fentanylsolutions.wawelauth.wawelserver.WawelServer;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
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

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (server.isSinglePlayer()) {
            return;
        }

        ServerConfig config = Config.server();
        if (config == null || !config.isWawelAuthEnabled()) {
            return;
        }

        if (config.hasAbsoluteLikeApiRoot()) {
            failAbsoluteApiRootStartup(config);
            return;
        }

        if (config.getEffectiveApiRoot() == null) {
            failMissingPublicBaseUrlStartup(config);
            return;
        }

        if (!server.isServerInOnlineMode()) {
            failOfflineModeStartup();
        }
    }

    public void serverStarting(FMLServerStartingEvent event) {
        if (event.getServer()
            .isSinglePlayer()) {
            return;
        }

        if (Config.server() != null && Config.server()
            .isWawelAuthEnabled()) {
            if (!event.getServer()
                .isServerInOnlineMode()) {
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

    private void failAbsoluteApiRootStartup(ServerConfig config) {
        WawelAuth.LOG.error("============================================================");
        WawelAuth.LOG.error("Wawel Auth server module cannot start with an absolute apiRoot.");
        WawelAuth.LOG.error("apiRoot is now a relative path, but server.json currently has:");
        WawelAuth.LOG.error("  apiRoot={}", config.getApiRoot());
        WawelAuth.LOG.error("Required fix:");
        WawelAuth.LOG.error("  1. Move the base URL into publicBaseUrl");
        WawelAuth.LOG.error("  2. Set apiRoot to a relative path like auth");
        WawelAuth.LOG.error("Example:");
        WawelAuth.LOG.error("  \"publicBaseUrl\": \"auth.example.com:25565\"");
        WawelAuth.LOG.error("  \"apiRoot\": \"auth\"");
        WawelAuth.LOG.error("============================================================");
        FMLCommonHandler.instance()
            .exitJava(1, false);
    }

    private void failMissingPublicBaseUrlStartup(ServerConfig config) {
        WawelAuth.LOG.error("============================================================");
        WawelAuth.LOG.error("Wawel Auth server module cannot start without publicBaseUrl.");
        WawelAuth.LOG.error("publicBaseUrl is required to build the public auth API root,");
        WawelAuth.LOG.error("texture URLs, and authlib-injector metadata.");
        WawelAuth.LOG.error("Current server.json values:");
        WawelAuth.LOG.error(
            "  publicBaseUrl={}",
            config.getPublicBaseUrl()
                .isEmpty() ? "<empty>" : config.getPublicBaseUrl());
        WawelAuth.LOG.error(
            "  apiRoot={}",
            config.getApiRoot()
                .isEmpty() ? "<root>" : config.getApiRoot());
        WawelAuth.LOG.error("Required fix:");
        WawelAuth.LOG.error("  1. Set publicBaseUrl in server.json");
        WawelAuth.LOG.error("  2. Leave apiRoot as a relative path like auth");
        WawelAuth.LOG.error("Example:");
        WawelAuth.LOG.error("  \"publicBaseUrl\": \"auth.example.com:25565\"");
        WawelAuth.LOG.error("  \"apiRoot\": \"auth\"");
        WawelAuth.LOG.error("This would publish the API at:");
        WawelAuth.LOG.error("  http://auth.example.com:25565/auth");
        WawelAuth.LOG.error("============================================================");
        FMLCommonHandler.instance()
            .exitJava(1, false);
    }

    private void failOfflineModeStartup() {
        WawelAuth.LOG.error("============================================================");
        WawelAuth.LOG.error("Wawel Auth server module cannot start in offline mode.");
        WawelAuth.LOG.error("Startup is being aborted because Wawel Auth is enabled, but");
        WawelAuth.LOG.error("server.properties currently has:");
        WawelAuth.LOG.error("  online-mode=false");
        WawelAuth.LOG.error("Required fix:");
        WawelAuth.LOG.error("  1. Open server.properties");
        WawelAuth.LOG.error("  2. Set online-mode=true");
        WawelAuth.LOG.error("  3. Restart the server");
        WawelAuth.LOG.error("Wawel Auth depends on Mojang/Session verification being enabled.");
        WawelAuth.LOG.error("============================================================");
        FMLCommonHandler.instance()
            .exitJava(1, false);
    }
}
