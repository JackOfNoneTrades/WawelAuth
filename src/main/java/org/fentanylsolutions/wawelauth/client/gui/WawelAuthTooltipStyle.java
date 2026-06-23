package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.screen.RichTooltipEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class WawelAuthTooltipStyle {

    private static final WawelAuthTooltipStyle INSTANCE = new WawelAuthTooltipStyle();
    private static final int STRIP_HEIGHT = 1;
    private static final int BACKGROUND_COLOR = WawelAuthStyle.BACKGROUND_OVERLAY;
    private static volatile boolean registered;

    private WawelAuthTooltipStyle() {}

    public static synchronized void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent
    public void onTooltipColor(RichTooltipEvent.Color event) {
        if (!isWawelAuthScreen()) {
            return;
        }

        event.setBackground(BACKGROUND_COLOR);
        event.setBorderStart(BACKGROUND_COLOR);
        event.setBorderEnd(BACKGROUND_COLOR);
    }

    @SubscribeEvent
    public void onTooltipPostBackground(RichTooltipEvent.PostBackground event) {
        if (!isWawelAuthScreen()) {
            return;
        }

        int x = event.getX() - 3;
        int y = event.getY() + event.getHeight() + 3;
        Gui.drawRect(x, y, event.getX() + event.getWidth() + 3, y + STRIP_HEIGHT, WawelAuthStyle.accent());
    }

    private static boolean isWawelAuthScreen() {
        GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
        return currentScreen instanceof IMuiScreen
            && ((IMuiScreen) currentScreen).getScreen() instanceof ParentAwareModularScreen;
    }
}
