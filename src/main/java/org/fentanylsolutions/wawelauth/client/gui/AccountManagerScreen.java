package org.fentanylsolutions.wawelauth.client.gui;

import java.awt.image.BufferedImage;
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
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.fentlib.util.FileUtil;
import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.api.SkinImageUtil;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.client.compat.EtFuturumCompat;
import org.fentanylsolutions.wawelauth.client.ClipboardHelper;
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
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.IMuiScreen;
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
    private static final int VISIBLE_PROVIDER_ROWS = 6;
    private static final int VISIBLE_ACCOUNT_ROWS = 7;
    private static final int ACCOUNT_SECTION_TOP_SPACE = 6;
    private static final int PROVIDER_LABEL_HEIGHT = 12;
    private static final int PROVIDER_LIST_FRAME_VERTICAL_MARGIN = 1;
    private static final int ADD_PROVIDER_BUTTON_HEIGHT = 16;
    private static final int PREVIEW_PANEL_TOP_MARGIN = 2;
    private static final int PREVIEW_PANEL_HEIGHT = PROVIDER_LABEL_HEIGHT
        + AccountManagerProviderListPanel.PROVIDER_ROW_HEIGHT * VISIBLE_PROVIDER_ROWS
        + PROVIDER_LIST_FRAME_VERTICAL_MARGIN * 2
        + ADD_PROVIDER_BUTTON_HEIGHT
        - PREVIEW_PANEL_TOP_MARGIN;
    private static final int ACCOUNT_ACTION_BUTTON_SIZE = 16;
    private static final int ACCOUNT_ACTION_ICON_SIZE = 12;
    private static final int ACCOUNT_ACTION_ROW_LEADING_SPACE = 0;
    private static final int ACCOUNT_ACTION_BUTTON_GAP = 4;
    private static final int ACCOUNT_ACTION_ROW_WIDTH = ACCOUNT_ACTION_ROW_LEADING_SPACE
        + ACCOUNT_ACTION_BUTTON_SIZE * 7
        + ACCOUNT_ACTION_BUTTON_GAP * 6;
    private static final int ACCOUNT_ACTION_ICON_SOURCE_SIZE = 12;
    private static final int ACCOUNT_ACTION_ICON_SHEET_WIDTH = ACCOUNT_ACTION_ICON_SOURCE_SIZE * 7;
    private static final int ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND = WawelAuthStyle.BUTTON_IDLE;
    private static final int ACCOUNT_ACTION_ICON_IDLE_COLOR = 0xFFE0E0E0;
    private static final int ACCOUNT_ACTION_ICON_HOVER_COLOR = 0xFFFFFFFF;
    private static final int PREVIEW_MODE_BUTTON_SIZE = 22;
    private static final int PREVIEW_MODE_ICON_SIZE = 18;
    private static final int PREVIEW_MODE_BUTTON_EDGE_MARGIN = 3;
    private static final int PREVIEW_ENTITY_ROW_HEIGHT = PREVIEW_PANEL_HEIGHT - PREVIEW_MODE_BUTTON_SIZE
        - PREVIEW_MODE_BUTTON_EDGE_MARGIN;
    private static final int PREVIEW_ENTITY_VERTICAL_OFFSET = 13;
    private static final int TEXTURE_ACTION_BUTTON_WIDTH = 24;
    private static final int TEXTURE_ACTION_BUTTON_HEIGHT = 24;
    private static final int TEXTURE_ACTION_ICON_WIDTH = 8;
    private static final int TEXTURE_ACTION_ICON_HEIGHT = 16;
    private static final int TEXTURE_MODEL_ICON_WIDTH = 12;
    private static final int TEXTURE_MODEL_ICON_HEIGHT = 24;
    private static final int TEXTURE_MODEL_ICON_OFFSET_X = 0;
    private static final int TEXTURE_MODEL_ICON_OFFSET_Y = -1;
    private static final int TEXTURE_ACTION_BUTTON_GAP = 5;
    private static final int ACCOUNT_DETAIL_TOP_OFFSET = 2;
    private static final int TEXTURE_DIALOG_PREVIEW_HEIGHT = 92;
    private static final int TEXTURE_DIALOG_ENTITY_ROW_HEIGHT = 66;
    private static final int TEXTURE_DIALOG_ENTITY_VERTICAL_OFFSET = 8;
    private static final int TEXTURE_DIALOG_ENTITY_WIDTH = 64;
    private static final int TEXTURE_DIALOG_ENTITY_HEIGHT = 62;
    private static final int TEXTURE_ICON_IDLE_COLOR = 0xFFB8B8B8;
    private static final int TEXTURE_ICON_HOVER_COLOR = 0xFFFFFFFF;
    private static final int TEXTURE_ICON_DIM_COLOR = 0xFF686868;
    private static final int TEXTURE_ICON_DANGER_COLOR = 0x99D84444;
    private static final int TEXTURE_ICON_DANGER_HOVER_COLOR = 0xAAFF5555;
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
    private static final ColorType TEXTURE_ACTION_ICON_COLOR_TYPE = new ColorType(
        "wawelauth:texture_action_icon",
        theme -> TEXTURE_ICON_IDLE_COLOR);
    private static final ColorType TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE = new ColorType(
        "wawelauth:texture_action_icon_hover",
        theme -> TEXTURE_ICON_HOVER_COLOR);
    private static final ColorType TEXTURE_ACTION_ICON_DIM_COLOR_TYPE = new ColorType(
        "wawelauth:texture_action_icon_dim",
        theme -> TEXTURE_ICON_DIM_COLOR);
    private static final ColorType TEXTURE_ACTION_ICON_DANGER_COLOR_TYPE = new ColorType(
        "wawelauth:texture_action_icon_danger",
        theme -> TEXTURE_ICON_DANGER_COLOR);
    private static final ColorType TEXTURE_ACTION_ICON_DANGER_HOVER_COLOR_TYPE = new ColorType(
        "wawelauth:texture_action_icon_danger_hover",
        theme -> TEXTURE_ICON_DANGER_HOVER_COLOR);
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
    private static final IDrawable ACCOUNT_ACTION_TRUST_ICON = centeredIcon(
        actionIcon(5, "trust", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_TRUST_ICON_HOVER = centeredIcon(
        actionIcon(5, "trust_hover", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_FINGERPRINT_ICON = centeredIcon(
        actionIcon(6, "fingerprint", ACCOUNT_ACTION_ICON_COLOR_TYPE),
        ACCOUNT_ACTION_ICON_SIZE);
    private static final IDrawable ACCOUNT_ACTION_FINGERPRINT_ICON_HOVER = centeredIcon(
        actionIcon(6, "fingerprint_hover", ACCOUNT_ACTION_ICON_HOVER_COLOR_TYPE),
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
    private static final IDrawable TEXTURE_SKIN_ICON = centeredIcon(
        textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_COLOR_TYPE),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT);
    private static final IDrawable TEXTURE_SKIN_ICON_HOVER = centeredIcon(
        textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT);
    private static final IDrawable TEXTURE_CAPE_ICON = centeredIcon(
        textureActionTexture("player_model_wide_cape", TEXTURE_ACTION_ICON_COLOR_TYPE),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT);
    private static final IDrawable TEXTURE_CAPE_ICON_HOVER = centeredIcon(
        textureActionTexture("player_model_wide_cape", TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT);
    private static final IDrawable TEXTURE_SKIN_RESET_ICON = centeredIcon(
        layeredTexture(
            textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_COLOR_TYPE),
            textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_DANGER_COLOR_TYPE)),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT,
        0,
        0);
    private static final IDrawable TEXTURE_SKIN_RESET_ICON_HOVER = centeredIcon(
        layeredTexture(
            textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE),
            textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_DANGER_HOVER_COLOR_TYPE)),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT,
        0,
        0);
    private static final IDrawable TEXTURE_CAPE_RESET_ICON = centeredIcon(
        layeredTexture(
            textureActionTexture("player_model_wide_cape_grey", TEXTURE_ACTION_ICON_COLOR_TYPE),
            textureActionTexture("player_model_wide_cape_grey", TEXTURE_ACTION_ICON_DANGER_COLOR_TYPE)),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT,
        0,
        0);
    private static final IDrawable TEXTURE_CAPE_RESET_ICON_HOVER = centeredIcon(
        layeredTexture(
            textureActionTexture("player_model_wide_cape_grey", TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE),
            textureActionTexture("player_model_wide_cape_grey", TEXTURE_ACTION_ICON_DANGER_HOVER_COLOR_TYPE)),
        TEXTURE_ACTION_ICON_WIDTH,
        TEXTURE_ACTION_ICON_HEIGHT,
        0,
        0);
    private static final IDrawable TEXTURE_MODEL_CLASSIC_ICON = centeredIcon(
        textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE),
        TEXTURE_MODEL_ICON_WIDTH,
        TEXTURE_MODEL_ICON_HEIGHT,
        TEXTURE_MODEL_ICON_OFFSET_X,
        TEXTURE_MODEL_ICON_OFFSET_Y);
    private static final IDrawable TEXTURE_MODEL_CLASSIC_DIM_ICON = centeredIcon(
        textureActionTexture("player_model_wide", TEXTURE_ACTION_ICON_DIM_COLOR_TYPE),
        TEXTURE_MODEL_ICON_WIDTH,
        TEXTURE_MODEL_ICON_HEIGHT,
        TEXTURE_MODEL_ICON_OFFSET_X,
        TEXTURE_MODEL_ICON_OFFSET_Y);
    private static final IDrawable TEXTURE_MODEL_SLIM_ICON = centeredIcon(
        textureActionTexture("player_model_slim", TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE),
        TEXTURE_MODEL_ICON_WIDTH,
        TEXTURE_MODEL_ICON_HEIGHT,
        TEXTURE_MODEL_ICON_OFFSET_X,
        TEXTURE_MODEL_ICON_OFFSET_Y);
    private static final IDrawable TEXTURE_MODEL_SLIM_DIM_ICON = centeredIcon(
        textureActionTexture("player_model_slim", TEXTURE_ACTION_ICON_DIM_COLOR_TYPE),
        TEXTURE_MODEL_ICON_WIDTH,
        TEXTURE_MODEL_ICON_HEIGHT,
        TEXTURE_MODEL_ICON_OFFSET_X,
        TEXTURE_MODEL_ICON_OFFSET_Y);

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
    private IPanelHandler textureUploadDialogHandler;
    private IPanelHandler textureResetDialogHandler;
    private PreviewBackMode capePreviewMode = PreviewBackMode.CAPE;

    private ModularPanel mainPanel;
    private AccountManagerProviderListPanel providerListPanel;
    private AccountManagerAccountListPanel accountListPanel;
    private final VanillaPanoramaBackdrop panoramaBackdrop = new VanillaPanoramaBackdrop();

    public AccountManagerScreen() {
        super("wawelauth");
        openParentOnClose(true);
    }

    @Override
    protected boolean drawCustomBackdrop() {
        if (Minecraft.getMinecraft().theWorld != null) {
            return false;
        }

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

    public boolean canAcceptTextureDrop() {
        if (!isTexturePreviewActive()) {
            return false;
        }
        if (Minecraft.getMinecraft().currentScreen instanceof IMuiScreen muiScreen) {
            return muiScreen.getScreen() == this
                && getPanelManager().getTopMostPanel() == getPanelManager().getMainPanel();
        }
        return false;
    }

    /**
     * Accept a file dropped from outside the GUI as a skin or cape selection.
     */
    public void acceptDroppedTextureFile(File file, boolean isSkin) {
        AccountManagerScreenState state = state();
        if (!hasSelectedTextureAccount()) {
            state.textureUploadStatus = selectedTextureAccountMissingMessage();
            return;
        }
        if (isSkin && !isSkinUploadEnabledForSelectedAccount()) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.skin_upload_disabled");
            return;
        }
        if (!isSkin && !isCapeUploadEnabledForSelectedAccount()) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.cape_upload_disabled");
            return;
        }
        String lowerName = file.getName()
            .toLowerCase();
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".gif")) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.file_types_supported");
            return;
        }
        if (!file.isFile() || !file.canRead()) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.file_not_readable");
            return;
        }
        if (isSkin) {
            state.selectedSkinFile = file;
        } else {
            state.selectedCapeFile = file;
        }
        state.textureUploadStatus = "";
        openTextureUploadDialog(isSkin, file);
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
        ServerCapabilities capabilities = ServerBindingPersistence.getEffectiveLocalAuthCapabilities(serverData);
        WawelClient client = WawelClient.instance();
        if (client != null && hasLocalAuthMetadata(capabilities)) {
            try {
                ClientProvider provider = client.getLocalAuthProviderResolver()
                    .resolveOrCreate(capabilities);
                if (provider != null && !isBlank(provider.getName())) {
                    openForProvider(provider.getName());
                    return;
                }
            } catch (Exception e) {
                WawelAuth.LOG.warn("Failed to resolve local auth provider: {}", e.getMessage());
            }
        }
        ClientGUI.open(new AccountManagerScreen());
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        AccountManagerScreenState state = state();
        mainPanel = ModularPanel.defaultPanel("wawelauth_account_manager", 360, 240);
        mainPanel.background(WawelAuthStyle.panelBackground())
            .disableHoverThemeBackground(true);

        state.resetForBuild();

        if (Minecraft.getMinecraft().theWorld != null) {
            state.connectedServerCapabilities = detectConnectedServerLocalAuth();
        }

        providerListPanel = new AccountManagerProviderListPanel(
            state,
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
                selectAccount(account);
                rebuildAccountList();
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
        textureUploadDialogHandler = IPanelHandler.simple(mainPanel, (parent, player) -> buildTextureUploadDialog(), true);
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
            .height(AccountManagerProviderListPanel.PROVIDER_ROW_HEIGHT * VISIBLE_PROVIDER_ROWS)
            .margin(0, PROVIDER_LIST_FRAME_VERTICAL_MARGIN)
            .background(WawelAuthStyle.listBackground())
            .disableHoverThemeBackground(true)
            .child(providerListPanel.widget());

        Column accountListFrame = new Column();
        accountListFrame.widthRel(1.0f)
            .height(AccountManagerAccountListPanel.ACCOUNT_ROW_HEIGHT * VISIBLE_ACCOUNT_ROWS)
            .margin(0, PROVIDER_LIST_FRAME_VERTICAL_MARGIN)
            .background(WawelAuthStyle.listBackground())
            .disableHoverThemeBackground(true)
            .child(accountListPanel.widget());

        populateGeneralSidebar(leftSidebar, providerListFrame, accountListFrame);

        Column rightPanel = new Column();
        rightPanel.expanded()
            .heightRel(1.0f)
            .padding(4)
            .collapseDisabledChild();

        rightPanel.child(
            new Column().widthRel(1.0f)
                .height(PREVIEW_PANEL_HEIGHT)
                .margin(0, PREVIEW_PANEL_TOP_MARGIN)
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
                            .invisible()))
                .child(
                    new Row().widthRel(1.0f)
                        .height(PREVIEW_MODE_BUTTON_SIZE)
                        .mainAxisAlignment(Alignment.MainAxis.END)
                        .child(previewModeButton()))
                .child(new Widget<>().size(1, PREVIEW_MODE_BUTTON_EDGE_MARGIN)))
            .child(new Widget<>().size(1, ACCOUNT_DETAIL_TOP_OFFSET))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (state.selectedAccount == null) return GuiText.tr("wawelauth.gui.common.no_account_selected");
                String name = state.selectedAccount.getProfileName();
                return name != null ? name : "?";
            })).color(DETAIL_PRIMARY_TEXT_COLOR)
                .widthRel(1.0f)
                .height(12)
                .margin(0, 1))
            .child(profileUuidTextWidget())
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (state.selectedAccount == null) return "";
                if (isSelectedAccountOffline()) return "";
                return GuiText.tr(
                    "wawelauth.gui.account_manager.status_line",
                    StatusColors.getLabel(getLiveStatus(state.selectedAccount)));
            })).color(
                () -> state.selectedAccount != null ? StatusColors.getColor(getLiveStatus(state.selectedAccount))
                    : DETAIL_SECONDARY_TEXT_COLOR)
                .scale(0.8f)
                .widthRel(1.0f)
                .height(10)
                .setEnabledIf(widget -> state.selectedAccount != null && !isSelectedAccountOffline()))
            .child(new Widget<>().size(1, 8))
            .child(
                new Row().widthRel(1.0f)
                    .height(TEXTURE_ACTION_BUTTON_HEIGHT)
                    .margin(0, 1)
                    .setEnabledIf(widget -> isAnyTextureManagementEnabledForSelectedAccount())
                    .collapseDisabledChild()
                    .child(
                        textureActionButton(
                            TEXTURE_SKIN_ICON,
                            TEXTURE_SKIN_ICON_HOVER,
                            "wawelauth.gui.account_manager.upload_skin_title")
                                .setEnabledIf(widget -> isSkinUploadEnabledForSelectedAccount())
                            .onMousePressed(mouseButton -> {
                                chooseTextureFile(true);
                                return true;
                            }))
                    .child(textureActionGap().setEnabledIf(widget -> isSkinUploadEnabledForSelectedAccount()
                        && (isCapeUploadEnabledForSelectedAccount()
                            || isSkinResetEnabledForSelectedAccount()
                            || isCapeResetEnabledForSelectedAccount())))
                    .child(
                        textureActionButton(
                            TEXTURE_CAPE_ICON,
                            TEXTURE_CAPE_ICON_HOVER,
                            "wawelauth.gui.account_manager.upload_cape_title")
                                .setEnabledIf(widget -> isCapeUploadEnabledForSelectedAccount())
                            .onMousePressed(mouseButton -> {
                                chooseTextureFile(false);
                                return true;
                            }))
                    .child(
                        textureActionGap()
                            .setEnabledIf(widget -> isCapeUploadEnabledForSelectedAccount()
                                && (isSkinResetEnabledForSelectedAccount()
                                    || isCapeResetEnabledForSelectedAccount())))
                    .child(
                        textureActionButton(
                            TEXTURE_SKIN_RESET_ICON,
                            TEXTURE_SKIN_RESET_ICON_HOVER,
                            "wawelauth.gui.account_manager.reset_skin")
                                .setEnabledIf(widget -> isSkinResetEnabledForSelectedAccount())
                            .onMousePressed(mouseButton -> {
                                attemptTextureReset(TextureType.SKIN);
                                return true;
                            }))
                    .child(
                        textureActionGap()
                            .setEnabledIf(widget -> isSkinResetEnabledForSelectedAccount()
                                && isCapeResetEnabledForSelectedAccount()))
                    .child(
                        textureActionButton(
                            TEXTURE_CAPE_RESET_ICON,
                            TEXTURE_CAPE_RESET_ICON_HOVER,
                            "wawelauth.gui.account_manager.remove_cape")
                                .setEnabledIf(widget -> isCapeResetEnabledForSelectedAccount())
                            .onMousePressed(mouseButton -> {
                                attemptTextureReset(TextureType.CAPE);
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
                                widget -> isAnyTextureManagementEnabledForSelectedAccount()))
            .child(
                new Widget<>().widthRel(1.0f)
                    .expanded())
            .child(
                new Row().widthRel(1.0f)
                    .height(17)
                    .collapseDisabledChild()
                    .child(accountActionsRow())
                    .child(new Widget<>().expanded())
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

        return mainPanel;
    }

    private void populateGeneralSidebar(Column leftSidebar, Column providerListFrame, Column accountListFrame) {
        leftSidebar.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.providers")).widthRel(1.0f)
                .height(PROVIDER_LABEL_HEIGHT)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(providerListFrame)
            .child(
                textButton(
                    new ButtonWidget<>().widthRel(1.0f)
                        .height(ADD_PROVIDER_BUTTON_HEIGHT),
                    104,
                    "wawelauth.gui.add_provider.title").onMousePressed(mouseButton -> {
                        addProviderDialog.open();
                        return true;
                    }));
        leftSidebar.child(new Widget<>().size(1, ACCOUNT_SECTION_TOP_SPACE));
        appendSharedAccountSection(leftSidebar, accountListFrame);
    }

    private void appendSharedAccountSection(Column leftSidebar, Column accountListFrame) {
        leftSidebar.child(
            new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.accounts")).widthRel(1.0f)
                .height(12)
                .color(WawelAuthStyle.THEME_LIGHTER))
            .child(accountListFrame);
    }

    private Row accountActionsRow() {
        Row row = new Row();
        row.width(ACCOUNT_ACTION_ROW_WIDTH)
            .height(17)
            .collapseDisabledChild()
            .mainAxisAlignment(Alignment.MainAxis.START)
            .child(new Widget<>().size(ACCOUNT_ACTION_ROW_LEADING_SPACE, ACCOUNT_ACTION_BUTTON_SIZE))
            .child(
                accountActionButton(
                    ACCOUNT_ACTION_TRUST_ICON,
                    ACCOUNT_ACTION_TRUST_ICON_HOVER,
                    "wawelauth.gui.local_auth.trust_refresh")
                        .setEnabledIf(widget -> selectedLocalAuthProvider() != null)
                        .onMousePressed(mouseButton -> {
                            refreshSelectedLocalAuthProvider();
                            return true;
                        }))
            .child(actionGap().setEnabledIf(widget -> selectedLocalAuthProvider() != null))
            .child(localFingerprintButton())
            .child(actionGap().setEnabledIf(widget -> selectedLocalAuthProvider() != null))
            .child(
                dynamicAccountActionButton(
                    ACCOUNT_ACTION_LOGIN_ICON,
                    ACCOUNT_ACTION_LOGIN_ICON_HOVER,
                    this::primaryLoginTooltipKey).onMousePressed(mouseButton -> {
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
                            openLoginDialog(state.selectedAccount.getProviderName(), state.selectedAccount.getProfileName());
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
                    }));
        return row;
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

    private static ButtonWidget<?> dynamicAccountActionButton(IDrawable icon, IDrawable hoverIcon,
        java.util.function.Supplier<String> tooltipKey) {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(ACCOUNT_ACTION_BUTTON_SIZE, ACCOUNT_ACTION_BUTTON_SIZE)
            .background(WawelAuthStyle.rect(ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND))
            .hoverBackground(WawelAuthStyle.rect(WawelAuthStyle.BUTTON_HOVER))
            .overlay(icon)
            .hoverOverlay(hoverIcon)
            .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(() -> GuiText.tr(tooltipKey.get()))))
            .tooltipAutoUpdate(true);
        return button;
    }

    private ButtonWidget<?> localFingerprintButton() {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(ACCOUNT_ACTION_BUTTON_SIZE, ACCOUNT_ACTION_BUTTON_SIZE)
            .background(WawelAuthStyle.rect(ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND))
            .hoverBackground(WawelAuthStyle.rect(WawelAuthStyle.BUTTON_HOVER))
            .overlay(ACCOUNT_ACTION_FINGERPRINT_ICON)
            .hoverOverlay(ACCOUNT_ACTION_FINGERPRINT_ICON_HOVER)
            .setEnabledIf(widget -> selectedLocalAuthProvider() != null)
            .tooltip(tooltip -> {
                tooltip.addLine(
                    IKey.dynamic(() -> EnumChatFormatting.WHITE + GuiText.tr("wawelauth.gui.common.fingerprint")));
                tooltip.addLine(IKey.dynamic(() -> EnumChatFormatting.AQUA + getSelectedLocalAuthFingerprint()));
                tooltip.addLine(
                    IKey.dynamic(
                        () -> isLocalAuthFingerprintCopied()
                            ? EnumChatFormatting.GREEN + GuiText.tr("wawelauth.gui.common.copied")
                            : EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.common.click_to_copy")));
            })
            .tooltipAutoUpdate(true)
            .onMousePressed(mouseButton -> {
                String fingerprint = getSelectedLocalAuthFingerprint();
                if (!isBlank(fingerprint)) {
                    ClipboardHelper.copyToClipboard(fingerprint, "");
                    state.localAuthFingerprintCopiedUntilMs = System.currentTimeMillis() + 2000L;
                }
                return true;
            });
        return button;
    }

    private ButtonWidget<?> profileUuidTextWidget() {
        ButtonWidget<?> widget = new ButtonWidget<>();
        WawelAuthStyle.iconButton(widget);
        widget.background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY)
            .overlay(
                IKey.dynamic(this::selectedProfileUuidText)
                    .alignment(Alignment.CenterLeft)
                    .scale(0.8f)
                    .color(
                        () -> isSelectedProfileUuidCopyable() && widget.isBelowMouse() ? WawelAuthStyle.TEXT_PRIMARY
                            : DETAIL_SECONDARY_TEXT_COLOR))
            .widthRel(1.0f)
            .height(10)
            .tooltipDynamic(tooltip -> {
                if (!isSelectedProfileUuidCopyable()) {
                    return;
                }
                tooltip.addLine(
                    IKey.dynamic(
                        () -> isProfileUuidCopied()
                            ? EnumChatFormatting.GREEN + GuiText.tr("wawelauth.gui.common.copied")
                            : EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.common.click_to_copy")));
            })
            .tooltipAutoUpdate(true)
            .onMousePressed(mouseButton -> {
                copySelectedProfileUuid();
                return isSelectedProfileUuidCopyable();
            });
        return widget;
    }

    private static ButtonWidget<?> textureActionButton(IDrawable icon, IDrawable hoverIcon, String tooltipKey) {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(TEXTURE_ACTION_BUTTON_WIDTH, TEXTURE_ACTION_BUTTON_HEIGHT)
            .background(WawelAuthStyle.rect(ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND))
            .hoverBackground(WawelAuthStyle.rect(WawelAuthStyle.BUTTON_HOVER))
            .overlay(icon)
            .hoverOverlay(hoverIcon)
            .addTooltipLine(GuiText.tr(tooltipKey));
        return button;
    }

    private static Widget<?> textureActionGap() {
        return new Widget<>().size(TEXTURE_ACTION_BUTTON_GAP, TEXTURE_ACTION_BUTTON_HEIGHT);
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

    private static IDrawable textureActionTexture(String name, ColorType colorType) {
        return UITexture.builder()
            .location("wawelauth", name)
            .fullImage()
            .colorType(colorType)
            .nonOpaque()
            .name("wawelauth:texture_action_" + name + (colorType == TEXTURE_ACTION_ICON_HOVER_COLOR_TYPE ? "_hover"
                : ""))
            .build();
    }

    private static IDrawable layeredTexture(IDrawable... layers) {
        return (context, x, y, width, height, widgetTheme) -> {
            for (IDrawable layer : layers) {
                layer.draw(context, x, y, width, height, widgetTheme);
            }
        };
    }

    private static IDrawable centeredIcon(IDrawable icon, int drawSize) {
        return (context, x, y, width, height, widgetTheme) -> {
            int size = Math.min(drawSize, Math.min(width, height));
            int iconX = x + (width - size) / 2;
            int iconY = y + (height - size) / 2;
            icon.draw(context, iconX, iconY, size, size, widgetTheme);
        };
    }

    private static IDrawable centeredIcon(IDrawable icon, int drawWidth, int drawHeight) {
        return centeredIcon(icon, drawWidth, drawHeight, 0, 0);
    }

    private static IDrawable centeredIcon(IDrawable icon, int drawWidth, int drawHeight, int offsetX, int offsetY) {
        return (context, x, y, width, height, widgetTheme) -> {
            int iconWidth = Math.min(drawWidth, width);
            int iconHeight = Math.min(drawHeight, height);
            if (iconWidth * drawHeight > iconHeight * drawWidth) {
                iconWidth = iconHeight * drawWidth / drawHeight;
            } else {
                iconHeight = iconWidth * drawHeight / drawWidth;
            }
            int iconX = x + (width - iconWidth) / 2 + offsetX;
            int iconY = y + (height - iconHeight) / 2 + offsetY;
            icon.draw(context, iconX, iconY, iconWidth, iconHeight, widgetTheme);
        };
    }

    private void handlePrimaryLoginAction() {
        if (state.selectedProvider != null) {
            openLoginDialog(state.selectedProvider.getName());
        }
    }

    private String primaryLoginTooltipKey() {
        return state.selectedProvider != null && ProviderDisplayName.isOfflineProvider(state.selectedProvider.getName())
            ? "wawelauth.gui.common.add_account"
            : "wawelauth.gui.common.login";
    }

    private void handlePrimaryRegisterAction() {
        if (state.selectedProvider != null) {
            openRegisterDialog(state.selectedProvider.getName());
        }
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

    private static boolean hasLocalAuthMetadata(ServerCapabilities capabilities) {
        return capabilities != null && capabilities.isLocalAuthSupported()
            && !isBlank(capabilities.getLocalAuthApiRoot())
            && !isBlank(capabilities.getLocalAuthPublicKeyFingerprint());
    }

    private ClientProvider selectedLocalAuthProvider() {
        ClientProvider provider = resolveProvider(state.selectedProvider);
        if (provider == null
            || provider.getType() != ProviderType.CUSTOM
            || provider.isManualEntry()
            || !LocalAuthProviderResolver.isLocalAuthProvider(provider)) {
            return null;
        }
        return provider;
    }

    private String getSelectedLocalAuthFingerprint() {
        ClientProvider provider = selectedLocalAuthProvider();
        return provider != null && provider.getPublicKeyFingerprint() != null ? provider.getPublicKeyFingerprint() : "";
    }

    private boolean isLocalAuthFingerprintCopied() {
        return System.currentTimeMillis() < state.localAuthFingerprintCopiedUntilMs;
    }

    private String selectedProfileUuidText() {
        if (state.selectedAccount == null) return "";
        UUID uuid = state.selectedAccount.getProfileUuid();
        return uuid != null ? uuid.toString() : GuiText.tr("wawelauth.gui.account_manager.no_profile_bound");
    }

    private boolean isSelectedProfileUuidCopyable() {
        return state.selectedAccount != null && state.selectedAccount.getProfileUuid() != null;
    }

    private boolean isProfileUuidCopied() {
        return System.currentTimeMillis() < state.profileUuidCopiedUntilMs;
    }

    private void copySelectedProfileUuid() {
        if (!isSelectedProfileUuidCopyable()) {
            return;
        }
        ClipboardHelper.copyToClipboard(state.selectedAccount.getProfileUuid()
            .toString(), "");
        state.profileUuidCopiedUntilMs = System.currentTimeMillis() + 2000L;
    }

    private void refreshSelectedLocalAuthProvider() {
        ClientProvider resolved = selectedLocalAuthProvider();
        if (resolved == null) {
            return;
        }
        state.selectedProvider = resolved;
        ensureRegisterCapabilityProbe(resolved);
        if (providerListPanel != null) {
            providerListPanel.expandLocal();
            providerListPanel.scrollToSelected();
        }
        rebuildProviderList();
        rebuildAccountList();
        requestAccountListRebuild();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (Minecraft.getMinecraft().theWorld == null) {
            panoramaBackdrop.update();
        }

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
        applyCapePreviewMode(entity, capePreviewMode);
    }

    private void applyCapePreviewMode(PlayerPreviewEntity entity, PreviewBackMode mode) {
        if (entity == null) {
            return;
        }

        PreviewBackMode normalizedMode = normalizeCapePreviewMode(mode);
        boolean useElytra = normalizedMode == PreviewBackMode.ELYTRA;
        entity.setCapeVisible(normalizedMode != PreviewBackMode.NONE);
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
        state.pendingTextureUploadFile = null;
        state.pendingTextureResetType = null;
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

    private boolean isSelectedAccountOffline() {
        return state.selectedAccount != null
            && ProviderDisplayName.isOfflineProvider(state.selectedAccount.getProviderName());
    }

    private String getTextureActionInProgressKey() {
        return isOfflineTextureAction() ? "wawelauth.gui.account_manager.applying"
            : "wawelauth.gui.account_manager.uploading";
    }

    private void chooseTextureFile(boolean skin) {
        if (!hasSelectedTextureAccount()) {
            state.textureUploadStatus = selectedTextureAccountMissingMessage();
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
            } else {
                state.selectedCapeFile = picked;
            }
            state.textureUploadStatus = "";
            openTextureUploadDialog(skin, picked);
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

    private void openTextureUploadDialog(boolean skin, File file) {
        if (!hasSelectedTextureAccount()) {
            state.textureUploadStatus = selectedTextureAccountMissingMessage();
            return;
        }
        if (skin && !isSkinUploadEnabledForSelectedAccount()) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.skin_upload_disabled");
            return;
        }
        if (!skin && !isCapeUploadEnabledForSelectedAccount()) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.cape_upload_disabled");
            return;
        }
        if (file == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.choose_texture_first");
            return;
        }
        state.pendingTextureUploadSkin = skin;
        state.pendingTextureUploadFile = file;
        state.pendingTextureUploadSlim = true;
        this.textureUploadDialogHandler.deleteCachedPanel();
        this.textureUploadDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildTextureUploadDialog() {
        final boolean skin = state.pendingTextureUploadSkin;
        final TextureType textureType = skin ? TextureType.SKIN : TextureType.CAPE;
        final File file = state.pendingTextureUploadFile;
        final String titleKey = skin ? "wawelauth.gui.account_manager.upload_skin_title"
            : "wawelauth.gui.account_manager.upload_cape_title";
        final String path = file != null ? file.getAbsolutePath() : "";
        final String[] statusText = { GuiText.tr("wawelauth.gui.account_manager.file_path", path) };
        final String[] previewWarning = { "" };
        final PreviewBackMode[] dialogPreviewMode = { normalizeCapePreviewMode(capePreviewMode) };

        Dialog<Boolean> dialog = new Dialog<>("wawelauth_texture_upload");
        dialog.setCloseOnOutOfBoundsClick(false);

        ResourceLocation previewTexture = null;
        if (file != null) {
            try {
                previewTexture = registerTexturePreview(file, textureType);
            } catch (Exception e) {
                previewWarning[0] = GuiText.tr("wawelauth.gui.account_manager.preview_unavailable", e.getMessage());
                WawelAuth.debug("Texture preview failed: " + e.getMessage());
            }
        }

        PlayerPreviewEntity frontEntity = createTextureUploadPreviewEntity(textureType, previewTexture);
        PlayerPreviewEntity backEntity = createTextureUploadPreviewEntity(textureType, previewTexture);
        applyTextureUploadPreviewModel(frontEntity, backEntity);
        applyCapePreviewMode(frontEntity, dialogPreviewMode[0]);
        applyCapePreviewMode(backEntity, dialogPreviewMode[0]);
        warmTexturePreviewTextures(state.selectedAccount);

        int dialogWidth = 260;
        int dialogHeight = skin ? 220 : 176;
        int rootPadding = 8;
        int maxTextWidthPx = dialogWidth - rootPadding * 2 - 4;

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        WawelAuthStyle.textButton(cancelBtn.size(64, 18), 56, "wawelauth.gui.common.cancel")
            .onMousePressed(btn -> {
                state.pendingTextureUploadFile = null;
                dialog.closeIfOpen();
                return true;
            });

        ButtonWidget<?> uploadBtn = new ButtonWidget<>();
        WawelAuthStyle.textButton(uploadBtn.size(64, 18), 56, "wawelauth.gui.account_manager.apply")
            .onMousePressed(btn -> {
                attemptPendingTextureUpload(dialog, statusText);
                return true;
            });

        Column root = new Column();
        root.widthRel(1.0f)
            .heightRel(1.0f)
            .padding(rootPadding)
            .background(IDrawable.EMPTY)
            .disableHoverBackground()
            .child(
                new TextWidget<>(GuiText.key(titleKey))
                    .widthRel(1.0f)
                    .height(14)
                    .color(WawelAuthStyle.THEME_LIGHTER))
            .child(new Widget<>().size(1, 5))
            .child(textureUploadPreviewPanel(frontEntity, backEntity, dialogPreviewMode))
            .child(new Widget<>().size(1, 8))
            .child(
                new TextWidget<>(
                    IKey.dynamic(() -> GuiText.ellipsizeToPixelWidth(statusText[0], maxTextWidthPx)))
                        .tooltipDynamic(tooltip -> {
                            if (!isBlank(statusText[0])) {
                                tooltip.addLine(IKey.str(statusText[0]));
                            }
                            if (!isBlank(previewWarning[0])) {
                                tooltip.addLine(IKey.str(previewWarning[0]));
                            }
                        })
                        .tooltipAutoUpdate(true)
                        .widthRel(1.0f)
                        .height(10)
                        .scale(0.8f)
                        .color(DETAIL_SECONDARY_TEXT_COLOR));

        if (skin) {
            root.child(new Widget<>().size(1, 6))
                .child(
                    new Row().widthRel(1.0f)
                        .height(TEXTURE_ACTION_BUTTON_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.START)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(
                            new TextWidget<>(
                                IKey.dynamic(
                                    () -> GuiText.tr("wawelauth.gui.account_manager.skin_model") + ":"))
                                .width(58)
                                .height(12)
                                .scale(0.8f)
                                .color(DETAIL_SECONDARY_TEXT_COLOR))
                        .child(new Widget<>().size(6, TEXTURE_ACTION_BUTTON_HEIGHT))
                        .child(textureModelButton(true, frontEntity, backEntity))
                        .child(textureActionGap())
                        .child(textureModelButton(false, frontEntity, backEntity)));
        }

        root.child(new Widget<>().widthRel(1.0f)
            .expanded())
            .child(
                new Row().widthRel(1.0f)
                    .height(18)
                    .mainAxisAlignment(Alignment.MainAxis.END)
                    .child(cancelBtn)
                    .child(new Widget<>().size(8, 18))
                    .child(uploadBtn));

        WawelAuthStyle.dialog(dialog);
        dialog.size(dialogWidth, dialogHeight)
            .child(root);
        return dialog;
    }

    private Widget<?> textureUploadPreviewPanel(PlayerPreviewEntity frontEntity, PlayerPreviewEntity backEntity,
        PreviewBackMode[] previewMode) {
        return new Column().widthRel(1.0f)
            .height(TEXTURE_DIALOG_PREVIEW_HEIGHT)
            .background(WawelAuthStyle.rect(PREVIEW_PANEL_BACKGROUND_COLOR))
            .disableHoverThemeBackground(true)
            .child(
                new Row().widthRel(1.0f)
                    .height(TEXTURE_DIALOG_ENTITY_ROW_HEIGHT)
                    .mainAxisAlignment(Alignment.MainAxis.CENTER)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .child(textureUploadEntityWidget(frontEntity, false))
                    .child(new Widget<>().size(6, TEXTURE_DIALOG_ENTITY_HEIGHT))
                    .child(textureUploadEntityWidget(backEntity, true)))
            .child(
                new Row().widthRel(1.0f)
                    .height(PREVIEW_MODE_BUTTON_SIZE)
                    .mainAxisAlignment(Alignment.MainAxis.END)
                    .child(textureUploadPreviewModeButton(frontEntity, backEntity, previewMode)))
            .child(new Widget<>().size(1, PREVIEW_MODE_BUTTON_EDGE_MARGIN));
    }

    private ButtonWidget<?> textureUploadPreviewModeButton(PlayerPreviewEntity frontEntity, PlayerPreviewEntity backEntity,
        PreviewBackMode[] previewMode) {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(PREVIEW_MODE_BUTTON_SIZE, PREVIEW_MODE_BUTTON_SIZE)
            .margin(PREVIEW_MODE_BUTTON_EDGE_MARGIN, 0)
            .background(WawelAuthStyle.underlined(ACCOUNT_ACTION_BUTTON_IDLE_BACKGROUND))
            .hoverBackground(WawelAuthStyle.underlined(WawelAuthStyle.BUTTON_HOVER))
            .overlay((context, x, y, width, height, widgetTheme) -> previewModeIcon(
                normalizeCapePreviewMode(previewMode[0]),
                false).draw(context, x, y, width, height, widgetTheme))
            .hoverOverlay((context, x, y, width, height, widgetTheme) -> previewModeIcon(
                normalizeCapePreviewMode(previewMode[0]),
                true).draw(context, x, y, width, height, widgetTheme))
            .tooltip(
                tooltip -> tooltip.addLine(
                    IKey.dynamic(() -> GuiText.tr(normalizeCapePreviewMode(previewMode[0]).translationKey()))))
            .onMousePressed(mouseButton -> {
                previewMode[0] = normalizeCapePreviewMode(previewMode[0])
                    .next(EtFuturumCompat.isPreviewElytraAvailable());
                applyCapePreviewMode(frontEntity, previewMode[0]);
                applyCapePreviewMode(backEntity, previewMode[0]);
                return true;
            });
        return button;
    }

    private Widget<?> textureUploadEntityWidget(PlayerPreviewEntity entity, boolean backView) {
        return new EntityDisplayWidget(() -> entity) {

            @Override
            public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                applyTextureUploadPreviewModel(entity, null);
                PreviewEntityRenderContext.isRenderingInGui = true;
                try {
                    super.draw(context, x, y + TEXTURE_DIALOG_ENTITY_VERTICAL_OFFSET, width, height, widgetTheme);
                } finally {
                    PreviewEntityRenderContext.isRenderingInGui = false;
                }
            }
        }.doesLookAtMouse(!backView)
            .preDraw(preview -> { prepareEntityPreview((PlayerPreviewEntity) preview, backView); })
            .postDraw(preview -> { postEntityPreview(); })
            .asWidget()
            .size(TEXTURE_DIALOG_ENTITY_WIDTH, TEXTURE_DIALOG_ENTITY_HEIGHT)
            .invisible();
    }

    private ButtonWidget<?> textureModelButton(boolean slim, PlayerPreviewEntity frontEntity,
        PlayerPreviewEntity backEntity) {
        ButtonWidget<?> button = new ButtonWidget<>();
        WawelAuthStyle.iconButton(button);
        button.size(TEXTURE_ACTION_BUTTON_WIDTH, TEXTURE_ACTION_BUTTON_HEIGHT)
            .background(WawelAuthStyle.flat(WawelAuthStyle.BUTTON_IDLE, () -> state.pendingTextureUploadSlim == slim))
            .hoverBackground(
                WawelAuthStyle.flat(WawelAuthStyle.BUTTON_HOVER, () -> state.pendingTextureUploadSlim == slim))
            .overlay((context, x, y, width, height, widgetTheme) -> {
                IDrawable icon = slim
                    ? state.pendingTextureUploadSlim ? TEXTURE_MODEL_SLIM_ICON : TEXTURE_MODEL_SLIM_DIM_ICON
                    : state.pendingTextureUploadSlim ? TEXTURE_MODEL_CLASSIC_DIM_ICON : TEXTURE_MODEL_CLASSIC_ICON;
                icon.draw(context, x, y, width, height, widgetTheme);
            })
            .hoverOverlay(slim ? TEXTURE_MODEL_SLIM_ICON : TEXTURE_MODEL_CLASSIC_ICON)
            .addTooltipLine(
                GuiText.tr(
                    slim ? "wawelauth.gui.account_manager.skin_model.slim"
                        : "wawelauth.gui.account_manager.skin_model.classic"))
            .onMousePressed(btn -> {
                state.pendingTextureUploadSlim = slim;
                applyTextureUploadPreviewModel(frontEntity, backEntity);
                return true;
            });
        return button;
    }

    private PlayerPreviewEntity createTextureUploadPreviewEntity(TextureType textureType, ResourceLocation previewTexture) {
        ClientAccount account = state.selectedAccount;
        UUID profileId = account != null && account.getProfileUuid() != null ? account.getProfileUuid()
            : new UUID(0L, 0L);
        String profileName = account != null && account.getProfileName() != null ? account.getProfileName() : "?";
        PlayerPreviewEntity entity = new PlayerPreviewEntity(new GameProfile(profileId, profileName));
        if (textureType == TextureType.SKIN) {
            entity.setForcedSkin(previewTexture);
        } else if (textureType == TextureType.CAPE) {
            entity.setForcedCape(previewTexture);
        }
        entity.setCapeVisible(true);
        SkinLayersHelper.setSkinLayerHidden(entity, SkinLayersHelper.EnumPlayerModelParts.CAPE, false);
        EtFuturumCompat.applyPreviewElytra(entity, false);
        if (account != null && ProviderDisplayName.isOfflineProvider(account.getProviderName())) {
            entity.setForcedSkinModel(account.getLocalSkinModel());
        }
        return entity;
    }

    private void applyTextureUploadPreviewModel(PlayerPreviewEntity frontEntity, PlayerPreviewEntity backEntity) {
        if (!state.pendingTextureUploadSkin) {
            return;
        }
        SkinModel model = state.pendingTextureUploadSlim ? SkinModel.SLIM : SkinModel.CLASSIC;
        if (frontEntity != null) {
            frontEntity.setForcedSkinModel(model);
        }
        if (backEntity != null) {
            backEntity.setForcedSkinModel(model);
        }
    }

    private void warmTexturePreviewTextures(ClientAccount account) {
        if (account == null || account.getProfileUuid() == null) {
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return;
        }
        ClientProvider provider = resolveProvider(
            client.getProviderRegistry()
                .getProvider(account.getProviderName()));
        if (provider == null) {
            return;
        }
        String profileName = account.getProfileName() != null ? account.getProfileName() : "?";
        client.getTextureResolver()
            .getSkin(account.getProfileUuid(), profileName, provider, false);
        client.getTextureResolver()
            .getCape(account.getProfileUuid(), profileName, provider, false);
    }

    private ResourceLocation registerTexturePreview(File file, TextureType textureType) throws Exception {
        BufferedImage image = LocalTextureLoader.readImage(file);
        if (textureType == TextureType.SKIN) {
            image = SkinImageUtil.convertLegacySkin(image);
        }
        String key = "upload_preview/" + textureType.getApiName() + "/" + System.nanoTime();
        return LocalTextureLoader.registerBufferedImage(new ResourceLocation("wawelauth", key), image);
    }

    private void attemptPendingTextureUpload(Dialog<Boolean> dialog, String[] statusText) {
        if (!hasSelectedTextureAccount()) {
            closeTextureUploadDialog(dialog, selectedTextureAccountMissingMessage());
            return;
        }
        File file = state.pendingTextureUploadFile;
        if (file == null) {
            closeTextureUploadDialog(dialog, GuiText.tr("wawelauth.gui.account_manager.choose_texture_first"));
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            closeTextureUploadDialog(dialog, GuiText.tr("wawelauth.gui.common.client_not_running"));
            return;
        }

        final long accountId = state.selectedAccount.getId();
        final TextureType textureType = state.pendingTextureUploadSkin ? TextureType.SKIN : TextureType.CAPE;
        final boolean skinSlim = state.pendingTextureUploadSlim;
        statusText[0] = GuiText.tr(getTextureActionInProgressKey());

        client.getAccountManager()
            .uploadTexture(accountId, textureType, file, skinSlim)
            .whenComplete((result, err) -> Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    dialog.closeIfOpen();
                    state.pendingTextureUploadFile = null;
                    if (err != null) {
                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                        state.textureUploadStatus = GuiText
                            .tr("wawelauth.gui.common.failed_message", cause.getMessage());
                        WawelAuth.debug("Texture upload failed: " + cause.getMessage());
                        if (state.selectedAccount != null && state.selectedAccount.getId() == accountId) {
                            loadSkinForAccount(state.selectedAccount);
                        }
                        return;
                    }
                    handleTextureActionSuccess(client, accountId, result);
                }));
    }

    private void closeTextureUploadDialog(Dialog<Boolean> dialog, String message) {
        state.pendingTextureUploadFile = null;
        state.textureUploadStatus = message;
        dialog.closeIfOpen();
    }

    private void handleTextureActionSuccess(WawelClient client, long accountId, String result) {
        state.textureUploadStatus = result != null ? result
            : GuiText.tr("wawelauth.gui.account_manager.upload_complete");
        state.pendingTextureUploadFile = null;
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
        WawelAuthStyle.textField(pathField);
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
        WawelAuthStyle.textButton(openFolderBtn, 78, "wawelauth.gui.common.open_folder");

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
                } else {
                    state.selectedCapeFile = picked;
                }
                state.textureUploadStatus = "";
                dialog.closeIfOpen();
                openTextureUploadDialog(skin, picked);
                return true;
            });
        WawelAuthStyle.textButton(usePathBtn, 78, "wawelauth.gui.account_manager.use_path");

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        cancelBtn.size(70, 18)
            .onMousePressed(btn -> {
                dialog.closeIfOpen();
                return true;
            });
        WawelAuthStyle.textButton(cancelBtn, 62, "wawelauth.gui.common.cancel");

        WawelAuthStyle.dialog(dialog);
        dialog.size(316, 130)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(8)
                    .background(IDrawable.EMPTY)
                    .disableHoverBackground()
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.select_texture_file", label))
                            .widthRel(1.0f)
                            .height(14)
                            .color(WawelAuthStyle.THEME_LIGHTER))
                    .child(
                        new TextWidget<>(GuiText.key("wawelauth.gui.account_manager.path_help"))
                            .color(WawelAuthStyle.TEXT_SECONDARY)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10))
                    .child(pathField)
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> statusText[0])).color(WawelAuthStyle.WARNING)
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

    private ClientProvider selectedTextureProvider() {
        if (state.selectedAccount != null) {
            WawelClient client = WawelClient.instance();
            if (client != null) {
                ClientProvider provider = client.getProviderRegistry()
                    .getProvider(state.selectedAccount.getProviderName());
                if (provider != null) {
                    return resolveProvider(provider);
                }
            }
        }
        return resolveProvider(state.selectedProvider);
    }

    private boolean hasSelectedTextureAccount() {
        return state.selectedAccount != null && state.selectedAccount.getProfileUuid() != null;
    }

    private String selectedTextureAccountMissingMessage() {
        return state.selectedAccount == null ? GuiText.tr("wawelauth.gui.common.select_account_first")
            : GuiText.tr("wawelauth.gui.account_manager.no_profile_bound");
    }

    private boolean isSkinUploadDisabledForSelectedProvider() {
        ClientProvider provider = selectedTextureProvider();
        if (provider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(provider.getName())) {
            return false;
        }
        return ClientConfig.isSkinUploadDisabled(provider.getName(), provider.getApiRoot());
    }

    private boolean isCapeUploadDisabledForSelectedProvider() {
        ClientProvider provider = selectedTextureProvider();
        if (provider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(provider.getName())) {
            return false;
        }
        return ClientConfig.isCapeUploadDisabled(provider.getName(), provider.getApiRoot());
    }

    private boolean isTextureResetDisabledForSelectedProvider() {
        ClientProvider provider = selectedTextureProvider();
        if (provider == null) return false;
        if (ProviderDisplayName.isOfflineProvider(provider.getName())) {
            return false;
        }
        return ClientConfig
            .isTextureResetDisabled(provider.getName(), provider.getApiRoot());
    }

    private boolean isTextureResetEnabledForSelectedProvider() {
        return !isTextureResetDisabledForSelectedProvider();
    }

    private boolean isSkinUploadEnabledForSelectedAccount() {
        return hasSelectedTextureAccount() && !isSkinUploadDisabledForSelectedProvider();
    }

    private boolean isCapeUploadEnabledForSelectedAccount() {
        return hasSelectedTextureAccount() && !isCapeUploadDisabledForSelectedProvider();
    }

    private boolean isSkinResetEnabledForSelectedAccount() {
        return hasSelectedTextureAccount() && isTextureResetEnabledForSelectedProvider();
    }

    private boolean isCapeResetEnabledForSelectedAccount() {
        if (!hasSelectedTextureAccount() || !isTextureResetEnabledForSelectedProvider()) {
            return false;
        }
        ClientProvider provider = selectedTextureProvider();
        return provider == null || !ProviderDisplayName.isMicrosoftProvider(provider.getName());
    }

    private boolean isAnyTextureManagementEnabledForSelectedAccount() {
        return isSkinUploadEnabledForSelectedAccount()
            || isCapeUploadEnabledForSelectedAccount()
            || isSkinResetEnabledForSelectedAccount()
            || isCapeResetEnabledForSelectedAccount();
    }

    private void attemptTextureReset(TextureType textureType) {
        if (!hasSelectedTextureAccount()) {
            state.textureUploadStatus = selectedTextureAccountMissingMessage();
            return;
        }
        if (textureType == TextureType.CAPE && !isCapeResetEnabledForSelectedAccount()) {
            return;
        }
        if (textureType == TextureType.SKIN && !isSkinResetEnabledForSelectedAccount()) {
            return;
        }
        state.pendingTextureResetType = textureType;
        this.textureResetDialogHandler.deleteCachedPanel();
        this.textureResetDialogHandler.openPanel();
    }

    private Dialog<Boolean> buildTextureResetDialog() {
        Dialog<Boolean> dialog = new Dialog<>("wawelauth_confirm_texture_reset");
        dialog.setCloseOnOutOfBoundsClick(false);
        final TextureType textureType = state.pendingTextureResetType != null ? state.pendingTextureResetType
            : TextureType.SKIN;

        String name = state.selectedAccount != null && state.selectedAccount.getProfileName() != null
            ? state.selectedAccount.getProfileName()
            : GuiText.tr("wawelauth.gui.account_manager.this_account");
        String titleKey = textureType == TextureType.CAPE ? "wawelauth.gui.account_manager.remove_cape_title"
            : "wawelauth.gui.account_manager.reset_skin_title";
        String warningKey = textureType == TextureType.CAPE ? "wawelauth.gui.account_manager.remove_cape_warning"
            : "wawelauth.gui.account_manager.reset_skin_warning";
        String actionKey = textureType == TextureType.CAPE ? "wawelauth.gui.account_manager.remove_cape_short"
            : "wawelauth.gui.common.reset";

        int dialogWidth = 230;
        int dialogHeight = 86;
        int rootPadding = 8;
        int titleMaxWidthPx = dialogWidth - rootPadding * 2 - 4;

        ButtonWidget<?> cancelBtn = new ButtonWidget<>();
        WawelAuthStyle.textButton(cancelBtn.size(60, 18), 52, "wawelauth.gui.common.cancel")
            .onMousePressed(btn -> {
                state.pendingTextureResetType = null;
                dialog.closeIfOpen();
                return true;
            });

        ButtonWidget<?> resetBtn = new ButtonWidget<>();
        WawelAuthStyle.dangerTextButton(resetBtn.size(60, 18), 52, actionKey)
            .onMousePressed(btn -> {
                state.pendingTextureResetType = null;
                dialog.closeIfOpen();
                doTextureReset(textureType);
                return true;
            });

        WawelAuthStyle.dialog(dialog);
        dialog.size(dialogWidth, dialogHeight)
            .child(
                new Column().widthRel(1.0f)
                    .heightRel(1.0f)
                    .padding(rootPadding)
                    .background(IDrawable.EMPTY)
                    .disableHoverBackground()
                    .child(
                        new TextWidget<>(
                            IKey.dynamic(
                                () -> GuiText.ellipsizeToPixelWidth(GuiText.tr(titleKey, name), titleMaxWidthPx)))
                                    .tooltipDynamic(tooltip -> {
                                        String title = GuiText.tr(titleKey, name);
                                        if (!GuiText.ellipsizeToPixelWidth(title, titleMaxWidthPx)
                                            .equals(title)) {
                                            tooltip.addLine(IKey.str(title));
                                        }
                                    })
                                    .tooltipAutoUpdate(true)
                                    .widthRel(1.0f)
                                    .height(14)
                                    .color(WawelAuthStyle.THEME_LIGHTER))
                    .child(
                        new TextWidget<>(GuiText.key(warningKey)).color(WawelAuthStyle.TEXT_SECONDARY)
                            .scale(0.8f)
                            .widthRel(1.0f)
                            .height(10)
                            .margin(0, 6))
                    .child(
                        new Row().widthRel(1.0f)
                            .height(18)
                            .mainAxisAlignment(Alignment.MainAxis.CENTER)
                            .child(cancelBtn)
                            .child(new Widget<>().size(8, 18))
                            .child(resetBtn)));
        return dialog;
    }

    private void doTextureReset(TextureType textureType) {
        if (!hasSelectedTextureAccount()) {
            state.textureUploadStatus = selectedTextureAccountMissingMessage();
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) {
            state.textureUploadStatus = GuiText.tr("wawelauth.gui.common.client_not_running");
            return;
        }

        final long accountId = state.selectedAccount.getId();
        state.textureUploadStatus = GuiText.tr("wawelauth.gui.account_manager.resetting");

        client.getAccountManager()
            .deleteTexture(accountId, textureType)
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
                        handleTextureResetSuccess(client, accountId, textureType, result);
                    });
            });
    }

    private void handleTextureResetSuccess(WawelClient client, long accountId, TextureType textureType, String result) {
        state.textureUploadStatus = result != null ? result
            : GuiText.tr(
                textureType == TextureType.CAPE ? "wawelauth.gui.account_manager.cape_reset_complete"
                    : "wawelauth.gui.account_manager.skin_reset_complete");
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
