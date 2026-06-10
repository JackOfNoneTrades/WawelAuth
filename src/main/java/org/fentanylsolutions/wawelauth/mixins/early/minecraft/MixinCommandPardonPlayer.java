package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandPardonPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListBans;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@Mixin(CommandPardonPlayer.class)
public class MixinCommandPardonPlayer {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/UserListBans;func_152703_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // UserListBans.getBannedProfile
    private GameProfile wawelauth$resolveQualifiedPardonEntry(UserListBans bans, String rawInput) {
        return ProviderAwareCommandResolver
            .resolveProfileForListRemove(rawInput, bans, ProviderAwareUserListType.BANS, "ban");
    }

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true)
    private void wawelauth$completeProviderQualifiedPardon(ICommandSender sender, String[] args,
        CallbackInfoReturnable<List<String>> cir) {
        if (args.length == 1) {
            UserListBans bans = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152608_h();
            cir.setReturnValue(
                ProviderAwareCommandResolver.completeSavedList(args[0], bans, ProviderAwareUserListType.BANS));
        }
    }
}
