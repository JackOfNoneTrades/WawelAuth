package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;

import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge.LauncherImportCandidate;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Opens the first-time launcher import prompt once the main menu is showing
 * and a launcher session has been detected. Fires at most once per session.
 */
@SideOnly(Side.CLIENT)
public final class LauncherImportPromptHandler {

    private boolean prompted;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || prompted) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiMainMenu)) {
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }
        SessionBridge bridge = client.getSessionBridge();
        if (bridge == null) {
            return;
        }

        LauncherImportCandidate candidate = bridge.getPendingLauncherImport();
        if (candidate == null) {
            return;
        }

        prompted = true;
        LauncherImportPromptScreen.open(candidate);
    }
}
