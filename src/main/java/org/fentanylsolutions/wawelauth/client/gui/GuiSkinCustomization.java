package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.fentanylsolutions.wawelauth.packet.PacketHandler;
import org.fentanylsolutions.wawelauth.packet.PacketUpdateSkinLayers;

public class GuiSkinCustomization extends GuiScreen {

    // todo: I18n | config reading / writing

    protected GuiScreen parentScreen;

    public boolean showCape = true;
    public boolean showJacket = true;
    public boolean showLeftSleeve = true;
    public boolean showRightSleeve = true;
    public boolean showLeftPants = true;
    public boolean showRightPants = true;
    public boolean showHat = true;

    public GuiSkinCustomization(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 6 + 168, I18n.format("gui.done")));

        String[] labels = { "Cape", "Jacket", "Left Sleeve", "Right Sleeve", "Left Pants", "Right Pants", "Hat" };
        boolean[] values = { showCape, showJacket, showLeftSleeve, showRightSleeve, showLeftPants, showRightPants,
            showHat };

        for (int i = 0; i < 7; i++) {
            this.buttonList.add(
                new GuiCustomToggleButton(
                    i + 1,
                    this.width / 2 - 155 + i % 2 * 160,
                    this.height / 6 + 24 * (i >> 1),
                    150,
                    20,
                    labels[i],
                    values[i]));
        }

    }

    @Override
    public void onGuiClosed() {
        byte mask = 0;
        if (showCape) mask |= (1 << 0);
        if (showJacket) mask |= (1 << 1);
        if (showLeftSleeve) mask |= (1 << 2);
        if (showRightSleeve) mask |= (1 << 3);
        if (showLeftPants) mask |= (1 << 4);
        if (showRightPants) mask |= (1 << 5);
        if (showHat) mask |= (1 << 6);

        PacketHandler.sendToServer(new PacketUpdateSkinLayers(mask));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "Skin Customization", this.width / 2, 20, 16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof GuiCustomToggleButton toggle) {
            toggle.toggle();
            switch (button.id) {
                case 1:
                    showCape = toggle.getValue();
                    break;
                case 2:
                    showJacket = toggle.getValue();
                    break;
                case 3:
                    showLeftSleeve = toggle.getValue();
                    break;
                case 4:
                    showRightSleeve = toggle.getValue();
                    break;
                case 5:
                    showLeftPants = toggle.getValue();
                    break;
                case 6:
                    showRightPants = toggle.getValue();
                    break;
                case 7:
                    showHat = toggle.getValue();
                    break;
            }
        } else if (button.id == 0) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

}
