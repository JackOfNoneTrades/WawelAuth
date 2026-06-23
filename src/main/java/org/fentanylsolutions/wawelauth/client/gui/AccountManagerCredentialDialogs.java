package org.fentanylsolutions.wawelauth.client.gui;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

final class AccountManagerCredentialDialogs {

    private static final int DIALOG_WIDTH = 280;
    private static final int UNAVAILABLE_DIALOG_HEIGHT = 98;
    private static final int CREDENTIAL_DIALOG_HEIGHT = 210;
    private static final int DELETE_DIALOG_HEIGHT = 114;
    private static final int ROOT_PADDING = 10;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_TEXT_MAX_WIDTH = 92;
    private static final int STATUS_MAX_WIDTH_PX = DIALOG_WIDTH - ROOT_PADDING * 2 - 4;

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
            ButtonWidget<?> closeBtn = new ButtonWidget<>();
            closeBtn.size(70, BUTTON_HEIGHT)
                .onMousePressed(btn -> {
                    dialog.closeIfOpen();
                    return true;
                });
            WawelAuthStyle.textButton(closeBtn, 62, "wawelauth.gui.common.close");

            Column root = dialogRoot();
            root.child(
                new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.credentials")).widthRel(1.0f)
                    .height(14)
                    .color(WawelAuthStyle.THEME_LIGHTER))
                .child(new Widget<>().size(1, 8))
                .child(
                    new TextWidget<>(IKey.str(reason)).color(WawelAuthStyle.DANGER)
                        .widthRel(1.0f)
                        .height(12))
                .child(new Widget<>().size(1, 10))
                .child(
                    new Row().widthRel(1.0f)
                        .height(BUTTON_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(closeBtn));

            WawelAuthStyle.dialog(dialog);
            dialog.size(DIALOG_WIDTH, UNAVAILABLE_DIALOG_HEIGHT)
                .child(root);
            return dialog;
        }

        PasswordInputWidget currentPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.current_password"))
            .applyWawelAuthStyle();
        PasswordInputWidget newPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.new_password"))
            .applyWawelAuthStyle();
        PasswordInputWidget confirmPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.confirm_new_password"))
            .applyWawelAuthStyle();
        String[] statusText = { "" };
        boolean[] busy = { false };

        ButtonWidget<?> changePasswordBtn = new ButtonWidget<>();
        changePasswordBtn.size(100, BUTTON_HEIGHT)
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
        WawelAuthStyle
            .textButton(changePasswordBtn, BUTTON_TEXT_MAX_WIDTH, "wawelauth.gui.credentials.change_password");

        ButtonWidget<?> deleteServerAccountBtn = new ButtonWidget<>();
        deleteServerAccountBtn.size(106, BUTTON_HEIGHT)
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
        WawelAuthStyle.dangerTextButton(deleteServerAccountBtn, 98, "wawelauth.gui.credentials.delete_account");

        ButtonWidget<?> closeBtn = new ButtonWidget<>();
        closeBtn.size(70, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                dialog.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(closeBtn, 62, "wawelauth.gui.common.close");

        String selectedText = GuiText
            .tr("wawelauth.gui.credentials.selected", profileName, ProviderDisplayName.displayName(providerName));
        Column root = dialogRoot();
        root.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.credentials")).widthRel(1.0f)
                .height(14)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(
                new TextWidget<>(IKey.dynamic(() -> GuiText.ellipsizeToPixelWidth(selectedText, STATUS_MAX_WIDTH_PX)))
                    .tooltipDynamic(tooltip -> {
                        if (!GuiText.ellipsizeToPixelWidth(selectedText, STATUS_MAX_WIDTH_PX)
                            .equals(selectedText)) {
                            tooltip.addLine(IKey.str(selectedText));
                        }
                    })
                    .tooltipAutoUpdate(true)
                    .color(WawelAuthStyle.TEXT_SECONDARY)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10))
            .child(new Widget<>().size(1, 8))
            .child(
                currentPasswordField.widthRel(1.0f)
                    .height(FIELD_HEIGHT)
                    .setMaxLength(128))
            .child(new Widget<>().size(1, 7))
            .child(
                newPasswordField.widthRel(1.0f)
                    .height(FIELD_HEIGHT)
                    .setMaxLength(128))
            .child(new Widget<>().size(1, 7))
            .child(
                confirmPasswordField.widthRel(1.0f)
                    .height(FIELD_HEIGHT)
                    .setMaxLength(128))
            .child(new Widget<>().size(1, 8))
            .child(statusText(statusText))
            .child(new Widget<>().size(1, 10))
            .child(
                new Row().widthRel(1.0f)
                    .height(BUTTON_HEIGHT)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(changePasswordBtn)
                    .child(new Widget<>().size(8, BUTTON_HEIGHT))
                    .child(deleteServerAccountBtn))
            .child(new Widget<>().size(1, 8))
            .child(
                new Row().widthRel(1.0f)
                    .height(BUTTON_HEIGHT)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(closeBtn));

        WawelAuthStyle.dialog(dialog);
        dialog.size(DIALOG_WIDTH, CREDENTIAL_DIALOG_HEIGHT)
            .child(root);
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
        cancelBtn.size(64, BUTTON_HEIGHT)
            .onMousePressed(btn -> {
                if (busy[0]) return true;
                clearPendingDeleteState();
                dialog.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(cancelBtn, 56, "wawelauth.gui.common.cancel");

        ButtonWidget<?> deleteBtn = new ButtonWidget<>();
        deleteBtn.size(64, BUTTON_HEIGHT)
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
        WawelAuthStyle.dangerTextButton(deleteBtn, 56, "wawelauth.gui.common.delete");

        Column root = dialogRoot();
        root.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.credentials.delete_title", accountName)).widthRel(1.0f)
                .height(14)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(new Widget<>().size(1, 6))
            .child(
                new TextWidget<>(GuiText.key("wawelauth.gui.credentials.delete_warning"))
                    .color(WawelAuthStyle.TEXT_SECONDARY)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10))
            .child(new Widget<>().size(1, 8))
            .child(statusText(statusText))
            .child(new Widget<>().size(1, 10))
            .child(
                new Row().widthRel(1.0f)
                    .height(BUTTON_HEIGHT)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(cancelBtn)
                    .child(new Widget<>().size(8, BUTTON_HEIGHT))
                    .child(deleteBtn));

        WawelAuthStyle.dialog(dialog);
        dialog.size(DIALOG_WIDTH, DELETE_DIALOG_HEIGHT)
            .child(root);
        return dialog;
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
                    .color(WawelAuthStyle.DANGER)
                    .widthRel(1.0f)
                    .height(12);
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
