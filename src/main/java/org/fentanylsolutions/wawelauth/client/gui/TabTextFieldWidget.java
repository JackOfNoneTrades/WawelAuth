package org.fentanylsolutions.wawelauth.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.widget.IFocusedWidget;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * TextFieldWidget that cycles focus to the next/previous input on Tab/Shift+Tab.
 */
@SideOnly(Side.CLIENT)
public class TabTextFieldWidget extends TextFieldWidget {

    private Runnable onEnterPressed;

    public TabTextFieldWidget onEnterPressed(Runnable callback) {
        this.onEnterPressed = callback;
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
        collectFocusable(getPanel(), focusable);
        int idx = focusable.indexOf(this);
        if (idx < 0 || focusable.size() < 2) return;
        int next = reverse ? (idx - 1 + focusable.size()) % focusable.size() : (idx + 1) % focusable.size();
        getContext().focus(focusable.get(next));
    }

    static void collectFocusable(IWidget widget, List<IFocusedWidget> result) {
        if (!widget.isEnabled()) return;
        if (widget instanceof IFocusedWidget) {
            result.add((IFocusedWidget) widget);
        }
        for (IWidget child : widget.getChildren()) {
            collectFocusable(child, result);
        }
    }
}
