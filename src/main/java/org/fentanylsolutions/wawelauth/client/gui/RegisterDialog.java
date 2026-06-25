package org.fentanylsolutions.wawelauth.client.gui;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.ClipboardHelper;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ColorType;
import com.cleanroommc.modularui.drawable.UITexture;
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
public final class RegisterDialog {

    private static final int DIALOG_WIDTH = 260;
    private static final int DIALOG_HEIGHT = 198;
    private static final int ROOT_PADDING = 10;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_TEXT_MAX_WIDTH = 56;
    private static final int STATUS_MAX_WIDTH_PX = DIALOG_WIDTH - ROOT_PADDING * 2 - 4;
    private static final int PASTE_BUTTON_SIZE = FIELD_HEIGHT;
    private static final int PASTE_ICON_SOURCE_SIZE = 24;
    private static final int PASTE_ICON_SIZE = 12;
    private static final int INVITE_FIELD_WIDTH = DIALOG_WIDTH - ROOT_PADDING * 2 - PASTE_BUTTON_SIZE - 4;
    private static final Pattern GENERATED_INVITE_TOKEN_PATTERN = Pattern
        .compile("\\b[A-Za-z0-9_-]{5}(?:-[A-Za-z0-9_-]{5}){3}\\b");
    private static final Pattern SIMPLE_INVITE_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
    private static final ColorType PASTE_ICON_COLOR_TYPE = new ColorType(
        "wawelauth:clipboard_note_icon",
        theme -> WawelAuthStyle.TEXT_BUTTON_IDLE);
    private static final ColorType PASTE_ICON_HOVER_COLOR_TYPE = new ColorType(
        "wawelauth:clipboard_note_icon_hover",
        theme -> WawelAuthStyle.TEXT_PRIMARY);
    private static final IDrawable PASTE_ICON = centeredPasteIcon(clipboardNoteIcon("idle", PASTE_ICON_COLOR_TYPE));
    private static final IDrawable PASTE_ICON_HOVER = centeredPasteIcon(
        clipboardNoteIcon("hover", PASTE_ICON_HOVER_COLOR_TYPE));

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
            usernameField.setFocusOnGuiOpen(true);
            WawelAuthStyle.textField(usernameField);
            PasswordInputWidget passwordField = new PasswordInputWidget()
                .hintText(GuiText.tr("wawelauth.gui.common.password"))
                .applyWawelAuthStyle();
            PasswordInputWidget confirmPasswordField = new PasswordInputWidget()
                .hintText(GuiText.tr("wawelauth.gui.register.hint.confirm_password"))
                .applyWawelAuthStyle();
            StringValue inviteTokenValue = new StringValue(prefilledInviteToken());
            TabTextFieldWidget inviteTokenField = new TabTextFieldWidget();
            inviteTokenField.hintText(GuiText.tr("wawelauth.gui.register.hint.invite_token"));
            inviteTokenField.value(inviteTokenValue);
            WawelAuthStyle.textField(inviteTokenField);
            GuiFocus.focusAfterOpen(usernameField);

            String[] statusText = { "" };
            boolean[] busy = { false };
            boolean[] cancelled = { false };

            ButtonWidget<?> pasteTokenBtn = new ButtonWidget<>();
            WawelAuthStyle.iconButton(pasteTokenBtn);
            pasteTokenBtn.size(PASTE_BUTTON_SIZE, PASTE_BUTTON_SIZE)
                .background(WawelAuthStyle.underlined(WawelAuthStyle.BUTTON_IDLE))
                .hoverBackground(WawelAuthStyle.underlined(WawelAuthStyle.BUTTON_HOVER))
                .overlay(PASTE_ICON)
                .hoverOverlay(PASTE_ICON_HOVER)
                .addTooltipLine(GuiText.tr("wawelauth.gui.common.paste_clipboard"))
                .onMousePressed(mouseButton -> {
                    pasteInviteToken(inviteTokenField, inviteTokenValue);
                    return true;
                });

            ButtonWidget<?> cancelBtn = new ButtonWidget<>();
            cancelBtn.size(64, BUTTON_HEIGHT)
                .onMousePressed(mouseButton -> {
                    // Disarm any in-flight register completion so a late success
                    // cannot fire the result consumer of a dismissed dialog.
                    cancelled[0] = true;
                    dialog.closeWith(Boolean.FALSE);
                    return true;
                });
            WawelAuthStyle.textButton(cancelBtn, BUTTON_TEXT_MAX_WIDTH, "wawelauth.gui.common.cancel");

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
                                if (cancelled[0]) return;
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
            registerBtn.size(72, BUTTON_HEIGHT)
                .onMousePressed(mouseButton -> {
                    doRegister.run();
                    return true;
                });
            WawelAuthStyle.textButton(registerBtn, 64, "wawelauth.gui.common.register");

            Column root = new Column();
            root.widthRel(1.0f)
                .heightRel(1.0f)
                .padding(ROOT_PADDING)
                .background(IDrawable.EMPTY)
                .disableHoverBackground();
            root.child(
                new TextWidget<>(GuiText.key("wawelauth.gui.register.title", providerLabel)).widthRel(1.0f)
                    .height(14)
                    .color(WawelAuthStyle.THEME_LIGHTER))
                .child(new Widget<>().size(1, 8))
                .child(
                    usernameField.widthRel(1.0f)
                        .height(FIELD_HEIGHT)
                        .setMaxLength(64))
                .child(new Widget<>().size(1, 7))
                .child(
                    passwordField.widthRel(1.0f)
                        .height(FIELD_HEIGHT)
                        .setMaxLength(128))
                .child(new Widget<>().size(1, 7))
                .child(
                    confirmPasswordField.widthRel(1.0f)
                        .height(FIELD_HEIGHT)
                        .setMaxLength(128))
                .child(new Widget<>().size(1, 7))
                .child(
                    new Row().widthRel(1.0f)
                        .height(FIELD_HEIGHT)
                        .child(
                            inviteTokenField.width(INVITE_FIELD_WIDTH)
                                .height(FIELD_HEIGHT)
                                .setMaxLength(128))
                        .child(new Widget<>().size(4, FIELD_HEIGHT))
                        .child(pasteTokenBtn))
                .child(new Widget<>().size(1, 8))
                .child(
                    new TextWidget<>(
                        IKey.dynamic(
                            () -> GuiText.ellipsizeToPixelWidth(
                                statusText[0] != null ? statusText[0] : "",
                                STATUS_MAX_WIDTH_PX))).tooltipDynamic(tooltip -> {
                                    String fullText = statusText[0];
                                    if (fullText != null
                                        && !GuiText.ellipsizeToPixelWidth(fullText, STATUS_MAX_WIDTH_PX)
                                            .equals(fullText)) {
                                        tooltip.addLine(IKey.str(fullText));
                                    }
                                })
                                    .tooltipAutoUpdate(true)
                                    .color(WawelAuthStyle.DANGER)
                                    .widthRel(1.0f)
                                    .height(12))
                .child(new Widget<>().size(1, 10))
                .child(
                    new Row().widthRel(1.0f)
                        .height(BUTTON_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(cancelBtn)
                        .child(new Widget<>().size(8, BUTTON_HEIGHT))
                        .child(registerBtn));

            WawelAuthStyle.dialog(dialog);
            dialog.size(DIALOG_WIDTH, DIALOG_HEIGHT)
                .child(root);

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

    private static String prefilledInviteToken() {
        String token = extractInviteToken(readClipboard());
        return token != null ? token : "";
    }

    private static void pasteInviteToken(TabTextFieldWidget field, StringValue valueHolder) {
        String clipboard = readClipboard();
        String token = extractInviteToken(clipboard);
        setInviteToken(field, valueHolder, token != null ? token : clipboard.trim());
    }

    private static void setInviteToken(TabTextFieldWidget field, StringValue valueHolder, String text) {
        String value = text != null ? text : "";
        valueHolder.setStringValue(value);
        field.setText(value);
        if (field.getStringValue() != null) {
            field.getStringValue()
                .setStringValue(value);
        }
    }

    private static String readClipboard() {
        return ClipboardHelper.readFromClipboard();
    }

    private static String extractInviteToken(String clipboard) {
        if (clipboard == null) {
            return null;
        }
        String trimmed = clipboard.replaceAll("(?i)\u00a7[0-9A-FK-OR]", "")
            .trim();
        Matcher generated = GENERATED_INVITE_TOKEN_PATTERN.matcher(trimmed);
        if (generated.find()) {
            return generated.group();
        }
        return SIMPLE_INVITE_TOKEN_PATTERN.matcher(trimmed)
            .matches() ? trimmed : null;
    }

    private static IDrawable clipboardNoteIcon(String variant, ColorType colorType) {
        return UITexture.builder()
            .location("wawelauth", "gui/clipboard-note")
            .imageSize(PASTE_ICON_SOURCE_SIZE, PASTE_ICON_SOURCE_SIZE)
            .colorType(colorType)
            .nonOpaque()
            .name("wawelauth:clipboard_note_" + variant)
            .build();
    }

    private static IDrawable centeredPasteIcon(IDrawable icon) {
        return (context, x, y, width, height, widgetTheme) -> {
            int size = Math.min(PASTE_ICON_SIZE, Math.min(width, height));
            int iconX = x + (width - size) / 2;
            int iconY = y + (height - size) / 2;
            icon.draw(context, iconX, iconY, size, size, widgetTheme);
        };
    }
}
