package org.fentanylsolutions.wawelauth.client.gui;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelclient.ProviderRegistry;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;
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

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shared proxy settings dialog used by both the provider proxy dialog
 * (account manager) and the server connection proxy dialog (server picker).
 * Callers supply the subject lines, the async probe, and the save action.
 */
@SideOnly(Side.CLIENT)
final class ProxySettingsDialog {

    private static final int STATUS_NEUTRAL_COLOR = 0xFFAAAAAA;

    interface SettingsFinisher {

        ProviderProxySettings finish(ProviderProxySettings formSettings) throws Exception;
    }

    interface Probe {

        ProbeResult run(ProviderProxySettings formSettings);
    }

    interface Saver {

        void save(ProviderProxySettings formSettings) throws Exception;
    }

    static final class ProbeResult {

        ProviderRegistry.ProbeOutcome proxyOutcome = ProviderRegistry.ProbeOutcome.NEUTRAL;
        String proxyText = notTested();
        ProviderRegistry.ProbeOutcome secondaryOutcome = ProviderRegistry.ProbeOutcome.NEUTRAL;
        String secondaryText = notTested();
    }

    static final class Config {

        String name;
        int height;
        List<IKey> subjectLines;
        ProviderProxySettings initialSettings;
        String initialSecondaryText = notTested();
        String secondaryStatusKey;
        String secondaryTestingText;
        boolean showHttpAuthNote;
        SettingsFinisher finisher = formSettings -> formSettings;
        Probe probe;
        Saver saver;
        Runnable onClose = () -> {};
    }

    private ProxySettingsDialog() {}

    static Dialog<Boolean> build(Config cfg) {
        Dialog<Boolean> dialog = new Dialog<>(cfg.name);
        dialog.setCloseOnOutOfBoundsClick(false);

        ProviderProxySettings initialSettings = cfg.initialSettings;
        final boolean[] proxyEnabled = { initialSettings.isEnabled() };
        final ProviderProxyType[] proxyType = { initialSettings.getType() };
        final String[] proxyStatusText = { notTested() };
        final ProviderRegistry.ProbeOutcome[] proxyStatusOutcome = { ProviderRegistry.ProbeOutcome.NEUTRAL };
        final String[] secondaryStatusText = { cfg.initialSecondaryText };
        final ProviderRegistry.ProbeOutcome[] secondaryStatusOutcome = { ProviderRegistry.ProbeOutcome.NEUTRAL };
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
                    ProviderProxySettings formSettings = cfg.finisher.finish(
                        readSettingsFromForm(
                            proxyEnabled[0],
                            true,
                            proxyType[0],
                            hostField,
                            portField,
                            usernameField,
                            passwordField));
                    busy[0] = true;
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    secondaryStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    proxyStatusText[0] = GuiText.tr("wawelauth.gui.account_manager.proxy_testing_proxy");
                    secondaryStatusText[0] = cfg.secondaryTestingText;

                    CompletableFuture.supplyAsync(() -> cfg.probe.run(formSettings))
                        .whenComplete(
                            (probeResult, err) -> Minecraft.getMinecraft()
                                .func_152344_a(() -> {
                                    busy[0] = false;
                                    if (err != null) {
                                        proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                                        proxyStatusText[0] = formatThrowableMessage(err);
                                        secondaryStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                                        secondaryStatusText[0] = notTested();
                                        return;
                                    }
                                    proxyStatusOutcome[0] = probeResult.proxyOutcome;
                                    proxyStatusText[0] = probeResult.proxyText;
                                    secondaryStatusOutcome[0] = probeResult.secondaryOutcome;
                                    secondaryStatusText[0] = probeResult.secondaryText;
                                }));
                } catch (Exception e) {
                    proxyStatusOutcome[0] = ProviderRegistry.ProbeOutcome.ERROR;
                    proxyStatusText[0] = formatThrowableMessage(e);
                    secondaryStatusOutcome[0] = ProviderRegistry.ProbeOutcome.NEUTRAL;
                    secondaryStatusText[0] = notTested();
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
                    ProviderProxySettings formSettings = cfg.finisher.finish(
                        readSettingsFromForm(
                            proxyEnabled[0],
                            false,
                            proxyType[0],
                            hostField,
                            portField,
                            usernameField,
                            passwordField));
                    cfg.saver.save(formSettings);
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
                cfg.onClose.run();
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        Column content = new Column();
        content.widthRel(1.0f)
            .heightRel(1.0f)
            .padding(8);
        content.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.proxy_settings")).widthRel(1.0f)
                .height(14));
        for (IKey subjectLine : cfg.subjectLines) {
            content.child(
                new TextWidget<>(subjectLine).color(0xFFAAAAAA)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10));
        }
        content.child(
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
            .child(passwordField);
        if (cfg.showHttpAuthNote) {
            content.child(
                new TextWidget<>(
                    IKey.dynamic(() -> unsupportedHttpProxyAuthNote(proxyType[0], usernameField, passwordField)))
                        .color(0xFFFF5555)
                        .widthRel(1.0f)
                        .height(10)
                        .margin(0, 2));
        }
        content.child(
            new TextWidget<>(
                IKey.dynamic(() -> GuiText.tr("wawelauth.gui.account_manager.proxy_status_proxy", proxyStatusText[0])))
                    .color(() -> probeOutcomeColor(proxyStatusOutcome[0]))
                    .widthRel(1.0f)
                    .height(12)
                    .margin(0, 4))
            .child(
                new TextWidget<>(IKey.dynamic(() -> GuiText.tr(cfg.secondaryStatusKey, secondaryStatusText[0])))
                    .color(() -> probeOutcomeColor(secondaryStatusOutcome[0]))
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
                    .child(closeBtn));

        dialog.size(300, cfg.height)
            .child(content);

        return dialog;
    }

    static Dialog<Boolean> messageDialog(Dialog<Boolean> dialog, String messageKey) {
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

    static String notTested() {
        return GuiText.tr("wawelauth.gui.account_manager.proxy_status_not_tested");
    }

    static String formatThrowableMessage(Throwable throwable) {
        String fallback = null;
        Throwable current = throwable;
        while (current != null) {
            String message = StringUtil.trimToNull(current.getMessage());
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

    private static ProviderProxySettings readSettingsFromForm(boolean proxyEnabled, boolean ignoreEnabledToggle,
        ProviderProxyType proxyType, TextFieldWidget hostField, TextFieldWidget portField,
        TextFieldWidget usernameField, PasswordInputWidget passwordField) {
        ProviderProxySettings settings = new ProviderProxySettings();
        settings.setType(proxyType);

        ParsedProxyEndpoint endpoint = parseProxyEndpoint(
            StringUtil.trimToNull(hostField.getText()),
            StringUtil.trimToNull(portField.getText()));
        settings.setHost(endpoint.host);
        if (endpoint.port != null) {
            settings.setPort(endpoint.port);
        }

        String username = StringUtil.trimToNull(usernameField.getText());
        String password = StringUtil.trimToNull(passwordField.getText());
        settings.setUsername(username);
        settings.setPassword(password);
        settings.setEnabled(
            ignoreEnabledToggle ? endpoint.host != null || endpoint.port != null || username != null || password != null
                : proxyEnabled);
        return settings;
    }

    private static ParsedProxyEndpoint parseProxyEndpoint(String rawHost, String rawPort) {
        String host = rawHost;
        Integer explicitPort = parseProxyPort(rawPort);

        ParsedProxyEndpoint embedded = parseEmbeddedProxyEndpoint(host);
        host = embedded.host;

        ParsedProxyEndpoint resolved = new ParsedProxyEndpoint();
        resolved.host = host;
        resolved.port = explicitPort != null ? explicitPort : embedded.port;
        return resolved;
    }

    private static ParsedProxyEndpoint parseEmbeddedProxyEndpoint(String rawHost) {
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
            String hostPart = StringUtil.trimToNull(rawHost.substring(0, lastColon));
            String portPart = StringUtil.trimToNull(rawHost.substring(lastColon + 1));
            if (hostPart != null && portPart != null) {
                result.host = hostPart;
                result.port = parseProxyPort(portPart);
            }
        }
        return result;
    }

    private static Integer parseProxyPort(String portText) {
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

    private static int probeOutcomeColor(ProviderRegistry.ProbeOutcome outcome) {
        if (outcome == ProviderRegistry.ProbeOutcome.SUCCESS) {
            return 0xFF55FF55;
        }
        if (outcome == ProviderRegistry.ProbeOutcome.ERROR) {
            return 0xFFFF5555;
        }
        return STATUS_NEUTRAL_COLOR;
    }

    private static String unsupportedHttpProxyAuthNote(ProviderProxyType proxyType, TextFieldWidget usernameField,
        PasswordInputWidget passwordField) {
        if (proxyType != ProviderProxyType.HTTP || ProviderProxySupport.isModernHttpProxyAuthAvailable()) {
            return "";
        }
        if (StringUtil.trimToNull(usernameField.getText()) == null
            && StringUtil.trimToNull(passwordField.getText()) == null) {
            return "";
        }
        return GuiText.tr("wawelauth.gui.account_manager.proxy_java8_basic_auth_unsupported");
    }

    private static final class ParsedProxyEndpoint {

        private String host;
        private Integer port;
    }
}
