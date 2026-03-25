package org.fentanylsolutions.wawelauth.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler;
import org.fentanylsolutions.wawelauth.WawelAuth;

import com.cleanroommc.modularui.api.drawable.IKey;
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
 * Overlay screens for the server drag-and-drop flow.
 * <p>
 * Phase 1 (hint): Semi-transparent overlay telling the user to drop to add a server.
 * Phase 2 (confirm): Shows server name and address, asks the user to confirm.
 * On confirm, adds the server to the list and opens the multiplayer menu.
 */
@SideOnly(Side.CLIENT)
public final class ServerDropScreen {

    private static final String HINT_PANEL_NAME = "wawelauth_drop_hint";
    private static final String CONFIRM_PANEL_NAME = "wawelauth_drop_confirm";

    private static volatile boolean hintOpen;

    private ServerDropScreen() {}

    // ── Phase 1: Drag hint ──────────────────────────────────────────────

    public static void showHint() {
        if (hintOpen) return;
        hintOpen = true;
        ClientGUI.open(new HintScreen());
    }

    public static void dismissHint() {
        dismissHintThenRun(null);
    }

    /**
     * Dismiss the hint overlay and run an action after the screen has fully torn down.
     * Uses {@link GuiTransitionScheduler#nextTick} to defer, since ModularUI
     * needs a tick to clean up before a new screen can open.
     */
    public static void dismissHintThenRun(Runnable afterDismiss) {
        boolean wasOpen = hintOpen;
        hintOpen = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (wasOpen && mc.currentScreen instanceof com.cleanroommc.modularui.api.IMuiScreen muiScreen) {
            ModularPanel mainPanel = muiScreen.getScreen()
                .getMainPanel();
            if (mainPanel != null && HINT_PANEL_NAME.equals(mainPanel.getName())) {
                mainPanel.closeIfOpen();
                if (afterDismiss != null) {
                    GuiTransitionScheduler.nextTick(afterDismiss);
                }
                return;
            }
        }
        // Hint wasn't showing — run immediately
        if (afterDismiss != null) {
            afterDismiss.run();
        }
    }

    private static final class HintScreen extends ParentAwareModularScreen {

        HintScreen() {
            super("wawelauth");
        }

        @Override
        public ModularPanel buildUI(ModularGuiContext context) {
            return ModularPanel.defaultPanel(HINT_PANEL_NAME)
                .size(220, 50)
                .align(Alignment.Center)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.drop.hint_title")).widthRel(1.0f)
                                .height(14)
                                .alignment(Alignment.Center))
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.drop.hint_subtitle")).color(0xFFAAAAAA)
                                .scale(0.85f)
                                .widthRel(1.0f)
                                .height(12)
                                .alignment(Alignment.Center)));
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!hintOpen) {
                getMainPanel().closeIfOpen();
            }
        }
    }

    // ── Phase 2: Confirmation ───────────────────────────────────────────

    public static void showConfirmation(String serverName, String address) {
        ConfirmScreen.pendingName = serverName;
        ConfirmScreen.pendingAddress = address;
        ClientGUI.open(new ConfirmScreen());
    }

    private static final class ConfirmScreen extends ParentAwareModularScreen {

        private static String pendingName;
        private static String pendingAddress;

        ConfirmScreen() {
            super("wawelauth");
        }

        @Override
        public ModularPanel buildUI(ModularGuiContext context) {
            String name = pendingName;
            String address = pendingAddress;

            ModularPanel panel = ModularPanel.defaultPanel(CONFIRM_PANEL_NAME)
                .size(260, 110)
                .align(Alignment.Center);

            ButtonWidget<?> cancelBtn = new ButtonWidget<>();
            cancelBtn.size(60, 18)
                .onMousePressed(mouseButton -> {
                    panel.closeIfOpen();
                    return true;
                });
            GuiText.fitButtonLabel(cancelBtn, 60, "wawelauth.gui.common.cancel");

            ButtonWidget<?> addBtn = new ButtonWidget<>();
            addBtn.size(80, 18)
                .onMousePressed(mouseButton -> {
                    addServerAndOpen(name, address);
                    panel.closeIfOpen();
                    return true;
                });
            GuiText.fitButtonLabel(addBtn, 80, "wawelauth.gui.drop.add_server");

            panel.child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.drop.confirm_title")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(IKey.str(name)).color(0xFF55FF55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(
                        new TextWidget<>(IKey.str(address)).color(0xFFAAAAAA)
                            .scale(0.85f)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(new Widget<>().size(1, 6))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(cancelBtn)
                            .child(new Widget<>().size(8, 18))
                            .child(addBtn)));

            return panel;
        }
    }

    // ── Server list manipulation ────────────────────────────────────────

    private static void addServerAndOpen(String name, String address) {
        Minecraft mc = Minecraft.getMinecraft();

        ServerList serverList = new ServerList(mc);
        serverList.loadServerList();
        serverList.addServerData(new ServerData(name, address));
        serverList.saveServerList();

        WawelAuth.LOG.info("[ServerDropScreen] Added server '{}' ({})", name, address);

        // Navigate to the multiplayer menu so the user sees the new entry.
        GuiScreen parent = null;
        if (mc.currentScreen instanceof com.cleanroommc.modularui.api.IMuiScreen muiScreen) {
            parent = muiScreen.getScreen()
                .getContext()
                .getParentScreen();
        }

        // Use the parent screen (main menu or multiplayer) as the back button target.
        GuiScreen backScreen = parent instanceof GuiMainMenu ? parent : new GuiMainMenu();
        GuiTransitionScheduler.nextTick(() -> mc.displayGuiScreen(new GuiMultiplayer(backScreen)));
    }
}
