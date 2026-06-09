package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.wawelauth.wawelserver.compat.BetterQuestingCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import betterquesting.network.handlers.NetPartyAction;
import betterquesting.storage.NameCache;

@Mixin(value = NetPartyAction.class, remap = false)
public abstract class MixinNetPartyAction {

    @Redirect(
        method = "inviteUser",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;func_152612_a(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;"),
        remap = false)
    private static EntityPlayerMP wawelauth$resolveOnlineInviteTarget(ServerConfigurationManager manager,
        String username) {
        return BetterQuestingCommandResolver.resolveOnlinePartyActionPlayer(username);
    }

    @Redirect(
        method = "kickUser",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;func_152612_a(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;"),
        remap = false)
    private static EntityPlayerMP wawelauth$resolveOnlineKickTarget(ServerConfigurationManager manager,
        String username) {
        return BetterQuestingCommandResolver.resolveOnlinePartyActionPlayer(username);
    }

    @Redirect(
        method = "inviteUser",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/storage/NameCache;getUUID(Ljava/lang/String;)Ljava/util/UUID;"),
        remap = false)
    private static UUID wawelauth$resolveOfflineInviteTarget(NameCache cache, String username) {
        return BetterQuestingCommandResolver.resolvePartyActionPlayerId(username);
    }

    @Redirect(
        method = "kickUser",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/storage/NameCache;getUUID(Ljava/lang/String;)Ljava/util/UUID;"),
        remap = false)
    private static UUID wawelauth$resolveOfflineKickTarget(NameCache cache, String username) {
        return BetterQuestingCommandResolver.resolvePartyMemberActionPlayerId(username);
    }
}
