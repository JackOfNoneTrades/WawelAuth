package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserList;
import net.minecraft.server.management.UserListWhitelist;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;

import com.mojang.authlib.GameProfile;

public final class ProviderAwareCommandResolver {

    private ProviderAwareCommandResolver() {}

    public static GameProfile resolveProfileForListAdd(String rawInput, ProviderAwareUserListType listType) {
        ResolvedTarget target = resolveCommandTarget(rawInput);
        ProviderAwareUserListManager.storeProviderBinding(listType, target.profile, target.providerKey);
        return target.profile;
    }

    public static GameProfile resolveProfileForListRemove(
        String rawInput,
        UserList list,
        ProviderAwareUserListType listType,
        String listName) {
        GameProfile profile = resolveExistingListTarget(rawInput, list, listType, listName);
        ProviderAwareUserListManager.deleteProviderBinding(listType, profile);
        return profile;
    }

    public static List<String> completeOpAdd(String prefix) {
        ServerConfigurationManager manager = currentManager();
        return completeAddTargets(prefix, profile -> manager == null || !manager.func_152596_g(profile));
    }

    public static List<String> completeWhitelistAdd(String prefix, UserListWhitelist whitelist) {
        return completeAddTargets(
            prefix,
            profile -> ProviderAwareUserListManager.findProfileByUuid(whitelist, profile.getId()) == null);
    }

    public static List<String> completeBanTargets(String prefix) {
        ServerConfigurationManager manager = currentManager();
        UserList bans = manager == null ? null : manager.func_152608_h();
        return completeAddTargets(
            prefix,
            profile -> ProviderAwareUserListManager.findProfileByUuid(bans, profile.getId()) == null);
    }

    public static List<String> completeSavedList(String prefix, UserList list, ProviderAwareUserListType listType) {
        return ProviderAwareUserListManager.completeSavedList(prefix, list, listType);
    }

    private static ResolvedTarget resolveCommandTarget(String rawInput) {
        String input = trimToNull(rawInput);
        if (input == null) {
            throw new CommandException("wawelauth.commands.name_requires_online", "");
        }

        if (FallbackWhitelistLookup.isQualifiedProviderUsername(input)) {
            GameProfile profile = FallbackWhitelistLookup.resolveQualifiedProfile(input);
            if (profile == null || profile.getId() == null) {
                throw new CommandException("wawelauth.commands.qualified_not_found", input);
            }

            String providerKey = FallbackWhitelistLookup.resolveQualifiedProviderKey(input);
            if (providerKey == null) {
                providerKey = ProviderAwareUserListManager.resolveOnlineProviderKey(profile.getId());
            }
            return new ResolvedTarget(profile, providerKey);
        }

        EntityPlayerMP online = ProviderQualifiedPlayerLookup.findOnlinePlayerByName(input);
        if (online == null || online.getGameProfile() == null || online.getGameProfile()
            .getId() == null) {
            throw new CommandException("wawelauth.commands.name_requires_online", input);
        }

        GameProfile profile = online.getGameProfile();
        return new ResolvedTarget(profile, ProviderAwareUserListManager.resolveOnlineProviderKey(profile.getId()));
    }

    private static GameProfile resolveExistingListTarget(
        String rawInput,
        UserList list,
        ProviderAwareUserListType listType,
        String listName) {
        String input = trimToNull(rawInput);
        if (input == null) {
            throw new CommandException("wawelauth.commands.list_entry_missing", listName, "");
        }

        UUID uuid = parseUuid(input);
        if (uuid != null) {
            GameProfile saved = ProviderAwareUserListManager.findProfileByUuid(list, uuid);
            if (saved != null) {
                return saved;
            }
            throw new CommandException("wawelauth.commands.list_entry_missing", listName, input);
        }

        if (FallbackWhitelistLookup.isQualifiedProviderUsername(input)) {
            GameProfile savedByBinding = ProviderAwareUserListManager.findProfileByQualifiedBinding(
                input,
                list,
                listType);
            if (savedByBinding != null) {
                return savedByBinding;
            }

            GameProfile resolved = FallbackWhitelistLookup.resolveQualifiedProfile(input);
            if (resolved == null || resolved.getId() == null) {
                throw new CommandException("wawelauth.commands.qualified_not_found", input);
            }

            GameProfile saved = ProviderAwareUserListManager.findProfileByUuid(list, resolved.getId());
            if (saved != null) {
                return saved;
            }
            throw new CommandException("wawelauth.commands.list_entry_missing", listName, input);
        }

        EntityPlayerMP online = ProviderQualifiedPlayerLookup.findOnlinePlayerByName(input);
        if (online == null || online.getGameProfile() == null || online.getGameProfile()
            .getId() == null) {
            throw new CommandException("wawelauth.commands.name_requires_online", input);
        }

        GameProfile saved = ProviderAwareUserListManager.findProfileByUuid(list, online.getGameProfile()
            .getId());
        if (saved != null) {
            return saved;
        }
        throw new CommandException("wawelauth.commands.list_entry_missing", listName, input);
    }

    private static List<String> completeAddTargets(String prefix, Predicate<GameProfile> onlinePredicate) {
        String safePrefix = prefix == null ? "" : prefix;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        ServerConfigurationManager manager = currentManager();
        if (manager != null) {
            for (Object value : manager.playerEntityList) {
                if (!(value instanceof EntityPlayerMP)) {
                    continue;
                }

                GameProfile profile = ((EntityPlayerMP) value).getGameProfile();
                if (profile == null || profile.getId() == null || profile.getName() == null) {
                    continue;
                }
                if (onlinePredicate != null && !onlinePredicate.test(profile)) {
                    continue;
                }

                addMatching(candidates, safePrefix, commandTarget(profile));
            }
        }

        addProviderSuffixCandidates(candidates, safePrefix);
        return new ArrayList<>(candidates);
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

        for (String providerKey : knownProviderKeys()) {
            addMatching(candidates, prefix, username + "@" + providerKey);
        }
    }

    private static List<String> knownProviderKeys() {
        return ProviderAwareUserListManager.knownProviderKeys();
    }

    private static String commandTarget(GameProfile profile) {
        String providerKey = profile == null ? null : ProviderAwareUserListManager.resolveOnlineProviderKey(
            profile.getId());
        if (providerKey != null) {
            return qualifiedName(profile, providerKey);
        }
        return profile.getName();
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

    private static ServerConfigurationManager currentManager() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? null : server.getConfigurationManager();
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

    private static final class ResolvedTarget {

        private final GameProfile profile;
        private final String providerKey;

        private ResolvedTarget(GameProfile profile, String providerKey) {
            this.profile = profile;
            this.providerKey = providerKey;
        }
    }
}
