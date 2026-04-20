package org.fentanylsolutions.wawelauth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;

public final class ClipboardHelper {

    private ClipboardHelper() {}

    public static void copyToClipboard(String text, String description) {
        final String clipboardText = text == null ? "" : text;
        final String message = description == null ? "" : description;

        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.func_152344_a(() -> { // Minecraft.addScheduledTask
            if (!clipboardText.isEmpty()) {
                GuiScreen.setClipboardString(clipboardText);
            }
            if (!message.isEmpty() && minecraft.thePlayer != null) {
                minecraft.thePlayer.addChatMessage(new ChatComponentText(message));
            }
        });
    }
}
