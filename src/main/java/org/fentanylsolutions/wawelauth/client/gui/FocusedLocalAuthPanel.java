package org.fentanylsolutions.wawelauth.client.gui;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;

final class FocusedLocalAuthPanel {

    private static final int DETAIL_SECONDARY_TEXT_COLOR = 0xFF555555;

    private final ServerData serverData;
    private final ServerCapabilities capabilities;
    private final Supplier<ClientProvider> selectedProvider;
    private final Consumer<ClientProvider> setSelectedProvider;
    private final Supplier<ClientAccount> selectedAccount;
    private final Consumer<ClientAccount> setSelectedAccount;
    private final Function<ClientProvider, ClientProvider> resolveProvider;
    private final Consumer<ClientProvider> ensureRegisterCapabilityProbe;
    private final Runnable rebuildProviderList;
    private final Runnable rebuildAccountList;
    private final Runnable requestAccountListRebuild;
    private final Runnable clearPreview;
    private final Consumer<ClientProvider> openProviderProxyDialog;

    private String statusText = "";

    FocusedLocalAuthPanel(ServerData serverData, ServerCapabilities capabilities,
        Supplier<ClientProvider> selectedProvider, Consumer<ClientProvider> setSelectedProvider,
        Supplier<ClientAccount> selectedAccount, Consumer<ClientAccount> setSelectedAccount,
        Function<ClientProvider, ClientProvider> resolveProvider,
        Consumer<ClientProvider> ensureRegisterCapabilityProbe, Runnable rebuildProviderList,
        Runnable rebuildAccountList, Runnable requestAccountListRebuild, Runnable clearPreview,
        Consumer<ClientProvider> openProviderProxyDialog) {
        this.serverData = serverData;
        this.capabilities = capabilities;
        this.selectedProvider = selectedProvider;
        this.setSelectedProvider = setSelectedProvider;
        this.selectedAccount = selectedAccount;
        this.setSelectedAccount = setSelectedAccount;
        this.resolveProvider = resolveProvider;
        this.ensureRegisterCapabilityProbe = ensureRegisterCapabilityProbe;
        this.rebuildProviderList = rebuildProviderList;
        this.rebuildAccountList = rebuildAccountList;
        this.requestAccountListRebuild = requestAccountListRebuild;
        this.clearPreview = clearPreview;
        this.openProviderProxyDialog = openProviderProxyDialog;
    }

    boolean hasContext() {
        return serverData != null && capabilities != null;
    }

    boolean hasMetadata() {
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
        setSelectedProvider.accept(provider);
        if (provider != null) {
            ensureRegisterCapabilityProbe.accept(provider);
            if (statusText == null || statusText.isEmpty()) {
                statusText = GuiText.tr("wawelauth.gui.common.ready_message", provider.getName());
            }
        } else {
            statusText = hasMetadata() ? GuiText.tr("wawelauth.gui.local_auth.status.trust_first")
                : GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
            if (selectedAccount.get() != null) {
                setSelectedAccount.accept(null);
                clearPreview.run();
            }
        }
    }

    void populateSidebar(Column leftSidebar, Column accountListFrame,
        BiConsumer<Column, Column> appendSharedAccountSection) {
        String serverAddress = getServerAddress();
        String displayAddress = GuiText.ellipsizeToPixelWidth(serverAddress, 104);
        TextWidget<?> addressText = new TextWidget<>(IKey.str(displayAddress));
        addressText.widthRel(1.0f)
            .height(12);
        if (!displayAddress.equals(serverAddress)) {
            addressText.addTooltipLine(serverAddress);
        }
        leftSidebar.child(addressText);

        String serverName = getServerName();
        if (serverName != null && !serverName.isEmpty() && !serverName.equals(serverAddress)) {
            String displayName = GuiText.ellipsizeToPixelWidth(serverName, 104);
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

        ClientProvider provider = selectedProvider.get();
        if (provider != null) {
            String providerLine = GuiText
                .tr("wawelauth.gui.account_manager.provider_line", ProviderDisplayName.displayName(provider.getName()));
            String displayProviderLine = GuiText.ellipsizeToPixelWidth(providerLine, 104);
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
        String displayFingerprint = GuiText.ellipsizeToPixelWidth(fingerprint, 104);
        TextWidget<?> fingerprintText = new TextWidget<>(IKey.str(displayFingerprint));
        fingerprintText.color(0xFF55FFFF)
            .scale(0.7f)
            .widthRel(1.0f)
            .height(10);
        if (!displayFingerprint.equals(fingerprint)) {
            fingerprintText.addTooltipLine(fingerprint);
        }
        leftSidebar.child(fingerprintText)
            .child(
                new TextWidget<>(IKey.dynamic(() -> statusText != null ? statusText : "")).color(0xFFFFFF55)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10)
                    .margin(0, 2))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16)
                        .setEnabledIf(widget -> hasMetadata()),
                    104,
                    "wawelauth.gui.local_auth.trust_refresh")
                    .onMousePressed(mouseButton -> {
                        ensureProvider(null);
                        return true;
                    }))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16)
                        .margin(0, 2)
                        .setEnabledIf(widget -> hasMetadata()),
                    104,
                    "wawelauth.gui.account_manager.proxy_settings")
                    .onMousePressed(mouseButton -> {
                        ensureProvider(() -> {
                            ClientProvider current = selectedProvider.get();
                            if (current != null) {
                                openProviderProxyDialog.accept(current);
                            }
                        });
                        return true;
                    }))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16)
                        .margin(0, 2)
                        .setEnabledIf(widget -> selectedProvider.get() != null),
                    104,
                    "wawelauth.gui.local_auth.remove_auth")
                    .onMousePressed(mouseButton -> {
                        removeProvider();
                        return true;
                    }));

        appendSharedAccountSection.accept(leftSidebar, accountListFrame);
    }

    void ensureProvider(Runnable onReady) {
        if (!hasMetadata()) {
            statusText = GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            statusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        ClientProvider existing = client.getLocalAuthProviderResolver()
            .findExisting(capabilities);
        if (existing != null) {
            ClientProvider resolved = resolveProvider.apply(existing);
            setSelectedProvider.accept(resolved);
            ensureRegisterCapabilityProbe.accept(resolved);
            statusText = GuiText.tr("wawelauth.gui.common.ready_message", resolved.getName());
            rebuildAccountList.run();
            requestAccountListRebuild.run();
            if (onReady != null) {
                onReady.run();
            }
            return;
        }

        statusText = GuiText.tr("wawelauth.gui.local_auth.status.resolving");
        CompletableFuture.supplyAsync(
            () -> client.getLocalAuthProviderResolver()
                .resolveOrCreate(capabilities))
            .whenComplete((provider, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            statusText = GuiText.tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            return;
                        }

                        ClientProvider resolved = resolveProvider.apply(provider);
                        setSelectedProvider.accept(resolved);
                        ensureRegisterCapabilityProbe.accept(resolved);
                        statusText = GuiText.tr("wawelauth.gui.common.ready_message", resolved.getName());
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
        ClientProvider provider = selectedProvider.get();
        if (provider == null) {
            statusText = GuiText.tr("wawelauth.gui.local_auth.status.trust_first");
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            statusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        String providerName = provider.getName();
        try {
            client.getProviderRegistry()
                .removeProvider(providerName);
            setSelectedProvider.accept(null);
            setSelectedAccount.accept(null);
            clearPreview.run();
            statusText = GuiText.tr("wawelauth.gui.local_auth.status.removed");
            rebuildAccountList.run();
            requestAccountListRebuild.run();
        } catch (Exception e) {
            statusText = e.getMessage();
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
                .findExisting(capabilities));
    }

    private String getServerAddress() {
        if (serverData != null && serverData.serverIP != null
            && !serverData.serverIP.trim()
                .isEmpty()) {
            return serverData.serverIP.trim();
        }
        return GuiText.tr("wawelauth.gui.common.server");
    }

    private String getServerName() {
        if (serverData != null && serverData.serverName != null) {
            String trimmed = serverData.serverName.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private String getFingerprint() {
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
