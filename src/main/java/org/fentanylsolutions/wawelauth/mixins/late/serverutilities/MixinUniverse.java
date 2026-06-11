package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import org.fentanylsolutions.wawelauth.wawelserver.compat.ServerUtilitiesForgePlayerResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;

@Mixin(value = Universe.class, remap = false)
public abstract class MixinUniverse {

    @Inject(
        method = "getPlayer(Ljava/lang/CharSequence;)Lserverutils/lib/data/ForgePlayer;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void wawelauth$getProviderQualifiedPlayer(CharSequence nameOrId, CallbackInfoReturnable<ForgePlayer> cir) {
        if (nameOrId == null) {
            return;
        }

        // Always cancels: ServerUtilities' name and substring fallbacks assume
        // unique names, which does not hold across providers. Offline players
        // must be referenced by qualified name.
        cir.setReturnValue(ServerUtilitiesForgePlayerResolver.resolveCommandPlayer((Universe) (Object) this, nameOrId));
    }
}
