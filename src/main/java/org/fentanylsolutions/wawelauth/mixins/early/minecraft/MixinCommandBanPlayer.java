package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandBanPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

/**
 * Supports provider-qualified bans:
 * /ban <username>@<fallbackName>
 */
@Mixin(CommandBanPlayer.class)
public class MixinCommandBanPlayer {

    @Unique
    private String wawelauth$qualifiedBanInput;

    @Unique
    private GameProfile wawelauth$qualifiedBanProfile;

    @Inject(method = "processCommand", at = @At("HEAD"))
    private void wawelauth$resetQualifiedBan(ICommandSender sender, String[] args, CallbackInfo ci) {
        wawelauth$qualifiedBanInput = null;
        wawelauth$qualifiedBanProfile = null;
    }

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/PlayerProfileCache;func_152655_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // PlayerProfileCache.getGameProfileForUsername
    private GameProfile wawelauth$resolveQualifiedBanEntry(PlayerProfileCache profileCache, String rawInput) {
        if (FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            return wawelauth$resolveQualifiedBanProfile(rawInput);
        }

        // Reject unqualified names.
        return null;
    }

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;func_152612_a(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;")) // ServerConfigurationManager.getPlayerForUsername
    private EntityPlayerMP wawelauth$findQualifiedBannedPlayer(ServerConfigurationManager manager, String rawInput) {
        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            return null;
        }

        GameProfile profile = rawInput.equals(wawelauth$qualifiedBanInput) ? wawelauth$qualifiedBanProfile
            : wawelauth$resolveQualifiedBanProfile(rawInput);
        if (profile == null || profile.getId() == null) {
            return null;
        }

        return ProviderQualifiedPlayerLookup.findOnlinePlayer(manager, profile.getId());
    }

    @Unique
    private GameProfile wawelauth$resolveQualifiedBanProfile(String rawInput) {
        wawelauth$qualifiedBanInput = rawInput;
        wawelauth$qualifiedBanProfile = FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
        return wawelauth$qualifiedBanProfile;
    }
}
