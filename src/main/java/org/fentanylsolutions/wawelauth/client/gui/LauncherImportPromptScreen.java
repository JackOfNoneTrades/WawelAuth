package org.fentanylsolutions.wawelauth.client.gui;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge.LauncherImportCandidate;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * First-time approval dialog for a launcher session detected at startup.
 * Offers Import / Not now / Don't ask again for the detected account.
 */
@SideOnly(Side.CLIENT)
public final class LauncherImportPromptScreen extends ParentAwareModularScreen {

    private static final String PANEL_NAME = "wawelauth_launcher_import";
    private static LauncherImportCandidate pending;

    private LauncherImportPromptScreen() {
        super("wawelauth");
        openParentOnClose(true);
    }

    public static void open(LauncherImportCandidate candidate) {
        pending = candidate;
        ClientGUI.open(new LauncherImportPromptScreen());
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        LauncherImportCandidate candidate = pending;
        String name = candidate != null ? candidate.getUsername() : "";
        String providerName = candidate != null ? candidate.getProviderName() : "";
        java.util.UUID profileUuid = candidate != null ? candidate.getProfileUuid() : null;

        WawelClient client = WawelClient.instance();
        SessionBridge bridge = client != null ? client.getSessionBridge() : null;

        ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME)
            .size(280, 116)
            .align(Alignment.Center);
        WawelAuthStyle.dialog(panel);

        ButtonWidget<?> yesBtn = new ButtonWidget<>();
        yesBtn.size(60, 18)
            .onMousePressed(mouseButton -> {
                if (bridge != null) {
                    bridge.confirmLauncherImport();
                }
                panel.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(yesBtn, 52, "wawelauth.gui.launcher_import.import");

        ButtonWidget<?> noBtn = new ButtonWidget<>();
        noBtn.size(60, 18)
            .onMousePressed(mouseButton -> {
                if (bridge != null) {
                    bridge.declineLauncherImport();
                }
                panel.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(noBtn, 52, "wawelauth.gui.launcher_import.not_now");

        ButtonWidget<?> dontAskBtn = new ButtonWidget<>();
        dontAskBtn.size(110, 18)
            .onMousePressed(mouseButton -> {
                if (bridge != null) {
                    bridge.suppressLauncherImport();
                }
                panel.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(dontAskBtn, 102, "wawelauth.gui.launcher_import.dont_ask");

        Row detailRow = new Row();
        detailRow.widthRel(1.0f)
            .height(24)
            .margin(0, 4)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        if (profileUuid != null) {
            detailRow.child(new FaceWidget(name, profileUuid, providerName).size(18, 18));
            detailRow.child(new Widget<>().size(4, 18));
        }
        detailRow.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.launcher_import.detail", name, providerName))
                .color(WawelAuthStyle.TEXT_SECONDARY)
                .scale(0.9f)
                .expanded()
                .height(48));

        panel.child(
            new Column().widthRel(1.0f)
                .heightRel(1.0f)
                .padding(8)
                .background(IDrawable.EMPTY)
                .disableHoverBackground()
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.launcher_import.title")).widthRel(1.0f)
                        .height(14)
                        .color(WawelAuthStyle.THEME_LIGHTER))
                .child(detailRow)
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.launcher_import.question")).widthRel(1.0f)
                        .height(12)
                        .color(WawelAuthStyle.TEXT_PRIMARY)
                        .margin(0, 2))
                .child(new Widget<>().size(1, 6))
                .child(
                    new Row().widthRel(1.0f)
                        .height(20)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(yesBtn)
                        .child(new Widget<>().size(8, 18))
                        .child(noBtn)
                        .child(new Widget<>().size(8, 18))
                        .child(dontAskBtn)));

        return panel;
    }
}
