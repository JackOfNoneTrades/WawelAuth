package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

import com.mojang.authlib.GameProfile;

public final class ProviderQualifiedPlayerLookup {

    private ProviderQualifiedPlayerLookup() {}

    public static EntityPlayerMP resolveOnlinePlayer(String rawInput) {
        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            return null;
        }

        return findOnlinePlayer(FallbackWhitelistLookup.resolveQualifiedProfile(rawInput));
    }

    public static EntityPlayerMP findOnlinePlayer(GameProfile profile) {
        return profile == null ? null : findOnlinePlayer(profile.getId());
    }

    public static EntityPlayerMP findOnlinePlayer(UUID id) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }

        return findOnlinePlayer(server.getConfigurationManager(), id);
    }

    public static EntityPlayerMP findOnlinePlayer(ServerConfigurationManager manager, UUID id) {
        if (manager == null || id == null) {
            return null;
        }

        for (Object value : manager.playerEntityList) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP player = (EntityPlayerMP) value;
            GameProfile profile = player.getGameProfile();
            if (profile != null && id.equals(profile.getId())) {
                return player;
            }
        }

        return null;
    }
}
