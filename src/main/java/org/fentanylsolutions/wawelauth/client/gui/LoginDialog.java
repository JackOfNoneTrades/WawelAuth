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
import com.cleanroommc.modularui.api.drawable.IDrawable;
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

    private static final int DIALOG_WIDTH = 260;
    private static final int DIRECT_MICROSOFT_DIALOG_HEIGHT = 108;
    private static final int OFFLINE_DIALOG_HEIGHT = 136;
    private static final int PASSWORD_DIALOG_HEIGHT = 150;
    private static final int ROOT_PADDING = 10;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_TEXT_MAX_WIDTH = 56;
    private static final int STATUS_MAX_WIDTH_PX = DIALOG_WIDTH - ROOT_PADDING * 2 - 4;

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
            boolean focusPassword = this.initialUsername != null && !this.initialUsername.trim()
                .isEmpty()
                && !offlineAccountLogin;
            boolean[] cancelled = { false };
            Dialog<ClientAccount> dialog = new Dialog<>("wawelauth_login", this.onResult);
            dialog.setCloseOnOutOfBoundsClick(false);
            dialog.onCloseAction(() -> cancelled[0] = true);

            TabTextFieldWidget usernameField = new TabTextFieldWidget();
            usernameField.hintText(GuiText.tr("wawelauth.gui.common.username"));
            usernameField.value(new StringValue(this.initialUsername != null ? this.initialUsername : ""));
            usernameField.setFocusOnGuiOpen(!directMicrosoftLogin && !focusPassword);
            WawelAuthStyle.textField(usernameField);
            PasswordInputWidget passwordField = new PasswordInputWidget()
                .hintText(GuiText.tr("wawelauth.gui.common.password"))
                .applyWawelAuthStyle()
                .setFocusOnGuiOpen(!directMicrosoftLogin && focusPassword);
            if (!directMicrosoftLogin) {
                GuiFocus.focusAfterOpen(focusPassword ? passwordField.focusTarget() : usernameField);
            }

            String initMsg = this.initialMessage;
            String[] errorText = { initMsg != null ? initMsg : "" };
            boolean[] isError = { initMsg == null };
            boolean[] isNeutralStatus = { false };
            boolean[] busy = { false };
            String[] oauthDeviceCode = { null };

            ButtonWidget<?> loginBtn = new ButtonWidget<>();
            ButtonWidget<?> cancelBtn = new ButtonWidget<>();
            ButtonWidget<?> oauthBtn = new ButtonWidget<>();
            ButtonWidget<?> copyBtn = new ButtonWidget<>();
            Runnable[] startOauthLogin = new Runnable[1];

            cancelBtn.size(64, BUTTON_HEIGHT)
                .onMousePressed(mouseButton -> {
                    // Disarm any in-flight auth completion so a late success
                    // cannot fire the result consumer of a dismissed dialog.
                    cancelled[0] = true;
                    dialog.closeWith(null);
                    return true;
                });
            WawelAuthStyle.textButton(cancelBtn, BUTTON_TEXT_MAX_WIDTH, "wawelauth.gui.common.cancel");

            Runnable doLogin = () -> {
                if (busy[0]) return;
                isError[0] = true;
                isNeutralStatus[0] = false;
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
                isError[0] = false;
                isNeutralStatus[0] = true;
                errorText[0] = GuiText.tr(
                    offlineAccountLogin ? "wawelauth.gui.login.status.creating_offline"
                        : "wawelauth.gui.login.status.authenticating");

                WawelClient.instance()
                    .getAccountManager()
                    .authenticate(provider, username, password)
                    .whenComplete((account, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> { // Minecraft.addScheduledTask
                                if (cancelled[0]) return;
                                busy[0] = false;
                                isNeutralStatus[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    isError[0] = true;
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

            loginBtn.size(64, BUTTON_HEIGHT)
                .setEnabledIf(widget -> !busy[0])
                .onMousePressed(mouseButton -> {
                    doLogin.run();
                    return true;
                });
            WawelAuthStyle.textButton(
                loginBtn,
                BUTTON_TEXT_MAX_WIDTH,
                offlineAccountLogin ? "wawelauth.gui.common.add" : "wawelauth.gui.common.login");

            startOauthLogin[0] = () -> {
                if (busy[0]) return;
                isError[0] = false;
                isNeutralStatus[0] = true;
                oauthDeviceCode[0] = null;

                if (!supportsOauthLogin) {
                    isError[0] = true;
                    isNeutralStatus[0] = false;
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
                                    isNeutralStatus[0] = true;
                                    errorText[0] = status;
                                }))
                        .whenComplete((account, err) -> {
                            Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    if (cancelled[0]) return;
                                    busy[0] = false;
                                    isNeutralStatus[0] = false;
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
                                    isNeutralStatus[0] = true;
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
                                    if (cancelled[0]) return;
                                    busy[0] = false;
                                    isNeutralStatus[0] = false;
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

            oauthBtn.size(76, BUTTON_HEIGHT)
                .setEnabledIf(widget -> !busy[0])
                .onMousePressed(mouseButton -> {
                    if (busy[0]) return true;

                    if (!supportsOauthLogin) {
                        isError[0] = true;
                        isNeutralStatus[0] = false;
                        errorText[0] = GuiText.tr("wawelauth.gui.login.error.oauth_only");
                        return true;
                    }
                    startOauthLogin[0].run();
                    return true;
                });
            WawelAuthStyle.textButton(
                oauthBtn,
                68,
                supportsMicrosoftLogin ? "wawelauth.gui.common.microsoft" : "wawelauth.gui.common.oauth");

            copyBtn.size(56, BUTTON_HEIGHT)
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
            WawelAuthStyle.textButton(copyBtn, 48, "wawelauth.gui.common.copy");
            copyBtn.tooltipDynamic(tooltip -> {
                if (oauthDeviceCode[0] != null) {
                    tooltip.addLine(IKey.str(oauthDeviceCode[0]));
                }
            });
            copyBtn.tooltipAutoUpdate(true);

            Row buttonRow = new Row();
            buttonRow.widthRel(1.0f)
                .height(BUTTON_HEIGHT)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .collapseDisabledChild();
            buttonRow.child(cancelBtn);
            if (!directMicrosoftLogin) {
                buttonRow.child(
                    new Widget<>().size(8, BUTTON_HEIGHT)
                        .setEnabledIf(widget -> !busy[0]))
                    .child(loginBtn);
            }
            if (supportsOauthLogin && !directMicrosoftLogin) {
                buttonRow.child(
                    new Widget<>().size(8, BUTTON_HEIGHT)
                        .setEnabledIf(widget -> !busy[0]))
                    .child(oauthBtn);
            }
            if (supportsProviderOAuth && !directMicrosoftLogin) {
                buttonRow.child(
                    new Widget<>().size(8, BUTTON_HEIGHT)
                        .setEnabledIf(widget -> oauthDeviceCode[0] != null))
                    .child(copyBtn);
            }

            Column root = new Column();
            root.widthRel(1.0f)
                .heightRel(1.0f)
                .padding(ROOT_PADDING)
                .background(IDrawable.EMPTY)
                .disableHoverBackground();
            root.child(
                new TextWidget<>(
                    offlineAccountLogin ? GuiText.key("wawelauth.gui.login.offline_title")
                        : GuiText.key("wawelauth.gui.login.title", providerLabel)).widthRel(1.0f)
                    .height(14)
                    .color(WawelAuthStyle.THEME_LIGHTER));

            if (!directMicrosoftLogin) {
                root.child(new Widget<>().size(1, 8));
                if (offlineAccountLogin) {
                    root.child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.login.offline_notice"))
                            .color(WawelAuthStyle.TEXT_SECONDARY)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(18));
                    root.child(new Widget<>().size(1, 6));
                }
                root.child(
                    usernameField.widthRel(1.0f)
                        .height(FIELD_HEIGHT)
                        .setMaxLength(offlineAccountLogin ? 16 : 64));
                if (!offlineAccountLogin) {
                    root.child(new Widget<>().size(1, 7));
                    root.child(
                        passwordField.widthRel(1.0f)
                            .height(FIELD_HEIGHT)
                            .setMaxLength(128));
                }
            } else {
                root.child(new Widget<>().size(1, 10));
                root.child(
                    new TextWidget<>(GuiText.key("wawelauth.gui.login.status.microsoft_starting"))
                        .color(WawelAuthStyle.TEXT_SECONDARY)
                        .widthRel(1.0f)
                        .height(12));
            }

            root.child(new Widget<>().size(1, 8));
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
                                .color(
                                    () -> isNeutralStatus[0] ? WawelAuthStyle.TEXT_SECONDARY
                                        : isError[0] ? WawelAuthStyle.DANGER : WawelAuthStyle.SUCCESS)
                                .widthRel(1.0f)
                                .height(12))
                .child(new Widget<>().size(1, 10))
                .child(buttonRow);

            WawelAuthStyle.dialog(dialog);
            dialog
                .size(
                    DIALOG_WIDTH,
                    directMicrosoftLogin ? DIRECT_MICROSOFT_DIALOG_HEIGHT
                        : (offlineAccountLogin ? OFFLINE_DIALOG_HEIGHT : PASSWORD_DIALOG_HEIGHT))
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
