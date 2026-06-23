package org.fentanylsolutions.wawelauth.client.gui;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class VanillaPanoramaBackdrop {

    private final GuiMainMenu mainMenu = new GuiMainMenu();
    private Method renderSkyboxMethod;
    private int scaledWidth = -1;
    private int scaledHeight = -1;
    private boolean unavailable;

    boolean draw(float partialTicks) {
        if (unavailable) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        try {
            ensureInitialized(mc);
            getRenderSkyboxMethod().invoke(mainMenu, 0, 0, partialTicks);
            restoreGuiFramebuffer(mc);
            return true;
        } catch (Exception e) {
            unavailable = true;
            restoreGuiFramebuffer(mc);
            WawelAuth.LOG.warn("[GUI] Failed to draw vanilla panorama backdrop", e);
            return false;
        }
    }

    void update() {
        if (!unavailable) {
            mainMenu.updateScreen();
        }
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

    private Method getRenderSkyboxMethod() {
        if (renderSkyboxMethod == null) {
            renderSkyboxMethod = ReflectionHelper.findMethod(
                GuiMainMenu.class,
                mainMenu,
                new String[] { "renderSkybox", "func_73971_c" },
                int.class,
                int.class,
                float.class);
        }
        return renderSkyboxMethod;
    }

    private void restoreGuiFramebuffer(Minecraft mc) {
        mc.getFramebuffer()
            .bindFramebuffer(true);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        mc.entityRenderer.setupOverlayRendering();
    }
}
