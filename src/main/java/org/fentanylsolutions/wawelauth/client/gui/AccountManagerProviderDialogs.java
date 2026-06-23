package org.fentanylsolutions.wawelauth.client.gui;

import java.util.Collections;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;
import org.fentanylsolutions.wawelauth.wawelclient.ProviderRegistry;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

final class AccountManagerProviderDialogs {

    private static final int DIALOG_WIDTH = 286;
    private static final int PROVIDER_SETTINGS_DIALOG_HEIGHT = 164;
    private static final int MANAGED_PROVIDER_SETTINGS_DIALOG_HEIGHT = 96;
    private static final int PROVIDER_DELETE_DIALOG_WIDTH = 260;
    private static final int PROVIDER_DELETE_DIALOG_HEIGHT = 106;
    private static final int ROOT_PADDING = 10;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int STATUS_MAX_WIDTH_PX = DIALOG_WIDTH - ROOT_PADDING * 2 - 4;

    private final AccountManagerScreenState state;
    private final Runnable clearSelectedAccountAndPreview;
    private final Runnable rebuildProviderList;
    private final Runnable rebuildAccountList;
    private final Consumer<ClientProvider> openProviderProxyDialog;
    private final Runnable openProviderDeleteDialog;
    private final Runnable invalidateProviderSettingsDialog;
    private final Runnable closeProviderSettingsDialog;

    AccountManagerProviderDialogs(AccountManagerScreenState state, Runnable clearSelectedAccountAndPreview,
        Runnable rebuildProviderList, Runnable rebuildAccountList, Consumer<ClientProvider> openProviderProxyDialog,
        Runnable openProviderDeleteDialog, Runnable invalidateProviderSettingsDialog,
        Runnable closeProviderSettingsDialog) {
        this.state = state;
        this.clearSelectedAccountAndPreview = clearSelectedAccountAndPreview;
        this.rebuildProviderList = rebuildProviderList;
        this.rebuildAccountList = rebuildAccountList;
        this.openProviderProxyDialog = openProviderProxyDialog;
        this.openProviderDeleteDialog = openProviderDeleteDialog;
        this.invalidateProviderSettingsDialog = invalidateProviderSettingsDialog;
        this.closeProviderSettingsDialog = closeProviderSettingsDialog;
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

        final String[] persistedName = { provider.getName() };
        final boolean managedProvider = provider.getType() != ProviderType.CUSTOM;
        final String[] statusText = { "" };

        TabTextFieldWidget nameField = new TabTextFieldWidget();
        nameField.hintText(GuiText.tr("wawelauth.gui.account_manager.provider_name"));
        nameField.value(new StringValue(persistedName[0]));
        nameField.widthRel(1.0f)
            .height(FIELD_HEIGHT)
            .setMaxLength(32);
        nameField.setEnabled(!managedProvider);
        WawelAuthStyle.textField(nameField);

        BooleanSupplier dirty = () -> !managedProvider && isProviderNameDirty(nameField, persistedName[0]);

        Runnable applyNameChange = () -> {
            if (managedProvider) {
                statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_managed_rename_forbidden");
                return;
            }

            String newName = nameField.getText()
                .trim();
            if (newName.isEmpty()) {
                statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_name_empty");
                return;
            }

            try {
                String oldName = persistedName[0];
                client.getProviderRegistry()
                    .renameProvider(oldName, newName);

                persistedName[0] = newName;
                state.pendingProviderSettingsName = newName;
                ClientProvider current = state.selectedProvider;
                if (current != null && oldName.equals(current.getName())) {
                    state.selectedProvider = client.getProviderRegistry()
                        .getProvider(newName);
                }

                rebuildProviderList.run();
                rebuildAccountList.run();
                nameField.setText(newName);
                statusText[0] = "";
            } catch (Exception e) {
                statusText[0] = e.getMessage();
                WawelAuth.debug("Provider rename failed: " + e.getMessage());
            }
        };

        ButtonWidget<?> applyBtn = new ButtonWidget<>();
        applyBtn.size(64, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                if (!dirty.getAsBoolean()) return false;
                applyNameChange.run();
                return true;
            });
        WawelAuthStyle.textButton(applyBtn, 56, "wawelauth.gui.account_manager.apply", dirty);

        ButtonWidget<?> resetBtn = new ButtonWidget<>();
        resetBtn.size(64, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                if (!dirty.getAsBoolean()) return false;
                nameField.setText(persistedName[0]);
                statusText[0] = managedProvider ? GuiText.tr("wawelauth.gui.account_manager.provider_managed_locked")
                    : "";
                return true;
            });
        WawelAuthStyle.textButton(resetBtn, 56, "wawelauth.gui.common.reset", dirty);

        ButtonWidget<?> deleteProviderBtn = new ButtonWidget<>();
        deleteProviderBtn.size(98, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                if (managedProvider) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_managed_remove_forbidden");
                    return true;
                }

                state.pendingProviderDeleteName = persistedName[0];
                openProviderDeleteDialog.run();
                return true;
            });
        WawelAuthStyle.dangerTextButton(
            deleteProviderBtn,
            90,
            "wawelauth.gui.account_manager.delete_provider",
            () -> !managedProvider);
        deleteProviderBtn.setEnabled(!managedProvider);

        ButtonWidget<?> proxySettingsBtn = new ButtonWidget<>();
        proxySettingsBtn.size(98, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                if (BuiltinProviders.isOfflineProvider(persistedName[0])) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_not_supported_offline");
                    return true;
                }
                ClientProvider currentProvider = client.getProviderRegistry()
                    .getProvider(persistedName[0]);
                if (currentProvider == null) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_gone");
                    return true;
                }
                openProviderProxyDialog.accept(currentProvider);
                return true;
            });
        WawelAuthStyle.textButton(proxySettingsBtn, 90, "wawelauth.gui.account_manager.proxy_settings");
        proxySettingsBtn.setEnabledIf(widget -> !BuiltinProviders.isOfflineProvider(persistedName[0]));

        ButtonWidget<?> doneBtn = new ButtonWidget<>();
        doneBtn.size(64, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                if (dirty.getAsBoolean()) return false;
                state.pendingProviderSettingsName = null;
                dialog.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(doneBtn, 56, "gui.done", () -> !dirty.getAsBoolean());

        nameField.onEnterPressed(() -> {
            if (dirty.getAsBoolean()) {
                applyNameChange.run();
            }
        });

        Column root = dialogRoot();
        root.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.provider_settings")).widthRel(1.0f)
                .height(14)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText.tr(
                            "wawelauth.gui.account_manager.provider_line",
                            ProviderDisplayName.displayName(persistedName[0])))).color(WawelAuthStyle.TEXT_SECONDARY)
                                .scale(0.8f)
                                .widthRel(1.0f)
                                .height(10));
        if (managedProvider) {
            root.child(new Widget<>().size(1, 3))
                .child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.provider_builtin_config_note"))
                        .color(WawelAuthStyle.TEXT_SECONDARY)
                        .scale(0.8f)
                        .widthRel(1.0f)
                        .height(10))
                .child(new Widget<>().size(1, 14))
                .child(
                    managedProviderSettingsActionRow(
                        proxySettingsBtn,
                        doneBtn,
                        !BuiltinProviders.isOfflineProvider(persistedName[0])));
        } else {
            root.child(new Widget<>().size(1, 8))
                .child(nameField)
                .child(new Widget<>().size(1, 8))
                .child(statusText(statusText))
                .child(new Widget<>().size(1, 10))
                .child(
                    new Row().widthRel(1.0f)
                        .height(BUTTON_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(deleteProviderBtn)
                        .child(new Widget<>().size(8, BUTTON_HEIGHT))
                        .child(proxySettingsBtn))
                .child(new Widget<>().size(1, 8))
                .child(providerSettingsActionRow(applyBtn, resetBtn, doneBtn));
        }

        WawelAuthStyle.dialog(dialog);
        dialog
            .size(
                DIALOG_WIDTH,
                managedProvider ? MANAGED_PROVIDER_SETTINGS_DIALOG_HEIGHT : PROVIDER_SETTINGS_DIALOG_HEIGHT)
            .child(root);

        return dialog;
    }

    Dialog<Boolean> buildProviderDeleteDialog() {
        WawelClient client = WawelClient.instance();
        if (client == null || state.pendingProviderDeleteName == null) {
            state.pendingProviderDeleteName = null;
            return unavailableDialog(
                "wawelauth_confirm_delete_provider",
                "wawelauth.gui.account_manager.provider_not_available");
        }

        final String providerName = state.pendingProviderDeleteName;
        ClientProvider provider = client.getProviderRegistry()
            .getProvider(providerName);
        if (provider == null) {
            state.pendingProviderDeleteName = null;
            return unavailableDialog(
                "wawelauth_confirm_delete_provider",
                "wawelauth.gui.account_manager.provider_gone");
        }
        if (provider.getType() != ProviderType.CUSTOM) {
            state.pendingProviderDeleteName = null;
            return unavailableDialog(
                "wawelauth_confirm_delete_provider",
                "wawelauth.gui.account_manager.provider_managed_remove_forbidden");
        }

        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_delete_provider");
        dialog.setCloseOnOutOfBoundsClick(false);
        dialog.onCloseAction(() -> state.pendingProviderDeleteName = null);

        String[] statusText = { "" };
        int titleMaxWidthPx = PROVIDER_DELETE_DIALOG_WIDTH - ROOT_PADDING * 2 - 4;

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        cancelBtn.size(64, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                state.pendingProviderDeleteName = null;
                dialog.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(cancelBtn, 56, "wawelauth.gui.common.cancel");

        ButtonWidget<?> deleteBtn = new ButtonWidget<>();
        deleteBtn.size(64, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                try {
                    client.getProviderRegistry()
                        .removeProvider(providerName);

                    ClientProvider current = state.selectedProvider;
                    if (current != null && providerName.equals(current.getName())) {
                        state.selectedProvider = null;
                        clearSelectedAccountAndPreview.run();
                    }

                    state.pendingProviderDeleteName = null;
                    state.pendingProviderSettingsName = null;
                    rebuildProviderList.run();
                    rebuildAccountList.run();
                    dialog.closeIfOpen();
                    closeProviderSettingsDialog.run();
                } catch (Exception e) {
                    statusText[0] = e.getMessage();
                    WawelAuth.debug("Provider deletion failed: " + e.getMessage());
                }
                return true;
            });
        WawelAuthStyle.dangerTextButton(deleteBtn, 56, "wawelauth.gui.common.delete");

        Column root = dialogRoot();
        root.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> GuiText.ellipsizeToPixelWidth(
                        GuiText.tr(
                            "wawelauth.gui.account_manager.delete_provider_title",
                            ProviderDisplayName.displayName(providerName)),
                        titleMaxWidthPx))).tooltipDynamic(tooltip -> {
                            String title = GuiText.tr(
                                "wawelauth.gui.account_manager.delete_provider_title",
                                ProviderDisplayName.displayName(providerName));
                            if (!GuiText.ellipsizeToPixelWidth(title, titleMaxWidthPx)
                                .equals(title)) {
                                tooltip.addLine(IKey.str(title));
                            }
                        })
                            .tooltipAutoUpdate(true)
                            .widthRel(1.0f)
                            .height(14)
                            .color(WawelAuthStyle.THEME_LIGHTER))
            .child(new Widget<>().size(1, 6))
            .child(
                new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.delete_provider_warning"))
                    .color(WawelAuthStyle.TEXT_SECONDARY)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10))
            .child(new Widget<>().size(1, 8))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText
                            .ellipsizeToPixelWidth(statusText[0] != null ? statusText[0] : "", titleMaxWidthPx)))
                                .color(WawelAuthStyle.DANGER)
                                .widthRel(1.0f)
                                .height(12))
            .child(new Widget<>().size(1, 8))
            .child(
                new Row().widthRel(1.0f)
                    .height(BUTTON_HEIGHT)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(cancelBtn)
                    .child(new Widget<>().size(8, BUTTON_HEIGHT))
                    .child(deleteBtn));

        WawelAuthStyle.dialog(dialog);
        dialog.size(PROVIDER_DELETE_DIALOG_WIDTH, PROVIDER_DELETE_DIALOG_HEIGHT)
            .child(root);
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
        cfg.height = 236;
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
        };
        cfg.onClose = () -> state.pendingProviderProxyName = null;
        return ProxySettingsDialog.build(cfg);
    }

    private static Column dialogRoot() {
        Column root = new Column();
        root.widthRel(1.0f)
            .heightRel(1.0f)
            .padding(ROOT_PADDING)
            .background(IDrawable.EMPTY)
            .disableHoverBackground();
        return root;
    }

    private static TextWidget<?> statusText(String[] statusText) {
        return new TextWidget<>(
            IKey.dynamic(
                () -> GuiText.ellipsizeToPixelWidth(statusText[0] != null ? statusText[0] : "", STATUS_MAX_WIDTH_PX)))
                    .tooltipDynamic(tooltip -> {
                        String fullText = statusText[0];
                        if (fullText != null && !GuiText.ellipsizeToPixelWidth(fullText, STATUS_MAX_WIDTH_PX)
                            .equals(fullText)) {
                            tooltip.addLine(IKey.str(fullText));
                        }
                    })
                    .tooltipAutoUpdate(true)
                    .color(WawelAuthStyle.WARNING)
                    .widthRel(1.0f)
                    .height(12);
    }

    private static Row providerSettingsActionRow(ButtonWidget<?> applyBtn, ButtonWidget<?> resetBtn,
        ButtonWidget<?> doneBtn) {
        Row row = new Row();
        row.widthRel(1.0f)
            .height(BUTTON_HEIGHT)
            .mainAxisAlignment(Alignment.MainAxis.CENTER);
        row.child(applyBtn)
            .child(new Widget<>().size(8, BUTTON_HEIGHT))
            .child(resetBtn)
            .child(new Widget<>().size(8, BUTTON_HEIGHT))
            .child(doneBtn);
        return row;
    }

    private static Row managedProviderSettingsActionRow(ButtonWidget<?> proxySettingsBtn, ButtonWidget<?> doneBtn,
        boolean showProxySettings) {
        Row row = new Row();
        row.widthRel(1.0f)
            .height(BUTTON_HEIGHT)
            .mainAxisAlignment(Alignment.MainAxis.END);
        if (showProxySettings) {
            row.child(proxySettingsBtn)
                .child(new Widget<>().size(8, BUTTON_HEIGHT));
        }
        row.child(doneBtn);
        return row;
    }

    private static boolean isProviderNameDirty(TabTextFieldWidget nameField, String persistedName) {
        return !trimProviderName(nameField).equals(trimProviderName(persistedName));
    }

    private static String trimProviderName(TabTextFieldWidget field) {
        return trimProviderName(field.getText());
    }

    private static String trimProviderName(String name) {
        return name != null ? name.trim() : "";
    }

    private Dialog<Boolean> unavailableDialog(String dialogName, String messageKey) {
        Dialog<Boolean> dialog = new Dialog<>(dialogName);
        dialog.setCloseOnOutOfBoundsClick(false);
        return ProxySettingsDialog.messageDialog(dialog, messageKey);
    }
}
