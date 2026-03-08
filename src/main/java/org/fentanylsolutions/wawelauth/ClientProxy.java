package org.fentanylsolutions.wawelauth;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import org.fentanylsolutions.wawelauth.client.gui.AnimatedCapeClientHandler;
import org.fentanylsolutions.wawelauth.client.gui.GuiTransitionScheduler;
import org.fentanylsolutions.wawelauth.client.gui.SkinResolverClientHandler;
import org.fentanylsolutions.wawelauth.client.gui.WawelAuthKeybind;
import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DConfig;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.WawelPingClientHooks;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        super.preInit(event);

        // Load skin rendering config before Minecraft client classes start using it.
        SkinLayers3DConfig.load(Config.getLocalConfigDir());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Resolve client data directory
        File dataDir;
        LocalConfig local = Config.local();
        if (local != null && local.isUseOsConfigDir()) {
            dataDir = Config.getDataConfigDir();
        } else {
            dataDir = new File(Config.getConfigDir(), "client");
        }

        // WawelClient persists for the entire game session.
        // JVM shutdown hook handles cleanup: we must NOT stop here because
        // init() only runs once and won't re-run after leaving singleplayer.
        WawelClient.start(dataDir);
        WawelPingClientHooks.register();
        GuiTransitionScheduler.register();
        AnimatedCapeClientHandler.register();
        SkinResolverClientHandler.register();

        // Register keybind for account manager
        WawelAuthKeybind keybind = new WawelAuthKeybind();
        ClientRegistry.registerKeyBinding(keybind.getKeyBinding());
        MinecraftForge.EVENT_BUS.register(keybind);
    }

    @Override
    public void onConfigReload() {
        super.onConfigReload();
        SkinLayers3DConfig.load(Config.getLocalConfigDir());
    }
}
