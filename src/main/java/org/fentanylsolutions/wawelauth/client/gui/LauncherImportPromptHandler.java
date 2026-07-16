package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;

import org.fentanylsolutions.wawelauth.wawelclient.LauncherAccountImport;
import org.fentanylsolutions.wawelauth.wawelclient.LauncherAccountImport.Candidate;

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

        LauncherAccountImport accountImport = LauncherAccountImport.instance();
        if (accountImport == null) {
            return;
        }
        Candidate candidate = accountImport.getPendingImport();
        if (candidate == null) {
            return;
        }

        prompted = true;
        LauncherImportPromptScreen.open(candidate);
    }
}
