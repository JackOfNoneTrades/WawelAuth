package org.fentanylsolutions.wawelauth.core;

import org.fentanylsolutions.fentlib.core.FentMixins;
import org.fentanylsolutions.fentlib.util.MiscUtil;
import org.fentanylsolutions.fentlib.util.MixinUtil;

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
        registry.mixin("MixinServerConfigurationManagerJoinSync")
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

        registry.mixin("MixinEntityPlayer")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.BOTH)
            .build();
        registry.mixin("MixinGameSettings")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
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
        registry.mixin("MixinNetworkManagerGameplayProxy")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinMinecraftSingleplayerAccount")
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
        registry.mixin("MixinNetHandlerPlayClientJoinSync")
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
        registry.mixin("MixinGuiSelectWorld")
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
        registry.mixin("MixinRenderingRegistry")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        // Client Mixins: 3D skin layers
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

        // Compat
        registry.mixin("MixinModEventHandlerClient")
            .modid("hbm")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinPlayerHeadIcon")
            .modid("serverutilities")
            .extraModid("ServerUtilities")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinEntityPlayerPreview")
            .modid("betterquesting")
            .extraModid("BetterQuesting")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("MixinPanelPlayerPortrait")
            .modid("betterquesting")
            .extraModid("BetterQuesting")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinDynmapForgePlayer")
            .modid("dynmap")
            .extraModid("Dynmap")
            .extraModid("gtnh-web-map")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("AccessorPlayerFaces")
            .modid("dynmap")
            .extraModid("Dynmap")
            .extraModid("gtnh-web-map")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.SERVER)
            .build();
        registry.mixin("MixinDynmapLoadPlayerImages")
            .modid("dynmap")
            .extraModid("Dynmap")
            .extraModid("gtnh-web-map")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.SERVER)
            .build();

        registry.mixin("MixinClientProxy")
            .modid("Botania")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinVoiceHandler")
            .modid("ServerTools")
            .phase(MixinUtil.Phase.LATE)
            .side(MiscUtil.Side.BOTH)
            .build();
    }

    public static java.util.List<String> getEarlyMixinsForLoader() {
        return INSTANCE.getEarlyMixins();
    }

    public static java.util.List<String> getLateMixinsForLoader(java.util.Set<String> loadedCoreMods) {
        return INSTANCE.getLateMixins(loadedCoreMods);
    }
}
