package org.fentanylsolutions.wawelauth.client.gui;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;

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

@SideOnly(Side.CLIENT)
public class LocalAuthManagerScreen extends ParentAwareModularScreen {

    private static ServerData pendingServerData;

    private final ServerData serverData;
    private ModularPanel mainPanel;
    private LoginDialog loginDialog;
    private RegisterDialog registerDialog;
    private String managedProviderName;
    private String statusText = "";
    private boolean busy;

    public static void open(ServerData serverData) {
        pendingServerData = serverData;
        ClientGUI.open(new LocalAuthManagerScreen());
    }

    private LocalAuthManagerScreen() {
        super("wawelauth");
        openParentOnClose(true);
        this.serverData = pendingServerData;
        pendingServerData = null;
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        final ServerData targetServerData = pendingServerData != null ? pendingServerData : serverData;
        if (targetServerData == null) {
            ModularPanel errorPanel = ModularPanel.defaultPanel("wawelauth_local_auth", 240, 90);
            errorPanel.child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.common.no_server_selected")).widthRel(1.0f)
                            .height(12))
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().width(80)
                                .height(18),
                            80,
                            "wawelauth.gui.common.close")
                            .onMousePressed(mouseButton -> {
                                GuiTransitionScheduler
                                    .transition(errorPanel, () -> ClientGUI.open(new AccountManagerScreen()));
                                return true;
                            })));
            return errorPanel;
        }

        IServerDataExt ext = (IServerDataExt) targetServerData;
        ServerCapabilities caps = ServerBindingPersistence.getEffectiveLocalAuthCapabilities(targetServerData);
        final boolean localSupported = hasLocalAuthMetadata(caps);
        final String serverName = targetServerData.serverName != null ? targetServerData.serverName
            : GuiText.tr("wawelauth.gui.common.server");
        final String missing = GuiText.tr("wawelauth.gui.common.missing");
        final String apiRoot = caps != null ? nvl(caps.getLocalAuthApiRoot(), missing) : missing;
        final String fingerprint = caps != null ? nvl(caps.getLocalAuthPublicKeyFingerprint(), missing) : missing;

        if (!localSupported) {
            statusText = GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
        } else if (statusText == null || statusText.isEmpty()) {
            statusText = GuiText.tr("wawelauth.gui.local_auth.status.trust_first");
        }

        mainPanel = ModularPanel.defaultPanel("wawelauth_local_auth", 300, 170);
        loginDialog = LoginDialog.attach(mainPanel, account -> onLoginResult(account, targetServerData, ext));
        registerDialog = RegisterDialog.attach(mainPanel, success -> {
            if (Boolean.TRUE.equals(success) && managedProviderName != null) {
                loginDialog.openAfterRegister(managedProviderName);
            }
        });

        ButtonWidget<?> trustButton = new ButtonWidget<>();
        trustButton.width(90)
            .height(18)
            .onMousePressed(mouseButton -> {
                if (!localSupported || busy) return true;
                ensureManagedProvider(caps, null);
                return true;
            });
        trustButton.overlay(
            IKey.dynamic(
                () -> GuiText.ellipsizeToPixelWidth(
                    busy ? GuiText.tr("wawelauth.gui.common.working")
                        : GuiText.tr("wawelauth.gui.local_auth.trust_refresh"),
                    82)));
        trustButton.tooltipDynamic(tooltip -> {
            String fullText = busy ? GuiText.tr("wawelauth.gui.common.working")
                : GuiText.tr("wawelauth.gui.local_auth.trust_refresh");
            if (!GuiText.ellipsizeToPixelWidth(fullText, 82)
                .equals(fullText)) {
                tooltip.addLine(IKey.str(fullText));
            }
        });
        trustButton.tooltipAutoUpdate(true);
        trustButton.setEnabled(localSupported);

        ButtonWidget<?> loginButton = new ButtonWidget<>();
        loginButton.width(70)
            .height(18)
            .onMousePressed(mouseButton -> {
                if (!localSupported || busy) return true;
                ensureManagedProvider(caps, () -> loginDialog.open(managedProviderName));
                return true;
            });
        GuiText.fitButtonLabel(loginButton, 70, "wawelauth.gui.common.login");
        loginButton.setEnabled(localSupported);

        ButtonWidget<?> registerButton = new ButtonWidget<>();
        registerButton.width(70)
            .height(18)
            .onMousePressed(mouseButton -> {
                if (!localSupported || busy) return true;
                ensureManagedProvider(caps, () -> registerDialog.open(managedProviderName));
                return true;
            });
        GuiText.fitButtonLabel(registerButton, 70, "wawelauth.gui.common.register");
        registerButton.setEnabled(localSupported);

        return mainPanel.child(
            new Column().widthRel(1.0f)
                .heightRel(1.0f)
                .padding(8)
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.local_auth.title", serverName)).widthRel(1.0f)
                        .height(12))
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.common.api_root", apiRoot)).color(0xFFAAAAAA)
                        .scale(0.8f)
                        .widthRel(1.0f)
                        .height(10)
                        .margin(0, 2))
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.common.fingerprint")).widthRel(1.0f)
                        .height(10)
                        .margin(0, 2))
                .child(
                    new TextWidget<>(IKey.str(fingerprint)).color(0xFF55FFFF)
                        .scale(0.7f)
                        .widthRel(1.0f)
                        .height(10))
                .child(new Widget<>().size(1, 4))
                .child(
                    new TextWidget<>(IKey.dynamic(() -> statusText != null ? statusText : "")).color(0xFFFFFF55)
                        .widthRel(1.0f)
                        .height(12))
                .child(new Widget<>().size(1, 4))
                .child(
                    new Row().widthRel(1.0f)
                        .height(20)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(trustButton)
                        .child(new Widget<>().size(6, 18))
                        .child(loginButton)
                        .child(new Widget<>().size(6, 18))
                        .child(registerButton))
                .child(new Widget<>().size(1, 8))
                .child(
                    new Row().widthRel(1.0f)
                        .height(20)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(
                            GuiText.fitButtonLabel(
                                new ButtonWidget<>().width(120)
                                    .height(18),
                                120,
                                "wawelauth.gui.common.manage_accounts")
                                .onMousePressed(mouseButton -> {
                                    GuiTransitionScheduler
                                        .transition(mainPanel, () -> ClientGUI.open(new AccountManagerScreen()));
                                    return true;
                                }))));
    }

    private void onLoginResult(ClientAccount account, ServerData targetServerData, IServerDataExt ext) {
        if (account == null) return;
        managedProviderName = account.getProviderName();
        ext.setWawelAccountId(account.getId());
        ext.setWawelProviderName(account.getProviderName());
        ServerBindingPersistence.markServerBindingOrigin(targetServerData);
        ServerBindingPersistence.persistServerSelection(targetServerData);
        statusText = GuiText.tr("wawelauth.gui.local_auth.status.logged_in", nvl(account.getProfileName(), "?"));
    }

    private void ensureManagedProvider(ServerCapabilities caps, Runnable onReady) {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            statusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        if (managedProviderName != null && client.getProviderDAO()
            .findByName(managedProviderName) != null) {
            if (onReady != null) onReady.run();
            return;
        }

        busy = true;
        statusText = GuiText.tr("wawelauth.gui.local_auth.status.resolving");
        CompletableFuture.supplyAsync(
            () -> client.getLocalAuthProviderResolver()
                .resolveOrCreate(caps))
            .whenComplete((provider, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> { // Minecraft.addScheduledTask
                        busy = false;
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            statusText = GuiText.tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            return;
                        }
                        managedProviderName = provider.getName();
                        statusText = GuiText.tr("wawelauth.gui.common.ready_message", managedProviderName);
                        if (onReady != null) onReady.run();
                    });
            });
    }

    private static boolean hasLocalAuthMetadata(ServerCapabilities caps) {
        return caps != null && caps.isLocalAuthSupported()
            && normalize(caps.getLocalAuthApiRoot()) != null
            && normalize(caps.getLocalAuthPublicKeyFingerprint()) != null;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }

}
