package org.fentanylsolutions.wawelauth.mixins.late.SmartMoving;

import net.minecraft.client.Minecraft;
import net.smart.moving.SmartMovingFactory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SmartMovingFactory.class, remap = false)
public class MixinSmartMovingFactory {

    @Inject(method = "doGetOtherSmartMoving", at = @At("HEAD"), cancellable = true)
    private void previewNPEFix(CallbackInfoReturnable cir) {
        if (Minecraft.getMinecraft().theWorld == null) {
            cir.cancel();
        }
    }

}
