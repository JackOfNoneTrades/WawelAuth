package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.fentanylsolutions.wawelauth.wawelserver.compat.BetterQuestingProviderSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import betterquesting.network.handlers.NetNameSync;

@Mixin(value = NetNameSync.class, remap = false)
public abstract class MixinNetNameSync {

    @Inject(method = "quickSync", at = @At("HEAD"), remap = false)
    private static void wawelauth$syncPartyProviders(EntityPlayerMP player, int partyID, CallbackInfo ci) {
        BetterQuestingProviderSync.syncParty(player, partyID);
    }

    @Inject(method = "sendNames", at = @At("HEAD"), remap = false)
    private static void wawelauth$syncNameProviders(EntityPlayerMP[] players, UUID[] uuids, String[] names,
        CallbackInfo ci) {
        BetterQuestingProviderSync.syncNames(players, uuids, names);
    }
}
