package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class GuiSkinCustomizationOptions {

    // todo: I18n

    private static final int BUTTON_ID = 456987;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiOptions) {
            int posX = event.gui.width / 2 - 155;
            int posY = event.gui.height / 6 + 48 - 6;

            event.buttonList.add(new GuiButton(BUTTON_ID, posX, posY, 150, 20, "Skin Customization..."));
        }
    }

    @SubscribeEvent
    public void onClick(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.gui instanceof GuiOptions) {
            if (event.button.id == BUTTON_ID) {
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiSkinCustomization(event.gui));
            }
        }
    }

}
