package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.player.EntityPlayerMP;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandBase.class)
public abstract class MixinCommandBase {

    @Inject(method = "getPlayer", at = @At("HEAD"), cancellable = true)
    private static void wawelauth$getProviderQualifiedPlayer(ICommandSender sender, String username,
        CallbackInfoReturnable<EntityPlayerMP> cir) {
        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(username)) {
            return;
        }

        EntityPlayerMP player = ProviderQualifiedPlayerLookup.resolveOnlinePlayer(username);
        if (player == null) {
            throw new PlayerNotFoundException();
        }

        cir.setReturnValue(player);
    }
}
