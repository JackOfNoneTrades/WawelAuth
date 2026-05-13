package org.fentanylsolutions.wawelauth.client.gui;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.util.EnumChatFormatting;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.fentlib.util.NetworkAddressUtil;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Row;

final class AccountManagerProviderListPanel {

    private static final UITexture PROVIDER_SETTINGS_TEXTURE = UITexture.fullImage("wawelauth", "gui/gears");
    private static final int PROVIDER_NAME_MAX_WIDTH_PX = 84;
    private static final IDrawable PROVIDER_SETTINGS_ICON = PROVIDER_SETTINGS_TEXTURE
        .getSubArea(0.0f, 0.0f, 0.5f, 1.0f);
    private static final IDrawable PROVIDER_SETTINGS_ICON_HOVER = PROVIDER_SETTINGS_TEXTURE
        .getSubArea(0.5f, 0.0f, 1.0f, 1.0f);

    private final ListWidget<IWidget, ?> providerList = new ListWidget<>();
    private final Supplier<ClientProvider> selectedProvider;
    private final Consumer<ClientProvider> setSelectedProvider;
    private final Consumer<ClientAccount> setSelectedAccount;
    private final Supplier<Boolean> hasFocusedLocalContext;
    private final Runnable refreshFocusedLocalProviderState;
    private final Consumer<ClientProvider> selectProvider;
    private final Runnable clearPreview;
    private final Consumer<ClientProvider> openProviderSettingsDialog;

    AccountManagerProviderListPanel(Supplier<ClientProvider> selectedProvider,
        Consumer<ClientProvider> setSelectedProvider, Consumer<ClientAccount> setSelectedAccount,
        Supplier<Boolean> hasFocusedLocalContext, Runnable refreshFocusedLocalProviderState,
        Consumer<ClientProvider> selectProvider, Runnable clearPreview,
        Consumer<ClientProvider> openProviderSettingsDialog) {
        this.selectedProvider = selectedProvider;
        this.setSelectedProvider = setSelectedProvider;
        this.setSelectedAccount = setSelectedAccount;
        this.hasFocusedLocalContext = hasFocusedLocalContext;
        this.refreshFocusedLocalProviderState = refreshFocusedLocalProviderState;
        this.selectProvider = selectProvider;
        this.clearPreview = clearPreview;
        this.openProviderSettingsDialog = openProviderSettingsDialog;
    }

    ListWidget<IWidget, ?> widget() {
        return providerList;
    }

    void rebuild() {
        providerList.removeAll();
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        if (Boolean.TRUE.equals(hasFocusedLocalContext.get())) {
            if (refreshFocusedLocalProviderState != null) {
                refreshFocusedLocalProviderState.run();
            }
            return;
        }

        List<ClientProvider> providers = new ArrayList<>();
        for (ClientProvider provider : client.getProviderRegistry()
            .listProviders()) {
            if (shouldShowProviderInGeneralList(provider)) {
                providers.add(provider);
            }
        }
        providers.sort(
            Comparator.comparing(
                provider -> normalizeSortKey(
                    ProviderDisplayName.displayName(provider != null ? provider.getName() : null)),
                String.CASE_INSENSITIVE_ORDER));

        boolean selectedVisible = false;
        ClientProvider currentSelected = selectedProvider.get();
        for (ClientProvider provider : providers) {
            String providerName = provider.getName() != null ? provider.getName() : "?";
            String providerDisplayName = ProviderDisplayName.displayName(providerName);
            String displayProviderName = GuiText.ellipsizeToPixelWidth(providerDisplayName, PROVIDER_NAME_MAX_WIDTH_PX);
            boolean isSelected = currentSelected != null && providerName.equals(currentSelected.getName());
            if (isSelected) {
                selectedVisible = true;
            }

            ButtonWidget<?> selectButton = new ButtonWidget<>();
            selectButton.expanded()
                .heightRel(1.0f);
            if (isSelected) {
                selectButton.background(new Rectangle().color(0x44FFFFFF));
            }
            selectButton.overlay(IKey.str(displayProviderName));
            addProviderTooltip(selectButton, provider, providerDisplayName, displayProviderName);
            selectButton.onMousePressed(mouseButton -> {
                selectProvider.accept(provider);
                rebuild();
                return true;
            });

            ButtonWidget<?> settingsButton = new ButtonWidget<>();
            settingsButton.size(14, 14)
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .overlay(PROVIDER_SETTINGS_ICON)
                .hoverOverlay(PROVIDER_SETTINGS_ICON_HOVER)
                .addTooltipLine(GuiText.tr("wawelauth.gui.account_manager.provider_settings"))
                .onMousePressed(mouseButton -> {
                    openProviderSettingsDialog.accept(provider);
                    return true;
                });

            Row providerRow = new Row();
            providerRow.widthRel(1.0f)
                .height(14)
                .child(selectButton)
                .child(new Widget<>().size(1, 14))
                .child(settingsButton);

            providerList.child(providerRow);
        }

        if (!selectedVisible) {
            if (!providers.isEmpty()) {
                selectProvider.accept(providers.get(0));
                rebuild();
            } else {
                setSelectedProvider.accept(null);
                setSelectedAccount.accept(null);
                clearPreview.run();
            }
        }
    }

    private boolean shouldShowProviderInGeneralList(ClientProvider provider) {
        return provider != null && (provider.getType() != ProviderType.CUSTOM || provider.isManualEntry());
    }

    private static void addProviderTooltip(ButtonWidget<?> button, ClientProvider provider, String providerName,
        String displayProviderName) {
        boolean showName = !providerName.equals(displayProviderName);
        boolean showLocalAddress = isLocalAuthProvider(provider);
        if (!showName && !showLocalAddress) {
            return;
        }

        if (showName) {
            button.addTooltipLine(providerName);
        }

        if (showLocalAddress) {
            String address = extractProviderServerAddress(provider);
            if (address != null) {
                button.addTooltipLine(
                    EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.common.server") + ": " + address);
            }
        }
    }

    private static boolean isLocalAuthProvider(ClientProvider provider) {
        if (provider == null) return false;
        String name = provider.getName();
        if (name != null && (name.startsWith("LocalAuth-") || name.startsWith("WawelAuth@"))) {
            return true;
        }

        String apiRoot = normalize(provider.getApiRoot());
        String fingerprint = normalize(provider.getPublicKeyFingerprint());
        if (apiRoot == null || fingerprint == null) {
            return false;
        }

        String authExpected = apiRoot + "/authserver";
        String sessionExpected = apiRoot + "/sessionserver";
        String auth = normalize(provider.getAuthServerUrl());
        String session = normalize(provider.getSessionServerUrl());
        return authExpected.equals(auth) && sessionExpected.equals(session);
    }

    private static String extractProviderServerAddress(ClientProvider provider) {
        String apiRoot = normalize(provider != null ? provider.getApiRoot() : null);
        if (apiRoot == null) {
            return null;
        }

        try {
            URI uri = new URI(apiRoot);
            if (uri.getHost() != null) {
                return NetworkAddressUtil.formatHostPort(uri.getHost(), uri.getPort());
            }
        } catch (Exception ignored) {}

        return apiRoot;
    }

    private static String normalizeSortKey(String value) {
        String normalized = normalize(value);
        return normalized != null ? normalized : "";
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
