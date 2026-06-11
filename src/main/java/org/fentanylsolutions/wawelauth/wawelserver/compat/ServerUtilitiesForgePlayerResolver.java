package org.fentanylsolutions.wawelauth.wawelserver.compat;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.ProviderAwareUserListType;
import org.fentanylsolutions.wawelauth.wawelserver.FallbackWhitelistLookup;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderAwareUserListManager;
import org.fentanylsolutions.wawelauth.wawelserver.ProviderQualifiedPlayerLookup;

import com.mojang.authlib.GameProfile;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;
import serverutils.lib.util.ServerUtils;

public final class ServerUtilitiesForgePlayerResolver {

    private static final ProviderAwareUserListType LIST_TYPE = ProviderAwareUserListType.FORGE_PLAYERS;

    private ServerUtilitiesForgePlayerResolver() {}

    public static String serializePlayerKey(ForgePlayer player) {
        if (player == null || player.getId() == null) {
            return "";
        }

        ProviderAwareUserListManager.resolveProviderKey(LIST_TYPE, player.getProfile(), true);
        return player.getId()
            .toString();
    }

    public static ForgePlayer resolveCommandPlayer(Universe universe, CharSequence rawInput) {
        String input = StringUtil.trimToNull(rawInput == null ? null : rawInput.toString());
        if (universe == null || input == null) {
            return null;
        }

        ForgePlayer special = resolveUuidOrFake(universe, input);
        if (special != null) {
            return special;
        }

        ForgePlayer qualified = resolveQualifiedPlayer(universe, input);
        if (qualified != null) {
            return qualified;
        }

        EntityPlayerMP online = ProviderQualifiedPlayerLookup.findOnlinePlayerByName(input);
        if (online == null) {
            return null;
        }

        ForgePlayer player = universe.getPlayer(online.getUniqueID());
        rememberOnlineProvider(player);
        return player;
    }

    public static ForgePlayer resolveStoredPlayer(Universe universe, CharSequence rawInput) {
        String input = StringUtil.trimToNull(rawInput == null ? null : rawInput.toString());
        if (universe == null || input == null) {
            return null;
        }

        ForgePlayer special = resolveUuidOrFake(universe, input);
        if (special != null) {
            return special;
        }

        ForgePlayer qualified = resolveQualifiedPlayer(universe, input);
        if (qualified != null) {
            return qualified;
        }

        return findStoredPlayerByExactName(universe, input);
    }

    private static ForgePlayer resolveUuidOrFake(Universe universe, String input) {
        UUID id = serverutils.lib.util.StringUtils.fromString(input);
        if (id != null) {
            return universe.getPlayer(id);
        }

        String fakeName = ServerUtils.FAKE_PLAYER_PROFILE.getName();
        if (fakeName != null && fakeName.equalsIgnoreCase(input)) {
            return universe.getPlayer(ServerUtils.FAKE_PLAYER_PROFILE.getId());
        }

        return null;
    }

    private static ForgePlayer resolveQualifiedPlayer(Universe universe, String input) {
        if (!FallbackWhitelistLookup.isQualifiedProviderUsername(input)) {
            return null;
        }

        GameProfile profile = FallbackWhitelistLookup.resolveQualifiedProfile(input);
        if (profile == null || profile.getId() == null) {
            return null;
        }

        ForgePlayer player = universe.getPlayer(profile.getId());
        String providerKey = FallbackWhitelistLookup.resolveQualifiedProviderKey(input);
        if (player != null) {
            ProviderAwareUserListManager.storeProviderBinding(LIST_TYPE, player.getProfile(), providerKey);
        }
        return player;
    }

    private static ForgePlayer findStoredPlayerByExactName(Universe universe, String input) {
        ForgePlayer match = null;
        for (ForgePlayer player : universe.getPlayers()) {
            String name = player == null ? null : player.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                if (match != null) {
                    // Same name under multiple providers, do not guess.
                    return null;
                }
                match = player;
            }
        }

        if (match != null) {
            ProviderAwareUserListManager.resolveProviderKey(LIST_TYPE, match.getProfile(), true);
        }
        return match;
    }

    private static void rememberOnlineProvider(ForgePlayer player) {
        if (player == null || player.getId() == null) {
            return;
        }

        String providerKey = ProviderAwareUserListManager.resolveOnlineProviderKey(player.getId());
        ProviderAwareUserListManager.storeProviderBinding(LIST_TYPE, player.getProfile(), providerKey);
    }
}
