package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Works around a ModularUI2 + LWJGL3IFY regression where the inventory key can
 * close the top panel before the later text-input event delivers the character.
 */
@SideOnly(Side.CLIENT)
public class SafeTextFieldWidget extends TextFieldWidget {

    @Override
    public @NotNull Result onKeyPressed(char character, int keyCode) {
        Result result = super.onKeyPressed(character, keyCode);
        if (result == Result.ACCEPT && isFocused()
            && ModularUI.Mods.LWJGL3IFY.isLoaded()
            && keyCode == Minecraft.getMinecraft().gameSettings.keyBindInventory.getKeyCode()) {
            return Result.SUCCESS;
        }
        return result;
    }
}
