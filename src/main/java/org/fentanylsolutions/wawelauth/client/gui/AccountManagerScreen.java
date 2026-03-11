package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.EntityDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AccountManagerScreen extends ParentAwareModularScreen {

    private static final UITexture PROVIDER_SETTINGS_TEXTURE = UITexture.fullImage("wawelauth", "gui/gears");
    private static final long STATUS_UI_REFRESH_INTERVAL_MS = 1000L;
    private static final int PROVIDER_NAME_MAX_WIDTH_PX = 84;
    private static final int ACCOUNT_NAME_MAX_WIDTH_PX = 90;
    private static final int TEXTURE_STATUS_MAX_WIDTH_PX = 212;
    private static final int DETAIL_PRIMARY_TEXT_COLOR = 0xFF000000;
    private static final int DETAIL_SECONDARY_TEXT_COLOR = 0xFF555555;
    private static final int PREVIEW_PANEL_BACKGROUND_COLOR = 0x22000000;
    private static final int LIST_OUTLINE_COLOR = PREVIEW_PANEL_BACKGROUND_COLOR;
    private static final IDrawable PROVIDER_SETTINGS_ICON = PROVIDER_SETTINGS_TEXTURE
        .getSubArea(0.0f, 0.0f, 0.5f, 1.0f);
    private static final IDrawable PROVIDER_SETTINGS_ICON_HOVER = PROVIDER_SETTINGS_TEXTURE
        .getSubArea(0.5f, 0.0f, 1.0f, 1.0f);

    private static ServerData pendingFocusedServerData;
    private static ServerCapabilities pendingFocusedCapabilities;

    private ClientProvider selectedProvider;
    private ClientAccount selectedAccount;
    private PlayerPreviewEntity previewFrontEntity;
    private PlayerPreviewEntity previewBackEntity;
    private AddProviderDialog addProviderDialog;
    private LoginDialog loginDialog;
    private RegisterDialog registerDialog;
    private IPanelHandler removeAccountDialogHandler;
    private IPanelHandler providerSettingsDialogHandler;
    private IPanelHandler credentialDialogHandler;
    private IPanelHandler credentialDeleteDialogHandler;
    private IPanelHandler texturePathDialogHandler;
    private IPanelHandler textureResetDialogHandler;
    private long pendingRemoveAccountId = -1L;
    private String pendingRemoveAccountName;
    private String pendingProviderSettingsName;
    private long pendingCredentialDeleteAccountId = -1L;
    private String pendingCredentialDeleteAccountName;
    private String pendingCredentialDeletePassword;
    private boolean texturePathDialogForSkin;
    private String texturePathDialogInitialPath;
    private boolean capePreviewEnabled = true;
    private File selectedSkinFile;
    private File selectedCapeFile;
    private boolean skinUploadSlim;
    private String textureSelectionStatus = "";
    private String textureUploadStatus = "";

    private ServerData focusedLocalServerData;
    private ServerCapabilities focusedLocalCapabilities;
    private String focusedLocalStatusText = "";

    private ModularPanel mainPanel;
    private ListWidget<IWidget, ?> providerList;
    private ListWidget<IWidget, ?> accountList;
    private Map<Long, Rectangle> accountStatusDots;
    private Map<Long, AccountStatus> renderedStatuses;
    private Map<String, Boolean> registerCapabilityByProvider;
    private Set<String> registerCapabilityProbeInFlight;
    private long nextStatusUiRefreshAtMs;
    private boolean accountListRebuildPending;

    public AccountManagerScreen() {
        super("wawelauth");
        openParentOnClose(true);
    }

    public static void openForLocalAuth(ServerData serverData) {
        pendingFocusedServerData = serverData;
        pendingFocusedCapabilities = ServerBindingPersistence.getEffectiveLocalAuthCapabilities(serverData);
        ClientGUI.open(new AccountManagerScreen());
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        mainPanel = ModularPanel.defaultPanel("wawelauth_account_manager", 360, 240);
        accountStatusDots = new HashMap<>();
        renderedStatuses = new HashMap<>();
        registerCapabilityByProvider = new HashMap<>();
        registerCapabilityProbeInFlight = new HashSet<>();
        nextStatusUiRefreshAtMs = 0L;
        accountListRebuildPending = false;

        if (pendingFocusedServerData != null || pendingFocusedCapabilities != null) {
            focusedLocalServerData = pendingFocusedServerData;
            focusedLocalCapabilities = pendingFocusedCapabilities;
            pendingFocusedServerData = null;
            pendingFocusedCapabilities = null;
        }

        if (hasFocusedLocalContext()) {
            selectedProvider = resolveFocusedLocalProvider();
            if (selectedProvider != null) {
                ensureRegisterCapabilityProbe(selectedProvider);
            }
            if (focusedLocalStatusText == null || focusedLocalStatusText.isEmpty()) {
                focusedLocalStatusText = selectedProvider != null
                    ? GuiText.tr("wawelauth.gui.common.ready_message", selectedProvider.getName())
                    : GuiText.tr("wawelauth.gui.local_auth.status.trust_first");
            }
        }

        providerList = new ListWidget<>();
        accountList = new ListWidget<>();
        addProviderDialog = AddProviderDialog.attach(mainPanel, provider -> {
            if (provider != null) {
                selectProvider(provider);
                rebuildProviderList();
            }
        });
        loginDialog = LoginDialog.attach(mainPanel, account -> {
            if (account != null) {
                rebuildAccountList();
                selectAccount(account);
            }
        });
        registerDialog = RegisterDialog.attach(mainPanel, success -> {
            if (Boolean.TRUE.equals(success) && selectedProvider != null) {
                loginDialog.openAfterRegister(selectedProvider.getName());
            }
        });
        removeAccountDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> buildRemoveAccountDialog(), true);
        providerSettingsDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> buildProviderSettingsDialog(), true);
        credentialDialogHandler = IPanelHandler.simple(mainPanel, (parent, player) -> buildCredentialDialog(), true);
        credentialDeleteDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> buildCredentialDeleteDialog(), true);
        texturePathDialogHandler = IPanelHandler.simple(mainPanel, (parent, player) -> buildTexturePathDialog(), true);
        textureResetDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> buildTextureResetDialog(), true);

        Column leftSidebar = new Column();
        leftSidebar.width(120)
            .heightRel(1.0f)
            .padding(4);

        providerList.widthRel(1.0f)
            .heightRel(1.0f);
        accountList.widthRel(1.0f)
            .heightRel(1.0f);

        Column providerListFrame = new Column();
        providerListFrame.widthRel(1.0f)
            .height(62)
            .margin(0, 2)
            .padding(1)
            .background(new Rectangle().color(LIST_OUTLINE_COLOR))
            .child(providerList);

        Column accountListFrame = new Column();
        accountListFrame.widthRel(1.0f)
            .expanded()
            .margin(0, 2)
            .padding(1)
            .background(new Rectangle().color(LIST_OUTLINE_COLOR))
            .child(accountList);

        if (hasFocusedLocalContext()) {
            populateFocusedLocalSidebar(leftSidebar, accountListFrame);
        } else {
            populateGeneralSidebar(leftSidebar, providerListFrame, accountListFrame);
        }

        Column rightPanel = new Column();
        rightPanel.expanded()
            .heightRel(1.0f)
            .padding(4)
            .collapseDisabledChild();

        rightPanel.child(
            new Column().widthRel(1.0f)
                .height(101)
                .margin(0, 2)
                .background(new Rectangle().color(PREVIEW_PANEL_BACKGROUND_COLOR))
                .child(new Widget<>().size(1, 9))
                .child(
                    new Row().widthRel(1.0f)
                        .height(77)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .child(
                            new EntityDisplayWidget(() -> previewFrontEntity).doesLookAtMouse(true)
                                .preDraw(entity -> { prepareEntityPreview((PlayerPreviewEntity) entity, false); })
                                .asWidget()
                                .size(72, 76)
                                .invisible())
                        .child(new Widget<>().size(6, 76))
                        .child(
                            new EntityDisplayWidget(() -> previewBackEntity).doesLookAtMouse(false)
                                .preDraw(entity -> { prepareEntityPreview((PlayerPreviewEntity) entity, true); })
                                .asWidget()
                                .size(72, 76)
                                .invisible())
                        .child(new Widget<>().size(40, 76)))
                .child(
                    new Row().widthRel(1.0f)
                        .height(12)
                        .mainAxisAlignment(Alignment.MainAxis.END)
                        .child(
                            new ButtonWidget<>().size(64, 12)
                                .margin(3, 0)
                                .overlay(
                                    IKey.dynamic(
                                        () -> GuiText.tr(
                                            capePreviewEnabled ? "wawelauth.gui.account_manager.cape_toggle_on"
                                                : "wawelauth.gui.account_manager.cape_toggle_off")))
                                .onMousePressed(mouseButton -> {
                                    capePreviewEnabled = !capePreviewEnabled;
                                    applyCapePreviewVisibility();
                                    return true;
                                }))))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (selectedAccount == null) return GuiText.tr("wawelauth.gui.common.no_account_selected");
                String name = selectedAccount.getProfileName();
                return name != null ? name : "?";
            })).color(DETAIL_PRIMARY_TEXT_COLOR)
                .widthRel(1.0f)
                .height(12)
                .margin(0, 1))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (selectedAccount == null) return "";
                UUID uuid = selectedAccount.getProfileUuid();
                return uuid != null ? uuid.toString() : GuiText.tr("wawelauth.gui.account_manager.no_profile_bound");
            })).color(DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (selectedAccount == null) return "";
                return GuiText.tr(
                    "wawelauth.gui.account_manager.provider_line",
                    ProviderDisplayName.displayName(selectedAccount.getProviderName()));
            })).color(DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (selectedAccount == null) return "";
                return GuiText.tr(
                    "wawelauth.gui.account_manager.status_line",
                    StatusColors.getLabel(getLiveStatus(selectedAccount)));
            })).color(
                () -> selectedAccount != null ? StatusColors.getColor(getLiveStatus(selectedAccount))
                    : DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10))
            .child(new Widget<>().size(1, 3))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText.ellipsizeToPixelWidth(textureSelectionStatus, TEXTURE_STATUS_MAX_WIDTH_PX)))
                            .tooltipDynamic(tooltip -> {
                                if (shouldShowTextureSelectionTooltip()) {
                                    tooltip.addLine(IKey.str(textureSelectionStatus));
                                }
                            })
                            .tooltipAutoUpdate(true)
                            .color(DETAIL_SECONDARY_TEXT_COLOR)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10)
                            .margin(0, 1)
                            .setEnabledIf(widget -> isAnyTextureUploadEnabled()))
            .child(
                new Row().widthRel(1.0f)
                    .height(14)
                    .mainAxisAlignment(Alignment.MainAxis.START)
                    .setEnabledIf(widget -> !isSkinUploadDisabledForSelectedProvider())
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.skin_model"))
                            .color(DETAIL_SECONDARY_TEXT_COLOR)
                            .scale(0.8f)
                            .size(44, 10))
                    .child(new Widget<>().size(4, 10))
                    .child(
                        new ButtonWidget<>().size(64, 12)
                            .overlay(
                                IKey.dynamic(
                                    () -> GuiText.tr(
                                        skinUploadSlim ? "wawelauth.gui.account_manager.skin_model.slim"
                                            : "wawelauth.gui.account_manager.skin_model.classic")))
                            .onMousePressed(mouseButton -> {
                                skinUploadSlim = !skinUploadSlim;
                                return true;
                            })))
            .child(
                new Row().widthRel(1.0f)
                    .height(18)
                    .margin(0, 1)
                    .setEnabledIf(widget -> isAnyTextureUploadEnabled() || isTextureResetEnabledForSelectedProvider())
                    .collapseDisabledChild()
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().size(56, 16)
                                .setEnabledIf(widget -> !isSkinUploadDisabledForSelectedProvider()),
                            56,
                            "wawelauth.gui.account_manager.skin_pick")
                            .onMousePressed(mouseButton -> {
                                chooseTextureFile(true);
                                return true;
                            }))
                    .child(new Widget<>().size(4, 16))
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().size(56, 16)
                                .setEnabledIf(widget -> !isCapeUploadDisabledForSelectedProvider()),
                            56,
                            "wawelauth.gui.account_manager.cape_pick")
                            .onMousePressed(mouseButton -> {
                                chooseTextureFile(false);
                                return true;
                            }))
                    .child(
                        new Widget<>().size(4, 16)
                            .setEnabledIf(widget -> !isCapeUploadDisabledForSelectedProvider()))
                    .child(
                        GuiText
                            .fitButtonLabel(
                                new ButtonWidget<>().size(64, 16),
                                64,
                                "wawelauth.gui.account_manager.upload")
                            .onMousePressed(mouseButton -> {
                                attemptTextureUpload();
                                return true;
                            }))
                    .child(
                        new Widget<>().size(4, 16)
                            .setEnabledIf(widget -> isTextureResetEnabledForSelectedProvider()))
                    .child(
                        new ButtonWidget<>().size(16, 16)
                            .overlay(
                                IKey.str("X")
                                    .color(0xFFFF4444))
                            .tooltip(
                                tooltip -> tooltip.addLine(GuiText.key("wawelauth.gui.account_manager.reset_textures")))
                            .setEnabledIf(widget -> isTextureResetEnabledForSelectedProvider())
                            .onMousePressed(mouseButton -> {
                                attemptTextureReset();
                                return true;
                            })))
            .child(
                new TextWidget<>(
                    IKey.dynamic(() -> GuiText.ellipsizeToPixelWidth(textureUploadStatus, TEXTURE_STATUS_MAX_WIDTH_PX)))
                        .tooltipDynamic(tooltip -> {
                            if (shouldShowTextureUploadTooltip()) {
                                tooltip.addLine(IKey.str(textureUploadStatus));
                            }
                        })
                        .tooltipAutoUpdate(true)
                        .color(0xFFFFAA55)
                        .scale(0.8f)
                        .widthRel(1.0f)
                        .height(10)
                        .margin(0, 1)
                        .setEnabledIf(
                            widget -> isAnyTextureUploadEnabled() || isTextureResetEnabledForSelectedProvider()))
            .child(
                new Widget<>().widthRel(1.0f)
                    .expanded())
            .child(
                new Row().widthRel(1.0f)
                    .height(18)
                    .margin(0, 1)
                    .child(
                        GuiText
                            .fitButtonLabel(
                                new ButtonWidget<>().size(56, 16),
                                56,
                                "wawelauth.gui.account_manager.reauth")
                            .onMousePressed(mouseButton -> {
                                if (selectedAccount == null) return true;
                                openLoginDialog(selectedAccount.getProviderName(), selectedAccount.getProfileName());
                                return true;
                            }))
                    .child(new Widget<>().size(4, 16))
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().size(74, 16)
                                .setEnabledIf(widget -> isCredentialManagementAvailableForSelectedAccount()),
                            74,
                            "wawelauth.gui.account_manager.credentials")
                            .onMousePressed(mouseButton -> {
                                openCredentialDialog();
                                return true;
                            }))
                    .child(new Widget<>().size(4, 16))
                    .child(
                        GuiText
                            .fitButtonLabel(
                                new ButtonWidget<>().size(88, 16),
                                88,
                                "wawelauth.gui.account_manager.remove_account")
                            .onMousePressed(mouseButton -> {
                                confirmAndRemoveSelectedAccount();
                                return true;
                            })));

        mainPanel.child(
            new Row().widthRel(1.0f)
                .heightRel(1.0f)
                .child(leftSidebar)
                .child(rightPanel));
        mainPanel.child(
            ButtonWidget.panelCloseButton()
                .tooltip(tooltip -> tooltip.addLine(GuiText.key("wawelauth.gui.common.close"))));

        clearTextureSelection();
        rebuildProviderList();
        if (hasFocusedLocalContext()) {
            rebuildAccountList();
        }

        return mainPanel;
    }

    private void populateGeneralSidebar(Column leftSidebar, Column providerListFrame, Column accountListFrame) {
        leftSidebar.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.providers")).widthRel(1.0f)
                .height(12))
            .child(providerListFrame)
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16),
                    104,
                    "wawelauth.gui.add_provider.title")
                    .onMousePressed(mouseButton -> {
                        addProviderDialog.open();
                        return true;
                    }));
        appendSharedAccountSection(leftSidebar, accountListFrame);
    }

    private void populateFocusedLocalSidebar(Column leftSidebar, Column accountListFrame) {
        String serverAddress = getFocusedLocalServerAddress();
        String displayAddress = GuiText.ellipsizeToPixelWidth(serverAddress, 104);
        TextWidget<?> addressText = new TextWidget<>(IKey.str(displayAddress));
        addressText.widthRel(1.0f)
            .height(12);
        if (!displayAddress.equals(serverAddress)) {
            addressText.addTooltipLine(serverAddress);
        }
        leftSidebar.child(addressText);

        String serverName = getFocusedLocalServerName();
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

        if (selectedProvider != null) {
            String providerLine = GuiText.tr(
                "wawelauth.gui.account_manager.provider_line",
                ProviderDisplayName.displayName(selectedProvider.getName()));
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

        String fingerprint = getFocusedLocalFingerprint();
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
                new TextWidget<>(IKey.dynamic(() -> focusedLocalStatusText != null ? focusedLocalStatusText : ""))
                    .color(0xFFFFFF55)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10)
                    .margin(0, 2))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16)
                        .setEnabledIf(widget -> hasFocusedLocalMetadata()),
                    104,
                    "wawelauth.gui.local_auth.trust_refresh")
                    .onMousePressed(mouseButton -> {
                        ensureFocusedLocalProvider(null);
                        return true;
                    }))
            .child(
                GuiText.fitButtonLabelMaxWidth(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16)
                        .margin(0, 2)
                        .setEnabledIf(widget -> selectedProvider != null),
                    104,
                    "wawelauth.gui.local_auth.remove_auth")
                    .onMousePressed(mouseButton -> {
                        removeFocusedLocalProvider();
                        return true;
                    }));

        appendSharedAccountSection(leftSidebar, accountListFrame);
    }

    private void appendSharedAccountSection(Column leftSidebar, Column accountListFrame) {
        leftSidebar.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.accounts")).widthRel(1.0f)
                .height(12)
                .margin(0, 4))
            .child(accountListFrame)
            .child(
                new Row().widthRel(1.0f)
                    .height(17)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .collapseDisabledChild()
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().size(52, 16)
                                .setEnabledIf(widget -> isRegisterVisibleForSelectedProvider()),
                            52,
                            "wawelauth.gui.common.login")
                            .onMousePressed(mouseButton -> {
                                handlePrimaryLoginAction();
                                return true;
                            }))
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().size(108, 16)
                                .setEnabledIf(widget -> !isRegisterVisibleForSelectedProvider()),
                            108,
                            "wawelauth.gui.common.login")
                            .onMousePressed(mouseButton -> {
                                handlePrimaryLoginAction();
                                return true;
                            }))
                    .child(
                        new Widget<>().size(4, 16)
                            .setEnabledIf(widget -> isRegisterVisibleForSelectedProvider()))
                    .child(
                        GuiText.fitButtonLabel(
                            new ButtonWidget<>().size(52, 16)
                                .setEnabledIf(widget -> isRegisterVisibleForSelectedProvider()),
                            52,
                            "wawelauth.gui.common.register")
                            .onMousePressed(mouseButton -> {
                                handlePrimaryRegisterAction();
                                return true;
                            })));
    }

    private void handlePrimaryLoginAction() {
        if (hasFocusedLocalContext()) {
            ensureFocusedLocalProvider(() -> {
                if (selectedProvider != null) {
                    openLoginDialog(selectedProvider.getName());
                }
            });
            return;
        }

        if (selectedProvider != null) {
            openLoginDialog(selectedProvider.getName());
        }
    }

    private void handlePrimaryRegisterAction() {
        if (hasFocusedLocalContext()) {
            ensureFocusedLocalProvider(() -> {
                if (selectedProvider != null) {
                    openRegisterDialog(selectedProvider.getName());
                }
            });
            return;
        }

        if (selectedProvider != null) {
            openRegisterDialog(selectedProvider.getName());
        }
    }

    private void ensureFocusedLocalProvider(Runnable onReady) {
        if (!hasFocusedLocalMetadata()) {
            focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        ClientProvider existing = client.getLocalAuthProviderResolver()
            .findExisting(focusedLocalCapabilities);
        if (existing != null) {
            selectedProvider = resolveProvider(existing);
            ensureRegisterCapabilityProbe(selectedProvider);
            focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.ready_message", selectedProvider.getName());
            rebuildAccountList();
            requestAccountListRebuild();
            if (onReady != null) {
                onReady.run();
            }
            return;
        }

        focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.resolving");
        CompletableFuture.supplyAsync(
            () -> client.getLocalAuthProviderResolver()
                .resolveOrCreate(focusedLocalCapabilities))
            .whenComplete((provider, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            focusedLocalStatusText = GuiText
                                .tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            return;
                        }

                        selectedProvider = resolveProvider(provider);
                        ensureRegisterCapabilityProbe(selectedProvider);
                        focusedLocalStatusText = GuiText
                            .tr("wawelauth.gui.common.ready_message", selectedProvider.getName());
                        rebuildProviderList();
                        rebuildAccountList();
                        requestAccountListRebuild();
                        if (onReady != null) {
                            onReady.run();
                        }
                    });
            });
    }

    private void removeFocusedLocalProvider() {
        if (selectedProvider == null) {
            focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.trust_first");
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            focusedLocalStatusText = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        String providerName = selectedProvider.getName();
        try {
            client.getProviderRegistry()
                .removeProvider(providerName);
            selectedProvider = null;
            selectedAccount = null;
            clearPreview();
            focusedLocalStatusText = GuiText.tr("wawelauth.gui.local_auth.status.removed");
            rebuildAccountList();
            requestAccountListRebuild();
        } catch (Exception e) {
            focusedLocalStatusText = e.getMessage();
            WawelAuth.debug("Focused local provider deletion failed: " + e.getMessage());
        }
    }

    private boolean hasFocusedLocalContext() {
        return focusedLocalServerData != null && focusedLocalCapabilities != null;
    }

    private boolean hasFocusedLocalMetadata() {
        return hasFocusedLocalContext() && notBlank(focusedLocalCapabilities.getLocalAuthApiRoot())
            && notBlank(focusedLocalCapabilities.getLocalAuthPublicKeyFingerprint());
    }

    private ClientProvider resolveFocusedLocalProvider() {
        if (!hasFocusedLocalContext()) {
            return null;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return null;
        }
        return resolveProvider(
            client.getLocalAuthProviderResolver()
                .findExisting(focusedLocalCapabilities));
    }

    private String getFocusedLocalServerAddress() {
        if (focusedLocalServerData != null && focusedLocalServerData.serverIP != null
            && !focusedLocalServerData.serverIP.trim()
                .isEmpty()) {
            return focusedLocalServerData.serverIP.trim();
        }
        return GuiText.tr("wawelauth.gui.common.server");
    }

    private String getFocusedLocalServerName() {
        if (focusedLocalServerData != null && focusedLocalServerData.serverName != null) {
            String trimmed = focusedLocalServerData.serverName.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }

    private String getFocusedLocalFingerprint() {
        if (focusedLocalCapabilities == null || focusedLocalCapabilities.getLocalAuthPublicKeyFingerprint() == null) {
            return GuiText.tr("wawelauth.gui.common.missing");
        }
        return focusedLocalCapabilities.getLocalAuthPublicKeyFingerprint();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        // Tick animated cape previews
        if (previewFrontEntity != null) {
            previewFrontEntity.tickAnimatedCape();
        }
        if (previewBackEntity != null) {
            previewBackEntity.tickAnimatedCape();
        }

        if (accountListRebuildPending) {
            accountListRebuildPending = false;
            rebuildAccountList();
        }

        long now = System.currentTimeMillis();
        if (now < nextStatusUiRefreshAtMs) {
            return;
        }
        nextStatusUiRefreshAtMs = now + STATUS_UI_REFRESH_INTERVAL_MS;
        refreshVisibleStatuses();
    }

    private void selectProvider(ClientProvider provider) {
        this.selectedProvider = resolveProvider(provider);
        ensureRegisterCapabilityProbe(this.selectedProvider);
        this.selectedAccount = null;
        clearTextureSelection();
        clearPreview();
        resetAccountListScroll();
        rebuildAccountList();
        requestAccountListRebuild();
    }

    private void selectAccount(ClientAccount account) {
        this.selectedAccount = account;
        clearTextureSelection();
        textureUploadStatus = "";
        if (account != null && account.getProfileUuid() != null) {
            GameProfile profile = new GameProfile(account.getProfileUuid(), account.getProfileName());
            previewFrontEntity = new PlayerPreviewEntity(profile);
            previewBackEntity = new PlayerPreviewEntity(profile);
            applyCapePreviewVisibility();
            loadSkinForAccount(account);
        } else {
            clearPreview();
        }
    }

    private void clearPreview() {
        if (previewFrontEntity != null) {
            previewFrontEntity.clearTextures();
        }
        if (previewBackEntity != null) {
            previewBackEntity.clearTextures();
        }
        previewFrontEntity = null;
        previewBackEntity = null;
    }

    private void rebuildProviderList() {
        providerList.removeAll();
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        if (hasFocusedLocalContext()) {
            selectedProvider = resolveFocusedLocalProvider();
            if (selectedProvider != null) {
                ensureRegisterCapabilityProbe(selectedProvider);
                if (focusedLocalStatusText == null || focusedLocalStatusText.isEmpty()) {
                    focusedLocalStatusText = GuiText
                        .tr("wawelauth.gui.common.ready_message", selectedProvider.getName());
                }
            } else {
                focusedLocalStatusText = hasFocusedLocalMetadata()
                    ? GuiText.tr("wawelauth.gui.local_auth.status.trust_first")
                    : GuiText.tr("wawelauth.gui.local_auth.status.not_advertised");
                if (selectedAccount != null) {
                    selectedAccount = null;
                    clearPreview();
                }
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
        for (ClientProvider provider : providers) {
            String providerName = provider.getName() != null ? provider.getName() : "?";
            String providerDisplayName = ProviderDisplayName.displayName(providerName);
            String displayProviderName = GuiText.ellipsizeToPixelWidth(providerDisplayName, PROVIDER_NAME_MAX_WIDTH_PX);
            boolean isSelected = selectedProvider != null && providerName.equals(selectedProvider.getName());
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
                selectProvider(provider);
                rebuildProviderList();
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
                    openProviderSettingsDialog(provider);
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
                selectProvider(providers.get(0));
                rebuildProviderList();
            } else {
                selectedProvider = null;
                selectedAccount = null;
                clearPreview();
            }
        }
    }

    private void rebuildAccountList() {
        ensureStatusCaches();
        resetAccountListScroll();
        accountList.removeAll();
        accountStatusDots.clear();
        renderedStatuses.clear();

        selectedProvider = resolveProvider(selectedProvider);
        if (selectedProvider == null) return;

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        List<ClientAccount> accounts = new ArrayList<>(
            client.getAccountManager()
                .listAccounts(selectedProvider.getName()));
        accounts.sort(
            Comparator.comparing(
                account -> normalizeSortKey(account != null ? account.getProfileName() : null),
                String.CASE_INSENSITIVE_ORDER));
        boolean selectedInProvider = false;
        if (selectedAccount != null) {
            long selectedId = selectedAccount.getId();
            for (ClientAccount account : accounts) {
                if (account.getId() == selectedId) {
                    selectedInProvider = true;
                    break;
                }
            }
        }

        if (!accounts.isEmpty() && !selectedInProvider) {
            selectAccount(accounts.get(0));
        } else if (accounts.isEmpty() && selectedAccount != null) {
            selectedAccount = null;
            clearPreview();
        }

        boolean selectedStillExists = false;
        for (ClientAccount account : accounts) {
            AccountStatus status = getLiveStatus(account);
            int statusColor = StatusColors.getColor(status);
            String profileName = account.getProfileName() != null ? account.getProfileName() : "?";
            String displayProfileName = GuiText.ellipsizeToPixelWidth(profileName, ACCOUNT_NAME_MAX_WIDTH_PX);
            boolean isSelected = selectedAccount != null && account.getId() == selectedAccount.getId();
            if (isSelected) {
                selectedAccount = account;
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
                selectAccount(account);
                rebuildAccountList();
                return true;
            });

            accountList.child(entry);
        }

        if (selectedAccount != null && !selectedStillExists) {
            selectedAccount = null;
            clearPreview();
        }
    }

    private Widget<?> createAccountFaceWidget(String displayName, UUID profileUuid, String providerName) {
        return new FaceWidget(displayName, profileUuid, providerName).size(8, 8)
            .margin(0, 3);
    }

    private void resetAccountListScroll() {
        if (accountList == null) {
            return;
        }
        try {
            accountList.getScrollData()
                .scrollTo(accountList.getScrollArea(), 0);
        } catch (Exception ignored) {
            // Scroll reset is best-effort; list still works without it.
        }
    }

    private void openProviderSettingsDialog(ClientProvider provider) {
        if (provider == null) return;
        this.pendingProviderSettingsName = provider.getName();
        this.providerSettingsDialogHandler.deleteCachedPanel();
        this.providerSettingsDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildProviderSettingsDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_provider_settings");
        dialog.setCloseOnOutOfBoundsClick(false);

        WawelClient client = WawelClient.instance();
        if (client == null || pendingProviderSettingsName == null) {
            dialog.size(230, 90)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.provider_not_available"))
                                .widthRel(1.0f)
                                .height(14))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
                                .margin(0, 6)
                                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .child(
                                    GuiText
                                        .fitButtonLabel(
                                            new ButtonWidget<>().size(70, 18),
                                            70,
                                            "wawelauth.gui.common.close")
                                        .onMousePressed(btn -> {
                                            dialog.closeIfOpen();
                                            return true;
                                        }))));
            return dialog;
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(pendingProviderSettingsName);
        if (provider == null) {
            dialog.size(230, 90)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.provider_gone")).widthRel(1.0f)
                                .height(14))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
                                .margin(0, 6)
                                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .child(
                                    GuiText
                                        .fitButtonLabel(
                                            new ButtonWidget<>().size(70, 18),
                                            70,
                                            "wawelauth.gui.common.close")
                                        .onMousePressed(btn -> {
                                            dialog.closeIfOpen();
                                            return true;
                                        }))));
            return dialog;
        }

        final String oldName = provider.getName();
        final boolean builtIn = provider.getType() == ProviderType.BUILTIN;
        final String[] statusText = {
            builtIn ? GuiText.tr("wawelauth.gui.account_manager.provider_builtin_locked") : "" };

        TextFieldWidget nameField = new TextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.provider_name"));
        nameField.widthRel(1.0f)
            .height(18)
            .setMaxLength(32)
            .margin(0, 2);
        nameField.setText(oldName);
        nameField.setEnabled(!builtIn);

        ButtonWidget<?> saveNameBtn = new ButtonWidget<>();
        saveNameBtn.size(88, 18)
            .onMousePressed(btn -> {
                if (builtIn) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_builtin_rename_forbidden");
                    return true;
                }

                String newName = nameField.getText()
                    .trim();
                if (newName.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_name_empty");
                    return true;
                }

                try {
                    client.getProviderRegistry()
                        .renameProvider(oldName, newName);

                    pendingProviderSettingsName = newName;
                    if (selectedProvider != null && oldName.equals(selectedProvider.getName())) {
                        selectedProvider = client.getProviderRegistry()
                            .getProvider(newName);
                    }

                    rebuildProviderList();
                    rebuildAccountList();
                    dialog.closeIfOpen();
                } catch (Exception e) {
                    statusText[0] = e.getMessage();
                    WawelAuth.debug("Provider rename failed: " + e.getMessage());
                }
                return true;
            });
        GuiText.fitButtonLabel(saveNameBtn, 88, "wawelauth.gui.account_manager.save_name");
        saveNameBtn.setEnabled(!builtIn);

        ButtonWidget<?> deleteProviderBtn = new ButtonWidget<>();
        deleteProviderBtn.size(98, 18)
            .onMousePressed(btn -> {
                if (builtIn) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.provider_builtin_remove_forbidden");
                    return true;
                }

                try {
                    client.getProviderRegistry()
                        .removeProvider(oldName);

                    if (selectedProvider != null && oldName.equals(selectedProvider.getName())) {
                        selectedProvider = null;
                        selectedAccount = null;
                        clearPreview();
                    }

                    pendingProviderSettingsName = null;
                    rebuildProviderList();
                    rebuildAccountList();
                    dialog.closeIfOpen();
                } catch (Exception e) {
                    statusText[0] = e.getMessage();
                    WawelAuth.debug("Provider deletion failed: " + e.getMessage());
                }
                return true;
            });
        GuiText.fitButtonLabel(deleteProviderBtn, 98, "wawelauth.gui.account_manager.delete_provider");
        deleteProviderBtn.setEnabled(!builtIn);

        ButtonWidget<?> closeBtn = new ButtonWidget<>();
        closeBtn.size(70, 18)
            .onMousePressed(btn -> {
                pendingProviderSettingsName = null;
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        dialog.size(270, 130)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.provider_settings")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(
                            GuiText.key(
                                "wawelauth.gui.account_manager.provider_line",
                                ProviderDisplayName.displayName(oldName))).color(0xFFAAAAAA)
                                    .scale(0.8f)
                                    .widthRel(1.0f)
                                    .height(10))
                    .child(nameField)
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 4)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(saveNameBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(deleteProviderBtn))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(closeBtn)));

        return dialog;
    }

    private void confirmAndRemoveSelectedAccount() {
        if (selectedAccount == null) return;

        this.pendingRemoveAccountName = selectedAccount.getProfileName() != null ? selectedAccount.getProfileName()
            : GuiText.tr("wawelauth.gui.account_manager.this_account");
        this.pendingRemoveAccountId = selectedAccount.getId();
        this.removeAccountDialogHandler.deleteCachedPanel();
        this.removeAccountDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildRemoveAccountDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_remove");
        dialog.setCloseOnOutOfBoundsClick(false);

        String name = pendingRemoveAccountName != null ? pendingRemoveAccountName
            : GuiText.tr("wawelauth.gui.account_manager.this_account");
        long accountId = pendingRemoveAccountId;

        TextWidget<?> warningText = new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.remove_warning"));
        warningText.color(0xFFAAAAAA)
            .scale(0.8f)
            .widthRel(1.0f)
            .height(10)
            .margin(0, 4);

        dialog.size(230, 80)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.remove_title", name)).widthRel(1.0f)
                            .height(14))
                    .child(warningText)
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(
                                GuiText
                                    .fitButtonLabel(
                                        new ButtonWidget<>().size(60, 18),
                                        60,
                                        "wawelauth.gui.common.cancel")
                                    .onMousePressed(btn -> {
                                        pendingRemoveAccountId = -1L;
                                        pendingRemoveAccountName = null;
                                        dialog.closeIfOpen();
                                        return true;
                                    }))
                            .child(new Widget<>().size(6, 18))
                            .child(
                                GuiText
                                    .fitButtonLabel(
                                        new ButtonWidget<>().size(60, 18),
                                        60,
                                        "wawelauth.gui.common.remove")
                                    .onMousePressed(btn -> {
                                        pendingRemoveAccountId = -1L;
                                        pendingRemoveAccountName = null;
                                        dialog.closeIfOpen();
                                        doRemoveAccount(accountId);
                                        return true;
                                    }))));
        return dialog;
    }

    private void openLoginDialog(String providerName, String username) {
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return;
        }
        if (ProviderDisplayName.isMicrosoftProvider(providerName)) {
            this.loginDialog.openMicrosoft(providerName);
            return;
        }
        this.loginDialog.open(providerName, username);
    }

    private void openLoginDialog(String providerName) {
        this.openLoginDialog(providerName, null);
    }

    private void openRegisterDialog(String providerName) {
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return;
        }
        this.registerDialog.open(providerName);
    }

    private void openCredentialDialog() {
        if (selectedAccount == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        if (!isCredentialManagementAvailableForSelectedAccount()) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.credentials_unavailable");
            return;
        }
        clearPendingCredentialDeleteState();
        this.credentialDialogHandler.deleteCachedPanel();
        this.credentialDialogHandler.openPanel();
    }

    private void doRemoveAccount(long accountId) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        client.getAccountManager()
            .removeAccount(accountId)
            .whenComplete((v, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> { // Minecraft.addScheduledTask
                        if (err != null) {
                            WawelAuth.LOG.warn("Failed to remove account: {}", err.getMessage());
                        } else {
                            ServerBindingPersistence.clearMissingAccountBindings(client.getAccountManager());
                        }
                        selectedAccount = null;
                        clearPreview();
                        rebuildAccountList();
                        requestAccountListRebuild();
                    });
            });
    }

    private ClientProvider resolveProvider(ClientProvider provider) {
        if (provider == null) {
            return null;
        }
        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return provider;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return provider;
        }
        ClientProvider resolved = client.getProviderRegistry()
            .getProvider(providerName);
        return resolved != null ? resolved : provider;
    }

    private AccountStatus getLiveStatus(ClientAccount account) {
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

    private void refreshVisibleStatuses() {
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
                        client.getSkinResolver()
                            .invalidate(account.getProfileUuid());
                    }
                }
            }

            if (selectedAccount != null && selectedAccount.getId() == accountId) {
                selectedAccount.setStatus(cached);
            }
        }
    }

    private void ensureStatusCaches() {
        if (accountStatusDots == null) {
            accountStatusDots = new HashMap<>();
        }
        if (renderedStatuses == null) {
            renderedStatuses = new HashMap<>();
        }
    }

    private void requestAccountListRebuild() {
        this.accountListRebuildPending = true;
    }

    private void loadSkinForAccount(ClientAccount account) {
        if (account.getProfileUuid() == null || previewFrontEntity == null || previewBackEntity == null) return;

        long frontRequestId = previewFrontEntity.newRequestId();
        long backRequestId = previewBackEntity.newRequestId();

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        UUID uuid = account.getProfileUuid();
        String name = account.getProfileName() != null ? account.getProfileName() : "?";
        WawelAuth.debug("Preview fetch profile via fillProfileFromProvider: " + UuidUtil.toUnsigned(uuid));

        CompletableFuture.supplyAsync(() -> {
            try {
                GameProfile probe = new GameProfile(uuid, name);
                GameProfile filled = client.getSessionBridge()
                    .fillProfileFromProvider(probe, true);
                if (filled == null || filled.getProperties()
                    .isEmpty()) {
                    return null;
                }
                return gameProfileToJson(filled);
            } catch (Exception e) {
                WawelAuth.debug("Failed to fetch profile for skin: " + e.getMessage());
                return null;
            }
        })
            .whenComplete((response, err) -> {
                if (err != null) {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    WawelAuth.debug("Profile request failed: " + cause.getMessage());
                }
                if (response == null || previewFrontEntity == null
                    || previewBackEntity == null
                    || previewFrontEntity.isRequestStale(frontRequestId)
                    || previewBackEntity.isRequestStale(backRequestId)) return;

                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (previewFrontEntity == null || previewBackEntity == null
                            || previewFrontEntity.isRequestStale(frontRequestId)
                            || previewBackEntity.isRequestStale(backRequestId)) return;
                        applyTexturesFromProfile(response, uuid, frontRequestId, backRequestId);
                    });
            });
    }

    private static JsonObject gameProfileToJson(GameProfile profile) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", UuidUtil.toUnsigned(profile.getId()));
        obj.addProperty("name", profile.getName());
        JsonArray props = new JsonArray();
        for (Map.Entry<String, java.util.Collection<com.mojang.authlib.properties.Property>> entry : profile
            .getProperties()
            .asMap()
            .entrySet()) {
            for (com.mojang.authlib.properties.Property prop : entry.getValue()) {
                JsonObject propObj = new JsonObject();
                propObj.addProperty("name", prop.getName());
                propObj.addProperty("value", prop.getValue());
                if (prop.getSignature() != null) {
                    propObj.addProperty("signature", prop.getSignature());
                }
                props.add(propObj);
            }
        }
        obj.add("properties", props);
        return obj;
    }

    private void applyTexturesFromProfile(JsonObject profileResponse, UUID uuid, long frontRequestId,
        long backRequestId) {
        if (profileResponse == null || !profileResponse.has("properties")) return;

        try {
            JsonArray properties = profileResponse.getAsJsonArray("properties");
            boolean foundTextures = false;
            boolean foundSkin = false;
            for (JsonElement elem : properties) {
                JsonObject prop = elem.getAsJsonObject();
                if (!prop.has("name") || !"textures".equals(
                    prop.get("name")
                        .getAsString())) {
                    continue;
                }
                foundTextures = true;

                String base64Value = prop.get("value")
                    .getAsString();
                String decoded = new String(
                    Base64.getDecoder()
                        .decode(base64Value),
                    StandardCharsets.UTF_8);
                JsonObject texturesWrapper = new JsonParser().parse(decoded)
                    .getAsJsonObject();
                if (!texturesWrapper.has("textures") || !texturesWrapper.get("textures")
                    .isJsonObject()) {
                    continue;
                }
                JsonObject textures = texturesWrapper.getAsJsonObject("textures");

                if (textures.has("SKIN")) {
                    JsonObject skinObj = textures.getAsJsonObject("SKIN");
                    String skinUrl = skinObj.get("url")
                        .getAsString();
                    SkinModel model = SkinModel.CLASSIC;
                    if (skinObj.has("metadata") && skinObj.get("metadata")
                        .isJsonObject()) {
                        JsonObject metadata = skinObj.getAsJsonObject("metadata");
                        if (metadata.has("model") && metadata.get("model")
                            .isJsonPrimitive()) {
                            model = SkinModel.fromYggdrasil(
                                metadata.get("model")
                                    .getAsString());
                        }
                    }
                    WawelAuth.debug("Preview skin model: " + model.name() + " for " + UuidUtil.toUnsigned(uuid));
                    WawelAuth.debug("Preview skin URL: " + skinUrl);
                    previewFrontEntity.setForcedSkinModel(model);
                    previewBackEntity.setForcedSkinModel(model);
                    previewFrontEntity.setSkinFromUrl(skinUrl, uuid, frontRequestId);
                    ResourceLocation sharedSkin = previewFrontEntity.getCustomSkinLocation();
                    if (sharedSkin != null) {
                        previewBackEntity.setSkinFromExisting(sharedSkin, backRequestId);
                    } else {
                        previewBackEntity.setSkinFromUrl(skinUrl, uuid, backRequestId);
                    }
                    foundSkin = true;
                }
                if (textures.has("CAPE")) {
                    JsonObject capeObj = textures.getAsJsonObject("CAPE");
                    String capeUrl = capeObj.get("url")
                        .getAsString();
                    boolean animated = false;
                    if (capeObj.has("metadata") && capeObj.get("metadata")
                        .isJsonObject()) {
                        JsonObject capeMeta = capeObj.getAsJsonObject("metadata");
                        if (capeMeta.has("animated") && "true".equals(
                            capeMeta.get("animated")
                                .getAsString())) {
                            animated = true;
                        }
                    }
                    WawelAuth.debug("Preview cape URL: " + capeUrl + (animated ? " (animated)" : ""));
                    if (animated) {
                        previewFrontEntity.setCapeAnimated(capeUrl, uuid, frontRequestId, () -> {
                            if (previewFrontEntity == null || previewBackEntity == null
                                || previewFrontEntity.isRequestStale(frontRequestId)
                                || previewBackEntity.isRequestStale(backRequestId)) {
                                return;
                            }
                            ResourceLocation sharedCape = previewFrontEntity.getCustomCapeLocation();
                            if (sharedCape != null) {
                                previewBackEntity.setCapeFromExisting(sharedCape, backRequestId);
                            }
                        });
                    } else {
                        previewFrontEntity.setCapeFromUrl(capeUrl, uuid, frontRequestId);
                        ResourceLocation sharedCape = previewFrontEntity.getCustomCapeLocation();
                        if (sharedCape != null) {
                            previewBackEntity.setCapeFromExisting(sharedCape, backRequestId);
                        } else {
                            previewBackEntity.setCapeFromUrl(capeUrl, uuid, backRequestId);
                        }
                    }
                }
                applyCapePreviewVisibility();
                break;
            }
            if (!foundTextures) {
                WawelAuth.debug("Profile response has no textures property for preview.");
            } else if (!foundSkin) {
                WawelAuth.debug("Textures property has no SKIN entry for preview.");
            }
        } catch (Exception e) {
            WawelAuth.debug("Failed to parse textures from profile: " + e.getMessage());
        }
    }

    private void prepareEntityPreview(PlayerPreviewEntity entity, boolean backView) {
        // EntityDisplayWidget may run after flat-color draw calls; ensure textured rendering state.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        entity.stabilizeCapePhysics();

        Minecraft mc = Minecraft.getMinecraft();
        if (entity.worldObj != null) {
            RenderManager.instance.cacheActiveRenderInfo(
                entity.worldObj,
                mc.getTextureManager(),
                mc.fontRenderer,
                entity,
                entity,
                mc.gameSettings,
                0.0F);
        }

        float yaw = backView ? 180.0F : 0.0F;
        entity.renderYawOffset = yaw;
        entity.rotationYaw = yaw;
        entity.rotationYawHead = yaw;
        entity.prevRotationYawHead = yaw;
        entity.rotationPitch = 0.0F;
    }

    private void applyCapePreviewVisibility() {
        if (previewFrontEntity != null) {
            previewFrontEntity.setCapeVisible(capePreviewEnabled);
        }
        if (previewBackEntity != null) {
            previewBackEntity.setCapeVisible(capePreviewEnabled);
        }
    }

    private Dialog<Boolean> buildCredentialDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_credentials");
        dialog.setCloseOnOutOfBoundsClick(false);
        WawelClient client = WawelClient.instance();
        ClientAccount account = selectedAccount;

        String profileName = account != null && account.getProfileName() != null ? account.getProfileName()
            : GuiText.tr("wawelauth.gui.common.account");
        String providerName = account != null ? account.getProviderName() : null;
        ClientProvider provider = client != null && providerName != null ? client.getProviderRegistry()
            .getProvider(providerName) : null;
        boolean credentialSupported = isCredentialManagementSupported(provider);

        if (account == null || client == null || !credentialSupported) {
            String reason = account == null ? GuiText.tr("wawelauth.gui.common.no_account_selected")
                : GuiText.tr("wawelauth.gui.account_manager.credentials_unavailable");
            dialog.size(258, 96)
                .child(
                    new Column().widthRel(1.0f)
                        .heightRel(1.0f)
                        .padding(8)
                        .child(
                            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.credentials")).widthRel(1.0f)
                                .height(14))
                        .child(
                            new TextWidget<>(IKey.str(reason)).color(0xFFFFAA55)
                                .widthRel(1.0f)
                                .height(12)
                                .margin(0, 8))
                        .child(
                            new Row().widthRel(1.0f)
                                .height(20)
                                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .child(
                                    GuiText
                                        .fitButtonLabel(
                                            new ButtonWidget<>().size(70, 18),
                                            70,
                                            "wawelauth.gui.common.close")
                                        .onMousePressed(btn -> {
                                            dialog.closeIfOpen();
                                            return true;
                                        }))));
            return dialog;
        }

        PasswordInputWidget currentPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.current_password"));
        PasswordInputWidget newPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.new_password"));
        PasswordInputWidget confirmPasswordField = new PasswordInputWidget()
            .hintText(GuiText.tr("wawelauth.gui.credentials.confirm_new_password"));
        String[] statusText = { "" };
        boolean[] busy = { false };

        ButtonWidget<?> changePasswordBtn = new ButtonWidget<>();
        changePasswordBtn.size(100, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;

                String currentPassword = currentPasswordField.getText();
                String newPassword = newPasswordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (currentPassword == null || currentPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.current_required");
                    return true;
                }
                if (newPassword == null || newPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.new_required");
                    return true;
                }
                if (!newPassword.equals(confirmPassword)) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.mismatch");
                    return true;
                }
                if (newPassword.equals(currentPassword)) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.same_password");
                    return true;
                }

                busy[0] = true;
                statusText[0] = GuiText.tr("wawelauth.gui.credentials.status.updating");

                client.getAccountManager()
                    .changePassword(account.getId(), currentPassword, newPassword)
                    .whenComplete((ignored, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> {
                                busy[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    statusText[0] = cause.getMessage();
                                    WawelAuth.debug("Password change failed: " + cause.getMessage());
                                    return;
                                }
                                statusText[0] = GuiText.tr("wawelauth.gui.credentials.status.changed");
                                currentPasswordField.setText("");
                                newPasswordField.setText("");
                                confirmPasswordField.setText("");
                            });
                    });
                return true;
            });
        GuiText.fitButtonLabel(changePasswordBtn, 100, "wawelauth.gui.credentials.change_password");

        ButtonWidget<?> deleteServerAccountBtn = new ButtonWidget<>();
        deleteServerAccountBtn.size(106, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;

                String currentPassword = currentPasswordField.getText();
                if (currentPassword == null || currentPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.delete_requires_password");
                    return true;
                }

                pendingCredentialDeleteAccountId = account.getId();
                pendingCredentialDeleteAccountName = account.getProfileName() != null ? account.getProfileName()
                    : GuiText.tr("wawelauth.gui.common.account");
                pendingCredentialDeletePassword = currentPassword;
                dialog.closeIfOpen();
                openCredentialDeleteDialog();
                return true;
            });
        GuiText.fitButtonLabel(deleteServerAccountBtn, 106, "wawelauth.gui.credentials.delete_account");

        ButtonWidget<?> closeBtn = new ButtonWidget<>();
        closeBtn.size(70, 18)
            .onMousePressed(btn -> {
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(closeBtn, 70, "wawelauth.gui.common.close");

        dialog.size(266, 177)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.credentials")).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(
                            GuiText.key(
                                "wawelauth.gui.credentials.selected",
                                profileName,
                                ProviderDisplayName.displayName(providerName))).color(0xFFAAAAAA)
                                    .scale(0.8f)
                                    .widthRel(1.0f)
                                    .height(10))
                    .child(
                        currentPasswordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 4))
                    .child(
                        newPasswordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 3))
                    .child(
                        confirmPasswordField.widthRel(1.0f)
                            .height(18)
                            .setMaxLength(128)
                            .margin(0, 3))
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 4)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(changePasswordBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(deleteServerAccountBtn))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(closeBtn)));
        return dialog;
    }

    private void openCredentialDeleteDialog() {
        if (pendingCredentialDeleteAccountId < 0L) {
            return;
        }
        this.credentialDeleteDialogHandler.deleteCachedPanel();
        this.credentialDeleteDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildCredentialDeleteDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_delete_server_account");
        dialog.setCloseOnOutOfBoundsClick(false);

        long accountId = pendingCredentialDeleteAccountId;
        String accountName = pendingCredentialDeleteAccountName != null ? pendingCredentialDeleteAccountName
            : GuiText.tr("wawelauth.gui.common.account");
        String currentPassword = pendingCredentialDeletePassword;
        WawelClient client = WawelClient.instance();

        String[] statusText = { "" };
        boolean[] busy = { false };

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        cancelBtn.size(62, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;
                clearPendingCredentialDeleteState();
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(cancelBtn, 62, "wawelauth.gui.common.cancel");

        ButtonWidget<?> deleteBtn = new ButtonWidget<>();
        deleteBtn.size(62, 18)
            .onMousePressed(btn -> {
                if (busy[0]) return true;

                if (client == null) {
                    statusText[0] = GuiText.tr("wawelauth.gui.common.client_not_running");
                    return true;
                }
                if (accountId < 0L) {
                    statusText[0] = GuiText.tr("wawelauth.gui.common.no_account_selected");
                    return true;
                }
                if (currentPassword == null || currentPassword.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.credentials.error.current_required");
                    return true;
                }

                busy[0] = true;
                statusText[0] = GuiText.tr("wawelauth.gui.credentials.status.deleting");

                client.getAccountManager()
                    .deleteWawelAuthAccount(accountId, currentPassword)
                    .whenComplete((ignored, err) -> {
                        Minecraft.getMinecraft()
                            .func_152344_a(() -> {
                                busy[0] = false;
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    statusText[0] = cause.getMessage();
                                    WawelAuth.debug("Server account deletion failed: " + cause.getMessage());
                                    return;
                                }

                                clearPendingCredentialDeleteState();
                                if (selectedAccount != null && selectedAccount.getId() == accountId) {
                                    selectedAccount = null;
                                    clearPreview();
                                }
                                ServerBindingPersistence.clearMissingAccountBindings(client.getAccountManager());
                                rebuildAccountList();
                                requestAccountListRebuild();
                                textureUploadStatus = GuiText.tr("wawelauth.gui.credentials.status.deleted");
                                dialog.closeIfOpen();
                            });
                    });
                return true;
            });
        GuiText.fitButtonLabel(deleteBtn, 62, "wawelauth.gui.common.delete");

        dialog.size(270, 104)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.credentials.delete_title", accountName))
                            .widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.credentials.delete_warning")).color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10))
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 6))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(cancelBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(deleteBtn)));
        return dialog;
    }

    private void clearPendingCredentialDeleteState() {
        pendingCredentialDeleteAccountId = -1L;
        pendingCredentialDeleteAccountName = null;
        pendingCredentialDeletePassword = null;
    }

    private void clearTextureSelection() {
        selectedSkinFile = null;
        selectedCapeFile = null;
        textureSelectionStatus = GuiText.tr("wawelauth.gui.account_manager.no_texture_selected");
    }

    private boolean shouldShowTextureSelectionTooltip() {
        if (selectedSkinFile == null && selectedCapeFile == null) {
            return false;
        }
        return !isBlank(textureSelectionStatus);
    }

    private boolean shouldShowTextureUploadTooltip() {
        return !isBlank(textureUploadStatus);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }

    private void chooseTextureFile(boolean skin) {
        if (selectedAccount == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        String label = GuiText.tr(skin ? "wawelauth.gui.account_manager.skin" : "wawelauth.gui.account_manager.cape");
        SystemOpenUtil.FilePickerResult result = SystemOpenUtil.pickImageFile(
            GuiText.tr("wawelauth.gui.account_manager.select_texture_image", label),
            getTexturePickerInitialDirectory(skin));

        if (result.getStatus() == SystemOpenUtil.FilePickerResult.Status.SELECTED) {
            File picked = result.getFile();
            if (skin) {
                selectedSkinFile = picked;
                textureSelectionStatus = GuiText
                    .tr("wawelauth.gui.account_manager.skin_selected", trimPath(picked.getAbsolutePath(), 68));
            } else {
                selectedCapeFile = picked;
                textureSelectionStatus = GuiText
                    .tr("wawelauth.gui.account_manager.cape_selected", trimPath(picked.getAbsolutePath(), 68));
            }
            textureUploadStatus = "";
            return;
        }

        if (result.getStatus() == SystemOpenUtil.FilePickerResult.Status.CANCELLED) {
            return;
        }

        String message = result.getMessage();
        if (message == null || message.trim()
            .isEmpty()) {
            message = GuiText.tr("wawelauth.gui.account_manager.file_picker_fallback");
        }
        WawelAuth.LOG.warn("Texture file picker failed ({}): {}", result.getStatus(), message);
        textureUploadStatus = message;
        openTexturePathDialog(skin);
    }

    private void attemptTextureUpload() {
        if (selectedAccount == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        if (selectedSkinFile == null && selectedCapeFile == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.choose_texture_first");
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        final long accountId = selectedAccount.getId();
        final File skin = selectedSkinFile;
        final File cape = selectedCapeFile;
        final boolean skinSlim = skinUploadSlim;
        textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.uploading");

        client.getAccountManager()
            .uploadTextures(accountId, skin, cape, skinSlim)
            .whenComplete((result, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            textureUploadStatus = GuiText.tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            WawelAuth.debug("Texture upload failed: " + cause.getMessage());
                            return;
                        }

                        textureUploadStatus = result != null ? result
                            : GuiText.tr("wawelauth.gui.account_manager.upload_complete");
                        ClientAccount refreshed = client.getAccountManager()
                            .getAccount(accountId);
                        if (selectedAccount != null && selectedAccount.getId() == accountId) {
                            if (refreshed != null) {
                                selectedAccount = refreshed;
                            }
                            // Invalidate cached profile so the next fetch gets the new skin URL
                            if (selectedAccount.getProfileUuid() != null) {
                                client.getSkinResolver()
                                    .invalidate(selectedAccount.getProfileUuid());
                            }
                            loadSkinForAccount(selectedAccount);
                        }
                        if (selectedProvider != null) {
                            rebuildAccountList();
                            requestAccountListRebuild();
                        }
                    });
            });
    }

    private void openTexturePathDialog(boolean skin) {
        this.texturePathDialogForSkin = skin;
        File current = skin ? selectedSkinFile : selectedCapeFile;
        this.texturePathDialogInitialPath = current != null ? current.getAbsolutePath() : defaultTexturePath(skin);
        this.texturePathDialogHandler.deleteCachedPanel();
        this.texturePathDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildTexturePathDialog() {
        final boolean skin = this.texturePathDialogForSkin;
        final String label = GuiText
            .tr(skin ? "wawelauth.gui.account_manager.skin" : "wawelauth.gui.account_manager.cape");

        Dialog<Boolean> dialog = new Dialog<>("wawelauth_texture_path");
        dialog.setCloseOnOutOfBoundsClick(false);

        final String[] statusText = { "" };
        TextFieldWidget pathField = new TextFieldWidget()
            .hintText(GuiText.tr("wawelauth.gui.account_manager.path_hint", label.toLowerCase()));
        pathField.widthRel(1.0f)
            .height(18)
            .setMaxLength(4096)
            .margin(0, 2);
        if (texturePathDialogInitialPath != null) {
            pathField.setText(texturePathDialogInitialPath);
        }

        ButtonWidget<?> openFolderBtn = new ButtonWidget<>();
        openFolderBtn.size(86, 18)
            .onMousePressed(btn -> {
                File folder = getTexturePickerInitialDirectory(skin);
                boolean opened = SystemOpenUtil.openFolder(folder);
                statusText[0] = opened
                    ? GuiText.tr("wawelauth.gui.account_manager.opened_path", trimPath(folder.getAbsolutePath(), 74))
                    : GuiText.tr("wawelauth.gui.account_manager.open_folder_failed");
                return true;
            });
        GuiText.fitButtonLabel(openFolderBtn, 86, "wawelauth.gui.common.open_folder");

        ButtonWidget<?> usePathBtn = new ButtonWidget<>();
        usePathBtn.size(86, 18)
            .onMousePressed(btn -> {
                String raw = pathField.getText();
                String path = raw != null ? raw.trim() : "";
                if (path.isEmpty()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.path_required", label);
                    return true;
                }

                File picked = new File(path);
                if (!picked.isFile()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.file_not_found");
                    return true;
                }
                if (!picked.canRead()) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.file_not_readable");
                    return true;
                }
                String lowerName = picked.getName()
                    .toLowerCase();
                if (!lowerName.endsWith(".png") && !lowerName.endsWith(".gif")) {
                    statusText[0] = GuiText.tr("wawelauth.gui.account_manager.file_types_supported");
                    return true;
                }

                if (skin) {
                    selectedSkinFile = picked;
                    textureSelectionStatus = GuiText
                        .tr("wawelauth.gui.account_manager.skin_selected", trimPath(picked.getAbsolutePath(), 68));
                } else {
                    selectedCapeFile = picked;
                    textureSelectionStatus = GuiText
                        .tr("wawelauth.gui.account_manager.cape_selected", trimPath(picked.getAbsolutePath(), 68));
                }
                textureUploadStatus = "";
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(usePathBtn, 86, "wawelauth.gui.account_manager.use_path");

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        cancelBtn.size(70, 18)
            .onMousePressed(btn -> {
                dialog.closeIfOpen();
                return true;
            });
        GuiText.fitButtonLabel(cancelBtn, 70, "wawelauth.gui.common.cancel");

        dialog.size(316, 130)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.select_texture_file", label))
                            .widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.path_help")).color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10))
                    .child(pathField)
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(0xFFFFAA55)
                            .widthRel(1.0f)
                            .height(12)
                            .margin(0, 2))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .margin(0, 4)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(openFolderBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(usePathBtn)
                            .child(new Widget<>().size(6, 18))
                            .child(cancelBtn)));

        return dialog;
    }

    private File getTexturePickerInitialDirectory(boolean skin) {
        File current = skin ? selectedSkinFile : selectedCapeFile;
        File other = skin ? selectedCapeFile : selectedSkinFile;

        File currentParent = parentDirectory(current);
        if (currentParent != null) {
            return currentParent;
        }

        File otherParent = parentDirectory(other);
        if (otherParent != null) {
            return otherParent;
        }

        return SystemOpenUtil.getDefaultFileSelectionDirectory();
    }

    private static File parentDirectory(File file) {
        if (file == null) {
            return null;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return parent;
        }
        return file.isDirectory() ? file : null;
    }

    private static String defaultTexturePath(boolean skin) {
        return new File(SystemOpenUtil.getDefaultFileSelectionDirectory(), skin ? "skin.png" : "cape.png")
            .getAbsolutePath();
    }

    private static String trimPath(String path, int maxLength) {
        if (path == null) return "";
        if (path.length() <= maxLength) return path;
        return "..." + path.substring(path.length() - maxLength + 3);
    }

    private boolean isSkinUploadDisabledForSelectedProvider() {
        if (selectedProvider == null) return false;
        return Config.client()
            .isSkinUploadDisabled(selectedProvider.getName(), selectedProvider.getApiRoot());
    }

    private boolean isCapeUploadDisabledForSelectedProvider() {
        if (selectedProvider == null) return false;
        return Config.client()
            .isCapeUploadDisabled(selectedProvider.getName(), selectedProvider.getApiRoot());
    }

    private boolean isAnyTextureUploadEnabled() {
        return !isSkinUploadDisabledForSelectedProvider() || !isCapeUploadDisabledForSelectedProvider();
    }

    private boolean isTextureResetDisabledForSelectedProvider() {
        if (selectedProvider == null) return false;
        return Config.client()
            .isTextureResetDisabled(selectedProvider.getName(), selectedProvider.getApiRoot());
    }

    private boolean isTextureResetEnabledForSelectedProvider() {
        return !isTextureResetDisabledForSelectedProvider();
    }

    private void attemptTextureReset() {
        if (selectedAccount == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        this.textureResetDialogHandler.deleteCachedPanel();
        this.textureResetDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildTextureResetDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_texture_reset");
        dialog.setCloseOnOutOfBoundsClick(false);

        String name = selectedAccount != null && selectedAccount.getProfileName() != null
            ? selectedAccount.getProfileName()
            : GuiText.tr("wawelauth.gui.account_manager.this_account");

        dialog.size(230, 80)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.reset_title", name)).widthRel(1.0f)
                            .height(14))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.reset_warning")).color(0xFFAAAAAA)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10)
                            .margin(0, 4))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(20)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(
                                GuiText
                                    .fitButtonLabel(
                                        new ButtonWidget<>().size(60, 18),
                                        60,
                                        "wawelauth.gui.common.cancel")
                                    .onMousePressed(btn -> {
                                        dialog.closeIfOpen();
                                        return true;
                                    }))
                            .child(new Widget<>().size(6, 18))
                            .child(
                                GuiText
                                    .fitButtonLabel(new ButtonWidget<>().size(60, 18), 60, "wawelauth.gui.common.reset")
                                    .onMousePressed(btn -> {
                                        dialog.closeIfOpen();
                                        doTextureReset();
                                        return true;
                                    }))));
        return dialog;
    }

    private void doTextureReset() {
        if (selectedAccount == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        final long accountId = selectedAccount.getId();
        textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.resetting");

        // Immediately clear preview to show default skin while the server request is in flight
        if (previewFrontEntity != null) {
            previewFrontEntity.clearTextures();
        }
        if (previewBackEntity != null) {
            previewBackEntity.clearTextures();
        }

        client.getAccountManager()
            .deleteTextures(accountId)
            .whenComplete((result, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        if (err != null) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            textureUploadStatus = GuiText.tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            WawelAuth.debug("Texture reset failed: " + cause.getMessage());
                            // Reload the old skin since the reset failed
                            if (selectedAccount != null && selectedAccount.getId() == accountId) {
                                loadSkinForAccount(selectedAccount);
                            }
                            return;
                        }

                        textureUploadStatus = result != null ? result
                            : GuiText.tr("wawelauth.gui.account_manager.reset_complete");
                        selectedSkinFile = null;
                        selectedCapeFile = null;
                        ClientAccount refreshed = client.getAccountManager()
                            .getAccount(accountId);
                        if (selectedAccount != null && selectedAccount.getId() == accountId) {
                            if (refreshed != null) {
                                selectedAccount = refreshed;
                            }
                            if (selectedAccount.getProfileUuid() != null) {
                                client.getSkinResolver()
                                    .invalidate(selectedAccount.getProfileUuid());
                            }
                            loadSkinForAccount(selectedAccount);
                        }
                        if (selectedProvider != null) {
                            rebuildAccountList();
                            requestAccountListRebuild();
                        }
                    });
            });
    }

    private boolean isCredentialManagementAvailableForSelectedAccount() {
        if (selectedAccount == null) {
            return false;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return false;
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(selectedAccount.getProviderName());
        return isCredentialManagementSupported(provider);
    }

    private boolean isCredentialManagementSupported(ClientProvider provider) {
        provider = resolveProvider(provider);
        if (provider == null) {
            return false;
        }

        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return false;
        }

        if (ProviderDisplayName.isMicrosoftProvider(providerName) || provider.getType() == ProviderType.BUILTIN) {
            return false;
        }

        if (registerCapabilityByProvider != null) {
            Boolean supported = registerCapabilityByProvider.get(providerName);
            if (supported != null) {
                return supported.booleanValue();
            }
        }

        ensureRegisterCapabilityProbe(provider);
        return false;
    }

    private boolean isRegisterVisibleForSelectedProvider() {
        if (hasFocusedLocalContext()) {
            return hasFocusedLocalMetadata();
        }

        ClientProvider provider = resolveProvider(selectedProvider);
        if (provider == null) {
            return false;
        }

        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return false;
        }

        if (ProviderDisplayName.isMicrosoftProvider(providerName) || provider.getType() == ProviderType.BUILTIN) {
            registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return false;
        }

        Boolean supported = registerCapabilityByProvider.get(providerName);
        if (supported != null) {
            return supported.booleanValue();
        }

        ensureRegisterCapabilityProbe(provider);
        return false;
    }

    private void ensureRegisterCapabilityProbe(ClientProvider provider) {
        if (provider == null) return;

        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return;
        }

        if (registerCapabilityByProvider.containsKey(providerName)
            || registerCapabilityProbeInFlight.contains(providerName)) {
            return;
        }

        if (ProviderDisplayName.isMicrosoftProvider(providerName) || provider.getType() == ProviderType.BUILTIN) {
            registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return;
        }

        registerCapabilityProbeInFlight.add(providerName);
        client.getAccountManager()
            .probeSupportsWawelRegister(providerName)
            .whenComplete((supported, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        registerCapabilityProbeInFlight.remove(providerName);
                        registerCapabilityByProvider.put(providerName, err == null && Boolean.TRUE.equals(supported));
                    });
            });
    }

    private boolean shouldShowProviderInGeneralList(ClientProvider provider) {
        return provider != null && (provider.getType() == ProviderType.BUILTIN || provider.isManualEntry());
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
        if (name != null && name.startsWith("LocalAuth-")) {
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

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeSortKey(String value) {
        String normalized = normalize(value);
        return normalized != null ? normalized : "";
    }

}
