package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AuthButton extends GuiButton {

    private static final String DEFAULT_LABEL_KEY = "wawelauth.gui.multiplayer.auth";
    private static final String DEFAULT_FALLBACK_KEY = "wawelauth.gui.multiplayer.auth_fallback";
    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(
        "wawelauth",
        "textures/gui/logo_2_outline.png");
    private static final int ICON_SIZE = 14;
    private static final int ICON_TEXT_GAP = 4;
    private static final int HORIZONTAL_PADDING = 6;
    private final String labelKey;
    private final String fallbackKey;

    public AuthButton(int id, int x, int y, int height) {
        this(id, x, y, height, DEFAULT_LABEL_KEY, DEFAULT_FALLBACK_KEY);
    }

    public AuthButton(int id, int x, int y, int height, String labelKey, String fallbackKey) {
        super(
            id,
            x,
            y,
            preferredWidth(Minecraft.getMinecraft().fontRenderer, labelKey, fallbackKey),
            height,
            translatedLabel(labelKey, fallbackKey));
        this.labelKey = labelKey;
        this.fallbackKey = fallbackKey;
    }

    public static int preferredWidth(FontRenderer fontRenderer) {
        return preferredWidth(fontRenderer, DEFAULT_LABEL_KEY, DEFAULT_FALLBACK_KEY);
    }

    public static int preferredWidth(FontRenderer fontRenderer, String labelKey, String fallbackKey) {
        String text = translatedLabel(labelKey, fallbackKey);
        int textWidth = fontRenderer != null ? fontRenderer.getStringWidth(text) : text.length() * 6;
        return HORIZONTAL_PADDING + ICON_SIZE + ICON_TEXT_GAP + textWidth + HORIZONTAL_PADDING;
    }

    private static String translatedLabel(String labelKey, String fallbackKey) {
        String translated = GuiText.tr(labelKey);
        return translated != null && !translated.trim()
            .isEmpty() ? translated : GuiText.tr(fallbackKey);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        String text = translatedLabel(this.labelKey, this.fallbackKey);
        this.displayString = "";
        super.drawButton(minecraft, mouseX, mouseY);
        this.displayString = text;

        if (!this.visible) {
            return;
        }

        int textWidth = minecraft.fontRenderer.getStringWidth(text);
        int contentWidth = ICON_SIZE + ICON_TEXT_GAP + textWidth;
        int contentX = this.xPosition + Math.max(0, (this.width - contentWidth) / 2);
        int iconX = contentX;
        int iconY = this.yPosition + (this.height - ICON_SIZE) / 2;
        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int textY = this.yPosition + (this.height - 8) / 2;

        minecraft.getTextureManager()
            .bindTexture(ICON_TEXTURE);
        float brightness = this.enabled ? 1.0F : 0.5F;
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        Gui.func_146110_a(iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        int color = 14737632;
        boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;
        if (!this.enabled) {
            color = 10526880;
        } else if (hovered) {
            color = 16777120;
        }

        this.drawString(minecraft.fontRenderer, text, textX, textY, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
