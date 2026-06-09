package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.authlib.GameProfile;

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
     * @reason Use WawelAuth skin resolver instead of Mojang-only.
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
     * @reason Render face from WawelAuth skin, preserve SU tint behavior.
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
        GameProfile profile = wawelauth$resolveProfile();
        if (profile == null || profile.getId() == null) {
            return WawelTextureResolver.getDefaultSkin();
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return WawelTextureResolver.getDefaultSkin();
        }

        List<ClientProvider> providers = client.resolvePlayerProviderCandidates(profile.getId());
        if (providers.isEmpty()) {
            return WawelTextureResolver.getDefaultSkin();
        }

        return client.getTextureResolver()
            .getSkinFromAnyProvider(profile.getId(), profile.getName(), providers);
    }

    @Unique
    private GameProfile wawelauth$resolveProfile() {
        Minecraft mc = Minecraft.getMinecraft();
        GameProfile localProfile = wawelauth$getLocalProfile(mc);
        if ((Object) this == ClientUtils.localPlayerHead && localProfile != null && localProfile.getId() != null) {
            return localProfile;
        }

        if (uuid == null) {
            return null;
        }

        if (localProfile != null && uuid.equals(localProfile.getId())) {
            return localProfile;
        }

        if (mc != null && mc.theWorld != null) {
            EntityPlayer player = mc.theWorld.func_152378_a(uuid);
            if (player != null) {
                GameProfile worldProfile = player.getGameProfile();
                if (worldProfile != null && worldProfile.getId() != null) {
                    return worldProfile;
                }
                return new GameProfile(uuid, player.getCommandSenderName());
            }
        }

        return new GameProfile(uuid, null);
    }

    @Unique
    private static GameProfile wawelauth$getLocalProfile(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        if (mc.thePlayer != null && mc.thePlayer.getGameProfile() != null) {
            return mc.thePlayer.getGameProfile();
        }
        try {
            return mc.getSession() == null ? null
                : mc.getSession()
                    .func_148256_e();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
