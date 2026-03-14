package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Collections;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelFaceRendererClient;
import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.api.internal.TextureRequest;
import org.fentanylsolutions.wawelauth.client.gui.AuthButton;
import org.fentanylsolutions.wawelauth.client.gui.ServerAccountPickerScreen;
import org.fentanylsolutions.wawelauth.wawelclient.SingleplayerAccountPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiSelectWorld.class)
public abstract class MixinGuiSelectWorld extends GuiScreen {

    @Unique
    private static final int WAWELAUTH_SINGLEPLAYER_ACCOUNT_BUTTON_ID = 202;
    @Unique
    private static final int WAWELAUTH_SINGLEPLAYER_HEAD_SIZE = 16;
    @Unique
    private static final int WAWELAUTH_SINGLEPLAYER_HEAD_GAP = 2;
    @Unique
    private GuiButton wawelauth$accountButton;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void wawelauth$onInitGui(CallbackInfo ci) {
        WawelClient client = WawelClient.instance();
        if (client != null) {
            SingleplayerAccountPersistence.clearMissingSelection(client.getAccountManager());
        }
    }

    @Inject(method = "func_146618_g", at = @At("RETURN"))
    private void wawelauth$addAccountButton(CallbackInfo ci) {
        int buttonWidth = AuthButton.preferredWidth(
            this.fontRendererObj,
            "wawelauth.gui.singleplayer.account",
            "wawelauth.gui.singleplayer.account_fallback");
        int x = this.width - 5 - buttonWidth;
        this.wawelauth$accountButton = new AuthButton(
            WAWELAUTH_SINGLEPLAYER_ACCOUNT_BUTTON_ID,
            x,
            5,
            20,
            "wawelauth.gui.singleplayer.account",
            "wawelauth.gui.singleplayer.account_fallback");
        this.buttonList.add(this.wawelauth$accountButton);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void wawelauth$onAction(GuiButton button, CallbackInfo ci) {
        if (button.id == WAWELAUTH_SINGLEPLAYER_ACCOUNT_BUTTON_ID) {
            ServerAccountPickerScreen.openSingleplayer();
            ci.cancel();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void wawelauth$drawSelectedAccount(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.wawelauth$accountButton == null) {
            return;
        }

        ClientAccount account = wawelauth$getSelectedSingleplayerAccount();
        if (account == null || account.getProfileUuid() == null) {
            return;
        }

        int headX = this.wawelauth$accountButton.xPosition - WAWELAUTH_SINGLEPLAYER_HEAD_GAP
            - WAWELAUTH_SINGLEPLAYER_HEAD_SIZE;
        int headY = this.wawelauth$accountButton.yPosition
            + Math.max(0, (this.wawelauth$accountButton.height - WAWELAUTH_SINGLEPLAYER_HEAD_SIZE) / 2);

        wawelauth$drawFace(account, headX, headY);

        if (mouseX >= headX && mouseX < headX + WAWELAUTH_SINGLEPLAYER_HEAD_SIZE
            && mouseY >= headY
            && mouseY < headY + WAWELAUTH_SINGLEPLAYER_HEAD_SIZE) {
            String profileName = account.getProfileName() != null ? account.getProfileName() : "?";
            this.drawHoveringText(
                Collections.singletonList(EnumChatFormatting.WHITE + profileName),
                mouseX,
                mouseY,
                this.fontRendererObj);
        }
    }

    @Unique
    private ClientAccount wawelauth$getSelectedSingleplayerAccount() {
        WawelClient client = WawelClient.instance();
        if (client == null) {
            return null;
        }
        return SingleplayerAccountPersistence.resolveSelectedAccount(client.getAccountManager());
    }

    @Unique
    private void wawelauth$drawFace(ClientAccount account, int x, int y) {
        WawelClient client = WawelClient.instance();
        ResourceLocation skin = WawelTextureResolver.getDefaultSkin();
        if (client != null) {
            String profileName = account.getProfileName() != null ? account.getProfileName() : "?";
            String providerName = account.getProviderName();
            if (providerName != null && !providerName.trim()
                .isEmpty()) {
                skin = client.getTextureResolver()
                    .getSkin(account.getProfileUuid(), profileName, providerName, TextureRequest.DEFAULT);
            } else {
                skin = client.getTextureResolver()
                    .getSkin(account.getProfileUuid(), profileName, TextureRequest.DEFAULT);
            }
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.disableStandardItemLighting();

            WawelFaceRendererClient
                .drawFace(skin, x, y, WAWELAUTH_SINGLEPLAYER_HEAD_SIZE, WAWELAUTH_SINGLEPLAYER_HEAD_SIZE, 1.0F);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }
}
