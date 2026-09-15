package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.config.SkinLayersConfig;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

public class GuiSkinCustomization extends GuiScreen {

    protected GuiScreen parentScreen;

    public GuiSkinCustomization(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        this.buttonList.add(new GuiButton(-1, this.width / 2 - 100, this.height / 6 + 168, I18n.format("gui.done")));

        for (SkinLayersHelper.EnumPlayerModelParts part : SkinLayersHelper.EnumPlayerModelParts.values()) {
            int ordinal = part.ordinal();
            GuiCustomToggleButton toggle = new GuiCustomToggleButton(
                ordinal + 1,
                this.width / 2 - 155 + ((ordinal) % 2) * 160,
                this.height / 6 + 24 * ((ordinal) >> 1),
                150,
                20,
                I18n.format(part.partName()),
                part.stateS()
                    .get());
            if (part == SkinLayersHelper.EnumPlayerModelParts.CAPE) toggle.updateTextDual();
            this.buttonList.add(toggle);
        }
    }

    @Override
    public void onGuiClosed() {
        ConfigurationManager.save(SkinLayersConfig.class);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("wawelauth.gui.skincustomization.title"),
            this.width / 2,
            20,
            16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof GuiCustomToggleButton toggle) {
            SkinLayersHelper.EnumPlayerModelParts part = SkinLayersHelper.EnumPlayerModelParts
                .fromOrdinal(button.id - 1);
            if (part != null) {
                if (part == SkinLayersHelper.EnumPlayerModelParts.CAPE) toggle.toggleDual();
                else toggle.toggle();
                part.stateC()
                    .accept(toggle.get());
            }
        } else if (button.id == -1) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

}
