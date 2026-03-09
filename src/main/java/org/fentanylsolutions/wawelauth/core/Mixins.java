package org.fentanylsolutions.wawelauth.core;

import java.util.ArrayList;
import java.util.List;

import org.fentanylsolutions.fentlib.core.FentMixins;
import org.fentanylsolutions.fentlib.util.MiscUtil;
import org.fentanylsolutions.fentlib.util.MixinUtil;

import cpw.mods.fml.common.Loader;

public class Mixins extends FentMixins {

    private static final Mixins INSTANCE = new Mixins();

    @Override
    protected void registerMixins(MixinUtil.Registry registry) {
        // Minecraft Accessors
        registry.mixin("AccessorNetworkSystem")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.BOTH)
            .build();

        // Minecraft Mixins
        registry.mixin("MixinNetworkSystem")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.BOTH)
            .build();
        registry.mixin("MixinNetHandlerLoginServerAuthThread")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinCommandWhitelist")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinCommandOp")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinCommandDeOp")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinBanListIpv6")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinEntityPlayerMPIpv6")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinNetHandlerLoginServerIpv6")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinCommandBanIpIpv6")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinCommandPardonIpIpv6")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.SERVER)
            .build();

        // Client Accessors
        registry.mixin("AccessorMinecraft")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Client Mixins: address normalization
        registry.mixin("MixinServerAddress")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Client Mixins: session handoff + server data extension
        registry.mixin("MixinGuiConnecting")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinNetHandlerLoginClient")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinServerData")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Client Mixins: GUI integration
        registry.mixin("MixinServerListEntryNormal")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinGuiMultiplayer")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinImageBufferDownload")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinSkinManager")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Client Mixins: modern skin rendering
        registry.mixin("MixinModelBiped")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinRenderPlayer")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinAbstractClientPlayer")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Client Mixins: 3D skin layers
        registry.mixin("AccessorThreadDownloadImageData")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinTileEntitySkullRenderer")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Authlib Mixins: texture verification + profile fetching
        registry.mixin("MixinYggdrasilMinecraftSessionService")
            .modid("authlib")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinYggdrasilGameProfileRepository")
            .modid("authlib")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.BOTH)
            .build();

    }

    public static java.util.List<String> getEarlyMixinsForLoader() {
        return INSTANCE.getEarlyMixins();
    }

    public static java.util.List<String> getLateMixinsForLoader(java.util.Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<>(INSTANCE.getLateMixins(loadedCoreMods));
        if (shouldLoadDynmapMixins(loadedCoreMods)) {
            mixins.add("dynmap.MixinDynmapForgePlayer");
            mixins.add("dynmap.MixinDynmapLoadPlayerImages");
        }
        return mixins;
    }

    private static boolean shouldLoadDynmapMixins(java.util.Set<String> loadedCoreMods) {
        if (!MiscUtil.isServer()) {
            return false;
        }

        if (loadedCoreMods != null && (loadedCoreMods.contains("gtnh-web-map") || loadedCoreMods.contains("dynmap")
            || loadedCoreMods.contains("org.dynmap.forge.DynmapPlugin"))) {
            return true;
        }

        if (Loader.isModLoaded("gtnh-web-map") || Loader.isModLoaded("dynmap")) {
            return true;
        }

        try {
            Class.forName("org.dynmap.PlayerFaces", false, Mixins.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
