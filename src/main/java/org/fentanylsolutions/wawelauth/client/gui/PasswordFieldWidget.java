package org.fentanylsolutions.wawelauth.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.widget.IFocusedWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.TextFieldTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.textfield.TextFieldHandler;
import com.cleanroommc.modularui.widgets.textfield.TextFieldRenderer;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PasswordFieldWidget extends TextFieldWidget {

    private final PasswordTextFieldRenderer passwordRenderer;
    private Runnable onEnterPressed;
    private int clipRightPadding = 0;

    public PasswordFieldWidget() {
        super();
        this.passwordRenderer = new PasswordTextFieldRenderer(this.handler);
        this.renderer = this.passwordRenderer;
        this.handler.setRenderer(this.passwordRenderer);
        setPasswordHidden(true);
    }

    public PasswordFieldWidget setPasswordHidden(boolean hidden) {
        this.passwordRenderer.setPasswordHidden(hidden);
        return this;
    }

    public PasswordFieldWidget togglePasswordVisibility() {
        this.passwordRenderer.setPasswordHidden(!this.passwordRenderer.isPasswordHidden());
        return this;
    }

    public boolean isPasswordHidden() {
        return this.passwordRenderer.isPasswordHidden();
    }

    public PasswordFieldWidget onEnterPressed(Runnable callback) {
        this.onEnterPressed = callback;
        return this;
    }

    public PasswordFieldWidget setClipRightPadding(int clipRightPadding) {
        this.clipRightPadding = Math.max(0, clipRightPadding);
        return this;
    }

    @Override
    public @NotNull Result onKeyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN && isFocused()) {
            if (onEnterPressed != null) onEnterPressed.run();
            return Result.SUCCESS;
        }
        if (keyCode == Keyboard.KEY_TAB && isFocused()) {
            cycleFocus(Interactable.hasShiftDown());
            return Result.SUCCESS;
        }
        return super.onKeyPressed(character, keyCode);
    }

    private void cycleFocus(boolean reverse) {
        List<IFocusedWidget> focusable = new ArrayList<>();
        TabTextFieldWidget.collectFocusable(getPanel(), focusable);
        int idx = focusable.indexOf(this);
        if (idx < 0 || focusable.size() < 2) return;
        int next = reverse ? (idx - 1 + focusable.size()) % focusable.size() : (idx + 1) % focusable.size();
        getContext().focus(focusable.get(next));
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean transformed) {
        if (transformed) {
            WidgetThemeEntry<TextFieldTheme> entry = getWidgetTheme(getPanel().getTheme(), TextFieldTheme.class);
            TextFieldTheme widgetTheme = entry.getTheme();
            this.renderer.setColor(this.textColor != null ? this.textColor : widgetTheme.getTextColor());
            this.renderer.setCursorColor(this.textColor != null ? this.textColor : widgetTheme.getTextColor());
            this.renderer.setMarkedColor(this.markedColor != null ? this.markedColor : widgetTheme.getMarkedColor());
            setupDrawText(context, widgetTheme);
            drawText(context, widgetTheme);
            return;
        }

        int clipWidth = Math.max(0, getArea().w() - 2 - this.clipRightPadding);
        Stencil.apply(1, 1, clipWidth, getArea().h() - 2, context);
    }

    private static final class PasswordTextFieldRenderer extends TextFieldRenderer {

        private static final char MASK_CHAR = '●';
        private boolean passwordHidden = true;

        private PasswordTextFieldRenderer(TextFieldHandler handler) {
            super(handler);
        }

        private void setPasswordHidden(boolean passwordHidden) {
            this.passwordHidden = passwordHidden;
        }

        private boolean isPasswordHidden() {
            return this.passwordHidden;
        }

        @Override
        public void draw(List<String> lines) {
            super.draw(maskLines(lines));
        }

        @Override
        public List<TextRenderer.Line> measureLines(List<String> lines) {
            return super.measureLines(maskLines(lines));
        }

        @Override
        public java.awt.Point getCursorPos(List<String> lines, int x, int y) {
            return super.getCursorPos(maskLines(lines), x, y);
        }

        private List<String> maskLines(List<String> lines) {
            if (!passwordHidden || lines == null || lines.isEmpty() || this.handler.isTextEmpty()) {
                return lines;
            }
            List<String> masked = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line == null || line.isEmpty()) {
                    masked.add("");
                    continue;
                }
                char[] chars = new char[line.length()];
                java.util.Arrays.fill(chars, MASK_CHAR);
                masked.add(new String(chars));
            }
            return masked;
        }
    }
}
