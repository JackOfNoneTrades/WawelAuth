package org.fentanylsolutions.wawelauth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public final class ClipboardHelper {

    private ClipboardHelper() {}

    /**
     * Shows a chat line with the text and a click-to-copy action. The clipboard
     * is only written when the player clicks, so servers cannot write it silently.
     */
    public static void showCopyPrompt(String text, String description) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.func_152344_a(() -> {
            if (minecraft.thePlayer == null) {
                return;
            }
            ChatComponentText token = new ChatComponentText(text);
            ChatStyle style = token.getChatStyle();
            style.setColor(EnumChatFormatting.AQUA);
            style.setChatClickEvent(new ClipboardClickEvent(text));
            style
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("Click to copy")));
            ChatComponentText hint = new ChatComponentText(" (click to copy)");
            hint.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            IChatComponent line = new ChatComponentText(description.isEmpty() ? "" : description + " ");
            line.appendSibling(token)
                .appendSibling(hint);
            minecraft.thePlayer.addChatMessage(line);
        });
    }

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
