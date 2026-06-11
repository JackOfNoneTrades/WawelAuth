package org.fentanylsolutions.wawelauth.wawelserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserList;
import net.minecraft.server.management.UserListEntry;

import org.fentanylsolutions.fentlib.util.NetUtil;
import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.mixins.early.minecraft.AccessorUserList;
import org.fentanylsolutions.wawelauth.mixins.early.minecraft.AccessorUserListEntry;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.storage.UserListProviderBindingDAO;

import com.mojang.authlib.GameProfile;

public final class ProviderAwareUserListManager {

    private ProviderAwareUserListManager() {}

    public static String resolveProviderKey(ProviderAwareUserListType listType, GameProfile profile,
        boolean persistInferred) {
        return resolveProviderKey(listType, profile, getProviderBindings(listType), persistInferred);
    }

    public static String resolveProviderKey(ProviderAwareUserListType listType, GameProfile profile,
        Map<UUID, String> providerBindings, boolean persistInferred) {
        if (profile == null || profile.getId() == null) {
            return null;
        }

        String storedProviderKey = StringUtil
            .trimToNull(providerBindings == null ? null : providerBindings.get(profile.getId()));
        if (storedProviderKey != null) {
            return storedProviderKey;
        }

        String inferredProviderKey = resolveInferredProviderKey(profile.getId());
        if (inferredProviderKey != null && persistInferred) {
            storeProviderBinding(listType, profile, inferredProviderKey);
        }
        return inferredProviderKey;
    }

    public static String resolveInferredProviderKey(UUID profileUuid) {
        String providerKey = resolveOnlineProviderKey(profileUuid);
        if (providerKey != null) {
            return providerKey;
        }
        return resolveLocalProviderKey(profileUuid);
    }

    public static String resolveKnownProviderKey(UUID profileUuid) {
        String providerKey = resolveInferredProviderKey(profileUuid);
        if (providerKey != null) {
            return providerKey;
        }

        for (ProviderAwareUserListType listType : ProviderAwareUserListType.values()) {
            providerKey = StringUtil.trimToNull(getProviderBindings(listType).get(profileUuid));
            if (providerKey != null) {
                return providerKey;
            }
        }

        return null;
    }

    public static String resolveOnlineProviderKey(UUID profileUuid) {
        if (profileUuid == null || ProviderQualifiedPlayerLookup.findOnlinePlayer(profileUuid) == null) {
            return null;
        }

        String providerName = StringUtil.trimToNull(LocalSessionVerifier.getPlayerProviderName(profileUuid));
        if (providerName != null) {
            return providerName;
        }

        return resolveProviderKeyFromSessionUrl(LocalSessionVerifier.getPlayerProviderSessionUrl(profileUuid));
    }

    public static void storeProviderBinding(ProviderAwareUserListType listType, GameProfile profile,
        String providerKey) {
        String key = StringUtil.trimToNull(providerKey);
        if (listType == null || profile == null || profile.getId() == null || key == null) {
            return;
        }

        UserListProviderBindingDAO dao = getProviderBindingDAO();
        if (dao == null) {
            return;
        }

        try {
            dao.putProviderKey(listType, profile.getId(), key);
        } catch (Exception e) {
            WawelAuth.LOG
                .warn("Failed to store {} provider binding for {}: {}", listType, profile.getId(), e.getMessage());
        }
    }

    public static void deleteProviderBinding(ProviderAwareUserListType listType, GameProfile profile) {
        if (listType == null || profile == null || profile.getId() == null) {
            return;
        }

        UserListProviderBindingDAO dao = getProviderBindingDAO();
        if (dao == null) {
            return;
        }

        try {
            dao.delete(listType, profile.getId());
        } catch (Exception e) {
            WawelAuth.LOG
                .warn("Failed to delete {} provider binding for {}: {}", listType, profile.getId(), e.getMessage());
        }
    }

    public static List<String> completeSavedList(String prefix, UserList list, ProviderAwareUserListType listType) {
        if (list == null) {
            return Collections.emptyList();
        }

        String safePrefix = prefix == null ? "" : prefix;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        Map<UUID, String> providerBindings = getProviderBindings(listType);

        for (GameProfile profile : getSavedProfiles(list)) {
            if (profile.getId() == null) {
                continue;
            }

            String providerKey = resolveProviderKey(listType, profile, providerBindings, false);
            if (providerKey != null) {
                addMatching(candidates, safePrefix, qualifiedName(profile, providerKey));
            } else if (ProviderQualifiedPlayerLookup.findOnlinePlayer(profile.getId()) != null) {
                addMatching(candidates, safePrefix, profile.getName());
            }
        }

        return new ArrayList<>(candidates);
    }

    public static List<String> knownProviderKeys() {
        LinkedHashSet<String> providers = new LinkedHashSet<>();

        ServerConfigurationManager manager = currentManager();
        if (manager != null) {
            for (Object value : manager.playerEntityList) {
                if (!(value instanceof EntityPlayerMP)) {
                    continue;
                }

                GameProfile profile = ((EntityPlayerMP) value).getGameProfile();
                addProviderKey(providers, profile == null ? null : resolveOnlineProviderKey(profile.getId()));
            }

            addKnownProvidersFromList(providers, manager.func_152603_m(), ProviderAwareUserListType.OPS);
            addKnownProvidersFromList(providers, manager.func_152599_k(), ProviderAwareUserListType.WHITELIST);
            addKnownProvidersFromList(providers, manager.func_152608_h(), ProviderAwareUserListType.BANS);
            for (String providerKey : getProviderBindings(ProviderAwareUserListType.FORGE_PLAYERS).values()) {
                addProviderKey(providers, providerKey);
            }
            for (String providerKey : getProviderBindings(ProviderAwareUserListType.BETTER_QUESTING_PLAYERS).values()) {
                addProviderKey(providers, providerKey);
            }
        }

        return new ArrayList<>(providers);
    }

    public static GameProfile findProfileByUuid(UserList list, UUID uuid) {
        if (list == null || uuid == null) {
            return null;
        }

        for (GameProfile profile : getSavedProfiles(list)) {
            if (!uuid.equals(profile.getId())) {
                continue;
            }
            if (list.func_152683_b(profile) != null) {
                return profile;
            }
        }

        return null;
    }

    public static GameProfile findProfileByQualifiedBinding(String rawInput, UserList list,
        ProviderAwareUserListType listType) {
        String username = StringUtil.trimToNull(FallbackWhitelistLookup.getQualifiedUsername(rawInput));
        String providerKey = StringUtil.trimToNull(FallbackWhitelistLookup.resolveQualifiedProviderKey(rawInput));
        if (providerKey == null) {
            providerKey = StringUtil.trimToNull(FallbackWhitelistLookup.getQualifiedProviderName(rawInput));
        }
        if (username == null || providerKey == null) {
            return null;
        }

        Map<UUID, String> providerBindings = getProviderBindings(listType);
        for (GameProfile profile : getSavedProfiles(list)) {
            if (profile.getId() == null || profile.getName() == null
                || !profile.getName()
                    .equalsIgnoreCase(username)) {
                continue;
            }

            String savedProviderKey = resolveProviderKey(listType, profile, providerBindings, false);
            if (savedProviderKey != null && savedProviderKey.equalsIgnoreCase(providerKey)) {
                return profile;
            }
        }

        return null;
    }

    public static List<GameProfile> getSavedProfiles(UserList list) {
        if (list == null) {
            return Collections.emptyList();
        }

        List<GameProfile> profiles = new ArrayList<>();
        for (UserListEntry entry : getEntries(list).values()) {
            if (entry == null) {
                continue;
            }

            Object value = ((AccessorUserListEntry) entry).wawelauth$getValue();
            if (value instanceof GameProfile) {
                GameProfile profile = (GameProfile) value;
                if (profile.getId() != null) {
                    profiles.add(profile);
                }
            }
        }
        return profiles;
    }

    public static Map<UUID, String> getProviderBindings(ProviderAwareUserListType listType) {
        UserListProviderBindingDAO dao = getProviderBindingDAO();
        if (dao == null || listType == null) {
            return Collections.emptyMap();
        }

        try {
            return dao.findAllProviderKeys(listType);
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to read {} provider bindings: {}", listType, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static void addKnownProvidersFromList(LinkedHashSet<String> providers, UserList list,
        ProviderAwareUserListType listType) {
        Map<UUID, String> providerBindings = getProviderBindings(listType);
        for (GameProfile profile : getSavedProfiles(list)) {
            String providerKey = profile.getId() == null ? null
                : resolveProviderKey(listType, profile, providerBindings, false);
            addProviderKey(providers, providerKey);
        }
    }

    private static void addProviderKey(LinkedHashSet<String> providers, String providerKey) {
        String key = StringUtil.trimToNull(providerKey);
        if (key != null) {
            providers.add(key);
        }
    }

    private static String resolveLocalProviderKey(UUID profileUuid) {
        if (profileUuid == null) {
            return null;
        }

        WawelServer server = WawelServer.instance();
        if (server == null || server.getProfileDAO() == null) {
            return null;
        }

        try {
            WawelProfile profile = server.getProfileDAO()
                .findByUuid(profileUuid);
            return profile == null ? null : "local";
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to check local profile provider for {}: {}", profileUuid, e.getMessage());
            return null;
        }
    }

    private static String resolveProviderKeyFromSessionUrl(String providerSessionUrl) {
        String normalizedSessionUrl = NetUtil.normalizeHttpUrl(providerSessionUrl);
        if (normalizedSessionUrl == null) {
            return null;
        }

        ServerConfig config = Config.server();
        if (config == null) {
            return null;
        }

        for (FallbackServer fallback : config.getFallbackServers()) {
            if (fallback == null) {
                continue;
            }

            String fallbackSessionUrl = NetUtil.normalizeHttpUrl(fallback.getSessionServerUrl());
            String fallbackName = StringUtil.trimToNull(fallback.getName());
            if (fallbackSessionUrl != null && fallbackName != null
                && fallbackSessionUrl.equalsIgnoreCase(normalizedSessionUrl)) {
                return fallbackName;
            }
        }

        return null;
    }

    private static Map<String, UserListEntry> getEntries(UserList list) {
        return ((AccessorUserList) list).wawelauth$getEntries();
    }

    private static ServerConfigurationManager currentManager() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? null : server.getConfigurationManager();
    }

    private static UserListProviderBindingDAO getProviderBindingDAO() {
        WawelServer server = WawelServer.instance();
        return server == null ? null : server.getUserListProviderBindingDAO();
    }

    private static String qualifiedName(GameProfile profile, String providerKey) {
        String name = StringUtil.trimToNull(profile == null ? null : profile.getName());
        String provider = StringUtil.trimToNull(providerKey);
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
}
