package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.wawelauth.wawelserver.compat.BetterQuestingCommandResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import betterquesting.commands.QuestCommandBase;

@Mixin(value = QuestCommandBase.class, remap = false)
public abstract class MixinQuestCommandBase {

    @Inject(method = "findPlayerID", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$findProviderAwarePlayerId(MinecraftServer server, ICommandSender sender, String name,
        CallbackInfoReturnable<UUID> cir) {
        cir.setReturnValue(BetterQuestingCommandResolver.resolvePlayerId(sender, name));
    }
}
