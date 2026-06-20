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
import org.fentanylsolutions.wawelauth.wawelclient.LocalAuthProviderResolver;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.HoveredWidgetList;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.WidgetTree;
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
    private static final int SHOW_LOCAL_BUTTON_HEIGHT = 10;
    private static final int PROVIDER_ROW_HEIGHT = 14;
    private static final int CONNECTED_PROVIDER_TEXT_COLOR = 0xFFFFFF88;

    private final ListWidget<IWidget, ?> providerList;
    private final AccountManagerScreenState state;
    private boolean showingLocal;
    private int pendingScrollOffset = -1;
    private final Supplier<Boolean> hasFocusedLocalContext;
    private final Runnable refreshFocusedLocalProviderState;
    private final Consumer<ClientProvider> selectProvider;
    private final Runnable clearPreview;
    private final Consumer<ClientProvider> openProviderSettingsDialog;

    AccountManagerProviderListPanel(AccountManagerScreenState state, Supplier<Boolean> hasFocusedLocalContext,
        Runnable refreshFocusedLocalProviderState, Consumer<ClientProvider> selectProvider, Runnable clearPreview,
        Consumer<ClientProvider> openProviderSettingsDialog) {
        this.state = state;
        this.hasFocusedLocalContext = hasFocusedLocalContext;
        this.refreshFocusedLocalProviderState = refreshFocusedLocalProviderState;
        this.selectProvider = selectProvider;
        this.clearPreview = clearPreview;
        this.openProviderSettingsDialog = openProviderSettingsDialog;
        this.providerList = new ChildPassthroughListWidget();
    }

    ListWidget<IWidget, ?> widget() {
        return providerList;
    }

    void expandLocal() {
        showingLocal = true;
    }

    void scrollToSelected() {
        pendingScrollOffset = 0; // will be updated with actual offset during rebuild
    }

    /**
     * Try to apply a pending scroll-to-selected offset. Call from onUpdate()
     * so the widget tree is laid out. Returns true when consumed.
     */
    boolean applyPendingScroll() {
        if (pendingScrollOffset < 0) return false;
        int offset = pendingScrollOffset;
        try {
            WidgetTree.resizeInternal(providerList.resizer(), false);
            providerList.getScrollData()
                .scrollTo(providerList.getScrollArea(), offset);
            pendingScrollOffset = -1;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    void rebuild() {
        boolean scrollToSelected = pendingScrollOffset >= 0;
        int savedScroll = saveScroll();
        providerList.removeAll();
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        if (Boolean.TRUE.equals(hasFocusedLocalContext.get())) {
            if (refreshFocusedLocalProviderState != null) {
                refreshFocusedLocalProviderState.run();
            }
            return;
        }

        ClientProvider connectedProvider = resolveConnectedProvider(client);

        List<ClientProvider> allProviders = client.getProviderRegistry()
            .listProviders();

        List<ClientProvider> providers = new ArrayList<>();
        List<ClientProvider> localProviders = new ArrayList<>();
        for (ClientProvider provider : allProviders) {
            if (isConnectedProvider(provider, connectedProvider)) {
                continue;
            }
            if (shouldShowProviderInGeneralList(provider)) {
                providers.add(provider);
            } else if (isLocalProvider(provider)) {
                localProviders.add(provider);
            }
        }
        Comparator<ClientProvider> byDisplayName = Comparator.comparing(
            provider -> normalizeSortKey(ProviderDisplayName.displayName(provider != null ? provider.getName() : null)),
            String.CASE_INSENSITIVE_ORDER);
        providers.sort(byDisplayName);

        boolean selectedVisible = false;
        int selectedRowOffset = -1;
        int rowOffset = 0;
        ClientProvider currentSelected = state.selectedProvider;
        if (connectedProvider != null) {
            boolean hit = addProviderRow(connectedProvider, currentSelected, true);
            if (hit) selectedRowOffset = rowOffset;
            selectedVisible |= hit;
            rowOffset += PROVIDER_ROW_HEIGHT;
        }
        for (ClientProvider provider : providers) {
            boolean hit = addProviderRow(provider, currentSelected);
            if (hit) selectedRowOffset = rowOffset;
            selectedVisible |= hit;
            rowOffset += PROVIDER_ROW_HEIGHT;
        }

        if (!localProviders.isEmpty()) {
            ButtonWidget<?> toggleButton = new ButtonWidget<>();
            toggleButton.widthRel(0.7f)
                .height(SHOW_LOCAL_BUTTON_HEIGHT)
                .background(
                    IDrawable.of(
                        new Rectangle().color(0x22000000),
                        new Rectangle().color(0xFF000000)
                            .hollow(1)))
                .overlay(
                    IKey.dynamic(
                        () -> GuiText.tr(
                            showingLocal ? "wawelauth.gui.account_manager.hide_local"
                                : "wawelauth.gui.account_manager.show_local"))
                        .scale(0.8f))
                .onMousePressed(mouseButton -> {
                    showingLocal = !showingLocal;
                    rebuild();
                    return true;
                });

            Row toggleRow = new Row();
            toggleRow.widthRel(1.0f)
                .height(SHOW_LOCAL_BUTTON_HEIGHT)
                .margin(0, 1)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .child(toggleButton);
            providerList.child(toggleRow);
            rowOffset += SHOW_LOCAL_BUTTON_HEIGHT + 2; // height + margin(0,1) top+bottom

            if (showingLocal) {
                localProviders.sort(byDisplayName);
                for (ClientProvider provider : localProviders) {
                    boolean hit = addProviderRow(provider, currentSelected);
                    if (hit) selectedRowOffset = rowOffset;
                    selectedVisible |= hit;
                    rowOffset += PROVIDER_ROW_HEIGHT;
                }
            }
        }

        if (!selectedVisible && !(isLocalProvider(currentSelected) && !localProviders.isEmpty())) {
            if (connectedProvider != null) {
                selectProvider.accept(connectedProvider);
                rebuild();
                return;
            } else if (!providers.isEmpty()) {
                selectProvider.accept(providers.get(0));
                rebuild();
                return;
            } else {
                state.selectedProvider = null;
                state.selectedAccount = null;
                clearPreview.run();
            }
        }

        if (scrollToSelected && selectedRowOffset >= 0) {
            pendingScrollOffset = selectedRowOffset;
        } else if (savedScroll > 0 && providerList.isValid()) {
            try {
                WidgetTree.resizeInternal(providerList.resizer(), false);
                providerList.getScrollData()
                    .scrollTo(providerList.getScrollArea(), savedScroll);
            } catch (Exception ignored) {}
        }
    }

    private int saveScroll() {
        try {
            return providerList.getScrollData()
                .getScroll();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean addProviderRow(ClientProvider provider, ClientProvider currentSelected) {
        return addProviderRow(provider, currentSelected, false);
    }

    private boolean addProviderRow(ClientProvider provider, ClientProvider currentSelected,
        boolean connectedHighlight) {
        String providerName = provider.getName() != null ? provider.getName() : "?";
        String providerDisplayName = ProviderDisplayName.displayName(providerName);
        String displayProviderName = GuiText.ellipsizeToPixelWidth(providerDisplayName, PROVIDER_NAME_MAX_WIDTH_PX);
        boolean isSelected = currentSelected != null && providerName.equals(currentSelected.getName());

        ButtonWidget<?> selectButton = new ButtonWidget<>();
        selectButton.expanded()
            .heightRel(1.0f);
        if (isSelected) {
            selectButton.background(new Rectangle().color(0x44FFFFFF));
        }
        if (connectedHighlight) {
            selectButton.overlay(
                IKey.str(displayProviderName)
                    .color(CONNECTED_PROVIDER_TEXT_COLOR));
        } else {
            selectButton.overlay(IKey.str(displayProviderName));
        }
        addProviderTooltip(selectButton, provider, providerDisplayName, displayProviderName);
        selectButton.onMousePressed(mouseButton -> {
            selectProvider.accept(provider);
            rebuild();
            return true;
        });

        ButtonWidget<?> settingsButton = new ButtonWidget<>();
        settingsButton.size(14, 14)
            .overlay(PROVIDER_SETTINGS_ICON)
            .hoverOverlay(PROVIDER_SETTINGS_ICON_HOVER)
            .addTooltipLine(GuiText.tr("wawelauth.gui.account_manager.provider_settings"))
            .onMousePressed(mouseButton -> {
                openProviderSettingsDialog.accept(provider);
                return true;
            });

        Row providerRow = new Row() {

            @Override
            public boolean canHover() {
                return false;
            }
        };
        providerRow.widthRel(1.0f)
            .height(14)
            .child(selectButton)
            .child(nonHoverable(1, 14))
            .child(settingsButton);

        providerList.child(providerRow);
        return isSelected;
    }

    private boolean shouldShowProviderInGeneralList(ClientProvider provider) {
        return provider != null && (provider.getType() != ProviderType.CUSTOM || provider.isManualEntry());
    }

    private static boolean isLocalProvider(ClientProvider provider) {
        return provider != null && provider.getType() == ProviderType.CUSTOM && !provider.isManualEntry();
    }

    private ClientProvider resolveConnectedProvider(WawelClient client) {
        if (state.connectedServerCapabilities == null) return null;
        try {
            ClientProvider existing = client.getLocalAuthProviderResolver()
                .findExisting(state.connectedServerCapabilities);
            if (existing != null) return existing;
            return client.getLocalAuthProviderResolver()
                .resolveOrCreate(state.connectedServerCapabilities);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isConnectedProvider(ClientProvider provider, ClientProvider connected) {
        if (connected == null || provider == null) return false;
        String name = provider.getName();
        String connectedName = connected.getName();
        return name != null && name.equals(connectedName);
    }

    private static void addProviderTooltip(ButtonWidget<?> button, ClientProvider provider, String providerName,
        String displayProviderName) {
        boolean showName = !providerName.equals(displayProviderName);
        boolean showLocalAddress = LocalAuthProviderResolver.isLocalAuthProvider(provider);
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

    private static Widget<?> nonHoverable(int width, int height) {
        NonHoverableWidget w = new NonHoverableWidget();
        w.size(width, height);
        return w;
    }

    private static class NonHoverableWidget extends Widget<NonHoverableWidget> {

        @Override
        public boolean canHover() {
            return false;
        }
    }

    /**
     * ListWidget that always lets children receive hover/click, even in the scrollbar area.
     */
    private static class ChildPassthroughListWidget extends ListWidget<IWidget, ChildPassthroughListWidget> {

        @Override
        public void getWidgetsAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y) {
            if (widgets.peek() == this && hasChildren()) {
                IViewport.getChildrenAt(this, stack, widgets, x, y);
            }
        }
    }
}
