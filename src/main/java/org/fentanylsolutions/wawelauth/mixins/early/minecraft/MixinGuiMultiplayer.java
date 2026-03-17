package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.api.WawelFaceRendererClient;
import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.client.gui.AccountManagerScreen;
import org.fentanylsolutions.wawelauth.client.gui.AuthButton;
import org.fentanylsolutions.wawelauth.client.gui.FolderIconButton;
import org.fentanylsolutions.wawelauth.client.gui.FolderOpenUtil;
import org.fentanylsolutions.wawelauth.client.gui.GuiText;
import org.fentanylsolutions.wawelauth.client.gui.IServerTooltipFaceHost;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.factory.ClientGUI;

@Mixin(GuiMultiplayer.class)
public abstract class MixinGuiMultiplayer extends GuiScreen implements IServerTooltipFaceHost {

    @Shadow
    private ServerSelectionList field_146803_h;

    @Shadow
    private ServerData field_146811_z;

    @Unique
    private static final int WAWELAUTH_ACCOUNTS_BUTTON_ID = 200;
    @Unique
    private static final int WAWELAUTH_OPEN_FOLDER_BUTTON_ID = 201;
    @Unique
    private static final int WAWELAUTH_TOOLTIP_FACE_SIZE = 8;
    @Unique
    private static final int WAWELAUTH_TOOLTIP_FACE_PADDING = 4;
    @Unique
    private static final int WAWELAUTH_TOOLTIP_FACE_AREA_WIDTH = WAWELAUTH_TOOLTIP_FACE_SIZE
        + WAWELAUTH_TOOLTIP_FACE_PADDING;
    @Unique
    private GuiButton wawelauth$openFolderButton;
    @Unique
    private UUID wawelauth$tooltipProfileUuid;
    @Unique
    private String wawelauth$tooltipDisplayName;
    @Unique
    private String wawelauth$tooltipProviderName;
    @Unique
    private ServerData wawelauth$pendingRemovedServerData;
    @Unique
    private boolean wawelauth$pendingEditedServerRetarget;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void wawelauth$onInitGui(CallbackInfo ci) {
        GuiMultiplayer self = (GuiMultiplayer) (Object) this;
        ServerBindingPersistence.setActiveServerList(self.func_146795_p()); // GuiMultiplayer.getServerList

        WawelClient client = WawelClient.instance();
        if (client != null) {
            ServerBindingPersistence.clearRetargetedServerBindings(client);
            ServerBindingPersistence.clearMissingAccountBindings(client.getAccountManager());
            ServerBindingPersistence.clearOrphanedLocalProviders(client);
        }
    }

    @Inject(method = "func_146794_g", at = @At("RETURN")) // GuiMultiplayer.createButtons
    private void wawelauth$addAccountsButton(CallbackInfo ci) {
        int accountsWidth = AuthButton.preferredWidth(this.fontRendererObj);
        int iconWidth = 20;
        int gap = 2;
        int x = this.width - 5 - (accountsWidth + gap + iconWidth);
        this.buttonList.add(new AuthButton(WAWELAUTH_ACCOUNTS_BUTTON_ID, x, 5, 20));
        this.wawelauth$openFolderButton = new FolderIconButton(
            WAWELAUTH_OPEN_FOLDER_BUTTON_ID,
            x + accountsWidth + gap,
            5);
        this.buttonList.add(this.wawelauth$openFolderButton);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void wawelauth$clearTooltipFace(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        wawelauth$clearServerTooltipFace();
    }

    @Redirect(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiMultiplayer;func_146283_a(Ljava/util/List;II)V"))
    private void wawelauth$drawTooltipWithFace(GuiMultiplayer instance, List<String> textLines, int x, int y) {
        if (wawelauth$tooltipProfileUuid == null || wawelauth$tooltipDisplayName == null
            || textLines == null
            || textLines.isEmpty()) {
            this.func_146283_a(textLines, x, y);
            return;
        }
        wawelauth$drawHoveringTextWithFace(textLines, x, y);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void wawelauth$onAction(GuiButton button, CallbackInfo ci) {
        if (button.id == WAWELAUTH_ACCOUNTS_BUTTON_ID) {
            ClientGUI.open(new AccountManagerScreen());
            ci.cancel();
            return;
        }
        if (button.id == WAWELAUTH_OPEN_FOLDER_BUTTON_ID) {
            FolderOpenUtil.openFolder(Config.getConfigDir());
            ci.cancel();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void wawelauth$drawFolderTooltip(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.wawelauth$openFolderButton instanceof FolderIconButton) {
            FolderIconButton folderButton = (FolderIconButton) this.wawelauth$openFolderButton;
            if (folderButton.isMouseOver(mouseX, mouseY)) {
                this.drawHoveringText(
                    Collections.singletonList(GuiText.tr("wawelauth.gui.multiplayer.open_folder")),
                    mouseX,
                    mouseY,
                    this.fontRendererObj);
            }
        }
    }

    @Inject(
        method = "confirmClicked",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;removeServerData(I)V"))
    private void wawelauth$captureServerBeforeDelete(boolean result, int id, CallbackInfo ci) {
        int selectedIndex = field_146803_h.func_148193_k();
        GuiListExtended.IGuiListEntry entry = selectedIndex < 0 ? null : field_146803_h.getListEntry(selectedIndex);
        if (entry instanceof ServerListEntryNormal) {
            wawelauth$pendingRemovedServerData = ((ServerListEntryNormal) entry).func_148296_a();
        }
    }

    @Inject(
        method = "confirmClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ServerData;func_152583_a(Lnet/minecraft/client/multiplayer/ServerData;)V"))
    private void wawelauth$resetBindingWhenServerRetargeted(boolean result, int id, CallbackInfo ci) {
        int selectedIndex = field_146803_h.func_148193_k();
        GuiListExtended.IGuiListEntry entry = selectedIndex < 0 ? null : field_146803_h.getListEntry(selectedIndex);
        if (!(entry instanceof ServerListEntryNormal) || field_146811_z == null) {
            return;
        }

        ServerData existing = ((ServerListEntryNormal) entry).func_148296_a();
        if (!ServerBindingPersistence.isRetargetedServerAddress(existing, field_146811_z.serverIP)) {
            return;
        }

        ServerBindingPersistence.clearBindingState(field_146811_z, true);
        wawelauth$pendingEditedServerRetarget = true;
    }

    @Inject(method = "confirmClicked", at = @At("RETURN"))
    private void wawelauth$cleanupAfterServerDelete(boolean result, int id, CallbackInfo ci) {
        if (wawelauth$pendingRemovedServerData == null && !wawelauth$pendingEditedServerRetarget) {
            return;
        }

        try {
            WawelClient client = WawelClient.instance();
            if (client != null) {
                ServerBindingPersistence.clearRetargetedServerBindings(client);
                ServerBindingPersistence.clearOrphanedLocalProviders(client);
            }
        } finally {
            wawelauth$pendingRemovedServerData = null;
            wawelauth$pendingEditedServerRetarget = false;
        }
    }

    @Override
    public void wawelauth$setServerTooltipFace(String displayName, UUID profileUuid, String providerName) {
        this.wawelauth$tooltipDisplayName = displayName;
        this.wawelauth$tooltipProfileUuid = profileUuid;
        this.wawelauth$tooltipProviderName = providerName;
    }

    @Override
    public void wawelauth$clearServerTooltipFace() {
        this.wawelauth$tooltipDisplayName = null;
        this.wawelauth$tooltipProfileUuid = null;
        this.wawelauth$tooltipProviderName = null;
    }

    @Unique
    private void wawelauth$drawHoveringTextWithFace(List<String> textLines, int mouseX, int mouseY) {
        if (textLines.isEmpty()) {
            return;
        }

        int firstLineWidth = this.fontRendererObj.getStringWidth(textLines.get(0));
        int detailWidth = 0;
        for (int i = 1; i < textLines.size(); i++) {
            detailWidth = Math.max(detailWidth, this.fontRendererObj.getStringWidth(textLines.get(i)));
        }
        int totalWidth = Math.max(detailWidth, firstLineWidth + WAWELAUTH_TOOLTIP_FACE_AREA_WIDTH);

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        int tooltipHeight = 8;
        if (textLines.size() > 1) {
            tooltipHeight += 2 + (textLines.size() - 1) * 10;
        }

        if (tooltipX + totalWidth > this.width) {
            tooltipX -= 28 + totalWidth;
        }

        if (tooltipY + tooltipHeight + 6 > this.height) {
            tooltipY = this.height - tooltipHeight - 6;
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        this.zLevel = 300.0F;
        itemRender.zLevel = 300.0F;
        int background = -267386864;
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 4,
            tooltipX + totalWidth + 3,
            tooltipY - 3,
            background,
            background);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY + tooltipHeight + 3,
            tooltipX + totalWidth + 3,
            tooltipY + tooltipHeight + 4,
            background,
            background);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 3,
            tooltipX + totalWidth + 3,
            tooltipY + tooltipHeight + 3,
            background,
            background);
        this.drawGradientRect(
            tooltipX - 4,
            tooltipY - 3,
            tooltipX - 3,
            tooltipY + tooltipHeight + 3,
            background,
            background);
        this.drawGradientRect(
            tooltipX + totalWidth + 3,
            tooltipY - 3,
            tooltipX + totalWidth + 4,
            tooltipY + tooltipHeight + 3,
            background,
            background);
        int borderStart = 1347420415;
        int borderEnd = (borderStart & 16711422) >> 1 | borderStart & -16777216;
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 2,
            tooltipX - 2,
            tooltipY + tooltipHeight + 2,
            borderStart,
            borderEnd);
        this.drawGradientRect(
            tooltipX + totalWidth + 2,
            tooltipY - 2,
            tooltipX + totalWidth + 3,
            tooltipY + tooltipHeight + 2,
            borderStart,
            borderEnd);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 3,
            tooltipX + totalWidth + 3,
            tooltipY - 2,
            borderStart,
            borderStart);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY + tooltipHeight + 2,
            tooltipX + totalWidth + 3,
            tooltipY + tooltipHeight + 3,
            borderEnd,
            borderEnd);

        int faceX = tooltipX;
        int headerTextX = faceX + WAWELAUTH_TOOLTIP_FACE_AREA_WIDTH;
        int detailTextX = tooltipX;
        WawelFaceRendererClient.drawFace(
            wawelauth$resolveTooltipSkin(),
            faceX,
            tooltipY,
            WAWELAUTH_TOOLTIP_FACE_SIZE,
            WAWELAUTH_TOOLTIP_FACE_SIZE,
            1.0F);

        int lineY = tooltipY;
        for (int i = 0; i < textLines.size(); i++) {
            int lineX = i == 0 ? headerTextX : detailTextX;
            this.fontRendererObj.drawStringWithShadow(textLines.get(i), lineX, lineY, -1);
            if (i == 0) {
                lineY += 2;
            }
            lineY += 10;
        }

        this.zLevel = 0.0F;
        itemRender.zLevel = 0.0F;
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    @Unique
    private ResourceLocation wawelauth$resolveTooltipSkin() {
        WawelClient client = WawelClient.instance();
        if (client == null || this.wawelauth$tooltipProfileUuid == null) {
            return WawelTextureResolver.getDefaultSkin();
        }

        String displayName = this.wawelauth$tooltipDisplayName != null ? this.wawelauth$tooltipDisplayName : "?";
        org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider provider = client
            .resolveProviderByName(this.wawelauth$tooltipProviderName);
        return client.getTextureResolver()
            .getSkin(this.wawelauth$tooltipProfileUuid, displayName, provider, false);
    }
}
