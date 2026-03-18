package org.fentanylsolutions.wawelauth;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import org.fentanylsolutions.wawelauth.client.WindowDropHandler;
import org.fentanylsolutions.wawelauth.client.gui.GuiSkinCustomizationHandler;
import org.fentanylsolutions.wawelauth.client.gui.GuiTransitionScheduler;
import org.fentanylsolutions.wawelauth.client.gui.WawelAuthKeybind;
import org.fentanylsolutions.wawelauth.client.render.SkinResolverClientHandler;
import org.fentanylsolutions.wawelauth.client.render.animatedcape.AnimatedCapeClientHandler;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.WawelPingClientHooks;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
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

        // Window drag-and-drop detection — requires lwjgl3ify (SDL3)
        if (Loader.isModLoaded("lwjgl3ify")) {
            WindowDropHandler.register();
        }

        // Register keybind for account manager
        WawelAuthKeybind keybind = new WawelAuthKeybind();
        ClientRegistry.registerKeyBinding(keybind.getKeyBinding());
        MinecraftForge.EVENT_BUS.register(keybind);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new GuiSkinCustomizationHandler());
    }

    @Override
    public void onConfigReload() {
        super.onConfigReload();
    }
}
