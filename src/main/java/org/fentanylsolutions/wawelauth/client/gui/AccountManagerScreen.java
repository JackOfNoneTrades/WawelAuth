package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;

import org.fentanylsolutions.fentlib.util.FileUtil;
import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.compat.EtFuturumCompat;
import org.fentanylsolutions.wawelauth.client.fakeworld.DummyEntityClientPlayerMP;
import org.fentanylsolutions.wawelauth.client.fakeworld.DummyWorldClient;
import org.fentanylsolutions.wawelauth.client.fakeworld.PreviewEntityRenderContext;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.EntityDisplayWidget;
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

    private enum PreviewBackMode {

        NONE("wawelauth.gui.account_manager.preview_none"),
        CAPE("wawelauth.gui.account_manager.preview_cape"),
        ELYTRA("wawelauth.gui.account_manager.preview_elytra");

        private final String translationKey;

        PreviewBackMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public PreviewBackMode next(boolean allowElytra) {
            switch (this) {
                case NONE:
                    return CAPE;
                case CAPE:
                    return allowElytra ? ELYTRA : NONE;
                case ELYTRA:
                default:
                    return NONE;
            }
        }
    }

    private static final long STATUS_UI_REFRESH_INTERVAL_MS = 1000L;
    private static final int TEXTURE_STATUS_MAX_WIDTH_PX = 212;
    private static final int DETAIL_PRIMARY_TEXT_COLOR = 0xFF000000;
    private static final int DETAIL_SECONDARY_TEXT_COLOR = 0xFF555555;
    private static final int PREVIEW_PANEL_BACKGROUND_COLOR = 0x22000000;
    private static final int LIST_OUTLINE_COLOR = PREVIEW_PANEL_BACKGROUND_COLOR;

    private static ServerData pendingFocusedServerData;
    private static ServerCapabilities pendingFocusedCapabilities;

    private ClientProvider selectedProvider;
    private ClientAccount selectedAccount;
    private PlayerPreviewEntity previewFrontEntity;
    private PlayerPreviewEntity previewBackEntity;
    private AddProviderDialog addProviderDialog;
    private LoginDialog loginDialog;
    private RegisterDialog registerDialog;
    private AccountManagerProviderDialogs providerDialogs;
    private AccountManagerCredentialDialogs credentialDialogs;
    private IPanelHandler removeAccountDialogHandler;
    private IPanelHandler providerSettingsDialogHandler;
    private IPanelHandler providerProxyDialogHandler;
    private IPanelHandler credentialDialogHandler;
    private IPanelHandler credentialDeleteDialogHandler;
    private IPanelHandler texturePathDialogHandler;
    private IPanelHandler textureResetDialogHandler;
    private long pendingRemoveAccountId = -1L;
    private String pendingRemoveAccountName;
    private String pendingProviderSettingsName;
    private String pendingProviderProxyName;
    private boolean texturePathDialogForSkin;
    private String texturePathDialogInitialPath;
    private PreviewBackMode capePreviewMode = PreviewBackMode.CAPE;
    private File selectedSkinFile;
    private File selectedCapeFile;
    private boolean skinUploadSlim;
    private String textureSelectionStatus = "";
    private String textureUploadStatus = "";

    private ModularPanel mainPanel;
    private FocusedLocalAuthPanel focusedLocalPanel;
    private AccountManagerProviderListPanel providerListPanel;
    private AccountManagerAccountListPanel accountListPanel;
    private Map<String, Boolean> registerCapabilityByProvider;
    private Set<String> registerCapabilityProbeInFlight;
    private long nextStatusUiRefreshAtMs;

    public AccountManagerScreen() {
        super("wawelauth");
        openParentOnClose(true);
    }

    /**
     * Whether the texture preview is active (an account with a profile is selected).
     * Used by the drop handler to decide if file drops should show the texture zone overlay.
     */
    public boolean isTexturePreviewActive() {
        return previewFrontEntity != null && selectedAccount != null;
    }

    /**
     * Accept a file dropped from outside the GUI as a skin or cape selection.
     */
    public void acceptDroppedTextureFile(File file, boolean isSkin) {
        if (selectedAccount == null) return;
        String lowerName = file.getName()
            .toLowerCase();
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".gif")) {
            textureSelectionStatus = GuiText.tr("wawelauth.gui.account_manager.file_types_supported");
            return;
        }
        if (!file.isFile() || !file.canRead()) {
            textureSelectionStatus = GuiText.tr("wawelauth.gui.account_manager.file_not_readable");
            return;
        }
        if (isSkin) {
            selectedSkinFile = file;
            textureSelectionStatus = GuiText
                .tr("wawelauth.gui.account_manager.skin_selected", trimPath(file.getAbsolutePath(), 68));
        } else {
            selectedCapeFile = file;
            textureSelectionStatus = GuiText
                .tr("wawelauth.gui.account_manager.cape_selected", trimPath(file.getAbsolutePath(), 68));
        }
        textureUploadStatus = "";
    }

    public static void openForLocalAuth(ServerData serverData) {
        pendingFocusedServerData = serverData;
        pendingFocusedCapabilities = ServerBindingPersistence.getEffectiveLocalAuthCapabilities(serverData);
        ClientGUI.open(new AccountManagerScreen());
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        mainPanel = ModularPanel.defaultPanel("wawelauth_account_manager", 360, 240);
        registerCapabilityByProvider = new HashMap<>();
        registerCapabilityProbeInFlight = new HashSet<>();
        nextStatusUiRefreshAtMs = 0L;

        ServerData focusedLocalServerData = null;
        ServerCapabilities focusedLocalCapabilities = null;
        if (pendingFocusedServerData != null || pendingFocusedCapabilities != null) {
            focusedLocalServerData = pendingFocusedServerData;
            focusedLocalCapabilities = pendingFocusedCapabilities;
            pendingFocusedServerData = null;
            pendingFocusedCapabilities = null;
        }

        focusedLocalPanel = new FocusedLocalAuthPanel(
            focusedLocalServerData,
            focusedLocalCapabilities,
            () -> selectedProvider,
            value -> selectedProvider = value,
            () -> selectedAccount,
            value -> selectedAccount = value,
            this::resolveProvider,
            this::ensureRegisterCapabilityProbe,
            this::rebuildProviderList,
            this::rebuildAccountList,
            this::requestAccountListRebuild,
            this::clearPreview,
            this::openProviderProxyDialog);
        focusedLocalPanel.initializeSelectedProvider();

        providerListPanel = new AccountManagerProviderListPanel(
            () -> selectedProvider,
            value -> selectedProvider = value,
            value -> selectedAccount = value,
            this::hasFocusedLocalContext,
            () -> focusedLocalPanel.refreshProviderListState(),
            this::selectProvider,
            this::clearPreview,
            this::openProviderSettingsDialog);
        accountListPanel = new AccountManagerAccountListPanel(
            () -> selectedProvider,
            value -> selectedProvider = value,
            this::resolveProvider,
            () -> selectedAccount,
            value -> selectedAccount = value,
            this::selectAccount,
            this::clearPreview);

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
        providerDialogs = new AccountManagerProviderDialogs(
            () -> pendingProviderSettingsName,
            value -> pendingProviderSettingsName = value,
            () -> pendingProviderProxyName,
            value -> pendingProviderProxyName = value,
            () -> selectedProvider,
            value -> selectedProvider = value,
            () -> {
                selectedAccount = null;
                clearPreview();
            },
            this::rebuildProviderList,
            this::rebuildAccountList,
            this::openProviderProxyDialog,
            () -> providerSettingsDialogHandler.deleteCachedPanel());
        credentialDialogs = new AccountManagerCredentialDialogs(
            () -> selectedAccount,
            value -> selectedAccount = value,
            this::isCredentialManagementSupported,
            value -> textureUploadStatus = value,
            this::openCredentialDeleteDialog,
            this::clearPreview,
            this::rebuildAccountList,
            this::requestAccountListRebuild);
        removeAccountDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> buildRemoveAccountDialog(), true);
        providerSettingsDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> providerDialogs.buildProviderSettingsDialog(), true);
        providerProxyDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> providerDialogs.buildProviderProxyDialog(), true);
        credentialDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> credentialDialogs.buildCredentialDialog(), true);
        credentialDeleteDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> credentialDialogs.buildCredentialDeleteDialog(), true);
        texturePathDialogHandler = IPanelHandler.simple(mainPanel, (parent, player) -> buildTexturePathDialog(), true);
        textureResetDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> buildTextureResetDialog(), true);

        Column leftSidebar = new Column();
        leftSidebar.width(120)
            .heightRel(1.0f)
            .padding(4);

        providerListPanel.widget()
            .widthRel(1.0f)
            .heightRel(1.0f);
        accountListPanel.widget()
            .widthRel(1.0f)
            .heightRel(1.0f);

        Column providerListFrame = new Column();
        providerListFrame.widthRel(1.0f)
            .height(62)
            .margin(0, 2)
            .padding(1)
            .background(new Rectangle().color(LIST_OUTLINE_COLOR))
            .child(providerListPanel.widget());

        Column accountListFrame = new Column();
        accountListFrame.widthRel(1.0f)
            .expanded()
            .margin(0, 2)
            .padding(1)
            .background(new Rectangle().color(LIST_OUTLINE_COLOR))
            .child(accountListPanel.widget());

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
                        .child(new EntityDisplayWidget(() -> previewFrontEntity) {

                            @Override
                            public void draw(GuiContext context, int x, int y, int width, int height,
                                WidgetTheme widgetTheme) {
                                PreviewEntityRenderContext.isRenderingInGui = true;
                                try {
                                    super.draw(context, x, y, width, height, widgetTheme);
                                } finally {
                                    PreviewEntityRenderContext.isRenderingInGui = false;
                                }
                            }
                        }.doesLookAtMouse(true)
                            .preDraw(entity -> { prepareEntityPreview((PlayerPreviewEntity) entity, false); })
                            .postDraw(entity -> { postEntityPreview(); })
                            .asWidget()
                            .size(72, 76)
                            .invisible())
                        .child(new Widget<>().size(6, 76))
                        .child(new EntityDisplayWidget(() -> previewBackEntity) {

                            @Override
                            public void draw(GuiContext context, int x, int y, int width, int height,
                                WidgetTheme widgetTheme) {
                                PreviewEntityRenderContext.isRenderingInGui = true;
                                try {
                                    super.draw(context, x, y, width, height, widgetTheme);
                                } finally {
                                    PreviewEntityRenderContext.isRenderingInGui = false;
                                }
                            }
                        }.doesLookAtMouse(false)
                            .preDraw(entity -> { prepareEntityPreview((PlayerPreviewEntity) entity, true); })
                            .postDraw(entity -> { postEntityPreview(); })
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
                                .overlay(IKey.dynamic(() -> GuiText.tr(capePreviewMode.translationKey())))
                                .onMousePressed(mouseButton -> {
                                    capePreviewMode = normalizeCapePreviewMode(capePreviewMode)
                                        .next(EtFuturumCompat.isPreviewElytraAvailable());
                                    applyCapePreviewMode();
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
                        new ButtonWidget<>().size(64, 16)
                            .overlay(
                                IKey.dynamic(
                                    () -> GuiText.ellipsizeToPixelWidth(GuiText.tr(getTextureActionLabelKey()), 56)))
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
        focusedLocalPanel.populateSidebar(leftSidebar, accountListFrame, this::appendSharedAccountSection);
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
            focusedLocalPanel.ensureProvider(() -> {
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
            focusedLocalPanel.ensureProvider(() -> {
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

    private boolean hasFocusedLocalContext() {
        return focusedLocalPanel != null && focusedLocalPanel.hasContext();
    }

    private boolean hasFocusedLocalMetadata() {
        return focusedLocalPanel != null && focusedLocalPanel.hasMetadata();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (accountListPanel != null && accountListPanel.consumeRebuildRequest()) {
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
            applyCapePreviewMode();
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
        if (providerListPanel != null) {
            providerListPanel.rebuild();
        }
    }

    private void rebuildAccountList() {
        if (accountListPanel != null) {
            accountListPanel.rebuild();
        }
    }

    private void resetAccountListScroll() {
        if (accountListPanel != null) {
            accountListPanel.resetScroll();
        }
    }

    private void openProviderSettingsDialog(ClientProvider provider) {
        if (provider == null) return;
        this.pendingProviderSettingsName = provider.getName();
        this.providerSettingsDialogHandler.deleteCachedPanel();
        this.providerSettingsDialogHandler.openPanel();
    }

    private void openProviderProxyDialog(ClientProvider provider) {
        if (provider == null) return;
        this.pendingProviderProxyName = provider.getName();
        this.providerProxyDialogHandler.deleteCachedPanel();
        this.providerProxyDialogHandler.openPanel();
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
        credentialDialogs.clearPendingDeleteState();
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
        if (accountListPanel != null) {
            return accountListPanel.getLiveStatus(account);
        }
        return account != null ? account.getStatus() : null;
    }

    private void refreshVisibleStatuses() {
        if (accountListPanel != null) {
            accountListPanel.refreshVisibleStatuses();
        }
    }

    private void requestAccountListRebuild() {
        if (accountListPanel != null) {
            accountListPanel.requestRebuild();
        }
    }

    private void loadSkinForAccount(ClientAccount account) {
        if (account.getProfileUuid() == null || previewFrontEntity == null || previewBackEntity == null) return;

        long frontRequestId = previewFrontEntity.newRequestId();
        long backRequestId = previewBackEntity.newRequestId();

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        UUID uuid = account.getProfileUuid();
        String name = account.getProfileName() != null ? account.getProfileName() : "?";
        ClientProvider provider = resolveProvider(
            client.getProviderRegistry()
                .getProvider(account.getProviderName()));
        if (ProviderDisplayName.isOfflineProvider(account.getProviderName())) {
            loadOfflinePreviewModel(account);
            applyCapePreviewMode();
            return;
        }
        WawelAuth.debug("Preview fetch profile via fillProfileFromProvider: " + UuidUtil.toUnsigned(uuid));

        CompletableFuture.supplyAsync(() -> {
            try {
                GameProfile probe = new GameProfile(uuid, name);
                GameProfile filled = client.getSessionBridge()
                    .fillProfileFromProvider(provider, probe, true);
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
                        applyTexturesFromProfile(response, uuid);
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

    private void applyTexturesFromProfile(JsonObject profileResponse, UUID uuid) {
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
                    foundSkin = true;
                }
                applyCapePreviewMode();
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

    private void loadOfflinePreviewModel(ClientAccount account) {
        SkinModel model = account.getLocalSkinModel();
        previewFrontEntity.setForcedSkinModel(model);
        previewBackEntity.setForcedSkinModel(model);
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

        mc.renderViewEntity = entity;
        mc.theWorld = DummyWorldClient.INSTANCE;
        mc.thePlayer = DummyEntityClientPlayerMP.INSTANCE;
    }

    private void postEntityPreview() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.renderViewEntity = null;
        mc.theWorld = null;
        mc.thePlayer = null;
    }

    private PreviewBackMode normalizeCapePreviewMode(PreviewBackMode mode) {
        if (mode == PreviewBackMode.ELYTRA && !EtFuturumCompat.isPreviewElytraAvailable()) {
            return PreviewBackMode.CAPE;
        }
        return mode;
    }

    private void applyCapePreviewMode() {
        capePreviewMode = normalizeCapePreviewMode(capePreviewMode);
        applyCapePreviewMode(previewFrontEntity);
        applyCapePreviewMode(previewBackEntity);
    }

    private void applyCapePreviewMode(PlayerPreviewEntity entity) {
        if (entity == null) {
            return;
        }

        boolean useElytra = capePreviewMode == PreviewBackMode.ELYTRA;
        entity.setCapeVisible(capePreviewMode != PreviewBackMode.NONE);
        SkinLayersHelper.setSkinLayerHidden(entity, SkinLayersHelper.EnumPlayerModelParts.CAPE, useElytra);
        EtFuturumCompat.applyPreviewElytra(entity, useElytra);
    }

    private void openCredentialDeleteDialog() {
        if (!credentialDialogs.hasPendingDelete()) {
            return;
        }
        this.credentialDeleteDialogHandler.deleteCachedPanel();
        this.credentialDeleteDialogHandler.openPanel();
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

    private boolean isOfflineTextureAction() {
        if (selectedAccount != null && ProviderDisplayName.isOfflineProvider(selectedAccount.getProviderName())) {
            return true;
        }
        return selectedProvider != null && ProviderDisplayName.isOfflineProvider(selectedProvider.getName());
    }

    private String getTextureActionLabelKey() {
        return isOfflineTextureAction() ? "wawelauth.gui.account_manager.apply"
            : "wawelauth.gui.account_manager.upload";
    }

    private String getTextureActionInProgressKey() {
        return isOfflineTextureAction() ? "wawelauth.gui.account_manager.applying"
            : "wawelauth.gui.account_manager.uploading";
    }

    private void chooseTextureFile(boolean skin) {
        if (selectedAccount == null) {
            textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        String label = GuiText.tr(skin ? "wawelauth.gui.account_manager.skin" : "wawelauth.gui.account_manager.cape");
        FileUtil.FilePickerResult result = FileUtil.pickFile(
            GuiText.tr("wawelauth.gui.account_manager.select_texture_image", label),
            getTexturePickerInitialDirectory(skin));

        if (result.getStatus() == FileUtil.FilePickerResult.Status.SELECTED) {
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

        if (result.getStatus() == FileUtil.FilePickerResult.Status.CANCELLED) {
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

    private void handleTextureActionSuccess(WawelClient client, long accountId, String result) {
        textureUploadStatus = result != null ? result : GuiText.tr("wawelauth.gui.account_manager.upload_complete");
        ClientAccount refreshed = client.getAccountManager()
            .getAccount(accountId);
        if (selectedAccount != null && selectedAccount.getId() == accountId) {
            if (refreshed != null) {
                selectedAccount = refreshed;
            }
            if (selectedAccount.getProfileUuid() != null) {
                client.getTextureResolver()
                    .invalidate(selectedAccount.getProfileUuid());
                LocalTextureLoader.invalidateOfflineCape(selectedAccount.getProfileUuid());
            }
            loadSkinForAccount(selectedAccount);
        }
        if (selectedProvider != null) {
            rebuildAccountList();
            requestAccountListRebuild();
        }
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

        if (previewFrontEntity != null) {
            previewFrontEntity.prepareTextureUpload();
        }
        if (previewBackEntity != null) {
            previewBackEntity.prepareTextureUpload();
        }

        final long accountId = selectedAccount.getId();
        final File skin = selectedSkinFile;
        final File cape = selectedCapeFile;
        final boolean skinSlim = skinUploadSlim;
        textureUploadStatus = GuiText.tr(getTextureActionInProgressKey());

        if (ProviderDisplayName.isOfflineProvider(selectedAccount.getProviderName())) {
            try {
                String result = client.getAccountManager()
                    .applyOfflineTextures(accountId, skin, cape, skinSlim);
                handleTextureActionSuccess(client, accountId, result);
            } catch (Exception e) {
                textureUploadStatus = GuiText.tr("wawelauth.gui.common.failed_message", e.getMessage());
                WawelAuth.debug("Texture apply failed: " + e.getMessage());
            }
            return;
        }

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
                        handleTextureActionSuccess(client, accountId, result);
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
        TextFieldWidget pathField = new SafeTextFieldWidget()
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
                boolean opened = FileUtil.openFolder(folder);
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

        return FileUtil.getDefaultFileSelectionDirectory();
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
        return new File(FileUtil.getDefaultFileSelectionDirectory(), skin ? "skin.png" : "cape.png").getAbsolutePath();
    }

    private static String trimPath(String path, int maxLength) {
        if (path == null) return "";
        if (path.length() <= maxLength) return path;
        return "..." + path.substring(path.length() - maxLength + 3);
    }

    private boolean isSkinUploadDisabledForSelectedProvider() {
        if (selectedProvider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(selectedProvider.getName())) {
            return false;
        }
        return ClientConfig.isSkinUploadDisabled(selectedProvider.getName(), selectedProvider.getApiRoot());
    }

    private boolean isCapeUploadDisabledForSelectedProvider() {
        if (selectedProvider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(selectedProvider.getName())) {
            return false;
        }
        return ClientConfig.isCapeUploadDisabled(selectedProvider.getName(), selectedProvider.getApiRoot());
    }

    private boolean isAnyTextureUploadEnabled() {
        return !isSkinUploadDisabledForSelectedProvider() || !isCapeUploadDisabledForSelectedProvider();
    }

    private boolean isTextureResetDisabledForSelectedProvider() {
        if (selectedProvider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(selectedProvider.getName())) {
            return false;
        }
        return ClientConfig.isTextureResetDisabled(selectedProvider.getName(), selectedProvider.getApiRoot());
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

        if (ProviderDisplayName.isOfflineProvider(selectedAccount.getProviderName())) {
            try {
                String result = client.getAccountManager()
                    .resetOfflineTextures(accountId);
                handleTextureResetSuccess(client, accountId, result);
            } catch (Exception e) {
                textureUploadStatus = GuiText.tr("wawelauth.gui.common.failed_message", e.getMessage());
                WawelAuth.debug("Texture reset failed: " + e.getMessage());
                if (selectedAccount != null && selectedAccount.getId() == accountId) {
                    loadSkinForAccount(selectedAccount);
                }
            }
            return;
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
                        handleTextureResetSuccess(client, accountId, result);
                    });
            });
    }

    private void handleTextureResetSuccess(WawelClient client, long accountId, String result) {
        textureUploadStatus = result != null ? result : GuiText.tr("wawelauth.gui.account_manager.reset_complete");
        selectedSkinFile = null;
        selectedCapeFile = null;
        ClientAccount refreshed = client.getAccountManager()
            .getAccount(accountId);
        if (selectedAccount != null && selectedAccount.getId() == accountId) {
            if (refreshed != null) {
                selectedAccount = refreshed;
            }
            if (selectedAccount.getProfileUuid() != null) {
                client.getTextureResolver()
                    .invalidate(selectedAccount.getProfileUuid());
                LocalTextureLoader.invalidateOfflineCape(selectedAccount.getProfileUuid());
            }
            loadSkinForAccount(selectedAccount);
        }
        if (selectedProvider != null) {
            rebuildAccountList();
            requestAccountListRebuild();
        }
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

}
