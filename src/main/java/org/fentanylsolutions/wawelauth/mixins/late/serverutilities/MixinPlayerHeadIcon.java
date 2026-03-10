package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.SkinRequest;
import org.fentanylsolutions.wawelauth.api.WawelSkinResolver;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import serverutils.lib.client.ClientUtils;
import serverutils.lib.gui.GuiHelper;
import serverutils.lib.icon.ImageIcon;
import serverutils.lib.icon.PlayerHeadIcon;

@Mixin(value = PlayerHeadIcon.class, priority = 999, remap = false)
public abstract class MixinPlayerHeadIcon extends ImageIcon {

    @Shadow(remap = false)
    @Final
    public UUID uuid;

    public MixinPlayerHeadIcon(ResourceLocation texture) {
        super(texture);
    }

    /**
     * @author Wawel Auth
     * @reason Use WawelAuth's resolved skin texture instead of Mojang-only fetching.
     */
    @Overwrite(remap = false)
    @SideOnly(Side.CLIENT)
    public void bindTexture() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(wawelauth$resolveSkin());
    }

    /**
     * @author Wawel Auth
     * @reason Render the face from the WawelAuth skin while preserving ServerUtilities tint behavior.
     */
    @Overwrite(remap = false)
    @SideOnly(Side.CLIENT)
    public void draw(int x, int y, int w, int h) {
        bindTexture();

        int texWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int texHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        boolean legacyLayout = texWidth > 0 && texHeight > 0 && texWidth == texHeight * 2;

        double v0 = legacyLayout ? 0.25D : 0.125D;
        double v1 = legacyLayout ? 0.50D : 0.25D;

        GuiHelper.drawTexturedRect(x, y, w, h, this.color, 0.125D, v0, 0.25D, v1);
        GuiHelper.drawTexturedRect(x, y, w, h, this.color, 0.625D, v0, 0.75D, v1);
    }

    @Unique
    private ResourceLocation wawelauth$resolveSkin() {
        UUID profileUuid = wawelauth$resolveProfileUuid();
        if (profileUuid == null) {
            return WawelSkinResolver.getDefaultSkin();
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return WawelSkinResolver.getDefaultSkin();
        }

        return client.getSkinResolver()
            .getSkin(profileUuid, wawelauth$resolveDisplayName(profileUuid), SkinRequest.DEFAULT);
    }

    @Unique
    private UUID wawelauth$resolveProfileUuid() {
        Minecraft mc = Minecraft.getMinecraft();
        if ((Object) this == ClientUtils.localPlayerHead) {
            if (mc.thePlayer != null && mc.thePlayer.getGameProfile() != null
                && mc.thePlayer.getGameProfile()
                    .getId() != null) {
                return mc.thePlayer.getGameProfile()
                    .getId();
            }
            if (mc.getSession() != null && mc.getSession()
                .func_148256_e() != null) {
                return mc.getSession()
                    .func_148256_e()
                    .getId();
            }
        }

        return uuid;
    }

    @Unique
    private String wawelauth$resolveDisplayName(UUID profileUuid) {
        if (profileUuid == null) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if ((Object) this == ClientUtils.localPlayerHead) {
            if (mc.thePlayer != null && mc.thePlayer.getGameProfile() != null
                && mc.thePlayer.getGameProfile()
                    .getName() != null) {
                return mc.thePlayer.getGameProfile()
                    .getName();
            }
            if (mc.thePlayer != null) {
                return mc.thePlayer.getCommandSenderName();
            }
        }

        if (mc.getSession() != null && mc.getSession()
            .func_148256_e() != null
            && profileUuid.equals(
                mc.getSession()
                    .func_148256_e()
                    .getId())) {
            return mc.getSession()
                .func_148256_e()
                .getName();
        }

        if (mc.theWorld != null) {
            EntityPlayer player = mc.theWorld.func_152378_a(profileUuid);
            if (player != null) {
                if (player.getGameProfile() != null && player.getGameProfile()
                    .getName() != null) {
                    return player.getGameProfile()
                        .getName();
                }
                return player.getCommandSenderName();
            }
        }

        return null;
    }
}
