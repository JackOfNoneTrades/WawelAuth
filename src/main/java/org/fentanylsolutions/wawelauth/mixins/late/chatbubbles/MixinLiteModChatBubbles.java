package org.fentanylsolutions.wawelauth.mixins.late.chatbubbles;

import org.fentanylsolutions.wawelauth.client.fakeworld.PreviewEntityRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent the LiteLoader build of Chat Bubbles from rendering inside account previews. */
@Pseudo
@Mixin(targets = "com.mamiyaotaru.chatbubbles.litemod.LiteModChatBubbles", remap = false)
public abstract class MixinLiteModChatBubbles {

    @Inject(method = "doRender", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wawelauth$skipAccountPreview(CallbackInfo ci) {
        if (PreviewEntityRenderContext.isRenderingInGui) {
            ci.cancel();
        }
    }
}
