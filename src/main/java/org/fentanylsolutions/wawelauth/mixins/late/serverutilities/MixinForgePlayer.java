package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import serverutils.lib.data.ForgePlayer;

@Mixin(value = ForgePlayer.class, remap = false)
public abstract class MixinForgePlayer {

    @Shadow(remap = false)
    public abstract UUID getId();

    @Redirect(
        method = "getNullablePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;func_152612_a(Ljava/lang/String;)Lnet/minecraft/entity/player/EntityPlayerMP;"),
        remap = false)
    private EntityPlayerMP wawelauth$getOnlinePlayerByUuid(ServerConfigurationManager manager, String name) {
        UUID id = getId();
        if (id == null) {
            return manager.func_152612_a(name);
        }

        return ProviderQualifiedPlayerLookup.findOnlinePlayer(manager, id);
    }
}
