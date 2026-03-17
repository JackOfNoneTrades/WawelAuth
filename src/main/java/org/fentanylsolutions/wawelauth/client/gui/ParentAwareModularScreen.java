package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.screen.CustomModularScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Keeps the parent GUI "live" while a ModularUI screen is open.
 */
@SideOnly(Side.CLIENT)
public abstract class ParentAwareModularScreen extends CustomModularScreen {

    private static final int OFFSCREEN_MOUSE_X = -10_000;
    private static final int OFFSCREEN_MOUSE_Y = -10_000;

    protected ParentAwareModularScreen(String owner) {
        super(owner);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        GuiScreen parent = getContext().getParentScreen();
        if (parent == null || parent instanceof IMuiScreen) {
            return;
        }
        parent.updateScreen();
    }

    @Override
    public void onResize(int width, int height) {
        super.onResize(width, height);
        GuiScreen parent = getContext().getParentScreen();
        if (parent == null || parent instanceof IMuiScreen) {
            return;
        }
        parent.setWorldAndResolution(Minecraft.getMinecraft(), width, height);
    }

    @Override
    public void drawScreen() {
        drawParentScreen();
        super.drawScreen();
    }

    private void drawParentScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen parent = getContext().getParentScreen();
        if (parent == null || parent == mc.currentScreen || parent instanceof IMuiScreen) {
            return;
        }

        // Render the parent as a dynamic backdrop, but with an off-screen cursor so
        // hover state, tooltips, and any mouse-driven visuals are suppressed.
        parent.drawScreen(OFFSCREEN_MOUSE_X, OFFSCREEN_MOUSE_Y, getContext().getPartialTicks());
    }
}
