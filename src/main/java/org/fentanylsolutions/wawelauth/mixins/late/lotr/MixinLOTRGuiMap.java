package org.fentanylsolutions.wawelauth.mixins.late.lotr;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.client.render.SkinTextureState;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "lotr.client.gui.LOTRGuiMap", remap = false)
public abstract class MixinLOTRGuiMap extends GuiScreen {

    @Shadow(remap = false)
    private static int mapXMin;

    @Shadow(remap = false)
    private static int mapXMax;

    @Shadow(remap = false)
    private static int mapYMin;

    @Shadow(remap = false)
    private static int mapYMax;

    /**
     * @author Wawel Auth
     * @reason Resolve LOTR map heads through WawelAuth and support HD/modern skin UVs.
     */
    @Overwrite(remap = false)
    private double renderPlayerIcon(GameProfile profile, String displayName, double x, double y, int mouseX,
        int mouseY) {
        Tessellator tessellator = Tessellator.instance;
        int halfSize = 4;
        int clampMargin = halfSize + 1;

        x = Math.max(mapXMin + clampMargin, x);
        x = Math.min(mapXMax - clampMargin - 1, x);
        y = Math.max(mapYMin + clampMargin, y);
        y = Math.min(mapYMax - clampMargin - 1, y);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        ResourceLocation skin = wawelauth$resolveMapSkin(profile, displayName);
        skin = wawelauth$bindSkin(skin);

        int texWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int texHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (texWidth <= 0 || texHeight <= 0) {
            texWidth = 64;
            texHeight = skin != null && skin.equals(WawelTextureResolver.getLegacyDefaultSkin()) ? 32 : 64;
        }

        boolean legacyLayout = texWidth == texHeight * 2;
        float uScale = texWidth / 64.0F;
        float vScale = texHeight / (legacyLayout ? 32.0F : 64.0F);
        int sampleW = Math.max(1, Math.round(8.0F * uScale));
        int sampleH = Math.max(1, Math.round(8.0F * vScale));

        double centerX = x + 0.5D;
        double centerY = y + 0.5D;

        wawelauth$drawFaceQuad(
            tessellator,
            centerX - halfSize,
            centerY + halfSize,
            centerX + halfSize,
            centerY - halfSize,
            8.0F * uScale,
            8.0F * vScale,
            sampleW,
            sampleH,
            texWidth,
            texHeight);

        float hatU = 40.0F * uScale;
        float hatV = 8.0F * vScale;
        if (hatU + sampleW <= texWidth && hatV + sampleH <= texHeight) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            wawelauth$drawFaceQuad(
                tessellator,
                centerX - halfSize - 0.5D,
                centerY + halfSize + 0.5D,
                centerX + halfSize + 0.5D,
                centerY - halfSize - 0.5D,
                hatU,
                hatV,
                sampleW,
                sampleH,
                texWidth,
                texHeight);
        }

        double relX = x - mouseX;
        double relY = y - mouseY;
        return Math.sqrt(relX * relX + relY * relY);
    }

    @Unique
    private void wawelauth$drawFaceQuad(Tessellator tessellator, double x0, double y1, double x1, double y0, float u,
        float v, int uWidth, int vHeight, int texWidth, int texHeight) {
        double u0 = u / texWidth;
        double u1 = (u + uWidth) / texWidth;
        double v0 = v / texHeight;
        double v1 = (v + vHeight) / texHeight;

        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x0, y1, this.zLevel, u0, v1);
        tessellator.addVertexWithUV(x1, y1, this.zLevel, u1, v1);
        tessellator.addVertexWithUV(x1, y0, this.zLevel, u1, v0);
        tessellator.addVertexWithUV(x0, y0, this.zLevel, u0, v0);
        tessellator.draw();
    }

    @Unique
    private ResourceLocation wawelauth$bindSkin(ResourceLocation skin) {
        Minecraft minecraft = mc;
        ResourceLocation fallback = WawelTextureResolver.getDefaultSkin();
        ResourceLocation bindable = skin == null ? fallback : skin;

        if (minecraft == null) {
            return bindable;
        }

        ITextureObject textureObject = minecraft.getTextureManager()
            .getTexture(bindable);
        if (!SkinTextureState.isReadyForRender(textureObject) && !wawelauth$isDefaultSkin(bindable)) {
            bindable = fallback;
        }

        try {
            minecraft.getTextureManager()
                .bindTexture(bindable);
            return bindable;
        } catch (RuntimeException ignored) {
            minecraft.getTextureManager()
                .bindTexture(fallback);
            return fallback;
        }
    }

    @Unique
    private ResourceLocation wawelauth$resolveMapSkin(GameProfile profile, String displayName) {
        if (profile == null || profile.getId() == null) {
            return WawelTextureResolver.getDefaultSkin();
        }

        UUID profileId = profile.getId();
        String resolvedName = displayName;
        if (resolvedName == null || resolvedName.isEmpty()) {
            resolvedName = profile.getName();
        }

        Minecraft minecraft = mc;
        if (minecraft != null) {
            if (minecraft.thePlayer instanceof AbstractClientPlayer
                && profileId.equals(minecraft.thePlayer.getUniqueID())) {
                return ((AbstractClientPlayer) minecraft.thePlayer).getLocationSkin();
            }

            if (minecraft.theWorld != null) {
                EntityPlayer worldPlayer = minecraft.theWorld.func_152378_a(profileId);
                if (worldPlayer instanceof AbstractClientPlayer) {
                    return ((AbstractClientPlayer) worldPlayer).getLocationSkin();
                }
                if ((resolvedName == null || resolvedName.isEmpty()) && worldPlayer != null) {
                    resolvedName = worldPlayer.getCommandSenderName();
                }
            }
        }

        WawelClient client = WawelClient.instance();
        if (client != null) {
            List<ClientProvider> trusted = client.getSessionBridge()
                .getTrustedProviders();
            if (!trusted.isEmpty()) {
                ResourceLocation trustedSkin = client.getTextureResolver()
                    .getSkinFromAnyProvider(profileId, resolvedName, trusted);
                if (!wawelauth$isDefaultSkin(trustedSkin)) {
                    return trustedSkin;
                }
            }

            ClientProvider provider = client.resolvePlayerProvider(profileId);
            if (provider != null) {
                return client.getTextureResolver()
                    .getSkin(profileId, resolvedName, provider, false);
            }
        }

        return wawelauth$resolveVanillaSkin(profile);
    }

    @Unique
    private ResourceLocation wawelauth$resolveVanillaSkin(GameProfile profile) {
        Minecraft minecraft = mc;
        if (minecraft == null || profile == null) {
            return WawelTextureResolver.getDefaultSkin();
        }

        ResourceLocation skin = WawelTextureResolver.getDefaultSkin();
        try {
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = minecraft.func_152342_ad()
                .func_152788_a(profile);
            MinecraftProfileTexture.Type type = MinecraftProfileTexture.Type.SKIN;
            if (textures.containsKey(type)) {
                skin = minecraft.func_152342_ad()
                    .func_152792_a(textures.get(type), type);
            }
        } catch (RuntimeException ignored) {}
        return skin;
    }

    @Unique
    private static boolean wawelauth$isDefaultSkin(ResourceLocation skin) {
        return skin == null || skin.equals(WawelTextureResolver.getDefaultSkin())
            || skin.equals(WawelTextureResolver.getLegacyDefaultSkin());
    }
}
