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

import org.fentanylsolutions.wawelauth.client.gui.AccountManagerScreen;
import org.fentanylsolutions.wawelauth.client.gui.GuiText;
import org.fentanylsolutions.wawelauth.client.gui.IServerTooltipFaceHost;
import org.fentanylsolutions.wawelauth.client.gui.ProviderDisplayName;
import org.fentanylsolutions.wawelauth.client.gui.ServerAccountPickerScreen;
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

        long accountId = ext.getWawelAccountId();
        boolean isHovering = mouseX >= iconX && mouseX < iconX + ICON_WIDTH
            && mouseY >= iconY
            && mouseY < iconY + ICON_HEIGHT;

        // Draw legacy textured indicator (ported from WawelAuthOLD).
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (isHovering) {
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
                .bindTexture(accountId >= 0 ? ICON_AUTHED : ICON_UNAUTHED);
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

        if (isHovering) {
            ServerCapabilities caps = ext.getWawelCapabilities();
            ServerCapabilities localAuthCaps = ServerBindingPersistence
                .getEffectiveLocalAuthCapabilities(field_148301_e);
            boolean localAuthAvailable = isLocalAuthAvailable(localAuthCaps);
            boolean shiftDown = isShiftDown();

            List<String> accountLines = new ArrayList<>();
            List<String> authLines = new ArrayList<>();

            String rawProviderName = ext.getWawelProviderName();
            String providerName = ProviderDisplayName.displayName(rawProviderName);
            String displayName = null;
            String authDisplayName = null;
            String authApiRoot = null;
            java.util.UUID profileUuid = null;

            WawelClient client = WawelClient.instance();
            ClientProvider selectedProvider = null;
            if (accountId >= 0) {
                if (client != null) {
                    ClientAccount account = client.getAccountManager()
                        .getAccount(accountId);
                    if (account != null) {
                        String profileName = account.getProfileName();
                        if (profileName != null && !profileName.trim()
                            .isEmpty()) {
                            displayName = profileName;
                        }
                        if (account.getProviderName() != null && !account.getProviderName()
                            .trim()
                            .isEmpty()) {
                            rawProviderName = account.getProviderName();
                            providerName = ProviderDisplayName.displayName(account.getProviderName());
                            selectedProvider = client.getProviderRegistry()
                                .getProvider(account.getProviderName());
                        }
                        profileUuid = account.getProfileUuid();
                    }
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

            if (authDisplayName != null) {
                authLines.add(
                    localAuthAvailable
                        ? EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.server_tooltip.local_auth")
                            + EnumChatFormatting.GOLD
                            + authDisplayName
                        : EnumChatFormatting.GRAY
                            + GuiText.tr("wawelauth.gui.server_tooltip.provider_tag", authDisplayName));
            }
            if (authApiRoot != null) {
                authLines.add(
                    EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.common.api_root_label")
                        + EnumChatFormatting.GOLD
                        + authApiRoot);
            }

            List<String> availableProviders = collectAvailableProviderHosts(caps);
            boolean providersUnknown = caps != null && caps.getUpdatedAtMs() > 0L
                && !caps.isWawelAuthAdvertised()
                && availableProviders.isEmpty();
            if (!availableProviders.isEmpty() || providersUnknown) {
                authLines.add(EnumChatFormatting.GRAY + GuiText.tr("wawelauth.gui.server_tooltip.available_providers"));
                if (providersUnknown) {
                    authLines.add(EnumChatFormatting.GRAY + "- " + GuiText.tr("wawelauth.gui.common.unknown"));
                } else {
                    for (String providerHost : availableProviders) {
                        authLines.add(EnumChatFormatting.AQUA + "- " + providerHost);
                    }
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

        int indicatorRelX = lastListWidth - ICON_OFFSET_FROM_RIGHT;
        if (relX >= indicatorRelX && relX < indicatorRelX + ICON_WIDTH
            && relY >= ICON_Y_OFFSET
            && relY < ICON_Y_OFFSET + ICON_HEIGHT) {

            if (isShiftDown()
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
