package org.fentanylsolutions.wawelauth.wawelserver.compat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareUserListManager;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;

import com.mojang.authlib.GameProfile;

import betterquesting.api.api.QuestingAPI;
import betterquesting.storage.NameCache;

public final class BetterQuestingCommandResolver {

    private static final ProviderAwareUserListType LIST_TYPE = ProviderAwareUserListType.BETTER_QUESTING_PLAYERS;

    private BetterQuestingCommandResolver() {}

    public static UUID resolvePlayerId(ICommandSender sender, String rawInput) {
        String input = trimToNull(rawInput);
        if (input == null) {
            throw new CommandException("wawelauth.commands.name_requires_online", "");
        }

        UUID uuid = parseUuid(input);
        if (uuid != null) {
            return uuid;
        }

        if (input.startsWith("@")) {
            return resolveSelector(sender, input);
        }

        if (FallbackWhitelistLookup.isQualifiedProviderUsername(input)) {
            return resolveQualifiedPlayerId(input);
        }

        EntityPlayerMP online = ProviderQualifiedPlayerLookup.findOnlinePlayerByName(input);
        if (online == null || online.getGameProfile() == null || online.getGameProfile()
            .getId() == null) {
            throw new CommandException("wawelauth.commands.name_requires_online", input);
        }

        rememberOnlineProvider(online);
        return QuestingAPI.getQuestingUUID(online);
    }

    public static List<String> completePlayerTargets(String prefix) {
        String safePrefix = prefix == null ? "" : prefix;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        addOnlineCandidates(candidates, safePrefix);
        addCachedNameCandidates(candidates, safePrefix);
        addProviderSuffixCandidates(candidates, safePrefix);

        return new ArrayList<>(candidates);
    }

    public static EntityPlayerMP resolveOnlinePartyActionPlayer(String rawInput) {
        String input = trimToNull(rawInput);
        if (input == null) {
            return null;
        }

        EntityPlayerMP player = FallbackWhitelistLookup.isQualifiedProviderUsername(input)
            ? ProviderQualifiedPlayerLookup.resolveOnlinePlayer(input)
            : ProviderQualifiedPlayerLookup.findOnlinePlayerByName(input);
        rememberOnlineProvider(player);
        return player;
    }

    public static UUID resolvePartyActionPlayerId(String rawInput) {
        String input = trimToNull(rawInput);
        if (input == null) {
            return null;
        }

        UUID uuid = parseUuid(input);
        if (uuid != null) {
            return uuid;
        }

        EntityPlayerMP online = resolveOnlinePartyActionPlayer(input);
        if (online != null) {
            return QuestingAPI.getQuestingUUID(online);
        }

        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(input)) {
            return null;
        }

        try {
            return resolveQualifiedPlayerId(input);
        } catch (CommandException ignored) {
            return null;
        }
    }

    public static UUID resolvePartyMemberActionPlayerId(String rawInput) {
        String input = trimToNull(rawInput);
        if (input == null) {
            return null;
        }

        UUID uuid = parseUuid(input);
        if (uuid != null) {
            return uuid;
        }

        EntityPlayerMP online = resolveOnlinePartyActionPlayer(input);
        if (online != null) {
            return QuestingAPI.getQuestingUUID(online);
        }

        if (FallbackWhitelistLookup.isQualifiedProviderUsername(input)) {
            try {
                return resolveQualifiedPlayerId(input);
            } catch (CommandException ignored) {
                return null;
            }
        }

        return NameCache.INSTANCE.getUUID(input);
    }

    public static void rememberOnlineProvider(EntityPlayerMP player) {
        if (player == null || player.getGameProfile() == null || player.getGameProfile()
            .getId() == null) {
            return;
        }

        String providerKey = ProviderAwareUserListManager.resolveOnlineProviderKey(player.getGameProfile()
            .getId());
        if (providerKey != null) {
            ProviderAwareUserListManager.storeProviderBinding(LIST_TYPE, player.getGameProfile(), providerKey);
        }
    }

    public static void rememberProviderBinding(UUID playerId, String playerName) {
        if (playerId == null) {
            return;
        }

        String providerKey = ProviderAwareUserListManager.resolveOnlineProviderKey(playerId);
        if (providerKey == null) {
            return;
        }

        ProviderAwareUserListManager.storeProviderBinding(
            LIST_TYPE,
            new GameProfile(playerId, trimToNull(playerName)),
            providerKey);
    }

    private static UUID resolveSelector(ICommandSender sender, String input) {
        try {
            EntityPlayerMP player = CommandBase.getPlayer(sender, input);
            if (player != null) {
                rememberOnlineProvider(player);
                return QuestingAPI.getQuestingUUID(player);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static UUID resolveQualifiedPlayerId(String input) {
        EntityPlayerMP online = ProviderQualifiedPlayerLookup.resolveOnlinePlayer(input);
        if (online != null && online.getGameProfile() != null && online.getGameProfile()
            .getId() != null) {
            rememberOnlineProvider(online);
            return QuestingAPI.getQuestingUUID(online);
        }

        GameProfile profile = FallbackWhitelistLookup.resolveQualifiedProfile(input);
        if (profile == null || profile.getId() == null) {
            throw new CommandException("wawelauth.commands.qualified_not_found", input);
        }

        String providerKey = FallbackWhitelistLookup.resolveQualifiedProviderKey(input);
        if (providerKey == null) {
            providerKey = ProviderAwareUserListManager.resolveInferredProviderKey(profile.getId());
        }
        ProviderAwareUserListManager.storeProviderBinding(LIST_TYPE, profile, providerKey);
        return profile.getId();
    }

    private static void addOnlineCandidates(LinkedHashSet<String> candidates, String prefix) {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }

        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP player = (EntityPlayerMP) value;
            GameProfile profile = player.getGameProfile();
            if (profile == null || profile.getId() == null || profile.getName() == null) {
                continue;
            }

            addMatching(candidates, prefix, commandTarget(profile));
        }
    }

    private static void addCachedNameCandidates(LinkedHashSet<String> candidates, String prefix) {
        for (String name : NameCache.INSTANCE.getAllNames()) {
            String playerName = trimToNull(name);
            if (playerName == null) {
                continue;
            }

            UUID playerId = NameCache.INSTANCE.getUUID(playerName);
            if (playerId == null) {
                continue;
            }

            GameProfile profile = new GameProfile(playerId, playerName);
            String providerKey = ProviderAwareUserListManager.resolveProviderKey(LIST_TYPE, profile, false);
            if (providerKey == null) {
                providerKey = ProviderAwareUserListManager.resolveKnownProviderKey(playerId);
                ProviderAwareUserListManager.storeProviderBinding(LIST_TYPE, profile, providerKey);
            }
            if (providerKey != null) {
                addMatching(candidates, prefix, qualifiedName(profile, providerKey));
            }
        }
    }

    private static void addProviderSuffixCandidates(LinkedHashSet<String> candidates, String prefix) {
        int at = prefix.lastIndexOf('@');
        if (at <= 0) {
            return;
        }

        String username = trimToNull(prefix.substring(0, at));
        if (username == null) {
            return;
        }

        for (String providerKey : ProviderAwareUserListManager.knownProviderKeys()) {
            addMatching(candidates, prefix, username + "@" + providerKey);
        }
    }

    private static String commandTarget(GameProfile profile) {
        String providerKey = ProviderAwareUserListManager.resolveOnlineProviderKey(profile.getId());
        if (providerKey != null) {
            return qualifiedName(profile, providerKey);
        }
        return null;
    }

    private static String qualifiedName(GameProfile profile, String providerKey) {
        String name = trimToNull(profile == null ? null : profile.getName());
        String provider = trimToNull(providerKey);
        if (name == null || provider == null) {
            return null;
        }
        return name + "@" + provider;
    }

    private static void addMatching(LinkedHashSet<String> candidates, String prefix, String candidate) {
        if (candidate != null && CommandBase.doesStringStartWith(prefix, candidate)) {
            candidates.add(candidate);
        }
    }

    private static UUID parseUuid(String rawInput) {
        try {
            return UUID.fromString(rawInput);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return StringUtil.trimToNull(value);
    }
}
