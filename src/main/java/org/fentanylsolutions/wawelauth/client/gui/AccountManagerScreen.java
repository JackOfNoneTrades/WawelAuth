package org.fentanylsolutions.wawelauth.client.gui;

import java.io.File;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;

import org.fentanylsolutions.fentlib.util.FileUtil;
import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.compat.EtFuturumCompat;
import org.fentanylsolutions.wawelauth.client.fakeworld.DummyEntityClientPlayerMP;
import org.fentanylsolutions.wawelauth.client.fakeworld.DummyWorldClient;
import org.fentanylsolutions.wawelauth.client.fakeworld.PreviewEntityRenderContext;
import org.fentanylsolutions.wawelauth.client.render.LocalTextureLoader;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.LocalAuthProviderResolver;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ColorType;
import com.cleanroommc.modularui.drawable.UITexture;
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
    private static final int DETAIL_PRIMARY_TEXT_COLOR = WawelAuthStyle.TEXT_PRIMARY;
    private static final int DETAIL_SECONDARY_TEXT_COLOR = WawelAuthStyle.TEXT_SECONDARY;
    private static final int PREVIEW_PANEL_BACKGROUND_COLOR = WawelAuthStyle.PANEL_INSET;
    private static final int PANORAMA_DIM_COLOR = 0x55000000;
    private static final int PREVIEW_PANEL_HEIGHT = 101;
    private static final int ACCOUNT_ACTION_BUTTON_SIZE = 16;
    private static final int ACCOUNT_ACTION_ICON_SIZE = 12;
    private static final int ACCOUNT_ACTION_ROW_LEADING_SPACE = 2;
    private static final int ACCOUNT_ACTION_BUTTON_GAP = 4;
    private static final int ACCOUNT_ACTION_ICON_SOURCE_SIZE = 12;
    private static final int ACCOUNT_ACTION_ICON_SHEET_WIDTH = ACCOUNT_ACTION_ICON_SOURCE_SIZE * 5;
    private static final int ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND = WawelAuthStyle.BUTTON_IDLE;
    private static final int ACCOUNT_ACTION_ICON_IDLE_COLOR = 0xFFE0E0E0;
    private static final int ACCOUNT_ACTION_ICON_HOVER_COLOR = 0xFFFFFFFF;
    private static final int PREVIEW_MODE_BUTTON_SIZE = 22;
    private static final int PREVIEW_MODE_ICON_SIZE = 18;
    private static final int PREVIEW_MODE_BUTTON_EDGE_MARGIN = 3;
    private static final int PREVIEW_ENTITY_ROW_HEIGHT = PREVIEW_PANEL_HEIGHT - PREVIEW_MODE_BUTTON_SIZE
        - PREVIEW_MODE_BUTTON_EDGE_MARGIN;
    private static final int PREVIEW_ENTITY_VERTICAL_OFFSET = 13;
    private static final ColorType ACCOUNT_ACTION_ICON_COLOR_TYPE = new ColorType(
        "wawelauth:account_action_icon",
        theme -> ACCOUNT_ACTION_ICON_IDLE_COLOR);
    private static final ColorType ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE = new ColorType(
        "wawelauth:account_action_icon_hover",
        theme -> ACCOUNT_ACTION_ICON_HOVER_COLOR);
    private static final ColorType ACCOUNT_ACTION_DANGER_ICON_COLOR_TYPE = new ColorType(
        "wawelauth:account_action_danger_icon",
        theme -> WawelAuthStyle.TEXT_DANGER);
    private static final ColorType ACCOUNT_ACTION_DANGER_ICON_HOVER_COLOR_TYPE = new ColorType(
        "wawelauth:account_action_danger_icon_hover",
        theme -> WawelAuthStyle.TEXT_DANGER_HOVER);
    private static final IDrawable ACCOUNT_ACTION_LOGIN_ICON = centeredIcon(
        actionIcon(0, "login", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_LOGIN_ICON_HOVER = centeredIcon(
        actionIcon(0, "login_hover", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_REGISTER_ICON = centeredIcon(
        actionIcon(1, "register", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_REGISTER_ICON_HOVER = centeredIcon(
        actionIcon(1, "register_hover", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_REAUTH_ICON = centeredIcon(
        actionIcon(2, "reauth", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_REAUTH_ICON_HOVER = centeredIcon(
        actionIcon(2, "reauth_hover", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_CREDENTIALS_ICON = centeredIcon(
        actionIcon(3, "credentials", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_CREDENTIALS_ICON_HOVER = centeredIcon(
        actionIcon(3, "credentials_hover", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_REMOVE_ICON = centeredIcon(
        actionIcon(4, "remove", ACCOUNT_ACTION_DANGER_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_REMOVE_ICON_HOVER = centeredIcon(
        actionIcon(4, "remove_hover", ACCOUNT_ACTION_DANGER_ICON_HOVER_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable PREVIEW_MODE_NONE_ICON = centeredIcon(
        previewModeTexture("none", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        PREVIEW_MODE_ICON_SIZE);
    private static final IDrawable PREVIEW_MODE_NONE_ICON_HOVER = centeredIcon(
        previewModeTexture("none", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        PREVIEW_MODE_ICON_SIZE);
    private static final IDrawable PREVIEW_MODE_CAPE_ICON = centeredIcon(
        previewModeTexture("cape", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        PREVIEW_MODE_ICON_SIZE);
    private static final IDrawable PREVIEW_MODE_CAPE_ICON_HOVER = centeredIcon(
        previewModeTexture("cape", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        PREVIEW_MODE_ICON_SIZE);
    private static final IDrawable PREVIEW_MODE_ELYTRA_ICON = centeredIcon(
        previewModeTexture("elytra", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        PREVIEW_MODE_ICON_SIZE);
    private static final IDrawable PREVIEW_MODE_ELYTRA_ICON_HOVER = centeredIcon(
        previewModeTexture("elytra", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        PREVIEW_MODE_ICON_SIZE);

    private static ServerData pendingFocusedServerData;
    private static ServerCapabilities pendingFocusedCapabilities;
    private static String pendingSelectedProviderName;
    private static long pendingSelectedAccountId = -1L;

    private AccountManagerScreenState state;

    private PlayerPreviewEntity previewFrontEntity;
    private PlayerPreviewEntity previewBackEntity;
    private WorldClient savedWorld;
    private EntityClientPlayerMP savedPlayer;
    private EntityLivingBase savedRenderViewEntity;
    private AddProviderDialog addProviderDialog;
    private LoginDialog loginDialog;
    private RegisterDialog registerDialog;
    private AccountManagerProviderDialogs providerDialogs;
    private AccountManagerCredentialDialogs credentialDialogs;
    private IPanelHandler removeAccountDialogHandler;
    private IPanelHandler providerSettingsDialogHandler;
    private IPanelHandler providerProxyDialogHandler;
    private IPanelHandler providerDeleteDialogHandler;
    private IPanelHandler credentialDialogHandler;
    private IPanelHandler credentialDeleteDialogHandler;
    private IPanelHandler texturePathDialogHandler;
    private IPanelHandler textureResetDialogHandler;
    private PreviewBackMode capePreviewMode = PreviewBackMode.CAPE;

    private ModularPanel mainPanel;
    private FocusedLocalAuthPanel focusedLocalPanel;
    private AccountManagerProviderListPanel providerListPanel;
    private AccountManagerAccountListPanel accountListPanel;
    private final VanillaPanoramaBackdrop panoramaBackdrop = new VanillaPanoramaBackdrop();

    public AccountManagerScreen() {
        super("wawelauth");
        openParentOnClose(true);
    }

    @Override
    protected boolean drawCustomBackdrop() {
        boolean drewPanorama = panoramaBackdrop.draw(getContext().getPartialTicks());
        if (drewPanorama) {
            drawPanoramaDim();
        }
        return drewPanorama;
    }

    private AccountManagerScreenState state() {
        if (state == null) {
            state = new AccountManagerScreenState();
        }
        return state;
    }

    /**
     * Whether the texture preview is active (an account with a profile is selected).
     * Used by the drop handler to decide if file drops should show the texture zone overlay.
     */
    public boolean isTexturePreviewActive() {
        return previewFrontEntity != null && state().selectedAccount != null;
    }

    /**
     * Accept a file dropped from outside the GUI as a skin or cape selection.
     */
    public void acceptDroppedTextureFile(File file, boolean isSkin) {
        AccountManagerScreenState state = state();
        if (state.selectedAccount == null) return;
        String lowerName = file.getName()
            .toLowerCase();
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".gif")) {
            state.textureSelectionStatus = GuiText.tr("wawelauth.gui.account_manager.file_types_supported");
            return;
        }
        if (!file.isFile() || !file.canRead()) {
            state.textureSelectionStatus = GuiText.tr("wawelauth.gui.account_manager.file_not_readable");
            return;
        }
        if (isSkin) {
            state.selectedSkinFile = file;
            state.textureSelectionStatus = GuiText
                .tr("wawelauth.gui.account_manager.skin_selected", trimPath(file.getAbsolutePath(), 68));
        } else {
            state.selectedCapeFile = file;
            state.textureSelectionStatus = GuiText
                .tr("wawelauth.gui.account_manager.cape_selected", trimPath(file.getAbsolutePath(), 68));
        }
        state.textureUploadStatus = "";
    }

    public static void openForProvider(String providerName) {
        openForProvider(providerName, -1L);
    }

    public static void openForProvider(String providerName, long accountId) {
        pendingSelectedProviderName = providerName;
        pendingSelectedAccountId = accountId;
        ClientGUI.open(new AccountManagerScreen());
    }

    public static void openForLocalAuth(ServerData serverData) {
        pendingFocusedServerData = serverData;
        pendingFocusedCapabilities = ServerBindingPersistence.getEffectiveLocalAuthCapabilities(serverData);
        ClientGUI.open(new AccountManagerScreen());
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        AccountManagerScreenState state = state();
        mainPanel = ModularPanel.defaultPanel("wawelauth_account_manager", 360, 240);
        mainPanel.background(WawelAuthStyle.panelBackground())
            .disableHoverThemeBackground(true);

        ServerData focusedLocalServerData = null;
        ServerCapabilities focusedLocalCapabilities = null;
        if (pendingFocusedServerData != null || pendingFocusedCapabilities != null) {
            focusedLocalServerData = pendingFocusedServerData;
            focusedLocalCapabilities = pendingFocusedCapabilities;
            pendingFocusedServerData = null;
            pendingFocusedCapabilities = null;
        }
        state.resetForBuild(focusedLocalServerData, focusedLocalCapabilities);

        if (focusedLocalServerData == null) {
            state.connectedServerCapabilities = detectConnectedServerLocalAuth();
        }

        focusedLocalPanel = new FocusedLocalAuthPanel(
            state,
            this::resolveProvider,
            this::ensureRegisterCapabilityProbe,
            this::rebuildProviderList,
            this::rebuildAccountList,
            this::requestAccountListRebuild,
            this::clearPreview,
            this::openProviderProxyDialog);
        focusedLocalPanel.initializeSelectedProvider();

        providerListPanel = new AccountManagerProviderListPanel(
            state,
            this::hasFocusedLocalContext,
            () -> focusedLocalPanel.refreshProviderListState(),
            this::selectProvider,
            this::clearPreview,
            this::openProviderSettingsDialog);
        accountListPanel = new AccountManagerAccountListPanel(
            state,
            this::resolveProvider,
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
            if (Boolean.TRUE.equals(success) && state.selectedProvider != null) {
                loginDialog.openAfterRegister(state.selectedProvider.getName());
            }
        });
        providerDialogs = new AccountManagerProviderDialogs(state, () -> {
            state.selectedAccount = null;
            clearPreview();
        },
            this::rebuildProviderList,
            this::rebuildAccountList,
            this::openProviderProxyDialog,
            this::openProviderDeleteDialog,
            () -> providerSettingsDialogHandler.deleteCachedPanel(),
            () -> providerSettingsDialogHandler.closePanel());
        credentialDialogs = new AccountManagerCredentialDialogs(
            state,
            this::isCredentialManagementSupported,
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
        providerDeleteDialogHandler = IPanelHandler
            .simple(mainPanel, (parent, player) -> providerDialogs.buildProviderDeleteDialog(), true);
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
            .background(WawelAuthStyle.listBackground())
            .disableHoverThemeBackground(true)
            .child(providerListPanel.widget());

        Column accountListFrame = new Column();
        accountListFrame.widthRel(1.0f)
            .expanded()
            .margin(0, 2)
            .background(WawelAuthStyle.listBackground())
            .disableHoverThemeBackground(true)
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
                .height(PREVIEW_PANEL_HEIGHT)
                .margin(0, 2)
                .background(WawelAuthStyle.rect(PREVIEW_PANEL_BACKGROUND_COLOR))
                .disableHoverThemeBackground(true)
                .child(
                    new Row().widthRel(1.0f)
                        .height(PREVIEW_ENTITY_ROW_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(new EntityDisplayWidget(() -> previewFrontEntity) {

                            @Override
                            public void draw(GuiContext context, int x, int y, int width, int height,
                                WidgetTheme widgetTheme) {
                                PreviewEntityRenderContext.isRenderingInGui = true;
                                try {
                                    super.draw(
                                        context,
                                        x,
                                        y + PREVIEW_ENTITY_VERTICAL_OFFSET,
                                        width,
                                        height,
                                        widgetTheme);
                                } finally {
                                    PreviewEntityRenderContext.isRenderingInGui = false;
                                }
                            }
                        }.doesLookAtMouse(true)
                            .preDraw(entity -> { prepareEntityPreview((PlayerPreviewEntity) entity, false); })
                            .postDraw(entity -> { postEntityPreview(); })
                            .asWidget()
                            .size(72, 66)
                            .invisible())
                        .child(new Widget<>().size(6, 66))
                        .child(new EntityDisplayWidget(() -> previewBackEntity) {

                            @Override
                            public void draw(GuiContext context, int x, int y, int width, int height,
                                WidgetTheme widgetTheme) {
                                PreviewEntityRenderContext.isRenderingInGui = true;
                                try {
                                    super.draw(
                                        context,
                                        x,
                                        y + PREVIEW_ENTITY_VERTICAL_OFFSET,
                                        width,
                                        height,
                                        widgetTheme);
                                } finally {
                                    PreviewEntityRenderContext.isRenderingInGui = false;
                                }
                            }
                        }.doesLookAtMouse(false)
                            .preDraw(entity -> { prepareEntityPreview((PlayerPreviewEntity) entity, true); })
                            .postDraw(entity -> { postEntityPreview(); })
                            .asWidget()
                            .size(72, 66)
                            .invisible())
                        .child(new Widget<>().size(40, 66)))
                .child(
                    new Row().widthRel(1.0f)
                        .height(PREVIEW_MODE_BUTTON_SIZE)
                        .mainAxisAlignment(Alignment.MainAxis.END)
                        .child(previewModeButton()))
                .child(new Widget<>().size(1, PREVIEW_MODE_BUTTON_EDGE_MARGIN)))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (state.selectedAccount == null) return GuiText.tr("wawelauth.gui.common.no_account_selected");
                String name = state.selectedAccount.getProfileName();
                return name != null ? name : "?";
            })).color(DETAIL_PRIMARY_TEXT_COLOR)
                .widthRel(1.0f)
                .height(12)
                .margin(0, 1))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (state.selectedAccount == null) return "";
                UUID uuid = state.selectedAccount.getProfileUuid();
                return uuid != null ? uuid.toString() : GuiText.tr("wawelauth.gui.account_manager.no_profile_bound");
            })).color(DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (state.selectedAccount == null) return "";
                return GuiText.tr(
                    "wawelauth.gui.account_manager.status_line",
                    StatusColors.getLabel(getLiveStatus(state.selectedAccount)));
            })).color(
                () -> state.selectedAccount != null ? StatusColors.getColor(getLiveStatus(state.selectedAccount))
                    : DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10))
            .child(new Widget<>().size(1, 3))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText.ellipsizeToPixelWidth(state.textureSelectionStatus, TEXTURE_STATUS_MAX_WIDTH_PX)))
                            .tooltipDynamic(tooltip -> {
                                if (shouldShowTextureSelectionTooltip()) {
                                    tooltip.addLine(IKey.str(state.textureSelectionStatus));
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
                        WawelAuthStyle.button(new ButtonWidget<>(), () -> state.skinUploadSlim)
                            .size(64, 12)
                            .overlay(
                                IKey.dynamic(
                                    () -> GuiText.tr(
                                        state.skinUploadSlim ? "wawelauth.gui.account_manager.skin_model.slim"
                                            : "wawelauth.gui.account_manager.skin_model.classic")))
                            .onMousePressed(mouseButton -> {
                                state.skinUploadSlim = !state.skinUploadSlim;
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
                            WawelAuthStyle.button(new ButtonWidget<>())
                                .size(56, 16)
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
                            WawelAuthStyle.button(new ButtonWidget<>())
                                .size(56, 16)
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
                        WawelAuthStyle.button(new ButtonWidget<>())
                            .size(64, 16)
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
                        WawelAuthStyle.button(new ButtonWidget<>())
                            .size(16, 16)
                            .overlay(
                                IKey.str("X")
                                    .color(WawelAuthStyle.DANGER))
                            .tooltip(
                                tooltip -> tooltip.addLine(GuiText.key("wawelauth.gui.account_manager.reset_textures")))
                            .setEnabledIf(widget -> isTextureResetEnabledForSelectedProvider())
                            .onMousePressed(mouseButton -> {
                                attemptTextureReset();
                                return true;
                            })))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> GuiText.ellipsizeToPixelWidth(state.textureUploadStatus, TEXTURE_STATUS_MAX_WIDTH_PX)))
                            .tooltipDynamic(tooltip -> {
                                if (shouldShowTextureUploadTooltip()) {
                                    tooltip.addLine(IKey.str(state.textureUploadStatus));
                                }
                            })
                            .tooltipAutoUpdate(true)
                            .color(WawelAuthStyle.WARNING)
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
                    .height(17)
                    .mainAxisAlignment(Alignment.MainAxis.END)
                    .child(
                        textButton(new ButtonWidget<>().size(52, ACCOUNT_ACTION_BUTTON_SIZE), 44, "gui.done")
                            .onMousePressed(mouseButton -> {
                                mainPanel.closeIfOpen();
                                return true;
                            })));

        mainPanel.child(
            new Row().widthRel(1.0f)
                .heightRel(1.0f)
                .child(leftSidebar)
                .child(rightPanel));

        clearTextureSelection();
        if (pendingSelectedProviderName != null) {
            String targetName = pendingSelectedProviderName;
            long targetAccountId = pendingSelectedAccountId;
            pendingSelectedProviderName = null;
            pendingSelectedAccountId = -1L;
            WawelClient targetClient = WawelClient.instance();
            if (targetClient != null) {
                ClientProvider target = targetClient.getProviderRegistry()
                    .getProvider(targetName);
                if (target != null) {
                    selectProvider(target);
                    if (target.getType() == ProviderType.CUSTOM && !target.isManualEntry()) {
                        providerListPanel.expandLocal();
                    }
                    providerListPanel.scrollToSelected();
                    if (targetAccountId >= 0) {
                        ClientAccount targetAccount = targetClient.getAccountManager()
                            .getAccount(targetAccountId);
                        if (targetAccount != null) {
                            selectAccount(targetAccount);
                        }
                    }
                }
            }
        }
        rebuildProviderList();
        if (hasFocusedLocalContext()) {
            rebuildAccountList();
        }

        return mainPanel;
    }

    private void populateGeneralSidebar(Column leftSidebar, Column providerListFrame, Column accountListFrame) {
        leftSidebar.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.providers")).widthRel(1.0f)
                .height(12)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(providerListFrame)
            .child(
                textButton(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(16),
                    104,
                    "wawelauth.gui.add_provider.title").onMousePressed(mouseButton -> {
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
                .margin(0, 4)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(accountListFrame)
            .child(
                new Row().widthRel(1.0f)
                    .height(17)
                    .mainAxisAlignment(Alignment.MainAxis.START)
                    .collapseDisabledChild()
                    .child(new Widget<>().size(ACCOUNT_ACTION_ROW_LEADING_SPACE, ACCOUNT_ACTION_BUTTON_SIZE))
                    .child(
                        accountActionButton(
                            ACCOUNT_ACTION_LOGIN_ICON,
                            ACCOUNT_ACTION_LOGIN_ICON_HOVER,
                            "wawelauth.gui.common.login").onMousePressed(mouseButton -> {
                                handlePrimaryLoginAction();
                                return true;
                            }))
                    .child(actionGap())
                    .child(
                        accountActionButton(
                            ACCOUNT_ACTION_REGISTER_ICON,
                            ACCOUNT_ACTION_REGISTER_ICON_HOVER,
                            "wawelauth.gui.common.register")
                                .setEnabledIf(widget -> isRegisterVisibleForSelectedProvider())
                                .onMousePressed(mouseButton -> {
                                    handlePrimaryRegisterAction();
                                    return true;
                                }))
                    .child(actionGap().setEnabledIf(widget -> isRegisterVisibleForSelectedProvider()))
                    .child(
                        accountActionButton(
                            ACCOUNT_ACTION_REAUTH_ICON,
                            ACCOUNT_ACTION_REAUTH_ICON_HOVER,
                            "wawelauth.gui.account_manager.reauth")
                                .setEnabledIf(widget -> isReauthVisibleForSelectedAccount())
                                .onMousePressed(mouseButton -> {
                                    if (state.selectedAccount == null) return true;
                                    openLoginDialog(
                                        state.selectedAccount.getProviderName(),
                                        state.selectedAccount.getProfileName());
                                    return true;
                                }))
                    .child(actionGap().setEnabledIf(widget -> isReauthVisibleForSelectedAccount()))
                    .child(
                        accountActionButton(
                            ACCOUNT_ACTION_CREDENTIALS_ICON,
                            ACCOUNT_ACTION_CREDENTIALS_ICON_HOVER,
                            "wawelauth.gui.account_manager.credentials")
                                .setEnabledIf(widget -> isCredentialManagementAvailableForSelectedAccount())
                                .onMousePressed(mouseButton -> {
                                    openCredentialDialog();
                                    return true;
                                }))
                    .child(actionGap().setEnabledIf(widget -> isCredentialManagementAvailableForSelectedAccount()))
                    .child(
                        accountActionButton(
                            ACCOUNT_ACTION_REMOVE_ICON,
                            ACCOUNT_ACTION_REMOVE_ICON_HOVER,
                            "wawelauth.gui.account_manager.remove_account").onMousePressed(mouseButton -> {
                                confirmAndRemoveSelectedAccount();
                                return true;
                            })));
    }

    private static ButtonWidget<?> accountActionButton(IDrawable icon, IDrawable hoverIcon, String tooltipKey) {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(ACCOUNT_ACTION_BUTTON_SIZE, ACCOUNT_ACTION_BUTTON_SIZE)
            .background(WawelAuthStyle.rect(ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND))
            .hoverBackground(WawelAuthStyle.rect(WawelAuthStyle.BUTTON_HOVER))
            .overlay(icon)
            .hoverOverlay(hoverIcon)
            .addTooltipLine(GuiText.tr(tooltipKey));
        return button;
    }

    private static ButtonWidget<?> textButton(ButtonWidget<?> button, int maxTextWidthPx, String translationKey) {
        WawelAuthStyle.button(button);
        button.overlay(
            IKey.dynamic(() -> GuiText.ellipsizeToPixelWidth(GuiText.tr(translationKey), maxTextWidthPx))
                .color(() -> button.isBelowMouse() ? ACCOUNT_ACTION_ICON_HOVER_COLOR : ACCOUNT_ACTION_ICON_IDLE_COLOR));
        return button;
    }

    private ButtonWidget<?> previewModeButton() {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(PREVIEW_MODE_BUTTON_SIZE, PREVIEW_MODE_BUTTON_SIZE)
            .margin(PREVIEW_MODE_BUTTON_EDGE_MARGIN, 0)
            .background(WawelAuthStyle.rect(ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND))
            .hoverBackground(WawelAuthStyle.rect(WawelAuthStyle.BUTTON_HOVER))
            .overlay(dynamicPreviewModeIcon(false))
            .hoverOverlay(dynamicPreviewModeIcon(true))
            .tooltip(
                tooltip -> tooltip.addLine(
                    IKey.dynamic(() -> GuiText.tr(normalizeCapePreviewMode(capePreviewMode).translationKey()))))
            .onMousePressed(mouseButton -> {
                capePreviewMode = normalizeCapePreviewMode(capePreviewMode)
                    .next(EtFuturumCompat.isPreviewElytraAvailable());
                applyCapePreviewMode();
                return true;
            });
        return button;
    }

    private IDrawable dynamicPreviewModeIcon(boolean hover) {
        return (context, x, y, width, height,
            widgetTheme) -> previewModeIcon(normalizeCapePreviewMode(capePreviewMode), hover)
                .draw(context, x, y, width, height, widgetTheme);
    }

    private static IDrawable previewModeIcon(PreviewBackMode mode, boolean hover) {
        switch (mode) {
            case CAPE:
                return hover ? PREVIEW_MODE_CAPE_ICON_HOVER : PREVIEW_MODE_CAPE_ICON;
            case ELYTRA:
                return hover ? PREVIEW_MODE_ELYTRA_ICON_HOVER : PREVIEW_MODE_ELYTRA_ICON;
            case NONE:
            default:
                return hover ? PREVIEW_MODE_NONE_ICON_HOVER : PREVIEW_MODE_NONE_ICON;
        }
    }

    private static Widget<?> actionGap() {
        return new Widget<>().size(ACCOUNT_ACTION_BUTTON_GAP, ACCOUNT_ACTION_BUTTON_SIZE);
    }

    private static IDrawable actionIcon(int index, String name, ColorType colorType) {
        return UITexture.builder()
            .location("wawelauth", "gui/account-action-icons")
            .imageSize(ACCOUNT_ACTION_ICON_SHEET_WIDTH, ACCOUNT_ACTION_ICON_SOURCE_SIZE)
            .colorType(colorType)
            .subAreaXYWH(
                index * ACCOUNT_ACTION_ICON_SOURCE_SIZE,
                0,
                ACCOUNT_ACTION_ICON_SOURCE_SIZE,
                ACCOUNT_ACTION_ICON_SOURCE_SIZE)
            .nonOpaque()
            .name("wawelauth:account_action_" + name)
            .build();
    }

    private static IDrawable previewModeTexture(String mode, ColorType colorType) {
        return UITexture.builder()
            .location("wawelauth", "icon_player_" + mode)
            .fullImage()
            .colorType(colorType)
            .nonOpaque()
            .name(
                "wawelauth:preview_mode_" + mode + (colorType == ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE ? "_hover" : ""))
            .build();
    }

    private static IDrawable centeredIcon(IDrawable icon, int drawSize) {
        return (context, x, y, width, height, widgetTheme) -> {
            int size = Math.min(drawSize, Math.min(width, height));
            int iconX = x + (width - size) / 2;
            int iconY = y + (height - size) / 2;
            icon.draw(context, iconX, iconY, size, size, widgetTheme);
        };
    }

    private void handlePrimaryLoginAction() {
        if (hasFocusedLocalContext()) {
            focusedLocalPanel.ensureProvider(() -> {
                if (state.selectedProvider != null) {
                    openLoginDialog(state.selectedProvider.getName());
                }
            });
            return;
        }

        if (state.selectedProvider != null) {
            openLoginDialog(state.selectedProvider.getName());
        }
    }

    private void handlePrimaryRegisterAction() {
        if (hasFocusedLocalContext()) {
            focusedLocalPanel.ensureProvider(() -> {
                if (state.selectedProvider != null) {
                    openRegisterDialog(state.selectedProvider.getName());
                }
            });
            return;
        }

        if (state.selectedProvider != null) {
            openRegisterDialog(state.selectedProvider.getName());
        }
    }

    private boolean hasFocusedLocalContext() {
        return focusedLocalPanel != null && focusedLocalPanel.hasContext();
    }

    private boolean hasFocusedLocalMetadata() {
        return focusedLocalPanel != null && focusedLocalPanel.hasMetadata();
    }

    private static ServerCapabilities detectConnectedServerLocalAuth() {
        ServerData serverData = Minecraft.getMinecraft()
            .func_147104_D();
        if (serverData instanceof IServerDataExt) {
            ServerCapabilities caps = ((IServerDataExt) serverData).getWawelCapabilities();
            if (caps != null && caps.isLocalAuthSupported()) {
                return caps;
            }
        }
        return null;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        panoramaBackdrop.update();

        if (providerListPanel != null) {
            providerListPanel.applyPendingScroll();
        }
        if (accountListPanel != null && accountListPanel.consumeRebuildRequest()) {
            rebuildAccountList();
        }

        long now = System.currentTimeMillis();
        if (now < state.nextStatusUiRefreshAtMs) {
            return;
        }
        state.nextStatusUiRefreshAtMs = now + STATUS_UI_REFRESH_INTERVAL_MS;
        refreshVisibleStatuses();
    }

    private void drawPanoramaDim() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        Gui.drawRect(0, 0, resolution.getScaledWidth(), resolution.getScaledHeight(), PANORAMA_DIM_COLOR);
    }

    private void selectProvider(ClientProvider provider) {
        state.selectedProvider = resolveProvider(provider);
        ensureRegisterCapabilityProbe(state.selectedProvider);
        state.selectedAccount = null;
        clearTextureSelection();
        clearPreview();
        resetAccountListScroll();
        rebuildAccountList();
        requestAccountListRebuild();
    }

    private void selectAccount(ClientAccount account) {
        state.selectedAccount = account;
        clearTextureSelection();
        state.textureUploadStatus = "";
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
        state.pendingProviderSettingsName = provider.getName();
        this.providerSettingsDialogHandler.deleteCachedPanel();
        this.providerSettingsDialogHandler.openPanel();
    }

    private void openProviderProxyDialog(ClientProvider provider) {
        if (provider == null) return;
        state.pendingProviderProxyName = provider.getName();
        this.providerProxyDialogHandler.deleteCachedPanel();
        this.providerProxyDialogHandler.openPanel();
    }

    private void openProviderDeleteDialog() {
        if (state.pendingProviderDeleteName == null) return;
        this.providerDeleteDialogHandler.deleteCachedPanel();
        this.providerDeleteDialogHandler.openPanel();
    }

    private void confirmAndRemoveSelectedAccount() {
        if (state.selectedAccount == null) return;

        state.pendingRemoveAccountName = state.selectedAccount.getProfileName() != null
            ? state.selectedAccount.getProfileName()
            : GuiText.tr("wawelauth.gui.account_manager.this_account");
        state.pendingRemoveAccountId = state.selectedAccount.getId();
        this.removeAccountDialogHandler.deleteCachedPanel();
        this.removeAccountDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildRemoveAccountDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_remove");
        dialog.setCloseOnOutOfBoundsClick(false);

        int dialogWidth = 260;
        int dialogHeight = 94;
        int rootPadding = 10;
        int buttonHeight = 18;
        int titleMaxWidthPx = dialogWidth - rootPadding * 2 - 4;
        String name = state.pendingRemoveAccountName != null ? state.pendingRemoveAccountName
            : GuiText.tr("wawelauth.gui.account_manager.this_account");
        long accountId = state.pendingRemoveAccountId;

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        cancelBtn.size(64, buttonHeight)
            .onMousePressed(btn -> {
                state.pendingRemoveAccountId = -1L;
                state.pendingRemoveAccountName = null;
                dialog.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(cancelBtn, 56, "wawelauth.gui.common.cancel");

        ButtonWidget<?> removeBtn = new ButtonWidget<>();
        removeBtn.size(64, buttonHeight)
            .onMousePressed(btn -> {
                state.pendingRemoveAccountId = -1L;
                state.pendingRemoveAccountName = null;
                dialog.closeIfOpen();
                doRemoveAccount(accountId);
                return true;
            });
        WawelAuthStyle.dangerTextButton(removeBtn, 56, "wawelauth.gui.common.remove");

        Column root = new Column();
        root.widthRel(1.0f)
            .heightRel(1.0f)
            .padding(rootPadding)
            .background(IDrawable.EMPTY)
            .disableHoverBackground();
        root.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> GuiText.ellipsizeToPixelWidth(
                        GuiText.tr("wawelauth.gui.account_manager.remove_title", name),
                        titleMaxWidthPx))).tooltipDynamic(tooltip -> {
                            String title = GuiText.tr("wawelauth.gui.account_manager.remove_title", name);
                            if (!GuiText.ellipsizeToPixelWidth(title, titleMaxWidthPx)
                                .equals(title)) {
                                tooltip.addLine(IKey.str(title));
                            }
                        })
                            .tooltipAutoUpdate(true)
                            .widthRel(1.0f)
                            .height(14)
                            .color(WawelAuthStyle.THEME_LIGHTER))
            .child(new Widget<>().size(1, 6))
            .child(
                new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.remove_warning"))
                    .color(WawelAuthStyle.TEXT_SECONDARY)
                    .scale(0.8f)
                    .widthRel(1.0f)
                    .height(10))
            .child(new Widget<>().size(1, 10))
            .child(
                new Row().widthRel(1.0f)
                    .height(buttonHeight)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .child(cancelBtn)
                    .child(new Widget<>().size(8, buttonHeight))
                    .child(removeBtn));

        WawelAuthStyle.dialog(dialog);
        dialog.size(dialogWidth, dialogHeight)
            .child(root);
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
        if (state.selectedAccount == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        if (!isCredentialManagementAvailableForSelectedAccount()) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.credentials_unavailable");
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
                        state.selectedAccount = null;
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

        WawelClient client = WawelClient.instance();
        if (client == null) return;

        if (ProviderDisplayName.isOfflineProvider(account.getProviderName())) {
            loadOfflinePreviewModel(account);
            applyCapePreviewMode();
            return;
        }

        ClientProvider provider = resolveProvider(
            client.getProviderRegistry()
                .getProvider(account.getProviderName()));
        if (provider != null) {
            // Warm the resolver cache; the render path reads texture and skin model from it.
            client.getTextureResolver()
                .getSkin(
                    account.getProfileUuid(),
                    account.getProfileName() != null ? account.getProfileName() : "?",
                    provider,
                    false);
        }
        applyCapePreviewMode();
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

        savedWorld = mc.theWorld;
        savedPlayer = mc.thePlayer;
        savedRenderViewEntity = mc.renderViewEntity;
        mc.renderViewEntity = entity;
        mc.theWorld = DummyWorldClient.INSTANCE;
        mc.thePlayer = DummyEntityClientPlayerMP.INSTANCE;
    }

    private void postEntityPreview() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.renderViewEntity = savedRenderViewEntity;
        mc.theWorld = savedWorld;
        mc.thePlayer = savedPlayer;
        savedRenderViewEntity = null;
        savedWorld = null;
        savedPlayer = null;
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
        state.selectedSkinFile = null;
        state.selectedCapeFile = null;
        state.textureSelectionStatus = GuiText.tr("wawelauth.gui.account_manager.no_texture_selected");
    }

    private boolean shouldShowTextureSelectionTooltip() {
        if (state.selectedSkinFile == null && state.selectedCapeFile == null) {
            return false;
        }
        return !isBlank(state.textureSelectionStatus);
    }

    private boolean shouldShowTextureUploadTooltip() {
        return !isBlank(state.textureUploadStatus);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }

    private boolean isOfflineTextureAction() {
        if (state.selectedAccount != null
            && ProviderDisplayName.isOfflineProvider(state.selectedAccount.getProviderName())) {
            return true;
        }
        return state.selectedProvider != null
            && ProviderDisplayName.isOfflineProvider(state.selectedProvider.getName());
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
        if (state.selectedAccount == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        String label = GuiText.tr(skin ? "wawelauth.gui.account_manager.skin" : "wawelauth.gui.account_manager.cape");
        FileUtil.FilePickerResult result = FileUtil.pickFile(
            GuiText.tr("wawelauth.gui.account_manager.select_texture_image", label),
            getTexturePickerInitialDirectory(skin));

        if (result.getStatus() == FileUtil.FilePickerResult.Status.SELECTED) {
            File picked = result.getFile();
            if (skin) {
                state.selectedSkinFile = picked;
                state.textureSelectionStatus = GuiText
                    .tr("wawelauth.gui.account_manager.skin_selected", trimPath(picked.getAbsolutePath(), 68));
            } else {
                state.selectedCapeFile = picked;
                state.textureSelectionStatus = GuiText
                    .tr("wawelauth.gui.account_manager.cape_selected", trimPath(picked.getAbsolutePath(), 68));
            }
            state.textureUploadStatus = "";
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
        state.textureUploadStatus = message;
        openTexturePathDialog(skin);
    }

    private void handleTextureActionSuccess(WawelClient client, long accountId, String result) {
        state.textureUploadStatus = result != null ? result
            : GuiText.tr("wawelauth.gui.account_manager.upload_complete");
        ClientAccount refreshed = client.getAccountManager()
            .getAccount(accountId);
        if (state.selectedAccount != null && state.selectedAccount.getId() == accountId) {
            if (refreshed != null) {
                state.selectedAccount = refreshed;
            }
            if (state.selectedAccount.getProfileUuid() != null) {
                client.getTextureResolver()
                    .invalidate(state.selectedAccount.getProfileUuid());
                LocalTextureLoader.invalidateOfflineCape(state.selectedAccount.getProfileUuid());
            }
            loadSkinForAccount(state.selectedAccount);
        }
        if (state.selectedProvider != null) {
            rebuildAccountList();
            requestAccountListRebuild();
        }
    }

    private void attemptTextureUpload() {
        if (state.selectedAccount == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        if (state.selectedSkinFile == null && state.selectedCapeFile == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.choose_texture_first");
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        if (previewFrontEntity != null) {
            previewFrontEntity.prepareTextureUpload();
        }
        if (previewBackEntity != null) {
            previewBackEntity.prepareTextureUpload();
        }

        final long accountId = state.selectedAccount.getId();
        final File skin = state.selectedSkinFile;
        final File cape = state.selectedCapeFile;
        final boolean skinSlim = state.skinUploadSlim;
        state.textureUploadStatus = GuiText.tr(getTextureActionInProgressKey());

        if (ProviderDisplayName.isOfflineProvider(state.selectedAccount.getProviderName())) {
            try {
                String result = client.getAccountManager()
                    .applyOfflineTextures(accountId, skin, cape, skinSlim);
                handleTextureActionSuccess(client, accountId, result);
            } catch (Exception e) {
                state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.failed_message", e.getMessage());
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
                            state.textureUploadStatus = GuiText
                                .tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            WawelAuth.debug("Texture upload failed: " + cause.getMessage());
                            return;
                        }
                        handleTextureActionSuccess(client, accountId, result);
                    });
            });
    }

    private void openTexturePathDialog(boolean skin) {
        state.texturePathDialogForSkin = skin;
        File current = skin ? state.selectedSkinFile : state.selectedCapeFile;
        state.texturePathDialogInitialPath = current != null ? current.getAbsolutePath() : defaultTexturePath(skin);
        this.texturePathDialogHandler.deleteCachedPanel();
        this.texturePathDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildTexturePathDialog() {
        final boolean skin = state.texturePathDialogForSkin;
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
        if (state.texturePathDialogInitialPath != null) {
            pathField.setText(state.texturePathDialogInitialPath);
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
                    state.selectedSkinFile = picked;
                    state.textureSelectionStatus = GuiText
                        .tr("wawelauth.gui.account_manager.skin_selected", trimPath(picked.getAbsolutePath(), 68));
                } else {
                    state.selectedCapeFile = picked;
                    state.textureSelectionStatus = GuiText
                        .tr("wawelauth.gui.account_manager.cape_selected", trimPath(picked.getAbsolutePath(), 68));
                }
                state.textureUploadStatus = "";
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
        File current = skin ? state.selectedSkinFile : state.selectedCapeFile;
        File other = skin ? state.selectedCapeFile : state.selectedSkinFile;

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
        if (state.selectedProvider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(state.selectedProvider.getName())) {
            return false;
        }
        return ClientConfig.isSkinUploadDisabled(state.selectedProvider.getName(), state.selectedProvider.getApiRoot());
    }

    private boolean isCapeUploadDisabledForSelectedProvider() {
        if (state.selectedProvider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(state.selectedProvider.getName())) {
            return false;
        }
        return ClientConfig.isCapeUploadDisabled(state.selectedProvider.getName(), state.selectedProvider.getApiRoot());
    }

    private boolean isAnyTextureUploadEnabled() {
        return !isSkinUploadDisabledForSelectedProvider() || !isCapeUploadDisabledForSelectedProvider();
    }

    private boolean isTextureResetDisabledForSelectedProvider() {
        if (state.selectedProvider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(state.selectedProvider.getName())) {
            return false;
        }
        return ClientConfig
            .isTextureResetDisabled(state.selectedProvider.getName(), state.selectedProvider.getApiRoot());
    }

    private boolean isTextureResetEnabledForSelectedProvider() {
        return !isTextureResetDisabledForSelectedProvider();
    }

    private void attemptTextureReset() {
        if (state.selectedAccount == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        this.textureResetDialogHandler.deleteCachedPanel();
        this.textureResetDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildTextureResetDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_texture_reset");
        dialog.setCloseOnOutOfBoundsClick(false);

        String name = state.selectedAccount != null && state.selectedAccount.getProfileName() != null
            ? state.selectedAccount.getProfileName()
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
        if (state.selectedAccount == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.select_account_first");
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        final long accountId = state.selectedAccount.getId();
        state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.resetting");

        // Immediately clear preview to show default skin while the server request is in flight
        if (previewFrontEntity != null) {
            previewFrontEntity.clearTextures();
        }
        if (previewBackEntity != null) {
            previewBackEntity.clearTextures();
        }

        if (ProviderDisplayName.isOfflineProvider(state.selectedAccount.getProviderName())) {
            try {
                String result = client.getAccountManager()
                    .resetOfflineTextures(accountId);
                handleTextureResetSuccess(client, accountId, result);
            } catch (Exception e) {
                state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.failed_message", e.getMessage());
                WawelAuth.debug("Texture reset failed: " + e.getMessage());
                if (state.selectedAccount != null && state.selectedAccount.getId() == accountId) {
                    loadSkinForAccount(state.selectedAccount);
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
                            state.textureUploadStatus = GuiText
                                .tr("wawelauth.gui.common.failed_message", cause.getMessage());
                            WawelAuth.debug("Texture reset failed: " + cause.getMessage());
                            // Reload the old skin since the reset failed
                            if (state.selectedAccount != null && state.selectedAccount.getId() == accountId) {
                                loadSkinForAccount(state.selectedAccount);
                            }
                            return;
                        }
                        handleTextureResetSuccess(client, accountId, result);
                    });
            });
    }

    private void handleTextureResetSuccess(WawelClient client, long accountId, String result) {
        state.textureUploadStatus = result != null ? result
            : GuiText.tr("wawelauth.gui.account_manager.reset_complete");
        state.selectedSkinFile = null;
        state.selectedCapeFile = null;
        ClientAccount refreshed = client.getAccountManager()
            .getAccount(accountId);
        if (state.selectedAccount != null && state.selectedAccount.getId() == accountId) {
            if (refreshed != null) {
                state.selectedAccount = refreshed;
            }
            if (state.selectedAccount.getProfileUuid() != null) {
                client.getTextureResolver()
                    .invalidate(state.selectedAccount.getProfileUuid());
                LocalTextureLoader.invalidateOfflineCape(state.selectedAccount.getProfileUuid());
            }
            loadSkinForAccount(state.selectedAccount);
        }
        if (state.selectedProvider != null) {
            rebuildAccountList();
            requestAccountListRebuild();
        }
    }

    private boolean isCredentialManagementAvailableForSelectedAccount() {
        if (state.selectedAccount == null) {
            return false;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return false;
        }

        ClientProvider provider = client.getProviderRegistry()
            .getProvider(state.selectedAccount.getProviderName());
        return isCredentialManagementSupported(provider);
    }

    private boolean isReauthVisibleForSelectedAccount() {
        return state.selectedAccount == null
            || !ProviderDisplayName.isOfflineProvider(state.selectedAccount.getProviderName());
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

        if (ClientConfig.isCredentialsDisabled(providerName, provider.getApiRoot())) {
            if (state.registerCapabilityByProvider != null) {
                state.registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            }
            return false;
        }

        if (LocalAuthProviderResolver.isLocalAuthProvider(provider)) {
            if (state.registerCapabilityByProvider != null) {
                state.registerCapabilityByProvider.put(providerName, Boolean.TRUE);
            }
            return true;
        }

        if (state.registerCapabilityByProvider != null) {
            Boolean supported = state.registerCapabilityByProvider.get(providerName);
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

        ClientProvider provider = resolveProvider(state.selectedProvider);
        if (provider == null) {
            return false;
        }

        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return false;
        }

        if (ProviderDisplayName.isMicrosoftProvider(providerName) || provider.getType() == ProviderType.BUILTIN) {
            state.registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return false;
        }

        if (ClientConfig.isCredentialsDisabled(providerName, provider.getApiRoot())) {
            state.registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return false;
        }

        if (LocalAuthProviderResolver.isLocalAuthProvider(provider)) {
            state.registerCapabilityByProvider.put(providerName, Boolean.TRUE);
            return true;
        }

        Boolean supported = state.registerCapabilityByProvider.get(providerName);
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

        if (state.registerCapabilityByProvider.containsKey(providerName)
            || state.registerCapabilityProbeInFlight.contains(providerName)) {
            return;
        }

        if (ProviderDisplayName.isMicrosoftProvider(providerName) || provider.getType() == ProviderType.BUILTIN) {
            state.registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return;
        }

        if (ClientConfig.isCredentialsDisabled(providerName, provider.getApiRoot())) {
            state.registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return;
        }

        if (LocalAuthProviderResolver.isLocalAuthProvider(provider)) {
            state.registerCapabilityByProvider.put(providerName, Boolean.TRUE);
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            state.registerCapabilityByProvider.put(providerName, Boolean.FALSE);
            return;
        }

        state.registerCapabilityProbeInFlight.add(providerName);
        client.getAccountManager()
            .probeSupportsWawelRegister(providerName)
            .whenComplete((supported, err) -> {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> {
                        state.registerCapabilityProbeInFlight.remove(providerName);
                        state.registerCapabilityByProvider
                            .put(providerName, err == null && Boolean.TRUE.equals(supported));
                    });
            });
    }

}
