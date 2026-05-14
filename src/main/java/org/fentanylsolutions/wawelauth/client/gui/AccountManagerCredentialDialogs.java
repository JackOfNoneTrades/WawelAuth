package org.fentanylsolutions.wawelauth.client.gui;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

final class AccountManagerCredentialDialogs {

    private final AccountManagerScreenState state;
    private final Predicate<ClientProvider> credentialManagementSupported;
    private final Runnable openCredentialDeleteDialog;
    private final Runnable clearPreview;
    private final Runnable rebuildAccountList;
    private final Runnable requestAccountListRebuild;

    AccountManagerCredentialDialogs(AccountManagerScreenState state,
        Predicate<ClientProvider> credentialManagementSupported, Runnable openCredentialDeleteDialog,
        Runnable clearPreview, Runnable rebuildAccountList, Runnable requestAccountListRebuild) {
        this.state = state;
        this.credentialManagementSupported = credentialManagementSupported;
        this.openCredentialDeleteDialog = openCredentialDeleteDialog;
        this.clearPreview = clearPreview;
        this.rebuildAccountList = rebuildAccountList;
        this.requestAccountListRebuild = requestAccountListRebuild;
    }

    Dialog<Boolean> buildCredentialDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_credentials");
        dialog.setCloseOnOutOfBoundsClick(false);
        WawelClient client = WawelClient.instance();
        ClientAccount account = state.selectedAccount;

        String profileName = account != null && account.getProfileName() != null ? account.getProfileName()
            : GuiText.tr("wawelauth.gui.common.account");
        String providerName = account != null ? account.getProviderName() : null;
        ClientProvider provider = client != null && providerName != null ? client.getProviderRegistry()
            .getProvider(providerName) : null;
        boolean credentialSupported = credentialManagementSupported.test(provider);

        if (account == null || client == null || !credentialSupported) {
            String reason = account == null ? GuiText.tr("wawelauth.gui.common.no_account_selected")
                : GuiText.tr("wawelauth.gui.account_manager.credentials_unavailable");
            dialog.size(258, 96)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.credentials")).widthRel(1.0f)
                                .height(14))
                        .child(
                            new TextWidget<>(IKey.str(reason)).color(0xFFFFAA55)
                                .widthRel(1.0f)
                                .height(12)
                                .margin(0, 8))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
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

        PasswordInputWidget currentPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.current_password"));
        PasswordInputWidget newPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.new_password"));
        PasswordInputWidget confirmPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.confirm_new_password"));
        String[] statusText = { "" };
        boolean[] busy = { false };

        ButtonWidget<?> changePasswordBtn = new ButtonWidget<>();
        changePasswordBtn.size(100, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;

                String currentPassword = currentPasswordField.getText();
                String newPassword = newPasswordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (currentPassword == null || currentPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.current_required");
                    return true;
                }
                if (newPassword == null || newPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.new_required");
                    return true;
                }
                if (!newPassword.equals(confirmPassword)) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.mismatch");
                    return true;
                }
                if (newPassword.equals(currentPassword)) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.same_password");
                    return true;
                }

                busy[0] = true;
                statusText[0] = GuiText.tr("wawelauth.gui.credentials.status.updating");

                client.getAccountManager()
                    .changePassword(account.getId(), currentPassword, newPassword)
                    .whenComplete((ignored, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> {
                                busy[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    statusText[0] = cause.getMessage();
                                    WawelAuth.debug("Password change failed: " + cause.getMessage());
                                    return;
                                }
                                statusText[0] = GuiText.tr("wawelauth.gui.credentials.status.changed");
                                currentPasswordField.setText("");
                                newPasswordField.setText("");
                                confirmPasswordField.setText("");
                            });
                    });
                return true;
            });
        GuiText.fitButtonLabel(changePasswordBtn, 100, "wawelauth.gui.credentials.change_password");

        ButtonWidget<?> deleteServerAccountBtn = new ButtonWidget<>();
        deleteServerAccountBtn.size(106, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;

                String currentPassword = currentPasswordField.getText();
                if (currentPassword == null || currentPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.delete_requires_password");
                    return true;
                }

                state.pendingCredentialDeleteAccountId = account.getId();
                state.pendingCredentialDeleteAccountName = account.getProfileName() != null ? account.getProfileName()
                    : GuiText.tr("wawelauth.gui.common.account");
                state.pendingCredentialDeletePassword = currentPassword;
                dialog.closeIfOpen();
                openCredentialDeleteDialog.run();
                return true;
            });
        GuiText.fitButtonLabel(deleteServerAccountBtn, 106, "wawelauth.gui.credentials.delete_account");

        ButtonWidget<?> closeBtn = new ButtonWidget<>();
        closeBtn.size(70, 18)
            .onMousePressed(btn -> {
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        dialog.size(266, 177)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.credentials")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(
                            GuiText.key(
                                "wawelauth.gui.credentials.selected",
                                profileName,
                                ProviderDisplayName.displayName(providerName))).color(0xFFAAAAAA)
                                    .scale(0.8f)
                                    .widthRel(1.0f)
                                    .height(10))
                    .child(
                        currentPasswordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 4))
                    .child(
                        newPasswordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 3))
                    .child(
                        confirmPasswordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 3))
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
                            .child(changePasswordBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(deleteServerAccountBtn))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(closeBtn)));
        return dialog;
    }

    Dialog<Boolean> buildCredentialDeleteDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_delete_server_account");
        dialog.setCloseOnOutOfBoundsClick(false);

        long accountId = state.pendingCredentialDeleteAccountId;
        String accountName = state.pendingCredentialDeleteAccountName != null ? state.pendingCredentialDeleteAccountName
            : GuiText.tr("wawelauth.gui.common.account");
        String currentPassword = state.pendingCredentialDeletePassword;
        WawelClient client = WawelClient.instance();

        String[] statusText = { "" };
        boolean[] busy = { false };

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        cancelBtn.size(62, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;
                clearPendingDeleteState();
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(cancelBtn, 62, "wawelauth.gui.common.cancel");

        ButtonWidget<?> deleteBtn = new ButtonWidget<>();
        deleteBtn.size(62, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;

                if (client == null) {
                    statusText[0] = GuiText.tr("wawelauth.gui.common.client_not_running");
                    return true;
                }
                if (accountId < 0L) {
                    statusText[0] = GuiText.tr("wawelauth.gui.common.no_account_selected");
                    return true;
                }
                if (currentPassword == null || currentPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.current_required");
                    return true;
                }

                busy[0] = true;
                statusText[0] = GuiText.tr("wawelauth.gui.credentials.status.deleting");

                client.getAccountManager()
                    .deleteWawelAuthAccount(accountId, currentPassword)
                    .whenComplete((ignored, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> {
                                busy[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    statusText[0] = cause.getMessage();
                                    WawelAuth.debug("Server account deletion failed: " + cause.getMessage());
                                    return;
                                }

                                clearPendingDeleteState();
                                ClientAccount current = state.selectedAccount;
                                if (current != null && current.getId() == accountId) {
                                    state.selectedAccount = null;
                                    clearPreview.run();
                                }
                                ServerBindingPersistence.clearMissingAccountBindings(client.getAccountManager());
                                rebuildAccountList.run();
                                requestAccountListRebuild.run();
                                state.textureUploadStatus = GuiText.tr("wawelauth.gui.credentials.status.deleted");
                                dialog.closeIfOpen();
                            });
                    });
                return true;
            });
        GuiText.fitButtonLabel(deleteBtn, 62, "wawelauth.gui.common.delete");

        dialog.size(270, 104)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.credentials.delete_title", accountName))
                            .widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.credentials.delete_warning")).color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10))
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 6))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(cancelBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(deleteBtn)));
        return dialog;
    }

    boolean hasPendingDelete() {
        return state.pendingCredentialDeleteAccountId >= 0L;
    }

    void clearPendingDeleteState() {
        state.pendingCredentialDeleteAccountId = -1L;
        state.pendingCredentialDeleteAccountName = null;
        state.pendingCredentialDeletePassword = null;
    }
}
