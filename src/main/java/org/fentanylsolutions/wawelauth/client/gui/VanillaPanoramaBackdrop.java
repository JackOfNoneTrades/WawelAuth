package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;

import org.fentanylsolutions.wawelauth.mixins.early.minecraft.AccessorGuiMainMenu;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class VanillaPanoramaBackdrop {

    private final GuiMainMenu mainMenu;
    private int scaledWidth;
    private int scaledHeight;

    public VanillaPanoramaBackdrop(Minecraft mc) {
        mainMenu = new GuiMainMenu();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        mainMenu.setWorldAndResolution(mc, width, height);
        mainMenu.initGui();
        scaledWidth = width;
        scaledHeight = height;
    }

    void draw(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        ensureInitialized(mc);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        ((AccessorGuiMainMenu) (mainMenu)).invokeRenderSkybox(0, 0, partialTicks);
        GL11.glPopAttrib();
    }

    void update() {
        mainMenu.updateScreen();
    }

    private void ensureInitialized(Minecraft mc) {
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        if (width == scaledWidth && height == scaledHeight) {
            return;
        }
        mainMenu.setWorldAndResolution(mc, width, height);
        scaledWidth = width;
        scaledHeight = height;
    }

}
