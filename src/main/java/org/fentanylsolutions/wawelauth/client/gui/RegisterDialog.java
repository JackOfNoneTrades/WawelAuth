package org.fentanylsolutions.wawelauth.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RegisterDialog {

    private final Consumer<Boolean> onResult;
    private final IPanelHandler panelHandler;
    private String providerName;

    private RegisterDialog(ModularPanel parentPanel, Consumer<Boolean> onResult) {
        this.onResult = onResult;
        this.panelHandler = IPanelHandler.simple(parentPanel, (parent, player) -> {
            String provider = this.providerName != null ? this.providerName : "";
            String providerLabel = ProviderDisplayName.displayName(provider);
            Dialog<Boolean> dialog = new Dialog<>("wawelauth_register", this.onResult);
            dialog.setCloseOnOutOfBoundsClick(false);

            TabTextFieldWidget usernameField = new TabTextFieldWidget();
            usernameField.hintText(GuiText.tr("wawelauth.gui.common.username"));
            PasswordInputWidget passwordField = new PasswordInputWidget()
                .hintText(GuiText.tr("wawelauth.gui.common.password"));
            PasswordInputWidget confirmPasswordField = new PasswordInputWidget()
                .hintText(GuiText.tr("wawelauth.gui.register.hint.confirm_password"));
            TabTextFieldWidget inviteTokenField = new TabTextFieldWidget();
            inviteTokenField.hintText(GuiText.tr("wawelauth.gui.register.hint.invite_token"));

            String[] statusText = { "" };
            boolean[] busy = { false };

            ButtonWidget<?> cancelBtn = new ButtonWidget<>();
            cancelBtn.size(62, 18)
                .onMousePressed(mouseButton -> {
                    if (busy[0]) return true;
                    dialog.closeWith(Boolean.FALSE);
                    return true;
                });
            GuiText.fitButtonLabel(cancelBtn, 62, "wawelauth.gui.common.cancel");

            Runnable doRegister = () -> {
                if (busy[0]) return;

                if (provider.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.register.error.no_provider");
                    return;
                }
                if (ProviderDisplayName.isMicrosoftProvider(provider)) {
                    statusText[0] = GuiText.tr("wawelauth.gui.register.error.microsoft_unsupported");
                    return;
                }

                String username = usernameField.getText()
                    .trim();
                String password = passwordField.getText();
                String confirm = confirmPasswordField.getText();

                if (username.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.register.error.username_required");
                    return;
                }
                if (password.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.register.error.password_required");
                    return;
                }
                if (!password.equals(confirm)) {
                    statusText[0] = GuiText.tr("wawelauth.gui.register.error.password_mismatch");
                    return;
                }

                String inviteToken = inviteTokenField.getText()
                    .trim();

                busy[0] = true;
                statusText[0] = GuiText.tr("wawelauth.gui.register.status.registering");

                WawelClient.instance()
                    .getAccountManager()
                    .register(provider, username, password, inviteToken)
                    .whenComplete((v, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> { // Minecraft.addScheduledTask
                                busy[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    statusText[0] = cause.getMessage();
                                    WawelAuth.debug("Register failed: " + cause.getMessage());
                                } else {
                                    dialog.closeWith(Boolean.TRUE);
                                }
                            });
                    });
            };

            usernameField.onEnterPressed(doRegister);
            passwordField.onEnterPressed(doRegister);
            confirmPasswordField.onEnterPressed(doRegister);
            inviteTokenField.onEnterPressed(doRegister);

            ButtonWidget<?> registerBtn = new ButtonWidget<>();
            registerBtn.size(72, 18)
                .onMousePressed(mouseButton -> {
                    doRegister.run();
                    return true;
                });
            GuiText.fitButtonLabel(registerBtn, 72, "wawelauth.gui.common.register");

            dialog.size(236, 176)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.register.title", providerLabel)).widthRel(1.0f)
                                .height(12))
                        .child(
                            usernameField.widthRel(1.0f)
                                .height(18)
                                .setMaxLength(64)
                                .margin(0, 3))
                        .child(
                            passwordField.widthRel(1.0f)
                                .height(18)
                                .setMaxLength(128)
                                .margin(0, 3))
                        .child(
                            confirmPasswordField.widthRel(1.0f)
                                .height(18)
                                .setMaxLength(128)
                                .margin(0, 3))
                        .child(
                            inviteTokenField.widthRel(1.0f)
                                .height(18)
                                .setMaxLength(128)
                                .margin(0, 3))
                        .child(
                            new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                                .widthRel(1.0f)
                                .height(12)
                                .margin(0, 2))
                        .child(new Widget<>().size(1, 4))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
                                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .child(cancelBtn)
                                .child(new Widget<>().size(6, 18))
                                .child(registerBtn)));

            return dialog;
        }, true);
    }

    public static RegisterDialog attach(ModularPanel parentPanel, Consumer<Boolean> onResult) {
        return new RegisterDialog(parentPanel, onResult);
    }

    public void open(String providerName) {
        this.providerName = providerName;
        this.panelHandler.deleteCachedPanel();
        this.panelHandler.openPanel();
    }
}
