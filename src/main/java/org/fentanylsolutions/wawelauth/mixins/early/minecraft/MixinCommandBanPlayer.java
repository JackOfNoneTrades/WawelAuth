package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandBanPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareCommandResolver;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

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
        wawelauth$qualifiedBanInput = rawInput;
        wawelauth$qualifiedBanProfile = ProviderAwareCommandResolver.resolveProfileForListAdd(
            rawInput,
            ProviderAwareUserListType.BANS);
        return wawelauth$qualifiedBanProfile;
    }

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;func_152612_a(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;")) // ServerConfigurationManager.getPlayerForUsername
    private EntityPlayerMP wawelauth$findQualifiedBannedPlayer(ServerConfigurationManager manager, String rawInput) {
        GameProfile profile = rawInput.equals(wawelauth$qualifiedBanInput) ? wawelauth$qualifiedBanProfile : null;
        if (profile == null || profile.getId() == null) {
            return null;
        }

        return ProviderQualifiedPlayerLookup.findOnlinePlayer(manager, profile.getId());
    }

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true)
    private void wawelauth$completeProviderQualifiedBan(
        ICommandSender sender,
        String[] args,
        CallbackInfoReturnable<List<String>> cir) {
        if (args.length == 1) {
            cir.setReturnValue(ProviderAwareCommandResolver.completeBanTargets(args[0]));
        } else if (args.length > 1) {
            cir.setReturnValue(null);
        }
    }
}
