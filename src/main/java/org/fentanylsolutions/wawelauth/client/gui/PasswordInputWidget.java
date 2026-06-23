package org.fentanylsolutions.wawelauth.client.gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.ColorType;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PasswordInputWidget extends ParentWidget<PasswordInputWidget> {

    private static final int PASSWORD_EYE_ICON_SOURCE_SIZE = 24;
    private static final int PASSWORD_EYE_ICON_SHEET_WIDTH = PASSWORD_EYE_ICON_SOURCE_SIZE * 2;
    private static final int PASSWORD_EYE_ICON_SIZE = 12;
    private static final int PASSWORD_EYE_BUTTON_SIZE = 12;
    private static final ColorType PASSWORD_EYE_ICON_COLOR_TYPE = new ColorType(
        "wawelauth:password_eye_icon",
        theme -> WawelAuthStyle.TEXT_BUTTON_IDLE);
    private static final ColorType PASSWORD_EYE_ICON_HOVER_COLOR_TYPE = new ColorType(
        "wawelauth:password_eye_icon_hover",
        theme -> WawelAuthStyle.TEXT_PRIMARY);
    private static final IDrawable EYE_OPEN_ICON = centeredIcon(passwordEyeIcon(0, PASSWORD_EYE_ICON_COLOR_TYPE));
    private static final IDrawable EYE_OPEN_ICON_HOVER = centeredIcon(
        passwordEyeIcon(0, PASSWORD_EYE_ICON_HOVER_COLOR_TYPE));
    private static final IDrawable EYE_CLOSED_ICON = centeredIcon(passwordEyeIcon(1, PASSWORD_EYE_ICON_COLOR_TYPE));
    private static final IDrawable EYE_CLOSED_ICON_HOVER = centeredIcon(
        passwordEyeIcon(1, PASSWORD_EYE_ICON_HOVER_COLOR_TYPE));

    private final PasswordFieldWidget field;
    private final ButtonWidget<?> toggleButton;

    public PasswordInputWidget() {
        this.field = new PasswordFieldWidget();
        this.toggleButton = new ButtonWidget<>();

        this.field.widthRel(1.0f)
            .heightRel(1.0f)
            // Reserve a larger right gutter so long text never collides with the eye button.
            .padding(4, 28, 0, 0);
        this.field.setClipRightPadding(24);

        this.toggleButton.size(PASSWORD_EYE_BUTTON_SIZE, PASSWORD_EYE_BUTTON_SIZE)
            .right(5)
            .top(3)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY)
            .onMousePressed(mouseButton -> {
                this.field.togglePasswordVisibility();
                refreshEyeIcon();
                return true;
            });

        refreshEyeIcon();
        child(this.field);
        child(this.toggleButton);
    }

    private void refreshEyeIcon() {
        IDrawable icon = this.field.isPasswordHidden() ? EYE_CLOSED_ICON : EYE_OPEN_ICON;
        IDrawable hoverIcon = this.field.isPasswordHidden() ? EYE_CLOSED_ICON_HOVER : EYE_OPEN_ICON_HOVER;
        this.toggleButton.overlay(icon)
            .hoverOverlay(hoverIcon);
    }

    private static IDrawable passwordEyeIcon(int index, ColorType colorType) {
        return UITexture.builder()
            .location("wawelauth", "gui/password_eye")
            .imageSize(PASSWORD_EYE_ICON_SHEET_WIDTH, PASSWORD_EYE_ICON_SOURCE_SIZE)
            .colorType(colorType)
            .subAreaXYWH(
                index * PASSWORD_EYE_ICON_SOURCE_SIZE,
                0,
                PASSWORD_EYE_ICON_SOURCE_SIZE,
                PASSWORD_EYE_ICON_SOURCE_SIZE)
            .nonOpaque()
            .name("wawelauth:password_eye_" + index)
            .build();
    }

    private static IDrawable centeredIcon(IDrawable icon) {
        return (context, x, y, width, height, widgetTheme) -> {
            int size = Math.min(PASSWORD_EYE_ICON_SIZE, Math.min(width, height));
            int iconX = x + (width - size) / 2;
            int iconY = y + (height - size) / 2;
            icon.draw(context, iconX, iconY, size, size, widgetTheme);
        };
    }

    public PasswordInputWidget applyWawelAuthStyle() {
        WawelAuthStyle.textField(this.field);
        return this;
    }

    public PasswordInputWidget hintText(String text) {
        this.field.hintText(text);
        return this;
    }

    public PasswordInputWidget setMaxLength(int maxLength) {
        this.field.setMaxLength(maxLength);
        return this;
    }

    public String getText() {
        return this.field.getText();
    }

    public PasswordInputWidget setText(String text) {
        this.field.setText(text == null ? "" : text);
        return this;
    }

    public PasswordInputWidget value(StringValue value) {
        this.field.value(value);
        return this;
    }

    public PasswordInputWidget setPasswordHidden(boolean hidden) {
        this.field.setPasswordHidden(hidden);
        refreshEyeIcon();
        return this;
    }

    public boolean isPasswordHidden() {
        return this.field.isPasswordHidden();
    }

    public PasswordInputWidget onEnterPressed(Runnable callback) {
        this.field.onEnterPressed(callback);
        return this;
    }
}
