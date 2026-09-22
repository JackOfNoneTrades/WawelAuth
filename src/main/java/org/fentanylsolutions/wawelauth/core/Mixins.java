package org.fentanylsolutions.wawelauth.core;

import static org.fentanylsolutions.fentlib.util.MiscUtil.Side.BOTH;
import static org.fentanylsolutions.fentlib.util.MiscUtil.Side.CLIENT;
import static org.fentanylsolutions.fentlib.util.MiscUtil.Side.SERVER;

import org.fentanylsolutions.fentlib.core.FentMixins;
import org.fentanylsolutions.fentlib.util.MiscUtil;
import org.fentanylsolutions.fentlib.util.MixinUtil;

public class Mixins extends FentMixins {

    private static final Mixins INSTANCE = new Mixins();

    /// ==========================================
    /// E A R L Y M I X I N S
    /// ==========================================
    protected void registerEarlyMixins(MixinUtil.Registry registry) {
        // Modern Skin Support
        earlyMixin(BOTH, registry, "modernskinsupport.MixinEntityPlayer");
        earlyMixin(BOTH, registry, "modernskinsupport.MixinModelBiped");
        earlyMixin(BOTH, registry, "modernskinsupport.MixinGameSettings");
        earlyMixin(CLIENT, registry, "modernskinsupport.MixinImageBufferDownload");
        earlyMixin(CLIENT, registry, "modernskinsupport.MixinRenderPlayer");
        earlyMixin(CLIENT, registry, "modernskinsupport.MixinAbstractClientPlayer");
        earlyMixin(CLIENT, registry, "modernskinsupport.MixinTileEntitySkullRenderer");

        // Accessors
        earlyMixin(SERVER, registry, "AccessorUserList");
        earlyMixin(SERVER, registry, "AccessorUserListEntry");
        earlyMixin(CLIENT, registry, "AccessorMinecraft");
        earlyMixin(CLIENT, registry, "AccessorGuiMainMenu");

        // Minecraft Mixins
        earlyMixin(SERVER, registry, "MixinNetHandlerLoginServerAuthThread");
        earlyMixin(SERVER, registry, "MixinCommandWhitelist");
        earlyMixin(SERVER, registry, "MixinCommandBase");
        earlyMixin(SERVER, registry, "MixinCommandOp");
        earlyMixin(SERVER, registry, "MixinCommandDeOp");
        earlyMixin(SERVER, registry, "MixinCommandBanPlayer");
        earlyMixin(SERVER, registry, "MixinCommandPardonPlayer");
        earlyMixin(SERVER, registry, "MixinServerConfigurationManagerJoinSync");

        // Session handoff + server data extension
        earlyMixin(CLIENT, registry, "MixinGuiConnecting");
        earlyMixin(CLIENT, registry, "MixinNetworkManagerGameplayProxy");
        earlyMixin(CLIENT, registry, "MixinMinecraftSingleplayerAccount");
        earlyMixin(CLIENT, registry, "MixinNetHandlerLoginClient");
        earlyMixin(CLIENT, registry, "MixinServerData");
        earlyMixin(CLIENT, registry, "MixinServerListPersistence");
        earlyMixin(CLIENT, registry, "MixinNetHandlerPlayClientJoinSync");

        // GUI integration
        earlyMixin(CLIENT, registry, "MixinServerListEntryNormal");
        earlyMixin(CLIENT, registry, "MixinGuiMultiplayer");
        earlyMixin(CLIENT, registry, "MixinGuiSelectWorld");
        earlyMixin(CLIENT, registry, "MixinGuiChat");
        earlyMixin(CLIENT, registry, "MixinSkinManager");

        // Authlib: texture verification + profile fetching
        earlyMixin("authlib", CLIENT, registry, "MixinYggdrasilMinecraftSessionService");
        earlyMixin("authlib", CLIENT, registry, "MixinYggdrasilGameProfileRepository");
    }

    /// ==========================================
    /// L A T E M I X I N S
    /// ==========================================
    public void registerLateMixins(MixinUtil.Registry registry) {
        lateMixin("serverutilities", CLIENT, registry, "MixinPlayerHeadIcon");
        lateMixin("serverutilities", CLIENT, registry, "MixinTabSkinCache");
        lateMixin("serverutilities", CLIENT, registry, "AccessorGuiManagePlayersButtonBase");
        lateMixin("serverutilities", CLIENT, registry, "MixinGuiManagePlayerButtons");
        lateMixin("serverutilities", CLIENT, registry, "MixinGuiTransferOwnershipButton");
        lateMixin("serverutilities", SERVER, registry, "MixinUniverse");
        lateMixin("serverutilities", SERVER, registry, "MixinForgePlayer");
        lateMixin("serverutilities", SERVER, registry, "MixinForgeTeam");
        lateMixin("betterquesting", SERVER, registry, "MixinNetNameSync");
        lateMixin("betterquesting", SERVER, registry, "MixinNetPartySync");
        lateMixin("betterquesting", SERVER, registry, "MixinNetPartyAction");
        lateMixin("betterquesting", SERVER, registry, "MixinQuestCommandBase");
        lateMixin("betterquesting", SERVER, registry, "MixinBQCommandAdmin");
        lateMixin("betterquesting", SERVER, registry, "MixinBQCopyProgress");
        lateMixin("betterquesting", CLIENT, registry, "MixinGuiPartyInvite");
        lateMixin("betterquesting", CLIENT, registry, "MixinGuiPartyManage");
        lateMixin("etfuturum", CLIENT, registry, "MixinTileEntityFancySkullRenderer");
        lateMixin("aether_legacy", CLIENT, registry, "MixinAetherItemRenderer");
        lateMixin("dynmap", SERVER, registry, "MixinDynmapForgePlayer");
        lateMixin("dynmap", SERVER, registry, "AccessorPlayerFaces");
        lateMixin("dynmap", SERVER, registry, "MixinDynmapLoadPlayerImages");
        lateMixin("Botania", CLIENT, registry, "MixinClientProxy");
        lateMixin("chatbubbles", CLIENT, registry, "MixinChatBubblesMod");
        lateMixin("chatbubbles", CLIENT, registry, "MixinLiteModChatBubbles");
    }

    private void earlyMixin(MiscUtil.Side side, MixinUtil.Registry registry, String name) {
        registry.mixin(name)
            .phase(MixinUtil.Phase.EARLY)
            .side(side)
            .build();
    }

    private void earlyMixin(String modid, MiscUtil.Side side, MixinUtil.Registry registry, String name) {
        registry.mixin(name)
            .phase(MixinUtil.Phase.EARLY)
            .modid(modid)
            .side(side)
            .build();
    }

    private void lateMixin(String modid, MiscUtil.Side side, MixinUtil.Registry registry, String name) {
        registry.mixin(name)
            .phase(MixinUtil.Phase.LATE)
            .modid(modid)
            .side(side)
            .build();
    }

    @Override
    protected void registerMixins(MixinUtil.Registry registry) {
        registerEarlyMixins(registry);
        registerLateMixins(registry);
    }

    public static java.util.List<String> getEarlyMixinsForLoader() {
        return INSTANCE.getEarlyMixins();
    }

    public static java.util.List<String> getLateMixinsForLoader(java.util.Set<String> loadedCoreMods) {
        return INSTANCE.getLateMixins(loadedCoreMods);
    }
}
