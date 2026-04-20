package org.fentanylsolutions.wawelauth.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.ClipboardHelper;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.oauth.ProviderOAuthClients;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class LoginDialog {

    private static final int STATUS_MAX_WIDTH_PX = 214;

    private final Consumer<ClientAccount> onResult;
    private final IPanelHandler panelHandler;
    private String providerName;
    private boolean forceMicrosoftLogin;
    private String initialMessage;
    private String initialUsername;

    private LoginDialog(ModularPanel parentPanel, Consumer<ClientAccount> onResult) {
        this.onResult = onResult;
        this.panelHandler = IPanelHandler.simple(parentPanel, (parent, player) -> {
            String provider = this.providerName != null ? this.providerName : "";
            String providerLabel = ProviderDisplayName.displayName(provider);
            boolean supportsMicrosoftLogin = ProviderDisplayName.isMicrosoftProvider(provider);
            boolean supportsProviderOAuth = ProviderOAuthClients.supports(provider);
            boolean supportsOauthLogin = supportsMicrosoftLogin || supportsProviderOAuth;
            boolean offlineAccountLogin = ProviderDisplayName.isOfflineProvider(provider);
            boolean directMicrosoftLogin = supportsMicrosoftLogin && this.forceMicrosoftLogin;
            Dialog<ClientAccount> dialog = new Dialog<>("wawelauth_login", this.onResult);
            dialog.setCloseOnOutOfBoundsClick(false);

            TabTextFieldWidget usernameField = new TabTextFieldWidget();
            usernameField.hintText(GuiText.tr("wawelauth.gui.common.username"));
            usernameField.value(new StringValue(this.initialUsername != null ? this.initialUsername : ""));
            PasswordInputWidget passwordField = new PasswordInputWidget()
                .hintText(GuiText.tr("wawelauth.gui.common.password"));

            String initMsg = this.initialMessage;
            String[] errorText = { initMsg != null ? initMsg : "" };
            boolean[] isError = { initMsg == null };
            boolean[] busy = { false };
            String[] oauthDeviceCode = { null };

            ButtonWidget<?> loginBtn = new ButtonWidget<>();
            ButtonWidget<?> cancelBtn = new ButtonWidget<>();
            ButtonWidget<?> oauthBtn = new ButtonWidget<>();
            ButtonWidget<?> copyBtn = new ButtonWidget<>();
            Runnable[] startOauthLogin = new Runnable[1];

            cancelBtn.size(56, 18)
                .onMousePressed(mouseButton -> {
                    dialog.closeWith(null);
                    return true;
                });
            GuiText.fitButtonLabel(cancelBtn, 56, "wawelauth.gui.common.cancel");

            Runnable doLogin = () -> {
                if (busy[0]) return;
                isError[0] = true;
                oauthDeviceCode[0] = null;

                if (provider.isEmpty()) {
                    errorText[0] = GuiText.tr("wawelauth.gui.login.error.no_provider");
                    return;
                }

                String username = usernameField.getText()
                    .trim();
                String password = passwordField.getText();

                if (username.isEmpty()) {
                    errorText[0] = GuiText.tr("wawelauth.gui.login.error.username_required");
                    return;
                }
                if (!offlineAccountLogin && password.isEmpty()) {
                    errorText[0] = GuiText.tr("wawelauth.gui.login.error.password_required");
                    return;
                }

                busy[0] = true;
                errorText[0] = GuiText.tr(
                    offlineAccountLogin ? "wawelauth.gui.login.status.creating_offline"
                        : "wawelauth.gui.login.status.authenticating");

                WawelClient.instance()
                    .getAccountManager()
                    .authenticate(provider, username, password)
                    .whenComplete((account, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> { // Minecraft.addScheduledTask
                                busy[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    errorText[0] = cause.getMessage();
                                    WawelAuth.debug("Login failed: " + cause.getMessage());
                                } else {
                                    dialog.closeWith(account);
                                }
                            });
                    });
            };

            usernameField.onEnterPressed(doLogin);
            passwordField.onEnterPressed(doLogin);

            loginBtn.size(56, 18)
                .setEnabledIf(widget -> !busy[0])
                .onMousePressed(mouseButton -> {
                    doLogin.run();
                    return true;
                });
            GuiText.fitButtonLabel(loginBtn, 56, "wawelauth.gui.common.login");

            startOauthLogin[0] = () -> {
                if (busy[0]) return;
                isError[0] = false;
                oauthDeviceCode[0] = null;

                if (!supportsOauthLogin) {
                    isError[0] = true;
                    errorText[0] = GuiText.tr("wawelauth.gui.login.error.oauth_only");
                    return;
                }

                busy[0] = true;
                errorText[0] = GuiText.tr(
                    supportsMicrosoftLogin ? "wawelauth.gui.login.status.microsoft_opening"
                        : "wawelauth.gui.login.status.oauth_opening");

                if (supportsMicrosoftLogin) {
                    WawelClient.instance()
                        .getAccountManager()
                        .authenticateMicrosoft(
                            provider,
                            status -> Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    isError[0] = false;
                                    errorText[0] = status;
                                }))
                        .whenComplete((account, err) -> {
                            Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    busy[0] = false;
                                    if (err != null) {
                                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                                        isError[0] = true;
                                        errorText[0] = cause.getMessage();
                                        WawelAuth.debug("Microsoft login failed: " + cause.getMessage());
                                    } else {
                                        dialog.closeWith(account);
                                    }
                                });
                        });
                } else {
                    WawelClient.instance()
                        .getAccountManager()
                        .authenticateOAuth(
                            provider,
                            usernameField.getText()
                                .trim(),
                            status -> Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    isError[0] = false;
                                    errorText[0] = status;
                                }),
                            deviceCode -> Minecraft.getMinecraft()
                                .func_152344_a(
                                    () -> {
                                        oauthDeviceCode[0] = deviceCode != null && !deviceCode.trim()
                                            .isEmpty() ? deviceCode.trim() : null;
                                    }))
                        .whenComplete((account, err) -> {
                            Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    busy[0] = false;
                                    oauthDeviceCode[0] = err != null ? null : oauthDeviceCode[0];
                                    if (err != null) {
                                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                                        isError[0] = true;
                                        errorText[0] = cause.getMessage();
                                        WawelAuth.debug("OAuth login failed: " + cause.getMessage());
                                    } else {
                                        dialog.closeWith(account);
                                    }
                                });
                        });
                }
            };

            oauthBtn.size(70, 18)
                .setEnabledIf(widget -> !busy[0])
                .onMousePressed(mouseButton -> {
                    if (busy[0]) return true;

                    if (!supportsOauthLogin) {
                        errorText[0] = GuiText.tr("wawelauth.gui.login.error.oauth_only");
                        return true;
                    }
                    startOauthLogin[0].run();
                    return true;
                });
            GuiText.fitButtonLabel(
                oauthBtn,
                70,
                supportsMicrosoftLogin ? "wawelauth.gui.common.microsoft" : "wawelauth.gui.common.oauth");

            copyBtn.size(50, 18)
                .setEnabledIf(widget -> oauthDeviceCode[0] != null)
                .onMousePressed(mouseButton -> {
                    if (oauthDeviceCode[0] == null) {
                        return true;
                    }
                    ClipboardHelper.copyToClipboard(
                        oauthDeviceCode[0],
                        GuiText.tr("wawelauth.gui.login.status.oauth_code_copied"));
                    return true;
                });
            GuiText.fitButtonLabel(copyBtn, 50, "wawelauth.gui.common.copy");
            copyBtn.tooltipDynamic(tooltip -> {
                if (oauthDeviceCode[0] != null) {
                    tooltip.addLine(IKey.str(oauthDeviceCode[0]));
                }
            });
            copyBtn.tooltipAutoUpdate(true);

            Row buttonRow = new Row();
            buttonRow.widthRel(1.0f)
                .height(20)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .collapseDisabledChild();
            buttonRow.child(cancelBtn);
            if (!directMicrosoftLogin) {
                buttonRow.child(
                    new Widget<>().size(6, 18)
                        .setEnabledIf(widget -> !busy[0]))
                    .child(loginBtn);
            }
            if (supportsOauthLogin && !directMicrosoftLogin) {
                buttonRow.child(
                    new Widget<>().size(6, 18)
                        .setEnabledIf(widget -> !busy[0]))
                    .child(oauthBtn);
            }
            if (supportsProviderOAuth && !directMicrosoftLogin) {
                buttonRow.child(
                    new Widget<>().size(6, 18)
                        .setEnabledIf(widget -> oauthDeviceCode[0] != null))
                    .child(copyBtn);
            }

            Column root = new Column();
            root.widthRel(1.0f)
                .heightRel(1.0f)
                .padding(8);
            root.child(
                new TextWidget<>(GuiText.key("wawelauth.gui.login.title", providerLabel)).widthRel(1.0f)
                    .height(12));

            if (!directMicrosoftLogin) {
                if (offlineAccountLogin) {
                    root.child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.login.offline_notice")).color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(20)
                            .margin(0, 4));
                }
                root.child(
                    usernameField.widthRel(1.0f)
                        .height(18)
                        .setMaxLength(offlineAccountLogin ? 16 : 64)
                        .margin(0, 3));
                if (!offlineAccountLogin) {
                    root.child(
                        passwordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 3));
                }
            } else {
                root.child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.login.status.microsoft_starting")).color(0xFFAAAAAA)
                        .widthRel(1.0f)
                        .height(12)
                        .margin(0, 8));
            }

            root.child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText
                            .ellipsizeToPixelWidth(errorText[0] != null ? errorText[0] : "", STATUS_MAX_WIDTH_PX)))
                                .tooltipDynamic(tooltip -> {
                                    String fullText = errorText[0];
                                    if (fullText != null
                                        && !GuiText.ellipsizeToPixelWidth(fullText, STATUS_MAX_WIDTH_PX)
                                            .equals(fullText)) {
                                        tooltip.addLine(IKey.str(fullText));
                                    }
                                })
                                .tooltipAutoUpdate(true)
                                .color(() -> isError[0] ? 0xFFFF5555 : 0xFF55FF55)
                                .widthRel(1.0f)
                                .height(12)
                                .margin(0, 2))
                .child(new Widget<>().size(1, 4))
                .child(buttonRow);

            dialog.size(236, directMicrosoftLogin ? 112 : (offlineAccountLogin ? 142 : 150))
                .child(root);

            if (directMicrosoftLogin) {
                Minecraft.getMinecraft()
                    .func_152344_a(startOauthLogin[0]);
            }

            return dialog;
        }, true);
    }

    public static LoginDialog attach(ModularPanel parentPanel, Consumer<ClientAccount> onResult) {
        return new LoginDialog(parentPanel, onResult);
    }

    public void open(String providerName, String username) {
        this.providerName = providerName;
        this.forceMicrosoftLogin = false;
        this.initialMessage = null;
        this.initialUsername = username != null ? username.trim() : null;
        this.panelHandler.deleteCachedPanel();
        this.panelHandler.openPanel();
    }

    public void open(String providerName) {
        this.open(providerName, null);
    }

    public void openAfterRegister(String providerName) {
        this.providerName = providerName;
        this.forceMicrosoftLogin = false;
        this.initialMessage = GuiText.tr("wawelauth.gui.login.status.after_register");
        this.initialUsername = null;
        this.panelHandler.deleteCachedPanel();
        this.panelHandler.openPanel();
    }

    public void openMicrosoft(String providerName) {
        this.providerName = providerName;
        this.forceMicrosoftLogin = true;
        this.initialMessage = null;
        this.initialUsername = null;
        this.panelHandler.deleteCachedPanel();
        this.panelHandler.openPanel();
    }
}
