package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler;
import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.eigenraven.lwjgl3ify.api.InputEvents;

/**
 * Keeps the parent GUI "live" while a ModularUI screen is open.
 */
@SideOnly(Side.CLIENT)
public abstract class ParentAwareModularScreen extends CustomModularScreen {

    private static final String LWJGL3IFY_MOD_ID = "lwjgl3ify";
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
        if (!drawCustomBackdrop()) {
            drawParentScreen();
        }
        super.drawScreen();
    }

    protected boolean drawCustomBackdrop() {
        return false;
    }

    @Override
    public boolean onKeyPressed(char typedChar, int keyCode) {
        if (super.onKeyPressed(typedChar, keyCode)) {
            return true;
        }

        return closeFromEscape(keyCode);
    }

    @Optional.Method(modid = LWJGL3IFY_MOD_ID)
    @Override
    public void onKeyEvent(InputEvents.KeyEvent event) {
        if (dispatchLwjgl3ifyKeyEvent(event)) {
            return;
        }

        if (event.action == InputEvents.KeyAction.PRESSED) {
            scheduleCloseFromEscape(event.lwjgl2KeyCode);
        }
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

    @Optional.Method(modid = LWJGL3IFY_MOD_ID)
    private boolean dispatchLwjgl3ifyKeyEvent(InputEvents.KeyEvent event) {
        for (ModularPanel panel : getPanelManager().getOpenPanels()) {
            if (panel.onKeyEvent(event)) {
                return true;
            }
            if (panel.disablePanelsBelow()) {
                break;
            }
        }
        return false;
    }

    private boolean closeFromEscape(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        if (keyCode != Keyboard.KEY_ESCAPE || mc.theWorld != null || !isActive()) {
            return false;
        }

        if (getContext().hasDraggable()) {
            getContext().dropDraggable(true);
        } else {
            getPanelManager().closeTopPanel();
        }
        return true;
    }

    @Optional.Method(modid = LWJGL3IFY_MOD_ID)
    private void scheduleCloseFromEscape(int keyCode) {
        if (keyCode != Keyboard.KEY_ESCAPE) {
            return;
        }
        GuiTransitionScheduler.nextTick(() -> closeFromEscape(keyCode));
    }
}
