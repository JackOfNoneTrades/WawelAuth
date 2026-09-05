package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;

public class GuiCustomToggleButton extends GuiButton {

    private final String prefix;
    private SkinLayersHelper.PartState value;

    public GuiCustomToggleButton(int id, int x, int y, int width, int height, String prefix,
        SkinLayersHelper.PartState initialValue) {
        super(id, x, y, width, height, "");
        this.prefix = prefix;
        this.value = initialValue;
        updateText();
    }

    public void toggle() {
        this.value = this.value.next();
        updateText();
    }

    public void toggleDual() {
        this.value = this.value.isDisabled() ? SkinLayersHelper.PartState.FLAT : SkinLayersHelper.PartState.DISABLED;
        updateTextDual();
    }

    public SkinLayersHelper.PartState get() {
        return this.value;
    }

    public void updateText() {
        switch (this.value) {
            case DISABLED -> this.displayString = this.prefix + ": "
                + EnumChatFormatting.RED
                + I18n.format("wawelauth.gui.skincustomization.disabled");
            case FLAT -> this.displayString = this.prefix + ": "
                + EnumChatFormatting.GREEN
                + I18n.format("wawelauth.gui.skincustomization.flat");
            case VOLUMETRIC -> this.displayString = this.prefix + ": "
                + EnumChatFormatting.BOLD
                + EnumChatFormatting.GOLD
                + I18n.format("wawelauth.gui.skincustomization.volumetric");
        }
    }

    public void updateTextDual() {
        switch (this.value) {
            case DISABLED -> this.displayString = this.prefix + ": "
                + EnumChatFormatting.RED
                + I18n.format("wawelauth.gui.skincustomization.disabled");
            case FLAT -> this.displayString = this.prefix + ": "
                + EnumChatFormatting.BOLD
                + EnumChatFormatting.GOLD
                + I18n.format("wawelauth.gui.skincustomization.enabled");
        }
    }

}
