package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;

@Mixin(value = Universe.class, remap = false)
public abstract class MixinUniverse {

    @Shadow(remap = false)
    public abstract ForgePlayer getPlayer(UUID id);

    @Inject(
        method = "getPlayer(Ljava/lang/CharSequence;)Lserverutils/lib/data/ForgePlayer;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void wawelauth$getProviderQualifiedPlayer(CharSequence nameOrId, CallbackInfoReturnable<ForgePlayer> cir) {
        if (nameOrId == null) {
            return;
        }

        String rawInput = nameOrId.toString();
        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            return;
        }

        GameProfile profile = FallbackWhitelistLookup.resolveQualifiedProfile(rawInput);
        if (profile == null || profile.getId() == null) {
            cir.setReturnValue(null);
            return;
        }

        cir.setReturnValue(getPlayer(profile.getId()));
    }
}
