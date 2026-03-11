package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.SkinRequest;
import org.fentanylsolutions.wawelauth.api.WawelSkinResolver;
import org.fentanylsolutions.wawelauth.client.gui.AccountManagerScreen;
import org.fentanylsolutions.wawelauth.client.gui.GuiText;
import org.fentanylsolutions.wawelauth.client.gui.IServerTooltipFaceHost;
import org.fentanylsolutions.wawelauth.client.gui.ProviderDisplayName;
import org.fentanylsolutions.wawelauth.client.gui.ServerAccountPickerScreen;
import org.fentanylsolutions.wawelauth.client.gui.StatusColors;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerListEntryNormal.class)
public class MixinServerListEntryNormal {

    private static final String STATUS_SQUARE = "\u25A0";

    @Shadow
    private ServerData field_148301_e; // ServerListEntryNormal.serverData

    @Shadow
    @Final
    private GuiMultiplayer field_148303_c; // ServerListEntryNormal.owner (parent screen)

    @Shadow
    @Final
    private Minecraft field_148300_d; // ServerListEntryNormal.mc

    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 17;
    private static final int ICON_OFFSET_FROM_RIGHT = 34; // width + 18 px gap to ping icon
    private static final int ICON_Y_OFFSET = 10;
    private static final int ACCOUNT_HEAD_SIZE = 12;
    private static final int ACCOUNT_STATUS_SIZE = 8;
    private static final int ACCOUNT_DECORATION_GAP = 2;

    private static final ResourceLocation ICON_AUTHED = new ResourceLocation("wawelauth", "textures/authed.png");
    private static final ResourceLocation ICON_UNAUTHED = new ResourceLocation("wawelauth", "textures/unauthed.png");
    private static final ResourceLocation ICON_OUTLINE = new ResourceLocation("wawelauth", "textures/outline.png");

    private int lastListWidth;

    @Inject(method = "drawEntry", at = @At("RETURN"))
    private void wawelauth$drawIndicator(int slotIndex, int x, int y, int listWidth, int slotHeight,
        Tessellator tessellator, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {

        if (field_148301_e == null) return;

        this.lastListWidth = listWidth;

        IServerDataExt ext = (IServerDataExt) field_148301_e;
        int iconX = x + listWidth - ICON_OFFSET_FROM_RIGHT;
        int iconY = y + ICON_Y_OFFSET;

        ServerCapabilities caps = ext.getWawelCapabilities();
        long accountId = ext.getWawelAccountId();
        WawelClient client = WawelClient.instance();
        ClientAccount selectedAccount = null;
        AccountStatus selectedStatus = null;
        boolean wawelAuthAdvertised = caps != null && caps.getUpdatedAtMs() > 0L && caps.isWawelAuthAdvertised();
        if (accountId >= 0 && client != null) {
            selectedAccount = client.getAccountManager()
                .getAccount(accountId);
            selectedStatus = client.getAccountManager()
                .getAccountStatus(accountId);
        }

        int statusX = iconX - ACCOUNT_DECORATION_GAP - ACCOUNT_STATUS_SIZE;
        int statusY = iconY + (ICON_HEIGHT - ACCOUNT_STATUS_SIZE) / 2;
        int headX = statusX - ACCOUNT_DECORATION_GAP - ACCOUNT_HEAD_SIZE;
        int headY = iconY + (ICON_HEIGHT - ACCOUNT_HEAD_SIZE) / 2;

        boolean iconHovering = isMouseWithin(mouseX, mouseY, iconX, iconY, ICON_WIDTH, ICON_HEIGHT);
        boolean statusHovering = selectedAccount != null
            && isMouseWithin(mouseX, mouseY, statusX, statusY, ACCOUNT_STATUS_SIZE, ACCOUNT_STATUS_SIZE);
        boolean headHovering = selectedAccount != null
            && isMouseWithin(mouseX, mouseY, headX, headY, ACCOUNT_HEAD_SIZE, ACCOUNT_HEAD_SIZE);
        boolean shiftDown = isShiftDown();

        // Draw legacy textured indicator (ported from WawelAuthOLD).
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (iconHovering) {
                field_148300_d.getTextureManager()
                    .bindTexture(ICON_OUTLINE);
                Gui.func_146110_a(
                    /* drawModalRectWithCustomSizedTexture */iconX,
                    iconY,
                    0,
                    0,
                    ICON_WIDTH,
                    ICON_HEIGHT - 1,
                    16.0f,
                    16.0f);
            }
            field_148300_d.getTextureManager()
                .bindTexture(wawelAuthAdvertised ? ICON_AUTHED : ICON_UNAUTHED);
            Gui.func_146110_a(
                /* drawModalRectWithCustomSizedTexture */iconX,
                iconY,
                0,
                0,
                ICON_WIDTH,
                ICON_HEIGHT,
                16.0f,
                16.0f);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }

        if (selectedAccount != null) {
            drawStatusSquare(statusX, statusY, selectedStatus);
            drawSelectedAccountHead(
                headX,
                headY,
                selectedAccount,
                normalizeTooltipValue(ext.getWawelProviderName()),
                client);
            if (shiftDown && headHovering) {
                Gui.drawRect(headX, headY, headX + ACCOUNT_HEAD_SIZE, headY + ACCOUNT_HEAD_SIZE, 0x55FF0000);
            }
        }

        if (headHovering && selectedAccount != null) {
            List<String> tooltipLines = new ArrayList<>();
            tooltipLines.add(EnumChatFormatting.WHITE + resolveAccountDisplayName(selectedAccount, accountId));
            if (shiftDown && selectedAccount.getProfileUuid() != null) {
                tooltipLines.add(
                    EnumChatFormatting.GRAY + selectedAccount.getProfileUuid()
                        .toString());
            }
            if (shiftDown) {
                tooltipLines.add(EnumChatFormatting.RED + GuiText.tr("wawelauth.gui.server_tooltip.unselect_account"));
            }
            field_148303_c.func_146793_a(joinTooltipLines(tooltipLines));
            return;
        }

        if (statusHovering && selectedAccount != null) {
            field_148303_c.func_146793_a(StatusColors.getLabel(selectedStatus));
            return;
        }

        if (iconHovering) {
            ServerCapabilities localAuthCaps = ServerBindingPersistence
                .getEffectiveLocalAuthCapabilities(field_148301_e);
            boolean localAuthAvailable = isLocalAuthAvailable(localAuthCaps);

            List<String> accountLines = new ArrayList<>();
            List<String> authLines = new ArrayList<>();

            String rawProviderName = ext.getWawelProviderName();
            String providerName = ProviderDisplayName.displayName(rawProviderName);
            String displayName = null;
            String authDisplayName = null;
            String authApiRoot = null;
            java.util.UUID profileUuid = null;

            ClientProvider selectedProvider = null;
            if (accountId >= 0) {
                if (selectedAccount != null) {
                    displayName = normalizeTooltipValue(selectedAccount.getProfileName());
                    if (selectedAccount.getProviderName() != null && !selectedAccount.getProviderName()
                        .trim()
                        .isEmpty()) {
                        rawProviderName = selectedAccount.getProviderName();
                        providerName = ProviderDisplayName.displayName(selectedAccount.getProviderName());
                        selectedProvider = client.getProviderRegistry()
                            .getProvider(selectedAccount.getProviderName());
                    }
                    profileUuid = selectedAccount.getProfileUuid();
                }

                if (selectedProvider == null && client != null
                    && rawProviderName != null
                    && !rawProviderName.trim()
                        .isEmpty()) {
                    selectedProvider = client.getProviderRegistry()
                        .getProvider(rawProviderName);
                }

                if (displayName == null) {
                    displayName = GuiText.tr("wawelauth.gui.server_tooltip.account_fallback", Long.valueOf(accountId));
                }

                accountLines.add(displayName);
                if (profileUuid != null && shiftDown) {
                    accountLines.add(EnumChatFormatting.GRAY + profileUuid.toString());
                }
                if (profileUuid != null && field_148303_c instanceof IServerTooltipFaceHost) {
                    ((IServerTooltipFaceHost) field_148303_c)
                        .wawelauth$setServerTooltipFace(displayName, profileUuid, rawProviderName);
                }
                if (client != null) {
                    AccountStatus status = client.getAccountManager()
                        .getAccountStatus(accountId);
                    if (status != null) {
                        accountLines.add(
                            EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.common.status")
                                + ": "
                                + statusColorCode(status)
                                + STATUS_SQUARE);
                    }
                }
            } else {
                accountLines.add(GuiText.tr("wawelauth.gui.server_tooltip.no_account"));
            }

            if (localAuthAvailable) {
                ClientProvider localAuthProvider = client != null ? client.getLocalAuthProviderResolver()
                    .findExisting(localAuthCaps) : null;
                if (localAuthProvider != null) {
                    authDisplayName = ProviderDisplayName.displayName(localAuthProvider.getName());
                    authApiRoot = normalizeTooltipValue(localAuthProvider.getApiRoot());
                } else {
                    authDisplayName = fallbackLocalAuthProviderName(localAuthCaps);
                }
                if (authApiRoot == null) {
                    authApiRoot = normalizeTooltipValue(localAuthCaps.getLocalAuthApiRoot());
                }
            } else {
                authDisplayName = providerName;
                authApiRoot = selectedProvider != null ? normalizeTooltipValue(selectedProvider.getApiRoot()) : null;
            }

            List<String> availableProviders = collectAvailableProviderHosts(caps);
            boolean noPingData = caps == null || caps.getUpdatedAtMs() <= 0L;
            boolean providersUnknown = caps != null && caps.getUpdatedAtMs() > 0L
                && !caps.isWawelAuthAdvertised()
                && availableProviders.isEmpty();
            boolean unknownProvider = !localAuthAvailable && providersUnknown
                && authApiRoot == null
                && (authDisplayName == null || "?".equals(authDisplayName));
            boolean suppressNoPingProviderRow = !localAuthAvailable && noPingData
                && authApiRoot == null
                && !notBlank(rawProviderName);
            boolean microsoftProvider = !localAuthAvailable && ProviderDisplayName.isMicrosoftProvider(rawProviderName);
            boolean serverAuthUnknown = !localAuthAvailable
                && (noPingData || (caps != null && !caps.isWawelAuthAdvertised()));

            if (unknownProvider) {
                authLines.add(EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.server_tooltip.unknown_provider"));
            } else {
                if (authDisplayName != null && !suppressNoPingProviderRow) {
                    authLines.add(
                        EnumChatFormatting.GRAY
                            + GuiText.tr(
                                serverAuthUnknown ? "wawelauth.gui.server_tooltip.account_auth_label"
                                    : "wawelauth.gui.server_tooltip.auth_label")
                            + EnumChatFormatting.GOLD
                            + authDisplayName);
                }
                if (authApiRoot != null && !microsoftProvider) {
                    authLines.add(
                        EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.common.api_root_label")
                            + EnumChatFormatting.GOLD
                            + authApiRoot);
                }
                if (!microsoftProvider && !availableProviders.isEmpty()) {
                    authLines
                        .add(EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.server_tooltip.available_providers"));
                    for (String providerHost : availableProviders) {
                        authLines.add(EnumChatFormatting.AQUA + "- " + providerHost);
                    }
                }
                if (serverAuthUnknown) {
                    authLines
                        .add(EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.server_tooltip.server_auth_unknown"));
                }
            }
            if (localAuthAvailable) {
                if (!authLines.isEmpty()) {
                    authLines.add("");
                }
                authLines.add(
                    (shiftDown ? EnumChatFormatting.WHITE : EnumChatFormatting.GRAY)
                        + GuiText.tr("wawelauth.gui.server_tooltip.shift_local_auth"));
            }

            List<String> tooltipLines = new ArrayList<>(accountLines);
            if (!accountLines.isEmpty() && !authLines.isEmpty()) {
                tooltipLines.add("");
            }
            tooltipLines.addAll(authLines);
            field_148303_c.func_146793_a(joinTooltipLines(tooltipLines)); // GuiScreen.setToolTip
        }
    }

    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void wawelauth$onMousePressed(int slotIndex, int mouseX, int mouseY, int mouseButton, int relX, int relY,
        CallbackInfoReturnable<Boolean> cir) {

        if (mouseButton != 0) return;
        if (field_148301_e == null) return;

        IServerDataExt ext = (IServerDataExt) field_148301_e;
        long accountId = ext.getWawelAccountId();
        int indicatorRelX = lastListWidth - ICON_OFFSET_FROM_RIGHT;
        int statusRelX = indicatorRelX - ACCOUNT_DECORATION_GAP - ACCOUNT_STATUS_SIZE;
        int headRelX = statusRelX - ACCOUNT_DECORATION_GAP - ACCOUNT_HEAD_SIZE;
        boolean shiftDown = isShiftDown();

        if (shiftDown && accountId >= 0
            && relX >= headRelX
            && relX < headRelX + ACCOUNT_HEAD_SIZE
            && relY >= ICON_Y_OFFSET + (ICON_HEIGHT - ACCOUNT_HEAD_SIZE) / 2
            && relY < ICON_Y_OFFSET + (ICON_HEIGHT - ACCOUNT_HEAD_SIZE) / 2 + ACCOUNT_HEAD_SIZE) {
            ext.setWawelAccountId(-1L);
            ext.setWawelProviderName(null);
            ServerBindingPersistence.markServerBindingOrigin(field_148301_e);
            ServerBindingPersistence.persistServerSelection(field_148301_e);
            cir.setReturnValue(true);
            return;
        }

        if (relX >= indicatorRelX && relX < indicatorRelX + ICON_WIDTH
            && relY >= ICON_Y_OFFSET
            && relY < ICON_Y_OFFSET + ICON_HEIGHT) {

            if (shiftDown
                && isLocalAuthAvailable(ServerBindingPersistence.getEffectiveLocalAuthCapabilities(field_148301_e))) {
                AccountManagerScreen.openForLocalAuth(field_148301_e);
                cir.setReturnValue(true);
                return;
            }

            ServerAccountPickerScreen.open(field_148301_e);
            cir.setReturnValue(true);
        }
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean isLocalAuthAvailable(ServerCapabilities localAuthCapabilities) {
        return localAuthCapabilities != null && localAuthCapabilities.isLocalAuthSupported()
            && notBlank(localAuthCapabilities.getLocalAuthApiRoot())
            && notBlank(localAuthCapabilities.getLocalAuthPublicKeyFingerprint());
    }

    private static String fallbackLocalAuthProviderName(ServerCapabilities localAuthCapabilities) {
        String fingerprint = normalizeTooltipValue(
            localAuthCapabilities != null ? localAuthCapabilities.getLocalAuthPublicKeyFingerprint() : null);
        if (fingerprint == null) {
            return GuiText.tr("wawelauth.gui.common.unknown");
        }

        String suffix = fingerprint.length() > 12 ? fingerprint.substring(0, 12) : fingerprint;
        return "LocalAuth-" + suffix;
    }

    private static String normalizeTooltipValue(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveAccountDisplayName(ClientAccount account, long accountId) {
        String displayName = normalizeTooltipValue(account != null ? account.getProfileName() : null);
        if (displayName != null) {
            return displayName;
        }
        return GuiText.tr("wawelauth.gui.server_tooltip.account_fallback", Long.valueOf(accountId));
    }

    private static void drawStatusSquare(int x, int y, AccountStatus status) {
        Gui.drawRect(x, y, x + ACCOUNT_STATUS_SIZE, y + ACCOUNT_STATUS_SIZE, 0xFF000000);
        Gui.drawRect(
            x + 1,
            y + 1,
            x + ACCOUNT_STATUS_SIZE - 1,
            y + ACCOUNT_STATUS_SIZE - 1,
            StatusColors.getColor(status));
    }

    private static void drawSelectedAccountHead(int x, int y, ClientAccount account, String fallbackProviderName,
        WawelClient client) {
        ResourceLocation skin = resolveSelectedAccountSkin(account, fallbackProviderName, client);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            WawelSkinResolver.drawFace(skin, x, y, ACCOUNT_HEAD_SIZE, ACCOUNT_HEAD_SIZE, 1.0F);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private static ResourceLocation resolveSelectedAccountSkin(ClientAccount account, String fallbackProviderName,
        WawelClient client) {
        if (account == null || client == null) {
            return WawelSkinResolver.getDefaultSkin();
        }

        java.util.UUID profileUuid = account.getProfileUuid();
        String displayName = normalizeTooltipValue(account.getProfileName());
        if (displayName == null) {
            displayName = "?";
        }

        String providerName = normalizeTooltipValue(account.getProviderName());
        if (providerName == null) {
            providerName = fallbackProviderName;
        }

        if (providerName != null) {
            return client.getSkinResolver()
                .getSkin(profileUuid, displayName, providerName, SkinRequest.DEFAULT);
        }
        return client.getSkinResolver()
            .getSkin(profileUuid, displayName, SkinRequest.DEFAULT);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }

    private static String joinTooltipLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static boolean isMouseWithin(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static List<String> collectAvailableProviderHosts(ServerCapabilities caps) {
        List<String> providers = new ArrayList<>();
        if (caps == null || caps.getAcceptedAuthServerUrls()
            .isEmpty()) {
            return providers;
        }

        for (String authUrl : caps.getAcceptedAuthServerUrls()) {
            String providerHost = authUrl;
            try {
                URI uri = new URI(authUrl);
                if (uri.getHost() != null) {
                    providerHost = NetworkAddressUtil.formatHostPort(uri.getHost(), uri.getPort());
                }
            } catch (Exception ignored) {}

            if (!providers.contains(providerHost)) {
                providers.add(providerHost);
            }
        }
        return providers;
    }

    private static EnumChatFormatting statusColorCode(AccountStatus status) {
        if (status == null) return EnumChatFormatting.GRAY;
        switch (status) {
            case VALID:
            case REFRESHED:
                return EnumChatFormatting.GREEN;
            case UNVERIFIED:
                return EnumChatFormatting.YELLOW;
            case EXPIRED:
                return EnumChatFormatting.RED;
            default:
                return EnumChatFormatting.GRAY;
        }
    }
}
