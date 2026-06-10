package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "serverutils.client.tab.TabSkinCache", priority = 999, remap = false)
public abstract class MixinTabSkinCache {

    @Inject(method = "getOrLoadSkin", at = @At("HEAD"), cancellable = true, remap = false)
    private void wawelauth$resolveTabSkin(String playerName, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = wawelauth$resolveSkin(playerName);
        if (skin != null && !skin.equals(WawelTextureResolver.getDefaultSkin())
            && !skin.equals(WawelTextureResolver.getLegacyDefaultSkin())) {
            cir.setReturnValue(skin);
        }
    }

    @Unique
    private static ResourceLocation wawelauth$resolveSkin(String playerName) {
        GameProfile profile = wawelauth$resolveProfile(playerName);
        if (profile == null || profile.getId() == null) {
            return null;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            return null;
        }

        List<ClientProvider> providers = client.resolvePlayerProviderCandidates(profile.getId());
        if (providers.isEmpty()) {
            return null;
        }

        return client.getTextureResolver()
            .getSkinFromAnyProvider(profile.getId(), profile.getName(), providers);
    }

    @Unique
    private static GameProfile wawelauth$resolveProfile(String playerName) {
        String normalizedName = wawelauth$normalizeName(playerName);
        if (normalizedName == null) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return null;
        }

        if (mc.theWorld != null) {
            EntityPlayer player = mc.theWorld.getPlayerEntityByName(normalizedName);
            if (player == null) {
                player = wawelauth$findWorldPlayerByProfileName(normalizedName);
            }
            if (player != null) {
                GameProfile profile = player.getGameProfile();
                if (profile != null && profile.getId() != null) {
                    return profile;
                }
                UUID uuid = player.getUniqueID();
                if (uuid != null) {
                    return new GameProfile(uuid, player.getCommandSenderName());
                }
            }
        }

        GameProfile localProfile = wawelauth$getLocalProfile(mc);
        if (localProfile != null && normalizedName.equals(localProfile.getName())) {
            return localProfile;
        }

        return null;
    }

    @Unique
    private static EntityPlayer wawelauth$findWorldPlayerByProfileName(String playerName) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return null;
        }

        for (Object entry : mc.theWorld.playerEntities) {
            if (!(entry instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entry;
            GameProfile profile = player.getGameProfile();
            if (profile != null && playerName.equals(profile.getName())) {
                return player;
            }
        }

        return null;
    }

    @Unique
    private static GameProfile wawelauth$getLocalProfile(Minecraft mc) {
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

    @Unique
    private static String wawelauth$normalizeName(String playerName) {
        if (playerName == null) {
            return null;
        }
        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(playerName)
            .trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
