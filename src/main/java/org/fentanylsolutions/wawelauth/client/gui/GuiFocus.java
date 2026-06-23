package org.fentanylsolutions.wawelauth.client.gui;

import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler;

import com.cleanroommc.modularui.api.widget.IFocusedWidget;
import com.cleanroommc.modularui.api.widget.IWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class GuiFocus {

    private GuiFocus() {}

    static void focusAfterOpen(IFocusedWidget widget) {
        if (widget == null) {
            return;
        }
        GuiTransitionScheduler.nextTick(() -> GuiTransitionScheduler.nextTick(() -> focusNow(widget)));
    }

    private static void focusNow(IFocusedWidget widget) {
        if (!(widget instanceof IWidget)) {
            return;
        }
        IWidget uiWidget = (IWidget) widget;
        if (!uiWidget.isValid() || !uiWidget.isEnabled()) {
            return;
        }
        uiWidget.getContext()
            .focus(widget);
    }
}
