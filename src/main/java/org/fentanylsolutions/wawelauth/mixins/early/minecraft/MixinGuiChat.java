package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.fentanylsolutions.wawelauth.client.ClipboardClickEvent;
import org.fentanylsolutions.wawelauth.client.ClipboardHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiChat.class)
public abstract class MixinGuiChat {

    @Redirect(
        method = "mouseClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ChatStyle;getChatClickEvent()Lnet/minecraft/event/ClickEvent;"))
    private ClickEvent wawelauth$resolveLocalClipboardCopy(ChatStyle style) {
        ClickEvent event = style.getChatClickEvent();
        if (event instanceof ClipboardClickEvent clipboardEvent && !GuiScreen.isShiftKeyDown()) {
            ClipboardHelper
                .copyToClipboard(clipboardEvent.getCopyText(), EnumChatFormatting.GREEN + "Copied to clipboard.");
            // Hide the event from vanilla so nothing is sent or suggested.
            return null;
        }
        return event;
    }
}
