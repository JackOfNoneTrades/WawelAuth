package org.fentanylsolutions.wawelauth.mixins.late.ears;

import java.util.UUID;

import org.fentanylsolutions.wawelauth.client.render.EarsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps standalone Ears from resolving WawelAuth-owned profiles through Mojang. */
@Pseudo
@Mixin(targets = "com.unascribed.ears.legacy.LegacyHelper", remap = false)
public abstract class MixinEarsLegacyHelper {

    @Inject(
        method = "ensureLookedUpAsynchronously(Ljava/util/UUID;Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void wawelauth$useResolvedSkin(UUID uuid, String name, CallbackInfo ci) {
        if (EarsCompat.prepareSkin(uuid, name)) ci.cancel();
    }
}
