package org.fentanylsolutions.wawelauth.client;

import net.minecraft.event.ClickEvent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Click event that copies text to the clipboard, resolved entirely client side
 * by MixinGuiChat. Components deserialized from the network can only carry
 * plain ClickEvent instances, so a server cannot forge one. The
 * SUGGEST_COMMAND action is a harmless fallback if another mod replaces chat
 * click handling: the text is inserted into the chat input instead.
 */
@SideOnly(Side.CLIENT)
public final class ClipboardClickEvent extends ClickEvent {

    private final String copyText;

    public ClipboardClickEvent(String copyText) {
        super(Action.SUGGEST_COMMAND, copyText);
        this.copyText = copyText;
    }

    public String getCopyText() {
        return copyText;
    }
}
