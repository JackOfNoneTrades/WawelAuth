package org.fentanylsolutions.wawelauth.client.gui;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;

final class FocusedLocalAuthPanel {

    private static final int DETAIL_SECONDARY_TEXT_COLOR = WawelAuthStyle.TEXT_SECONDARY;
    private static final int SIDEBAR_TEXT_MAX_WIDTH_PX = 104;

    private final AccountManagerScreenState state;
    private final Function<ClientProvider, ClientProvider> resolveProvider;
    private final Consumer<ClientProvider> ensureRegisterCapabilityProbe;
    private final Runnable rebuildProviderList;
    private final Runnable rebuildAccountList;
    private final Runnable requestAccountListRebuild;
    private final Runnable clearPreview;
    private final Consumer<ClientProvider> openProviderProxyDialog;

    FocusedLocalAuthPanel(AccountManagerScreenState state, Function<ClientProvider, ClientProvider> resolveProvider,
        Consumer<ClientProvider> ensureRegisterCapabilityProbe, Runnable rebuildProviderList,
        Runnable rebuildAccountList, Runnable requestAccountListRebuild, Runnable clearPreview,
        Consumer<ClientProvider> openProviderProxyDialog) {
        this.state = state;
        this.resolveProvider = resolveProvider;
        this.ensureRegisterCapabilityProbe = ensureRegisterCapabilityProbe;
        this.rebuildProviderList = rebuildProviderList;
        this.rebuildAccountList = rebuildAccountList;
        this.requestAccountListRebuild = requestAccountListRebuild;
        this.clearPreview = clearPreview;
        this.openProviderProxyDialog = openProviderProxyDialog;
    }

    boolean hasContext() {
        return state.focusedLocalServerData != null && state.focusedLocalCapabilities != null;
    }

    boolean hasMetadata() {
        ServerCapabilities capabilities = state.focusedLocalCapabilities;
        return hasContext() && notBlank(capabilities.getLocalAuthApiRoot())
            && notBlank(capabilities.getLocalAuthPublicKeyFingerprint());
    }

    void initializeSelectedProvider() {
        if (!hasContext()) {
            return;
        }
        refreshProviderListState();
    }

    void refreshProviderListState() {
        if (!hasContext()) {
            return;
        }

        ClientProvider provider = resolveFocusedProvider();
        state.selectedProvider = provider;
        if (provider != null) {
            ensureRegisterCapabilityProbe.accept(provider);
            if (state.focusedLocalStatusText == null || state.focusedLocalStatusText.isEmpty()) {
                state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.ready_message", provider.getName());
            }
        } else {
            state.focusedLocalStatusText = hasMetadata() ? GuiText.tr("wawelauth.gui.local_auth.status.trust_first")
                : GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
            if (state.selectedAccount != null) {
                state.selectedAccount = null;
                clearPreview.run();
            }
        }
    }

    void populateSidebar(Column leftSidebar, Column accountListFrame,
        BiConsumer<Column, Column> appendSharedAccountSection) {
        String serverAddress = getServerAddress();
        String displayAddress = GuiText.ellipsizeToPixelWidth(serverAddress, SIDEBAR_TEXT_MAX_WIDTH_PX);
        TextWidget<?> addressText = new TextWidget<>(IKey.str(displayAddress));
        addressText.widthRel(1.0f)
            .height(12)
            .color(WawelAuthStyle.THEME_LIGHTER);
        if (!displayAddress.equals(serverAddress)) {
            addressText.addTooltipLine(serverAddress);
        }
        leftSidebar.child(addressText);

        String serverName = getServerName();
        if (serverName != null && !serverName.isEmpty() && !serverName.equals(serverAddress)) {
            String displayName = GuiText.ellipsizeToPixelWidth(serverName, SIDEBAR_TEXT_MAX_WIDTH_PX);
            TextWidget<?> nameText = new TextWidget<>(IKey.str(displayName));
            nameText.color(DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10);
            if (!displayName.equals(serverName)) {
                nameText.addTooltipLine(serverName);
            }
            leftSidebar.child(nameText);
        }

        ClientProvider provider = state.selectedProvider;
        if (provider != null) {
            String providerLine = GuiText
                .tr("wawelauth.gui.account_manager.provider_line", ProviderDisplayName.displayName(provider.getName()));
            String displayProviderLine = GuiText.ellipsizeToPixelWidth(providerLine, SIDEBAR_TEXT_MAX_WIDTH_PX);
            TextWidget<?> providerText = new TextWidget<>(IKey.str(displayProviderLine));
            providerText.color(DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10);
            if (!displayProviderLine.equals(providerLine)) {
                providerText.addTooltipLine(providerLine);
            }
            leftSidebar.child(providerText);
        }

        leftSidebar.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.common.fingerprint")).widthRel(1.0f)
                .height(10)
                .margin(0, 2));

        String fingerprint = getFingerprint();
        String displayFingerprint = GuiText.ellipsizeToPixelWidth(fingerprint, SIDEBAR_TEXT_MAX_WIDTH_PX);
        TextWidget<?> fingerprintText = new TextWidget<>(IKey.str(displayFingerprint));
        fingerprintText.color(WawelAuthStyle.FINGERPRINT)
            .scale(0.7f)
            .widthRel(1.0f)
            .height(10);
        if (!displayFingerprint.equals(fingerprint)) {
            fingerprintText.addTooltipLine(fingerprint);
        }
        leftSidebar.child(fingerprintText)
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText.ellipsizeToPixelWidth(getFocusedLocalStatusText(), SIDEBAR_TEXT_MAX_WIDTH_PX)))
                            .tooltipDynamic(tooltip -> {
                                String fullText = getFocusedLocalStatusText();
                                if (!GuiText.ellipsizeToPixelWidth(fullText, SIDEBAR_TEXT_MAX_WIDTH_PX)
                                    .equals(fullText)) {
                                    tooltip.addLine(IKey.str(fullText));
                                }
                            })
                            .tooltipAutoUpdate(true)
                            .color(WawelAuthStyle.WARNING)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10)
                            .margin(0, 2))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    WawelAuthStyle.button(new ButtonWidget<>())
                        .widthRel(1.0f)
                        .height(16)
                        .setEnabledIf(widget -> hasMetadata()),
                    SIDEBAR_TEXT_MAX_WIDTH_PX,
                    "wawelauth.gui.local_auth.trust_refresh")
                    .onMousePressed(mouseButton -> {
                        ensureProvider(null);
                        return true;
                    }))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    WawelAuthStyle.button(new ButtonWidget<>())
                        .widthRel(1.0f)
                        .height(16)
                        .margin(0, 2)
                        .setEnabledIf(widget -> hasMetadata()),
                    SIDEBAR_TEXT_MAX_WIDTH_PX,
                    "wawelauth.gui.account_manager.proxy_settings")
                    .onMousePressed(mouseButton -> {
                        ensureProvider(() -> {
                            ClientProvider current = state.selectedProvider;
                            if (current != null) {
                                openProviderProxyDialog.accept(current);
                            }
                        });
                        return true;
                    }))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    WawelAuthStyle.button(new ButtonWidget<>())
                        .widthRel(1.0f)
                        .height(16)
                        .margin(0, 2)
                        .setEnabledIf(widget -> state.selectedProvider != null),
                    SIDEBAR_TEXT_MAX_WIDTH_PX,
                    "wawelauth.gui.local_auth.remove_auth")
                    .onMousePressed(mouseButton -> {
                        removeProvider();
                        return true;
                    }));

        appendSharedAccountSection.accept(leftSidebar, accountListFrame);
    }

    void ensureProvider(Runnable onReady) {
        if (!hasMetadata()) {
            state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        ClientProvider existing = client.getLocalAuthProviderResolver()
            .findExisting(state.focusedLocalCapabilities);
        if (existing != null) {
            ClientProvider resolved = resolveProvider.apply(existing);
            state.selectedProvider = resolved;
            ensureRegisterCapabilityProbe.accept(resolved);
            state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.ready_message", resolved.getName());
            rebuildAccountList.run();
            requestAccountListRebuild.run();
            if (onReady != null) {
                onReady.run();
            }
            return;
        }

        state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.resolving");
        CompletableFuture.supplyAsync(
            () -> client.getLocalAuthProviderResolver()
                .resolveOrCreate(state.focusedLocalCapabilities))
            .whenComplete((provider, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            state.focusedLocalStatusText = GuiText
                                .tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            return;
                        }

                        ClientProvider resolved = resolveProvider.apply(provider);
                        state.selectedProvider = resolved;
                        ensureRegisterCapabilityProbe.accept(resolved);
                        state.focusedLocalStatusText = GuiText
                            .tr("wawelauth.gui.common.ready_message", resolved.getName());
                        rebuildProviderList.run();
                        rebuildAccountList.run();
                        requestAccountListRebuild.run();
                        if (onReady != null) {
                            onReady.run();
                        }
                    });
            });
    }

    void removeProvider() {
        ClientProvider provider = state.selectedProvider;
        if (provider == null) {
            state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.trust_first");
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        String providerName = provider.getName();
        try {
            client.getProviderRegistry()
                .removeProvider(providerName);
            state.selectedProvider = null;
            state.selectedAccount = null;
            clearPreview.run();
            state.focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.removed");
            rebuildAccountList.run();
            requestAccountListRebuild.run();
        } catch (Exception e) {
            state.focusedLocalStatusText = e.getMessage();
            WawelAuth.debug("Focused local provider deletion failed: " + e.getMessage());
        }
    }

    private ClientProvider resolveFocusedProvider() {
        if (!hasContext()) {
            return null;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return null;
        }
        return resolveProvider.apply(
            client.getLocalAuthProviderResolver()
                .findExisting(state.focusedLocalCapabilities));
    }

    private String getFocusedLocalStatusText() {
        return state.focusedLocalStatusText != null ? state.focusedLocalStatusText : "";
    }

    private String getServerAddress() {
        net.minecraft.client.multiplayer.ServerData serverData = state.focusedLocalServerData;
        if (serverData != null && serverData.serverIP != null
            && !serverData.serverIP.trim()
                .isEmpty()) {
            return serverData.serverIP.trim();
        }
        return GuiText.tr("wawelauth.gui.common.server");
    }

    private String getServerName() {
        net.minecraft.client.multiplayer.ServerData serverData = state.focusedLocalServerData;
        if (serverData != null && serverData.serverName != null) {
            String trimmed = serverData.serverName.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private String getFingerprint() {
        ServerCapabilities capabilities = state.focusedLocalCapabilities;
        if (capabilities == null || capabilities.getLocalAuthPublicKeyFingerprint() == null) {
            return GuiText.tr("wawelauth.gui.common.missing");
        }
        return capabilities.getLocalAuthPublicKeyFingerprint();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }
}
