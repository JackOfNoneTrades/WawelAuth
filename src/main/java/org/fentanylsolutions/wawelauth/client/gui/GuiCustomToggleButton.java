package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

public class GuiCustomToggleButton extends GuiButton {

    private final String prefix;
    private boolean value;

    public GuiCustomToggleButton(int id, int x, int y, int width, int height, String prefix, boolean initialValue) {
        super(id, x, y, width, height, "");
        this.prefix = prefix;
        this.value = initialValue;
        updateText();
    }

    public void toggle() {
        this.value = !this.value;
        updateText();
    }

    public boolean getEnabled() {
        return this.value;
    }

    private void updateText() {
        this.displayString = this.prefix + ": " + (this.value ? I18n.format("options.on") : I18n.format("options.off"));
    }

}
