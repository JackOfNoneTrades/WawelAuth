package org.fentanylsolutions.wawelauth.mixins.late.SmartMoving;

import net.minecraft.client.Minecraft;
import net.smart.render.SmartRenderRender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmartRenderRender.class, remap = false)
public class MixinSmartRenderRender {

    @Inject(method = "rotatePlayer", at = @At("HEAD"), cancellable = true)
    private void previewNPEFix(CallbackInfo ci) {
        if (Minecraft.getMinecraft().renderViewEntity == null) {
            ci.cancel();
        }
    }

}
