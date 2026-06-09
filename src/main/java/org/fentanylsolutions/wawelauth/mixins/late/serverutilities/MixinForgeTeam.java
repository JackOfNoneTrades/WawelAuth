package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import org.fentanylsolutions.wawelauth.wawelserver.compat.ServerUtilitiesForgePlayerResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;

@Mixin(value = ForgeTeam.class, remap = false)
public abstract class MixinForgeTeam {

    @Redirect(
        method = "serializeNBT",
        at = @At(value = "INVOKE", target = "Lserverutils/lib/data/ForgePlayer;getName()Ljava/lang/String;"),
        remap = false)
    private String wawelauth$serializeProviderAwarePlayerKey(ForgePlayer player) {
        return ServerUtilitiesForgePlayerResolver.serializePlayerKey(player);
    }

    @Redirect(
        method = "deserializeNBT",
        at = @At(
            value = "INVOKE",
            target = "Lserverutils/lib/data/Universe;getPlayer(Ljava/lang/CharSequence;)Lserverutils/lib/data/ForgePlayer;"),
        remap = false)
    private ForgePlayer wawelauth$deserializeProviderAwarePlayerKey(Universe universe, CharSequence rawInput) {
        return ServerUtilitiesForgePlayerResolver.resolveStoredPlayer(universe, rawInput);
    }
}
