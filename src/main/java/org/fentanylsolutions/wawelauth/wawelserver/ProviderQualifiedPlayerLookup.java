package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.fentlib.util.StringUtil;

import com.mojang.authlib.GameProfile;

public final class ProviderQualifiedPlayerLookup {

    private ProviderQualifiedPlayerLookup() {}

    public static EntityPlayerMP resolveOnlinePlayer(String rawInput) {
        if (FallbackWhitelistLookup.isQualifiedProviderUsername(rawInput)) {
            return findOnlinePlayer(FallbackWhitelistLookup.resolveQualifiedProfile(rawInput));
        }

        return findOnlinePlayerByName(rawInput);
    }

    public static EntityPlayerMP findOnlinePlayerByName(String rawInput) {
        String username = StringUtil.trimToNull(rawInput);
        if (username == null) {
            return null;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }

        return server.getConfigurationManager()
            .func_152612_a(username);
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
