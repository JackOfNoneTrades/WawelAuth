package org.fentanylsolutions.wawelauth.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Row;

final class AccountManagerAccountListPanel {

    private static final int ACCOUNT_NAME_MAX_WIDTH_PX = 90;

    private final ListWidget<IWidget, ?> accountList = new ListWidget<>();
    private final Supplier<ClientProvider> selectedProvider;
    private final Consumer<ClientProvider> setSelectedProvider;
    private final Function<ClientProvider, ClientProvider> resolveProvider;
    private final Supplier<ClientAccount> selectedAccount;
    private final Consumer<ClientAccount> setSelectedAccount;
    private final Consumer<ClientAccount> selectAccount;
    private final Runnable clearPreview;

    private Map<Long, Rectangle> accountStatusDots = new HashMap<>();
    private Map<Long, AccountStatus> renderedStatuses = new HashMap<>();
    private boolean rebuildPending;

    AccountManagerAccountListPanel(Supplier<ClientProvider> selectedProvider,
        Consumer<ClientProvider> setSelectedProvider, Function<ClientProvider, ClientProvider> resolveProvider,
        Supplier<ClientAccount> selectedAccount, Consumer<ClientAccount> setSelectedAccount,
        Consumer<ClientAccount> selectAccount, Runnable clearPreview) {
        this.selectedProvider = selectedProvider;
        this.setSelectedProvider = setSelectedProvider;
        this.resolveProvider = resolveProvider;
        this.selectedAccount = selectedAccount;
        this.setSelectedAccount = setSelectedAccount;
        this.selectAccount = selectAccount;
        this.clearPreview = clearPreview;
    }

    ListWidget<IWidget, ?> widget() {
        return accountList;
    }

    void requestRebuild() {
        rebuildPending = true;
    }

    boolean consumeRebuildRequest() {
        if (!rebuildPending) {
            return false;
        }
        rebuildPending = false;
        return true;
    }

    void rebuild() {
        ensureStatusCaches();
        resetScroll();
        accountList.removeAll();
        accountStatusDots.clear();
        renderedStatuses.clear();

        ClientProvider provider = resolveProvider.apply(selectedProvider.get());
        setSelectedProvider.accept(provider);
        if (provider == null) return;

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        List<ClientAccount> accounts = new ArrayList<>(
            client.getAccountManager()
                .listAccounts(provider.getName()));
        accounts.sort(
            Comparator.comparing(
                account -> normalizeSortKey(account != null ? account.getProfileName() : null),
                String.CASE_INSENSITIVE_ORDER));

        boolean selectedInProvider = false;
        ClientAccount currentSelected = selectedAccount.get();
        if (currentSelected != null) {
            long selectedId = currentSelected.getId();
            for (ClientAccount account : accounts) {
                if (account.getId() == selectedId) {
                    selectedInProvider = true;
                    break;
                }
            }
        }

        if (!accounts.isEmpty() && !selectedInProvider) {
            selectAccount.accept(accounts.get(0));
        } else if (accounts.isEmpty() && currentSelected != null) {
            setSelectedAccount.accept(null);
            clearPreview.run();
        }

        boolean selectedStillExists = false;
        for (ClientAccount account : accounts) {
            AccountStatus status = getLiveStatus(account);
            int statusColor = StatusColors.getColor(status);
            String profileName = account.getProfileName() != null ? account.getProfileName() : "?";
            String displayProfileName = GuiText.ellipsizeToPixelWidth(profileName, ACCOUNT_NAME_MAX_WIDTH_PX);
            ClientAccount selected = selectedAccount.get();
            boolean isSelected = selected != null && account.getId() == selected.getId();
            if (isSelected) {
                setSelectedAccount.accept(account);
                selectedStillExists = true;
            }

            ButtonWidget<?> entry = new ButtonWidget<>();
            entry.widthRel(1.0f)
                .height(14);
            if (isSelected) {
                entry.background(new Rectangle().color(0x44FFFFFF));
            }

            Row dot = new Row();
            Rectangle dotBorderRect = new Rectangle().color(0xFF2A2A2A);
            Rectangle dotFillRect = new Rectangle().color(statusColor);
            Widget<?> dotFill = new Widget<>();
            dotFill.size(6, 6)
                .margin(1, 1)
                .background(dotFillRect);

            dot.size(8, 8);
            dot.margin(1, 3);
            dot.background(dotBorderRect);
            dot.child(dotFill);
            accountStatusDots.put(account.getId(), dotFillRect);
            renderedStatuses.put(account.getId(), status);

            TextWidget<?> nameLabel = new TextWidget<>(IKey.str(displayProfileName));
            nameLabel.expanded()
                .heightRel(1.0f);
            if (!displayProfileName.equals(profileName)) {
                nameLabel.addTooltipLine(profileName);
            }

            Row row = new Row();
            row.widthRel(1.0f)
                .heightRel(1.0f);
            row.child(new Widget<>().size(2, 14));
            if (account.getProfileUuid() != null) {
                row.child(createAccountFaceWidget(profileName, account.getProfileUuid(), account.getProviderName()));
                row.child(new Widget<>().size(2, 14));
            }
            row.child(dot);
            row.child(new Widget<>().size(2, 14));
            row.child(nameLabel);

            entry.child(row);
            entry.onMousePressed(mouseButton -> {
                selectAccount.accept(account);
                rebuild();
                return true;
            });

            accountList.child(entry);
        }

        if (selectedAccount.get() != null && !selectedStillExists) {
            setSelectedAccount.accept(null);
            clearPreview.run();
        }
    }

    AccountStatus getLiveStatus(ClientAccount account) {
        if (account == null) return null;
        WawelClient client = WawelClient.instance();
        if (client == null) return account.getStatus();

        AccountStatus cached = client.getAccountManager()
            .getAccountStatus(account.getId());
        if (cached != null) {
            account.setStatus(cached);
            return cached;
        }
        return account.getStatus();
    }

    void refreshVisibleStatuses() {
        ensureStatusCaches();
        WawelClient client = WawelClient.instance();
        if (client == null || accountStatusDots.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, Rectangle> entry : accountStatusDots.entrySet()) {
            long accountId = entry.getKey();
            AccountStatus cached = client.getAccountManager()
                .getAccountStatus(accountId);
            if (cached == null) {
                continue;
            }

            AccountStatus rendered = renderedStatuses.get(accountId);
            if (cached != rendered) {
                entry.getValue()
                    .color(StatusColors.getColor(cached));
                renderedStatuses.put(accountId, cached);

                if (cached == AccountStatus.VALID || cached == AccountStatus.REFRESHED) {
                    ClientAccount account = client.getAccountManager()
                        .getAccount(accountId);
                    if (account != null && account.getProfileUuid() != null) {
                        client.getTextureResolver()
                            .invalidate(account.getProfileUuid());
                    }
                }
            }

            ClientAccount selected = selectedAccount.get();
            if (selected != null && selected.getId() == accountId) {
                selected.setStatus(cached);
            }
        }
    }

    void resetScroll() {
        try {
            accountList.getScrollData()
                .scrollTo(accountList.getScrollArea(), 0);
        } catch (Exception ignored) {
            // Scroll reset is best-effort; list still works without it.
        }
    }

    private Widget<?> createAccountFaceWidget(String displayName, UUID profileUuid, String providerName) {
        return new FaceWidget(displayName, profileUuid, providerName).size(8, 8)
            .margin(0, 3);
    }

    private void ensureStatusCaches() {
        if (accountStatusDots == null) {
            accountStatusDots = new HashMap<>();
        }
        if (renderedStatuses == null) {
            renderedStatuses = new HashMap<>();
        }
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
