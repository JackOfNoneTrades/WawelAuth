package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import java.util.List;
import java.util.UUID;

import net.minecraft.command.ICommandSender;

import org.fentanylsolutions.wawelauth.wawelserver.compat.BetterQuestingCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import betterquesting.commands.BQ_CopyProgress;

@Mixin(value = BQ_CopyProgress.class, remap = false)
public abstract class MixinBQCopyProgress {

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$completeProviderAwarePlayerTargets(ICommandSender sender, String[] args,
        CallbackInfoReturnable<List<String>> cir) {
        if (args == null || args.length == 0 || args.length > 2) {
            return;
        }

        cir.setReturnValue(BetterQuestingCommandResolver.completePlayerTargets(args[args.length - 1]));
    }

    @Inject(method = "getPlayerUUID", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wawelauth$getProviderAwarePlayerUuid(String data, CallbackInfoReturnable<UUID> cir) {
        cir.setReturnValue(BetterQuestingCommandResolver.resolvePlayerId(null, data));
    }
}
