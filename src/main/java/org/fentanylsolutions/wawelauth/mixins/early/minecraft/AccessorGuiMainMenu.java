package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.gui.GuiMainMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiMainMenu.class)
public interface AccessorGuiMainMenu {

    @Invoker("renderSkybox")
    void invokeRenderSkybox(int p_73971_1_, int p_73971_2_, float p_73971_3_);
}
