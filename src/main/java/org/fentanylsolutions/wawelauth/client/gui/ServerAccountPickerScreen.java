package org.fentanylsolutions.wawelauth.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ProviderRegistry;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.ServerConnectionProxySupport;
import org.fentanylsolutions.wawelauth.wawelclient.SingleplayerAccountPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ServerAccountPickerScreen extends ParentAwareModularScreen {

    private static final int ACCOUNT_LABEL_MAX_WIDTH_PX = 174;
    private static final int PROXY_DIALOG_STATUS_COLOR = 0xFFFF5555;
    private static final int ACCOUNT_ENTRY_HEIGHT = 16;
    private static final int ACCOUNT_LIST_TOP_MARGIN = 4;
    private static final int ACCOUNT_LIST_MAX_VISIBLE_ROWS = 8;
    private static final int PICKER_PANEL_HEIGHT_SINGLEPLAYER = 204;
    private static final int PICKER_PANEL_HEIGHT_SERVER_ONLY = 202;
    private static final int PICKER_PANEL_HEIGHT_SERVER_WITH_LOCAL_AUTH = 246;

    /**
     * ModularScreen's constructor calls buildUI() immediately, before subclass
     * field assignments execute. Pass serverData through a static field so it
     * is available during buildUI(). Single-threaded (render thread only).
     */
    private static ServerData pendingServerData;
    private static String pendingStatusMessage;
    private static boolean pendingSingleplayerMode;

    private final ServerData serverData;
    private final boolean singleplayerMode;

    public static void open(ServerData serverData) {
        open(serverData, null);
    }

    public static void open(ServerData serverData, String statusMessage) {
        pendingServerData = serverData;
        pendingStatusMessage = statusMessage;
        pendingSingleplayerMode = false;
        ClientGUI.open(new ServerAccountPickerScreen());
    }

    public static void openSingleplayer() {
        pendingServerData = null;
        pendingStatusMessage = null;
        pendingSingleplayerMode = true;
        ClientGUI.open(new ServerAccountPickerScreen());
    }

    private ServerAccountPickerScreen() {
        super("wawelauth");
        openParentOnClose(true);
        this.serverData = pendingServerData;
        this.singleplayerMode = pendingSingleplayerMode;
        pendingServerData = null;
        pendingSingleplayerMode = false;
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        boolean singleplayerMode = pendingSingleplayerMode || this.singleplayerMode;
        ServerData targetServerData = pendingServerData != null ? pendingServerData : serverData;
        if (targetServerData == null && !singleplayerMode) {
            return ModularPanel.defaultPanel("wawelauth_server_picker", 200, 80)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(6)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.common.no_server_selected")).widthRel(1.0f)
                                .height(14)));
        }

        WawelClient client = WawelClient.instance();

        IServerDataExt ext = null;
        String title;
        String statusMessage = singleplayerMode ? null : pendingStatusMessage;
        ServerCapabilities localAuthCapabilities = null;
        boolean wawelAuthServer = false;
        boolean localAuthAvailable = false;
        String[] trustedLocalProviderName = { null };
        long selectedAccountId;

        if (singleplayerMode) {
            title = GuiText.tr("wawelauth.gui.singleplayer_picker.title");
            if (client != null) {
                SingleplayerAccountPersistence.clearMissingSelection(client.getAccountManager());
            }
            selectedAccountId = SingleplayerAccountPersistence.getSelectedAccountId();
        } else {
            ext = (IServerDataExt) targetServerData;
            String serverName = targetServerData.serverName != null ? targetServerData.serverName
                : GuiText.tr("wawelauth.gui.common.server");
            title = GuiText.tr("wawelauth.gui.server_picker.title", serverName);

            ServerCapabilities capabilities = ext.getWawelCapabilities();
            localAuthCapabilities = ServerBindingPersistence.getEffectiveLocalAuthCapabilities(targetServerData);
            wawelAuthServer = capabilities != null && capabilities.isWawelAuthAdvertised();
            localAuthAvailable = localAuthCapabilities != null && localAuthCapabilities.isLocalAuthSupported()
                && notBlank(localAuthCapabilities.getLocalAuthApiRoot())
                && notBlank(localAuthCapabilities.getLocalAuthPublicKeyFingerprint());
            selectedAccountId = ext.getWawelAccountId();
        }
        pendingStatusMessage = null;

        List<ClientAccount> allAccounts = client != null ? new ArrayList<>(
            client.getAccountManager()
                .listAccounts())
            : new ArrayList<ClientAccount>();
        Collections.sort(
            allAccounts,
            Comparator.comparing(ServerAccountPickerScreen::sortKeyForAccount, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(
                    account -> ProviderDisplayName.displayName(account.getProviderName()),
                    String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(ClientAccount::getId));

        boolean showLocalAuthControls = !singleplayerMode && localAuthCapabilities != null
            && localAuthCapabilities.isLocalAuthSupported();
        boolean showLocalAuthActions = wawelAuthServer && showLocalAuthControls;
        int accountRowCount = allAccounts.size() + (singleplayerMode ? 1 : 0);
        int visibleRowCount = Math.max(1, Math.min(accountRowCount, ACCOUNT_LIST_MAX_VISIBLE_ROWS));
        int listHeight = visibleRowCount * ACCOUNT_ENTRY_HEIGHT;

        int baseHeight = 12 + 14 + ACCOUNT_LIST_TOP_MARGIN * 2 + 18; // padding + title + list margins + manage btn
        if (!singleplayerMode) baseHeight += 21; // server proxy btn
        if (showLocalAuthControls) baseHeight += 21; // manage local auth btn
        if (showLocalAuthActions) baseHeight += 23; // login/register row
        if (statusMessage != null && !statusMessage.isEmpty()) baseHeight += 14;

        int panelHeight = baseHeight + listHeight;
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(
            Minecraft.getMinecraft(),
            Minecraft.getMinecraft().displayWidth,
            Minecraft.getMinecraft().displayHeight);
        int maxPanelHeight = sr.getScaledHeight() - 10;
        if (panelHeight > maxPanelHeight) {
            visibleRowCount = Math.max(1, (maxPanelHeight - baseHeight) / ACCOUNT_ENTRY_HEIGHT);
            listHeight = visibleRowCount * ACCOUNT_ENTRY_HEIGHT;
            panelHeight = baseHeight + listHeight;
        }

        ModularPanel panel = ModularPanel.defaultPanel("wawelauth_server_picker", 200, panelHeight);

        final ServerData serverDataRef = targetServerData;
        final IServerDataExt serverExt = ext;
        final ServerCapabilities localAuthCapabilitiesRef = localAuthCapabilities;
        final IPanelHandler serverProxyDialogHandler = !singleplayerMode && serverExt != null
            ? IPanelHandler.simple(panel, (parent, player) -> buildServerProxyDialog(serverDataRef), true)
            : null;

        LoginDialog loginDialog = LoginDialog.attach(panel, account -> {
            if (account == null) return;
            trustedLocalProviderName[0] = account.getProviderName();
            if (serverExt == null || serverDataRef == null) {
                return;
            }
            serverExt.setWawelAccountId(account.getId());
            serverExt.setWawelProviderName(account.getProviderName());
            ServerBindingPersistence.markServerBindingOrigin(serverDataRef);
            ServerBindingPersistence.persistServerSelection(serverDataRef);
            String successMessage = GuiText.tr(
                "wawelauth.gui.server_picker.status.bound",
                account.getProfileName() != null ? account.getProfileName() : "?");
            GuiTransitionScheduler
                .transition(panel, () -> ServerAccountPickerScreen.open(serverDataRef, successMessage));
        });
        RegisterDialog registerDialog = RegisterDialog.attach(panel, success -> {
            if (Boolean.TRUE.equals(success) && trustedLocalProviderName[0] != null) {
                loginDialog.openAfterRegister(trustedLocalProviderName[0]);
            }
        });

        ListWidget<IWidget, ?> accountList = new ListWidget<>();
        accountList.widthRel(1.0f)
            .height(listHeight);

        if (singleplayerMode) {
            accountList.child(buildSingleplayerClearEntry(selectedAccountId, panel));
        }
        for (ClientAccount account : allAccounts) {
            if (singleplayerMode) {
                accountList.child(
                    buildAccountEntry(
                        account,
                        selectedAccountId,
                        panel,
                        selected -> SingleplayerAccountPersistence.setSelectedAccountId(selected.getId())));
            } else {
                accountList.child(buildAccountEntry(account, selectedAccountId, panel, selected -> {
                    if (serverExt == null || serverDataRef == null) {
                        return;
                    }
                    serverExt.setWawelAccountId(selected.getId());
                    serverExt.setWawelProviderName(selected.getProviderName());
                    ServerBindingPersistence.markServerBindingOrigin(serverDataRef);
                    ServerBindingPersistence.persistServerSelection(serverDataRef);
                }));
            }
        }

        accountList.margin(0, ACCOUNT_LIST_TOP_MARGIN);

        ButtonWidget<?> manageLocalAuthBtn = new ButtonWidget<>();
        manageLocalAuthBtn.widthRel(1.0f)
            .height(18)
            .margin(0, 3, 0, 0)
            .onMousePressed(mouseButton -> {
                GuiTransitionScheduler.transition(panel, () -> AccountManagerScreen.openForLocalAuth(targetServerData));
                return true;
            });
        GuiText.fitButtonLabelMaxWidth(manageLocalAuthBtn, 180, "wawelauth.gui.server_picker.manage_local_auth");
        manageLocalAuthBtn.setEnabled(localAuthAvailable);

        ButtonWidget<?> serverProxyBtn = new ButtonWidget<>();
        serverProxyBtn.widthRel(1.0f)
            .height(18)
            .margin(0, 3, 0, 0)
            .onMousePressed(mouseButton -> {
                if (serverProxyDialogHandler != null) {
                    serverProxyDialogHandler.deleteCachedPanel();
                    serverProxyDialogHandler.openPanel();
                }
                return true;
            });
        GuiText.fitButtonLabelMaxWidth(serverProxyBtn, 180, "wawelauth.gui.server_picker.server_proxy");
        serverProxyBtn.setEnabled(!singleplayerMode && serverProxyDialogHandler != null);

        Column content = new Column();
        content.widthRel(1.0f)
            .heightRel(1.0f)
            .padding(6);
        content.child(
            new TextWidget<>(IKey.str(title)).widthRel(1.0f)
                .height(14));
        content.child(accountList);
        if (statusMessage != null && !statusMessage.isEmpty()) {
            content.child(
                new TextWidget<>(IKey.str(statusMessage)).color(0xFF55FF55)
                    .widthRel(1.0f)
                    .height(10)
                    .margin(0, 2));
        }
        content.child(
            GuiText.fitButtonLabelMaxWidth(
                new ButtonWidget<>().widthRel(1.0f)
                    .height(18),
                180,
                "wawelauth.gui.common.manage_accounts")
                .onMousePressed(mouseButton -> {
                    GuiTransitionScheduler.transition(panel, () -> ClientGUI.open(new AccountManagerScreen()));
                    return true;
                }));
        if (showLocalAuthControls) {
            content.child(manageLocalAuthBtn);
        }
        if (!singleplayerMode) {
            content.child(serverProxyBtn);
        }

        if (showLocalAuthActions) {
            ButtonWidget<?> loginLocalBtn = GuiText.fitButtonLabel(
                new ButtonWidget<>().width(90)
                    .height(18),
                90,
                "wawelauth.gui.common.login");
            loginLocalBtn.setEnabled(localAuthAvailable);
            loginLocalBtn.onMousePressed(mouseButton -> {
                openLocalAuthAction(
                    targetServerData,
                    localAuthCapabilitiesRef,
                    panel,
                    trustedLocalProviderName,
                    loginDialog,
                    registerDialog,
                    false);
                return true;
            });

            ButtonWidget<?> registerLocalBtn = GuiText.fitButtonLabel(
                new ButtonWidget<>().width(90)
                    .height(18),
                90,
                "wawelauth.gui.common.register");
            registerLocalBtn.setEnabled(localAuthAvailable);
            registerLocalBtn.onMousePressed(mouseButton -> {
                openLocalAuthAction(
                    targetServerData,
                    localAuthCapabilitiesRef,
                    panel,
                    trustedLocalProviderName,
                    loginDialog,
                    registerDialog,
                    true);
                return true;
            });

            content.child(new Widget<>().size(1, 3))
                .child(
                    new Row().widthRel(1.0f)
                        .height(20)
                        .child(loginLocalBtn)
                        .child(new Widget<>().size(6, 18))
                        .child(registerLocalBtn));
        }

        panel.child(content);

        return panel;
    }

    private static void openLocalAuthAction(ServerData targetServerData, ServerCapabilities capabilities,
        ModularPanel panel, String[] trustedLocalProviderName, LoginDialog loginDialog, RegisterDialog registerDialog,
        boolean register) {
        if (capabilities == null) {
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }

        ClientProvider trustedProvider = client.getLocalAuthProviderResolver()
            .findExisting(capabilities);
        if (trustedProvider == null) {
            GuiTransitionScheduler.transition(panel, () -> LocalAuthManagerScreen.open(targetServerData));
            return;
        }

        trustedLocalProviderName[0] = trustedProvider.getName();
        if (register) {
            registerDialog.open(trustedProvider.getName());
        } else {
            loginDialog.open(trustedProvider.getName());
        }
    }

    private Dialog<Boolean> buildServerProxyDialog(ServerData targetServerData) {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_server_proxy");
        dialog.setCloseOnOutOfBoundsClick(false);

        if (targetServerData == null || !(targetServerData instanceof IServerDataExt)) {
            dialog.size(230, 90)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.common.no_server_selected")).widthRel(1.0f)
                                .height(14))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
                                .margin(0, 6)
                                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .child(
                                    GuiText
                                        .fitButtonLabel(
                                            new ButtonWidget<>().size(70, 18),
                                            70,
                                            "wawelauth.gui.common.close")
                                        .onMousePressed(btn -> {
                                            dialog.closeIfOpen();
                                            return true;
                                        }))));
            return dialog;
        }

        IServerDataExt ext = (IServerDataExt) targetServerData;
        ServerAddress serverAddress = ServerAddress.func_78860_a(targetServerData.serverIP);
        final String targetHost = serverAddress != null ? serverAddress.getIP() : targetServerData.serverIP;
        final int targetPort = serverAddress != null ? serverAddress.getPort() : 25565;
        final String serverName = notBlank(targetServerData.serverName) ? targetServerData.serverName
            : GuiText.tr("wawelauth.gui.common.server");
        final String targetLine = targetHost + ":" + targetPort;

        ProviderProxySettings initialSettings = ServerConnectionProxySupport
            .copySettings(ext.getWawelServerProxySettings());
        final boolean[] proxyEnabled = { initialSettings.isEnabled() };
        final ProviderProxyType[] proxyType = { initialSettings.getType() };
        final String[] proxyStatusText = { GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested") };
        final ProviderRegistry.ProbeOutcome[] proxyStatusOutcome = { ProviderRegistry.ProbeOutcome.NEUTRAL };
        final String[] serverStatusText = { GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested") };
        final ProviderRegistry.ProbeOutcome[] serverStatusOutcome = { ProviderRegistry.ProbeOutcome.NEUTRAL };
        final boolean[] busy = { false };

        TextFieldWidget hostField = new TextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.proxy_address"));
        hostField.width(214)
            .height(18)
            .setMaxLength(255)
            .margin(0, 2);
        hostField.value(new StringValue(initialSettings.getHost() != null ? initialSettings.getHost() : ""));

        TextFieldWidget portField = new TextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.proxy_port"));
        portField.width(64)
            .height(18)
            .setMaxLength(5)
            .margin(0, 2);
        portField
            .value(new StringValue(initialSettings.getPort() != null ? String.valueOf(initialSettings.getPort()) : ""));

        TextFieldWidget usernameField = new TextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.proxy_username"));
        usernameField.widthRel(1.0f)
            .height(18)
            .setMaxLength(128)
            .margin(0, 2);
        usernameField
            .value(new StringValue(initialSettings.getUsername() != null ? initialSettings.getUsername() : ""));

        PasswordInputWidget passwordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.proxy_password"));
        passwordField.widthRel(1.0f)
            .height(18)
            .setMaxLength(128)
            .margin(0, 2);
        passwordField
            .value(new StringValue(initialSettings.getPassword() != null ? initialSettings.getPassword() : ""));

        ButtonWidget<?> enabledBtn = new ButtonWidget<>();
        enabledBtn.size(94, 18)
            .onMousePressed(btn -> {
                proxyEnabled[0] = !proxyEnabled[0];
                return true;
            });
        enabledBtn.overlay(
            IKey.dynamic(
                () -> GuiText.ellipsizeToPixelWidth(
                    proxyEnabled[0] ? GuiText.tr("wawelauth.gui.account_manager.proxy_enabled")
                        : GuiText.tr("wawelauth.gui.account_manager.proxy_disabled"),
                    86)));

        ButtonWidget<?> typeBtn = new ButtonWidget<>();
        typeBtn.size(94, 18)
            .onMousePressed(btn -> {
                proxyType[0] = proxyType[0] == ProviderProxyType.HTTP ? ProviderProxyType.SOCKS
                    : ProviderProxyType.HTTP;
                return true;
            });
        typeBtn.overlay(
            IKey.dynamic(
                () -> GuiText.ellipsizeToPixelWidth(
                    proxyType[0] == ProviderProxyType.HTTP ? GuiText.tr("wawelauth.gui.account_manager.proxy_type_http")
                        : GuiText.tr("wawelauth.gui.account_manager.proxy_type_socks"),
                    86)));

        ButtonWidget<?> testBtn = new ButtonWidget<>();
        testBtn.size(70, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;
                try {
                    ProviderProxySettings formSettings = readServerProxySettingsFromForm(
                        proxyEnabled[0],
                        true,
                        proxyType[0],
                        hostField,
                        portField,
                        usernameField,
                        passwordField);
                    busy[0] = true;
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    serverStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    proxyStatusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_testing_proxy");
                    serverStatusText[0] = GuiText.tr("wawelauth.gui.server_picker.proxy_testing_server");

                    CompletableFuture.supplyAsync(() -> probeServerProxy(targetHost, targetPort, formSettings))
                        .whenComplete(
                            (probeResult, err) -> Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    busy[0] = false;
                                    if (err != null) {
                                        proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                                        proxyStatusText[0] = formatThrowableMessage(err);
                                        serverStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                                        serverStatusText[0] = GuiText
                                            .tr("wawelauth.gui.account_manager.proxy_status_not_tested");
                                        return;
                                    }
                                    proxyStatusOutcome[0] = probeResult.proxyOutcome;
                                    proxyStatusText[0] = probeResult.proxyText;
                                    serverStatusOutcome[0] = probeResult.serverOutcome;
                                    serverStatusText[0] = probeResult.serverText;
                                }));
                } catch (Exception e) {
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                    proxyStatusText[0] = formatThrowableMessage(e);
                    serverStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    serverStatusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested");
                }
                return true;
            });
        testBtn.overlay(
            IKey.dynamic(
                () -> GuiText.ellipsizeToPixelWidth(
                    busy[0] ? GuiText.tr("wawelauth.gui.common.working")
                        : GuiText.tr("wawelauth.gui.account_manager.proxy_test"),
                    62)));

        ButtonWidget<?> saveBtn = new ButtonWidget<>();
        saveBtn.size(70, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;
                try {
                    ProviderProxySettings formSettings = readServerProxySettingsFromForm(
                        proxyEnabled[0],
                        false,
                        proxyType[0],
                        hostField,
                        portField,
                        usernameField,
                        passwordField);
                    ext.setWawelServerProxySettings(formSettings);
                    ServerBindingPersistence.persistServerSelection(targetServerData);
                    dialog.closeIfOpen();
                } catch (Exception e) {
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                    proxyStatusText[0] = formatThrowableMessage(e);
                }
                return true;
            });
        GuiText.fitButtonLabel(saveBtn, 70, "wawelauth.gui.account_manager.proxy_save");

        ButtonWidget<?> closeBtn = new ButtonWidget<>();
        closeBtn.size(70, 18)
            .onMousePressed(btn -> {
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        dialog.size(300, 236)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.proxy_settings")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.server_picker.proxy_server_line", serverName))
                            .color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.server_picker.proxy_target_line", targetLine))
                            .color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 4)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(enabledBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(typeBtn))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 2)
                            .child(hostField)
                            .child(new Widget<>().size(6, 18))
                            .child(portField))
                    .child(usernameField)
                    .child(passwordField)
                    .child(
                        new TextWidget<>(
                            IKey.dynamic(
                                () -> GuiText
                                    .tr("wawelauth.gui.account_manager.proxy_status_proxy", proxyStatusText[0])))
                                        .color(() -> probeOutcomeColor(proxyStatusOutcome[0]))
                                        .widthRel(1.0f)
                                        .height(12)
                                        .margin(0, 4))
                    .child(
                        new TextWidget<>(
                            IKey.dynamic(
                                () -> GuiText
                                    .tr("wawelauth.gui.server_picker.proxy_status_server", serverStatusText[0])))
                                        .color(() -> probeOutcomeColor(serverStatusOutcome[0]))
                                        .widthRel(1.0f)
                                        .height(12)
                                        .margin(0, 1))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 8)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(testBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(saveBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(closeBtn)));

        return dialog;
    }

    private ProviderProxySettings readServerProxySettingsFromForm(boolean proxyEnabled, boolean ignoreEnabledToggle,
        ProviderProxyType proxyType, TextFieldWidget hostField, TextFieldWidget portField,
        TextFieldWidget usernameField, PasswordInputWidget passwordField) {
        ProviderProxySettings settings = new ProviderProxySettings();
        settings.setType(proxyType);

        ParsedProxyEndpoint endpoint = parseProxyEndpoint(
            trimToNull(hostField.getText()),
            trimToNull(portField.getText()));
        settings.setHost(endpoint.host);
        if (endpoint.port != null) {
            settings.setPort(endpoint.port);
        }

        String username = trimToNull(usernameField.getText());
        String password = trimToNull(passwordField.getText());
        settings.setUsername(username);
        settings.setPassword(password);
        settings.setEnabled(
            ignoreEnabledToggle ? endpoint.host != null || endpoint.port != null || username != null || password != null
                : proxyEnabled);
        ProviderProxySettings normalized = ServerConnectionProxySupport.normalizeSettings(settings);
        ServerConnectionProxySupport.validateSettings(normalized);
        return normalized;
    }

    private ServerProxyProbeResult probeServerProxy(String targetHost, int targetPort, ProviderProxySettings settings) {
        ProviderProxySettings normalized = ServerConnectionProxySupport.normalizeSettings(settings);
        ServerConnectionProxySupport.validateSettings(normalized);

        ServerProxyProbeResult result = new ServerProxyProbeResult();
        try {
            ProviderProxySupport.probeEndpoint(normalized, 10_000);
            result.proxyOutcome = ProviderRegistry.ProbeOutcome.SUCCESS;
            result.proxyText = GuiText.tr("wawelauth.gui.server_picker.proxy_status_ok");
        } catch (Exception e) {
            result.proxyOutcome = ProviderRegistry.ProbeOutcome.ERROR;
            result.proxyText = describeProxyProbeFailure(normalized, e);
            result.serverOutcome = ProviderRegistry.ProbeOutcome.NEUTRAL;
            result.serverText = GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested");
            return result;
        }

        try {
            ServerConnectionProxySupport.probeGameServerConnection(targetHost, targetPort, normalized);
            result.serverOutcome = ProviderRegistry.ProbeOutcome.SUCCESS;
            result.serverText = GuiText
                .tr("wawelauth.gui.server_picker.proxy_server_ok", targetHost + ":" + targetPort);
        } catch (Exception e) {
            result.serverOutcome = ProviderRegistry.ProbeOutcome.ERROR;
            result.serverText = describeServerProbeFailure(targetHost, targetPort, e);
        }
        return result;
    }

    private ParsedProxyEndpoint parseProxyEndpoint(String rawHost, String rawPort) {
        String host = rawHost;
        Integer explicitPort = parseProxyPort(rawPort);

        ParsedProxyEndpoint embedded = parseEmbeddedProxyEndpoint(host);
        host = embedded.host;

        ParsedProxyEndpoint resolved = new ParsedProxyEndpoint();
        resolved.host = host;
        resolved.port = explicitPort != null ? explicitPort : embedded.port;
        return resolved;
    }

    private ParsedProxyEndpoint parseEmbeddedProxyEndpoint(String rawHost) {
        ParsedProxyEndpoint result = new ParsedProxyEndpoint();
        result.host = rawHost;
        result.port = null;

        if (rawHost == null) {
            return result;
        }

        if (rawHost.startsWith("[")) {
            int endBracket = rawHost.indexOf(']');
            if (endBracket > 0) {
                String hostPart = rawHost.substring(1, endBracket);
                if (endBracket + 1 < rawHost.length() && rawHost.charAt(endBracket + 1) == ':') {
                    result.host = hostPart;
                    result.port = parseProxyPort(rawHost.substring(endBracket + 2));
                    return result;
                }
                if (endBracket == rawHost.length() - 1) {
                    result.host = hostPart;
                }
            }
            return result;
        }

        int firstColon = rawHost.indexOf(':');
        int lastColon = rawHost.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            String hostPart = trimToNull(rawHost.substring(0, lastColon));
            String portPart = trimToNull(rawHost.substring(lastColon + 1));
            if (hostPart != null && portPart != null) {
                result.host = hostPart;
                result.port = parseProxyPort(portPart);
            }
        }
        return result;
    }

    private Integer parseProxyPort(String portText) {
        if (portText == null) {
            return null;
        }

        try {
            int port = Integer.parseInt(portText.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Proxy port must be between 1 and 65535.");
            }
            return Integer.valueOf(port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(GuiText.tr("wawelauth.gui.account_manager.proxy_port_invalid"));
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int probeOutcomeColor(ProviderRegistry.ProbeOutcome outcome) {
        if (outcome == ProviderRegistry.ProbeOutcome.SUCCESS) {
            return 0xFF55FF55;
        }
        if (outcome == ProviderRegistry.ProbeOutcome.ERROR) {
            return PROXY_DIALOG_STATUS_COLOR;
        }
        return 0xFFAAAAAA;
    }

    private static String describeProxyProbeFailure(ProviderProxySettings settings, Throwable error) {
        String target = settings.getHost() + ":" + settings.getPort();
        String detail = firstMeaningfulMessage(error);
        return detail != null ? "Proxy test failed via " + target + ": " + detail : "Proxy test failed via " + target;
    }

    private static String describeServerProbeFailure(String host, int port, Throwable error) {
        String detail = firstMeaningfulMessage(error);
        String target = host + ":" + port;
        return detail != null ? "Game server test failed via proxy to " + target + ": " + detail
            : "Game server test failed via proxy to " + target;
    }

    private static String formatThrowableMessage(Throwable error) {
        String detail = firstMeaningfulMessage(error);
        return detail != null ? detail
            : error.getClass()
                .getSimpleName();
    }

    private static String firstMeaningfulMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String trimmed = message.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("java.lang.RuntimeException:")) {
                    return trimmed;
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private ButtonWidget<?> buildAccountEntry(ClientAccount account, long selectedAccountId, ModularPanel panel,
        Consumer<ClientAccount> onSelect) {
        AccountStatus status = getLiveStatus(account);
        int statusColor = StatusColors.getColor(status);
        String profileName = account.getProfileName() != null ? account.getProfileName() : "?";
        String providerName = ProviderDisplayName.displayName(account.getProviderName());
        boolean isSelected = selectedAccountId == account.getId();

        ButtonWidget<?> entry = new ButtonWidget<>();
        entry.widthRel(1.0f)
            .height(16);
        if (isSelected) {
            entry.background(new Rectangle().color(0x44FFFFFF));
        }

        Row dot = new Row();
        dot.size(8, 8)
            .margin(1, 4)
            .background(new Rectangle().color(0xFF2A2A2A))
            .child(
                new Widget<>().size(6, 6)
                    .margin(1, 1)
                    .background(new Rectangle().color(statusColor)));

        String fullLabel = profileName;
        String displayLabel = GuiText.ellipsizeToPixelWidth(fullLabel, ACCOUNT_LABEL_MAX_WIDTH_PX);
        TextWidget<?> label = new TextWidget<>(IKey.str(displayLabel));
        label.expanded()
            .heightRel(1.0f);
        label.addTooltipLine(GuiText.tr("wawelauth.gui.server_picker.tooltip.account", fullLabel));
        label.addTooltipLine(GuiText.tr("wawelauth.gui.server_picker.tooltip.provider", providerName));

        Row row = new Row();
        row.widthRel(1.0f)
            .heightRel(1.0f)
            .child(new Widget<>().size(2, 16));
        if (account.getProfileUuid() != null) {
            row.child(createFaceWidget(profileName, account.getProfileUuid(), account.getProviderName()));
            row.child(new Widget<>().size(2, 16));
        }
        row.child(dot)
            .child(new Widget<>().size(2, 16))
            .child(label);

        entry.child(row);
        entry.onMousePressed(mouseButton -> {
            onSelect.accept(account);
            panel.closeIfOpen();
            return true;
        });

        return entry;
    }

    private ButtonWidget<?> buildSingleplayerClearEntry(long selectedAccountId, ModularPanel panel) {
        boolean isSelected = selectedAccountId < 0L;

        ButtonWidget<?> entry = new ButtonWidget<>();
        entry.widthRel(1.0f)
            .height(16);
        if (isSelected) {
            entry.background(new Rectangle().color(0x44FFFFFF));
        }

        TextWidget<?> label = new TextWidget<>(GuiText.key("wawelauth.gui.common.no_account_selected"));
        label.widthRel(1.0f)
            .heightRel(1.0f)
            .margin(2, 0, 0, 0);

        Row row = new Row();
        row.widthRel(1.0f)
            .heightRel(1.0f)
            .child(new Widget<>().size(2, 16))
            .child(label);

        entry.child(row);
        entry.onMousePressed(mouseButton -> {
            SingleplayerAccountPersistence.clearSelection();
            panel.closeIfOpen();
            return true;
        });
        return entry;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }

    private static AccountStatus getLiveStatus(ClientAccount account) {
        if (account == null) return null;
        WawelClient client = WawelClient.instance();
        if (client == null) return account.getStatus();
        AccountStatus cached = client.getAccountManager()
            .getAccountStatus(account.getId());
        if (cached != null) {
            account.setStatus(cached);
            return cached;
        }
        return account.getStatus();
    }

    private static String sortKeyForAccount(ClientAccount account) {
        if (account == null || account.getProfileName() == null) {
            return "";
        }
        return account.getProfileName();
    }

    private static Widget<?> createFaceWidget(String displayName, java.util.UUID profileUuid, String providerName) {
        return new FaceWidget(displayName, profileUuid, providerName).size(8, 8)
            .margin(0, 4);
    }

    private static final class ParsedProxyEndpoint {

        private String host;
        private Integer port;
    }

    private static final class ServerProxyProbeResult {

        private ProviderRegistry.ProbeOutcome proxyOutcome = ProviderRegistry.ProbeOutcome.NEUTRAL;
        private String proxyText = GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested");
        private ProviderRegistry.ProbeOutcome serverOutcome = ProviderRegistry.ProbeOutcome.NEUTRAL;
        private String serverText = GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested");
    }

}
