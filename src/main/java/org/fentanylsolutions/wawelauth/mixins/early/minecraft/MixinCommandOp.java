package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandOp;
import net.minecraft.server.management.PlayerProfileCache;

import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@Mixin(CommandOp.class)
public class MixinCommandOp {

    @Redirect(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/PlayerProfileCache;func_152655_a(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")) // PlayerProfileCache.getGameProfileForUsername
    private GameProfile wawelauth$resolveQualifiedOpEntry(PlayerProfileCache profileCache, String rawInput) {
        return ProviderAwareCommandResolver.resolveProfileForListAdd(rawInput, ProviderAwareUserListType.OPS);
    }

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true)
    private void wawelauth$completeProviderQualifiedOp(
        ICommandSender sender,
        String[] args,
        CallbackInfoReturnable<List<String>> cir) {
        if (args.length == 1) {
            cir.setReturnValue(ProviderAwareCommandResolver.completeOpAdd(args[0]));
        }
    }
}
