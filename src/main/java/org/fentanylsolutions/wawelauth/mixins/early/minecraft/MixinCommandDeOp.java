package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandDeOp;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOps;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@Mixin(CommandDeOp.class)
public class MixinCommandDeOp {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/UserListOps;func_152700_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // UserListOps.getByName
    private GameProfile wawelauth$resolveQualifiedDeOpEntry(UserListOps userListOps, String rawInput) {
        return ProviderAwareCommandResolver
            .resolveProfileForListRemove(rawInput, userListOps, ProviderAwareUserListType.OPS, "op");
    }

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true)
    private void wawelauth$completeProviderQualifiedDeOp(ICommandSender sender, String[] args,
        CallbackInfoReturnable<List<String>> cir) {
        if (args.length == 1) {
            UserListOps ops = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152603_m();
            cir.setReturnValue(
                ProviderAwareCommandResolver.completeSavedList(args[0], ops, ProviderAwareUserListType.OPS));
        }
    }
}
