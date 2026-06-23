package org.fentanylsolutions.wawelauth.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

public class ConfirmRemoveProviderScreen extends ParentAwareModularScreen {

    private static Pending pending;

    private final Pending data;

    public ConfirmRemoveProviderScreen(GuiScreen returnTo, String providerName, String title, String subtitle,
        String hintLine) {
        this(storePending(returnTo, providerName, title, subtitle, hintLine));
    }

    private ConfirmRemoveProviderScreen(Pending data) {
        super("wawelauth");
        this.data = data;
        pending = null;
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        Pending current = data != null ? data : pending;
        if (current == null) {
            current = new Pending(null, "", "", "", "");
        }
        final Pending screenData = current;

        int panelWidth = 340;
        int panelHeight = 144;
        int textWidthPx = panelWidth - 20;
        List<String> titleLines = wrapText(screenData.title, textWidthPx, 2);
        List<String> subtitleLines = wrapText(screenData.subtitle, textWidthPx, 2);
        List<String> hintLines = wrapText(screenData.hintLine, textWidthPx, 2);

        ModularPanel panel = ModularPanel.defaultPanel("wawelauth_confirm_remove_provider", panelWidth, panelHeight)
            .align(Alignment.Center);
        WawelAuthStyle.dialog(panel);

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        WawelAuthStyle.textButton(cancelBtn.size(64, 18), 56, "wawelauth.gui.common.cancel")
            .onMousePressed(mouseButton -> {
                closeTo(screenData.returnTo);
                return true;
            });

        ButtonWidget<?> removeBtn = new ButtonWidget<>();
        WawelAuthStyle.dangerTextButton(removeBtn.size(64, 18), 56, "wawelauth.gui.common.remove")
            .onMousePressed(mouseButton -> {
                removeProvider(screenData.providerName);
                closeTo(screenData.returnTo);
                return true;
            });

        Column root = new Column();
        root.widthRel(1.0f)
            .heightRel(1.0f)
            .padding(8)
            .background(IDrawable.EMPTY)
            .disableHoverBackground();

        for (String line : titleLines) {
            root.child(
                new TextWidget<>(IKey.str(line)).widthRel(1.0f)
                    .height(14)
                    .color(WawelAuthStyle.THEME_LIGHTER));
        }
        root.child(new Widget<>().size(1, 3));
        for (String line : subtitleLines) {
            root.child(
                new TextWidget<>(IKey.str(line)).widthRel(1.0f)
                    .height(12)
                    .color(WawelAuthStyle.TEXT_SECONDARY));
        }
        root.child(new Widget<>().size(1, 5));
        for (String line : hintLines) {
            root.child(
                new TextWidget<>(IKey.str(line)).widthRel(1.0f)
                    .height(11)
                    .scale(0.85f)
                    .color(WawelAuthStyle.TEXT_DANGER));
        }
        root.child(new Widget<>().expanded())
            .child(
                new Row().widthRel(1.0f)
                    .height(18)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(cancelBtn)
                    .child(new Widget<>().size(8, 18))
                    .child(removeBtn));

        panel.child(root);

        return panel;
    }

    private void removeProvider(String providerName) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }
        try {
            client.getProviderRegistry()
                .removeProvider(providerName);
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to remove local provider '{}': {}", providerName, e.getMessage());
        }
    }

    private void closeTo(GuiScreen returnTo) {
        Minecraft.getMinecraft()
            .displayGuiScreen(returnTo);
    }

    private static Pending storePending(GuiScreen returnTo, String providerName, String title, String subtitle,
        String hintLine) {
        pending = new Pending(returnTo, providerName, title, subtitle, hintLine);
        return pending;
    }

    private static List<String> wrapText(String text, int maxWidthPx, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim()
            .isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.trim()
            .split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (Minecraft.getMinecraft().fontRenderer.getStringWidth(candidate) <= maxWidthPx) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (lines.size() >= maxLines) {
                break;
            }
            line.append(word);
        }

        if (line.length() > 0 && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        int last = lines.size() - 1;
        lines.set(last, GuiText.ellipsizeToPixelWidth(lines.get(last), maxWidthPx));
        return lines;
    }

    private static final class Pending {

        private final GuiScreen returnTo;
        private final String providerName;
        private final String title;
        private final String subtitle;
        private final String hintLine;

        private Pending(GuiScreen returnTo, String providerName, String title, String subtitle, String hintLine) {
            this.returnTo = returnTo;
            this.providerName = providerName;
            this.title = title != null ? title : "";
            this.subtitle = subtitle != null ? subtitle : "";
            this.hintLine = hintLine != null ? hintLine : "";
        }
    }
}
