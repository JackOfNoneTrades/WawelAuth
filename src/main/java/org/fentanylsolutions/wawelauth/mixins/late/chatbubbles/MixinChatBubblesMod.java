package org.fentanylsolutions.wawelauth.mixins.late.chatbubbles;

import net.minecraftforge.client.event.RenderPlayerEvent;

import org.fentanylsolutions.wawelauth.client.gui.PlayerPreviewEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent Chat Bubbles from treating account-preview entities as players in the active world. */
@Pseudo
@Mixin(targets = "com.mamiyaotaru.chatbubbles.ChatBubblesMod", remap = false)
public abstract class MixinChatBubblesMod {

    @Inject(method = "onRenderPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$skipAccountPreview(RenderPlayerEvent.Pre event, CallbackInfo ci) {
        if (event.entity instanceof PlayerPreviewEntity) {
            ci.cancel();
        }
    }
}
