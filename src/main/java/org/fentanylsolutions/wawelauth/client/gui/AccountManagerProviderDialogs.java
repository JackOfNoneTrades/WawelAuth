package org.fentanylsolutions.wawelauth.client.gui;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.ProviderRegistry;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

final class AccountManagerProviderDialogs {

    private static final int DETAIL_SECONDARY_TEXT_COLOR = 0xFF555555;

    private final AccountManagerScreenState state;
    private final Runnable clearSelectedAccountAndPreview;
    private final Runnable rebuildProviderList;
    private final Runnable rebuildAccountList;
    private final Consumer<ClientProvider> openProviderProxyDialog;
    private final Runnable invalidateProviderSettingsDialog;

    AccountManagerProviderDialogs(AccountManagerScreenState state, Runnable clearSelectedAccountAndPreview,
        Runnable rebuildProviderList, Runnable rebuildAccountList, Consumer<ClientProvider> openProviderProxyDialog,
        Runnable invalidateProviderSettingsDialog) {
        this.state = state;
        this.clearSelectedAccountAndPreview = clearSelectedAccountAndPreview;
        this.rebuildProviderList = rebuildProviderList;
        this.rebuildAccountList = rebuildAccountList;
        this.openProviderProxyDialog = openProviderProxyDialog;
        this.invalidateProviderSettingsDialog = invalidateProviderSettingsDialog;
    }

    Dialog<Boolean> buildProviderSettingsDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_provider_settings");
        dialog.setCloseOnOutOfBoundsClick(false);

        WawelClient client = WawelClient.instance();
        if (client == null || state.pendingProviderSettingsName == null) {
            return unavailableDialog(dialog, "wawelauth.gui.account_manager.provider_not_available");
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(state.pendingProviderSettingsName);
        if (provider == null) {
            return unavailableDialog(dialog, "wawelauth.gui.account_manager.provider_gone");
        }

        final String oldName = provider.getName();
        final boolean managedProvider = provider.getType() != ProviderType.CUSTOM;
        final String[] statusText = {
            managedProvider ? GuiText.tr("wawelauth.gui.account_manager.provider_managed_locked") : "" };

        TextFieldWidget nameField = new SafeTextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.provider_name"));
        nameField.widthRel(1.0f)
            .height(18)
            .setMaxLength(32)
            .margin(0, 2);
        nameField.setText(oldName);
        nameField.setEnabled(!managedProvider);

        ButtonWidget<?> saveNameBtn = new ButtonWidget<>();
        saveNameBtn.size(88, 18)
            .onMousePressed(btn -> {
                if (managedProvider) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_managed_rename_forbidden");
                    return true;
                }

                String newName = nameField.getText()
                    .trim();
                if (newName.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_name_empty");
                    return true;
                }

                try {
                    client.getProviderRegistry()
                        .renameProvider(oldName, newName);

                    state.pendingProviderSettingsName = newName;
                    ClientProvider current = state.selectedProvider;
                    if (current != null && oldName.equals(current.getName())) {
                        state.selectedProvider = client.getProviderRegistry()
                            .getProvider(newName);
                    }

                    rebuildProviderList.run();
                    rebuildAccountList.run();
                    dialog.closeIfOpen();
                } catch (Exception e) {
                    statusText[0] = e.getMessage();
                    WawelAuth.debug("Provider rename failed: " + e.getMessage());
                }
                return true;
            });
        GuiText.fitButtonLabel(saveNameBtn, 88, "wawelauth.gui.account_manager.save_name");
        saveNameBtn.setEnabled(!managedProvider);

        ButtonWidget<?> deleteProviderBtn = new ButtonWidget<>();
        deleteProviderBtn.size(98, 18)
            .onMousePressed(btn -> {
                if (managedProvider) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_managed_remove_forbidden");
                    return true;
                }

                try {
                    client.getProviderRegistry()
                        .removeProvider(oldName);

                    ClientProvider current = state.selectedProvider;
                    if (current != null && oldName.equals(current.getName())) {
                        state.selectedProvider = null;
                        clearSelectedAccountAndPreview.run();
                    }

                    state.pendingProviderSettingsName = null;
                    rebuildProviderList.run();
                    rebuildAccountList.run();
                    dialog.closeIfOpen();
                } catch (Exception e) {
                    statusText[0] = e.getMessage();
                    WawelAuth.debug("Provider deletion failed: " + e.getMessage());
                }
                return true;
            });
        GuiText.fitButtonLabel(deleteProviderBtn, 98, "wawelauth.gui.account_manager.delete_provider");
        deleteProviderBtn.setEnabled(!managedProvider);

        boolean offlineBuiltin = BuiltinProviders.isOfflineProvider(oldName);
        ButtonWidget<?> proxySettingsBtn = new ButtonWidget<>();
        proxySettingsBtn.size(88, 18)
            .onMousePressed(btn -> {
                if (offlineBuiltin) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_not_supported_offline");
                    return true;
                }
                openProviderProxyDialog.accept(provider);
                return true;
            });
        GuiText.fitButtonLabel(proxySettingsBtn, 88, "wawelauth.gui.account_manager.proxy_settings");
        proxySettingsBtn.setEnabled(!offlineBuiltin);

        ButtonWidget<?> closeBtn = new ButtonWidget<>();
        closeBtn.size(70, 18)
            .onMousePressed(btn -> {
                state.pendingProviderSettingsName = null;
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        dialog.size(286, 154)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.provider_settings")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(
                            GuiText.key(
                                "wawelauth.gui.account_manager.provider_line",
                                ProviderDisplayName.displayName(oldName))).color(0xFFAAAAAA)
                                    .scale(0.8f)
                                    .widthRel(1.0f)
                                    .height(10))
                    .child(nameField)
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 4)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(saveNameBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(proxySettingsBtn))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(deleteProviderBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(closeBtn)));

        return dialog;
    }

    Dialog<Boolean> buildProviderProxyDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_provider_proxy");
        dialog.setCloseOnOutOfBoundsClick(false);

        WawelClient client = WawelClient.instance();
        if (client == null || state.pendingProviderProxyName == null) {
            return unavailableDialog(dialog, "wawelauth.gui.account_manager.provider_not_available");
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(state.pendingProviderProxyName);
        if (provider == null) {
            return unavailableDialog(dialog, "wawelauth.gui.account_manager.provider_gone");
        }

        ProviderProxySettings initialSettings = provider.getProxySettings();
        final boolean[] proxyEnabled = { initialSettings.isEnabled() };
        final ProviderProxyType[] proxyType = { initialSettings.getType() };
        final String[] proxyStatusText = { GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested") };
        final ProviderRegistry.ProbeOutcome[] proxyStatusOutcome = { ProviderRegistry.ProbeOutcome.NEUTRAL };
        final String[] providerStatusText = { BuiltinProviders.isOfflineProvider(provider.getName())
            ? GuiText.tr("wawelauth.gui.account_manager.proxy_not_supported_offline")
            : GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested") };
        final ProviderRegistry.ProbeOutcome[] providerStatusOutcome = { ProviderRegistry.ProbeOutcome.NEUTRAL };
        final boolean[] busy = { false };

        TextFieldWidget hostField = new SafeTextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.proxy_address"));
        hostField.width(214)
            .height(18)
            .setMaxLength(255)
            .margin(0, 2);
        hostField.value(new StringValue(initialSettings.getHost() != null ? initialSettings.getHost() : ""));

        TextFieldWidget portField = new SafeTextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.proxy_port"));
        portField.width(64)
            .height(18)
            .setMaxLength(5)
            .margin(0, 2);
        portField
            .value(new StringValue(initialSettings.getPort() != null ? String.valueOf(initialSettings.getPort()) : ""));

        TextFieldWidget usernameField = new SafeTextFieldWidget()
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
                    ProviderProxySettings formSettings = readProxySettingsFromForm(
                        proxyEnabled[0],
                        true,
                        proxyType[0],
                        hostField,
                        portField,
                        usernameField,
                        passwordField);
                    busy[0] = true;
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    providerStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    proxyStatusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_testing_proxy");
                    providerStatusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_testing_provider");

                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return client.getProviderRegistry()
                                .probeProviderConnection(provider.getName(), formSettings);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                        .whenComplete(
                            (probeResult, err) -> Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    busy[0] = false;
                                    if (err != null) {
                                        proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                                        proxyStatusText[0] = GuiText
                                            .tr("wawelauth.gui.account_manager.proxy_status_not_tested");
                                        providerStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                                        providerStatusText[0] = formatThrowableMessage(err);
                                        return;
                                    }
                                    proxyStatusOutcome[0] = probeResult.getProxyStatus()
                                        .getOutcome();
                                    proxyStatusText[0] = probeResult.getProxyStatus()
                                        .getMessage();
                                    providerStatusOutcome[0] = probeResult.getProviderApiStatus()
                                        .getOutcome();
                                    providerStatusText[0] = probeResult.getProviderApiStatus()
                                        .getMessage();
                                }));
                } catch (Exception e) {
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                    proxyStatusText[0] = formatThrowableMessage(e);
                    providerStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    providerStatusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested");
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
                    ProviderProxySettings formSettings = readProxySettingsFromForm(
                        proxyEnabled[0],
                        false,
                        proxyType[0],
                        hostField,
                        portField,
                        usernameField,
                        passwordField);
                    client.getProviderRegistry()
                        .updateProxySettings(provider.getName(), formSettings);
                    ClientProvider current = state.selectedProvider;
                    if (current != null && provider.getName()
                        .equals(current.getName())) {
                        state.selectedProvider = client.getProviderRegistry()
                            .getProvider(provider.getName());
                    }
                    invalidateProviderSettingsDialog.run();
                    state.pendingProviderProxyName = null;
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
                state.pendingProviderProxyName = null;
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        dialog.size(300, 224)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.proxy_settings")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(
                            GuiText.key(
                                "wawelauth.gui.account_manager.provider_line",
                                ProviderDisplayName.displayName(provider.getName()))).color(0xFFAAAAAA)
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
                                () -> unsupportedHttpProxyAuthNote(proxyType[0], usernameField, passwordField)))
                                    .color(0xFFFF5555)
                                    .widthRel(1.0f)
                                    .height(10)
                                    .margin(0, 2))
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
                                () -> GuiText.tr(
                                    "wawelauth.gui.account_manager.proxy_status_provider_api",
                                    providerStatusText[0]))).color(() -> probeOutcomeColor(providerStatusOutcome[0]))
                                        .widthRel(1.0f)
                                        .height(12)
                                        .margin(0, 1))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 7)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(testBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(saveBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(closeBtn)));

        return dialog;
    }

    private Dialog<Boolean> unavailableDialog(Dialog<Boolean> dialog, String messageKey) {
        dialog.size(230, 90)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key(messageKey)).widthRel(1.0f)
                            .height(14))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 6)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(
                                GuiText
                                    .fitButtonLabel(new ButtonWidget<>().size(70, 18), 70, "wawelauth.gui.common.close")
                                    .onMousePressed(btn -> {
                                        dialog.closeIfOpen();
                                        return true;
                                    }))));
        return dialog;
    }

    private ProviderProxySettings readProxySettingsFromForm(boolean proxyEnabled, boolean ignoreEnabledToggle,
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
        return settings;
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
            return Integer.valueOf(portText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(GuiText.tr("wawelauth.gui.account_manager.proxy_port_invalid"));
        }
    }

    private String formatThrowableMessage(Throwable throwable) {
        String fallback = null;
        Throwable current = throwable;
        while (current != null) {
            String message = trimToNull(current.getMessage());
            boolean wrapper = current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException
                || current.getClass() == RuntimeException.class;
            if (message != null) {
                if (!wrapper) {
                    return message;
                }
                if (fallback == null) {
                    fallback = message;
                }
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return fallback != null ? fallback : (throwable != null ? throwable.toString() : "");
    }

    private static int probeOutcomeColor(ProviderRegistry.ProbeOutcome outcome) {
        if (outcome == ProviderRegistry.ProbeOutcome.SUCCESS) {
            return 0xFF55FF55;
        }
        if (outcome == ProviderRegistry.ProbeOutcome.ERROR) {
            return 0xFFFF5555;
        }
        return DETAIL_SECONDARY_TEXT_COLOR;
    }

    private static String unsupportedHttpProxyAuthNote(ProviderProxyType proxyType, TextFieldWidget usernameField,
        PasswordInputWidget passwordField) {
        if (proxyType != ProviderProxyType.HTTP || ProviderProxySupport.isModernHttpProxyAuthAvailable()) {
            return "";
        }
        if (trimToNull(usernameField.getText()) == null && trimToNull(passwordField.getText()) == null) {
            return "";
        }
        return GuiText.tr("wawelauth.gui.account_manager.proxy_java8_basic_auth_unsupported");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class ParsedProxyEndpoint {

        private String host;
        private Integer port;
    }
}
