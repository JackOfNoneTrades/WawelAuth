package org.fentanylsolutions.wawelauth.mixins.late.hbm;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "com.hbm.main.ModEventHandlerClient", remap = false)
public class MixinModEventHandlerClient {

    // PR to NTM -> remove after next release
    @SuppressWarnings("LocalMayBeArgsOnly")
    @Inject(
        method = "preRenderEvent(Lnet/minecraftforge/client/event/RenderLivingEvent$Pre;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/hbm/items/armor/ArmorFSB;hasFSBArmor(Lnet/minecraft/entity/player/EntityPlayer;)Z"),
        cancellable = true)
    private void fixNPE(CallbackInfo ci, @Local EntityPlayer player) {
        if (player == null) {
            ci.cancel();
        }
    }

}
