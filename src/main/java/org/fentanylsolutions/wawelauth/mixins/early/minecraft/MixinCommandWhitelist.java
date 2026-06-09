package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandWhitelist;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.server.management.UserListWhitelist;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@Mixin(CommandWhitelist.class)
public class MixinCommandWhitelist {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/PlayerProfileCache;func_152655_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // PlayerProfileCache.getGameProfileForUsername
    private GameProfile wawelauth$resolveQualifiedWhitelistEntry(PlayerProfileCache profileCache, String rawInput) {
        return ProviderAwareCommandResolver.resolveProfileForListAdd(rawInput, ProviderAwareUserListType.WHITELIST);
    }

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/UserListWhitelist;func_152706_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // UserListWhitelist.getByName
    private GameProfile wawelauth$resolveQualifiedWhitelistRemove(UserListWhitelist whitelist, String rawInput) {
        return ProviderAwareCommandResolver.resolveProfileForListRemove(
            rawInput,
            whitelist,
            ProviderAwareUserListType.WHITELIST,
            "whitelist");
    }

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true)
    private void wawelauth$completeProviderQualifiedWhitelist(
        ICommandSender sender,
        String[] args,
        CallbackInfoReturnable<List<String>> cir) {
        if (args.length != 2) {
            return;
        }

        if ("add".equals(args[0])) {
            UserListWhitelist whitelist = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152599_k();
            cir.setReturnValue(ProviderAwareCommandResolver.completeWhitelistAdd(args[1], whitelist));
        } else if ("remove".equals(args[0])) {
            UserListWhitelist whitelist = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152599_k();
            cir.setReturnValue(
                ProviderAwareCommandResolver.completeSavedList(args[1], whitelist, ProviderAwareUserListType.WHITELIST));
        }
    }
}
