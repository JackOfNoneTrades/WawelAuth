package org.fentanylsolutions.wawelauth.client.gui;

import java.util.Collections;
import java.util.function.Consumer;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.ProviderRegistry;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

final class AccountManagerProviderDialogs {

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
        WawelClient client = WawelClient.instance();
        if (client == null || state.pendingProviderSettingsName == null) {
            return unavailableDialog(
                "wawelauth_provider_settings",
                "wawelauth.gui.account_manager.provider_not_available");
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(state.pendingProviderSettingsName);
        if (provider == null) {
            return unavailableDialog("wawelauth_provider_settings", "wawelauth.gui.account_manager.provider_gone");
        }

        Dialog<Boolean> dialog = new Dialog<>("wawelauth_provider_settings");
        dialog.setCloseOnOutOfBoundsClick(false);

        final String oldName = provider.getName();
        final boolean managedProvider = provider.getType() != ProviderType.CUSTOM;
        final String[] statusText = {
            managedProvider ? GuiText.tr("wawelauth.gui.account_manager.provider_managed_locked") : "" };

        TextFieldWidget nameField = new TextFieldWidget()
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
        WawelClient client = WawelClient.instance();
        if (client == null || state.pendingProviderProxyName == null) {
            return unavailableDialog(
                "wawelauth_provider_proxy",
                "wawelauth.gui.account_manager.provider_not_available");
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(state.pendingProviderProxyName);
        if (provider == null) {
            return unavailableDialog("wawelauth_provider_proxy", "wawelauth.gui.account_manager.provider_gone");
        }

        ProxySettingsDialog.Config cfg = new ProxySettingsDialog.Config();
        cfg.name = "wawelauth_provider_proxy";
        cfg.height = 224;
        cfg.subjectLines = Collections.singletonList(
            GuiText.key(
                "wawelauth.gui.account_manager.provider_line",
                ProviderDisplayName.displayName(provider.getName())));
        cfg.initialSettings = provider.getProxySettings();
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            cfg.initialSecondaryText = GuiText.tr("wawelauth.gui.account_manager.proxy_not_supported_offline");
        }
        cfg.secondaryStatusKey = "wawelauth.gui.account_manager.proxy_status_provider_api";
        cfg.secondaryTestingText = GuiText.tr("wawelauth.gui.account_manager.proxy_testing_provider");
        cfg.showHttpAuthNote = true;
        cfg.probe = formSettings -> {
            ProxySettingsDialog.ProbeResult result = new ProxySettingsDialog.ProbeResult();
            try {
                ProviderRegistry.ProviderProbeResult probe = client.getProviderRegistry()
                    .probeProviderConnection(provider.getName(), formSettings);
                result.proxyOutcome = probe.getProxyStatus()
                    .getOutcome();
                result.proxyText = probe.getProxyStatus()
                    .getMessage();
                result.secondaryOutcome = probe.getProviderApiStatus()
                    .getOutcome();
                result.secondaryText = probe.getProviderApiStatus()
                    .getMessage();
            } catch (Exception e) {
                result.secondaryOutcome = ProviderRegistry.ProbeOutcome.ERROR;
                result.secondaryText = ProxySettingsDialog.formatThrowableMessage(e);
            }
            return result;
        };
        cfg.saver = formSettings -> {
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
        };
        cfg.onClose = () -> state.pendingProviderProxyName = null;
        return ProxySettingsDialog.build(cfg);
    }

    private Dialog<Boolean> unavailableDialog(String dialogName, String messageKey) {
        Dialog<Boolean> dialog = new Dialog<>(dialogName);
        dialog.setCloseOnOutOfBoundsClick(false);
        return ProxySettingsDialog.messageDialog(dialog, messageKey);
    }
}
