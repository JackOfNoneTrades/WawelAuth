package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import net.minecraft.command.ICommandSender;

import org.fentanylsolutions.wawelauth.wawelserver.compat.BetterQuestingCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import betterquesting.commands.BQ_CommandAdmin;

@Mixin(value = BQ_CommandAdmin.class, remap = false)
public abstract class MixinBQCommandAdmin {

    @Inject(method = "addTabCompletionOptions", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$completeProviderAwarePlayerTargets(ICommandSender sender, String[] args,
        CallbackInfoReturnable<java.util.List<String>> cir) {
        if (!wawelauth$isPlayerTarget(args)) {
            return;
        }

        cir.setReturnValue(BetterQuestingCommandResolver.completePlayerTargets(args[args.length - 1]));
    }

    private static boolean wawelauth$isPlayerTarget(String[] args) {
        if (args == null || args.length < 2) {
            return false;
        }

        String command = args[0];
        int index = args.length - 1;
        return ("check".equalsIgnoreCase(command) && index == 1)
            || ("check_all".equalsIgnoreCase(command) && index == 1)
            || ("complete".equalsIgnoreCase(command) && index == 2)
            || ("reset".equalsIgnoreCase(command) && index == 2)
            || ("lives".equalsIgnoreCase(command) && index == 3);
    }
}
