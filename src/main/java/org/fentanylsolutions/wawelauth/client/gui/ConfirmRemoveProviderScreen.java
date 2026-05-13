package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

public class ConfirmRemoveProviderScreen extends GuiYesNo {

    private final GuiScreen returnTo;
    private final String providerName;
    private final String hintLine;

    public ConfirmRemoveProviderScreen(GuiScreen returnTo, String providerName, String title, String subtitle,
        String hintLine) {
        super((result, id) -> {}, title, subtitle, 0);
        this.returnTo = returnTo;
        this.providerName = providerName;
        this.hintLine = hintLine;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            WawelClient client = WawelClient.instance();
            if (client != null) {
                try {
                    client.getProviderRegistry()
                        .removeProvider(providerName);
                } catch (Exception e) {
                    WawelAuth.LOG.warn("Failed to remove local provider '{}': {}", providerName, e.getMessage());
                }
            }
        }
        this.mc.displayGuiScreen(returnTo);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRendererObj, this.hintLine, this.width / 2, 110, 0xAAAAAA);
    }
}
