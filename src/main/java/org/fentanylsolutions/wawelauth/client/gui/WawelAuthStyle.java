package org.fentanylsolutions.wawelauth.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.Gui;

import org.fentanylsolutions.fentlib.gui.sodiumgui.SodiumGuiTheme;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Small ModularUI facade for the Sodium settings-screen visual language.
 */
@SideOnly(Side.CLIENT)
final class WawelAuthStyle {

    static final int THEME = 0xFF94E4D3;
    static final int THEME_LIGHTER = 0xFFCCFDEE;
    static final int THEME_DARKER = 0xFF7A9E9E;
    static final int TEXT_PRIMARY = 0xFFFFFFFF;
    static final int TEXT_SECONDARY = 0xFFAAAAAA;
    static final int TEXT_MUTED = THEME_DARKER;

    static final int BACKGROUND_LIGHT = 0x30000000;
    static final int BACKGROUND_MEDIUM = 0x50000000;
    static final int BACKGROUND_HOVER = 0xE0000000;
    static final int BACKGROUND_OVERLAY = 0xEA000000;
    static final int BACKGROUND_DEFAULT = 0x78000000;
    static final int BACKGROUND_DARKER = 0xB0000000;
    static final int BACKGROUND_HIGHLIGHT = 0x08FFFFFF;
    static final int BUTTON_BORDER = 0x8000FFEE;

    static final int PANEL = BACKGROUND_LIGHT;
    static final int PANEL_INSET = BACKGROUND_MEDIUM;
    static final int BUTTON_IDLE = BACKGROUND_DEFAULT;
    static final int BUTTON_HOVER = 0xFF000000;
    static final int BUTTON_DISABLED = BACKGROUND_LIGHT;
    static final int ROW_IDLE = BACKGROUND_LIGHT;
    static final int ROW_SELECTED = BACKGROUND_DEFAULT;
    static final int ROW_HOVER = BACKGROUND_HOVER;
    static final int FIELD = 0x90000000;
    static final int FIELD_HOVER = BACKGROUND_DARKER;
    static final int SCROLLBAR_TRACK = 0x96323232;
    static final int SCROLLBAR_THUMB = 0x96646464;
    static final int FINGERPRINT = THEME_LIGHTER;
    static final int WARNING = 0xFFFF8C30;
    static final int DANGER = 0xFFFF5555;
    static final int SUCCESS = 0xFF55FF55;

    private static final int SCROLLBAR_WIDTH = 7;
    private static final int ROW_SELECTION_BAR_WIDTH = 3;

    private WawelAuthStyle() {}

    static int accent() {
        return ensureAlpha(
            SodiumGuiTheme.getDefault()
                .getAccentColor());
    }

    static IDrawable rect(int color) {
        return new Rectangle().color(color);
    }

    static IDrawable verticalGradient(int colorTop, int colorBottom) {
        return new Rectangle().verticalGradient(colorTop, colorBottom);
    }

    static IDrawable panelBackground() {
        return verticalGradient(BACKGROUND_LIGHT, BACKGROUND_DEFAULT);
    }

    static IDrawable listBackground() {
        return verticalGradient(BACKGROUND_LIGHT, BACKGROUND_DEFAULT);
    }

    static IDrawable flat(int color, BooleanSupplier selected) {
        return flat(color, selected, WawelAuthStyle::accent);
    }

    static IDrawable flat(int color, BooleanSupplier selected, IntSupplier accentColor) {
        return (context, x, y, width, height, widgetTheme) -> {
            Gui.drawRect(x, y, x + width, y + height, color);
            if (selected != null && selected.getAsBoolean()) {
                Gui.drawRect(x, y + height - 1, x + width, y + height, accentColor.getAsInt());
            }
        };
    }

    static IDrawable selectableRow(int idleColor, BooleanSupplier selected, IntSupplier accentColor) {
        return (context, x, y, width, height, widgetTheme) -> {
            int color = selected != null && selected.getAsBoolean() ? ROW_SELECTED : idleColor;
            Gui.drawRect(x, y, x + width, y + height, color);
            if (selected != null && selected.getAsBoolean()) {
                Gui.drawRect(x + width - ROW_SELECTION_BAR_WIDTH, y, x + width, y + height, accentColor.getAsInt());
            }
        };
    }

    static IDrawable dynamicRect(IntSupplier color) {
        return new IDrawable() {

            @Override
            public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                Gui.drawRect(x, y, x + width, y + height, color.getAsInt());
            }
        };
    }

    static ButtonWidget<?> button(ButtonWidget<?> button) {
        button.background(rect(BUTTON_IDLE))
            .hoverBackground(rect(BUTTON_HOVER));
        return button;
    }

    static ButtonWidget<?> button(ButtonWidget<?> button, BooleanSupplier selected) {
        button.background(flat(BUTTON_IDLE, selected))
            .hoverBackground(flat(BUTTON_HOVER, selected));
        return button;
    }

    static ButtonWidget<?> rowButton(ButtonWidget<?> button, BooleanSupplier selected) {
        button.background(selectableRow(ROW_IDLE, selected, WawelAuthStyle::accent))
            .hoverBackground(selectableRow(ROW_HOVER, selected, WawelAuthStyle::accent));
        return button;
    }

    static ButtonWidget<?> iconButton(ButtonWidget<?> button) {
        button.disableThemeBackground(true)
            .disableHoverThemeBackground(true);
        return button;
    }

    static TextFieldWidget textField(TextFieldWidget field) {
        field.background(rect(FIELD))
            .hoverBackground(rect(FIELD_HOVER))
            .setTextColor(TEXT_PRIMARY)
            .hintColor(TEXT_SECONDARY)
            .setMarkedColor(accent());
        return field;
    }

    static void styleList(ListWidget<?, ?> list) {
        VerticalScrollData scrollData = new VerticalScrollData(false, SCROLLBAR_WIDTH);
        scrollData.texture(rect(SCROLLBAR_THUMB));
        list.scrollDirection(scrollData)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .showScrollShadows(false);
        list.getScrollArea()
            .setScrollBarBackgroundColor(SCROLLBAR_TRACK);
    }

    private static int ensureAlpha(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }
}
