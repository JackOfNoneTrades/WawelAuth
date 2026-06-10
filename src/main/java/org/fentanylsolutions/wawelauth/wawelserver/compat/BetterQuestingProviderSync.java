package org.fentanylsolutions.wawelauth.wawelserver.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.ping.PlayerProviderSyncPayload;
import org.fentanylsolutions.wawelauth.wawelserver.LocalSessionVerifier;

import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.NameCache;
import cpw.mods.fml.common.FMLCommonHandler;

public final class BetterQuestingProviderSync {

    private BetterQuestingProviderSync() {}

    public static void syncParty(EntityPlayerMP player, int partyId) {
        IParty party = PartyManager.INSTANCE.getValue(partyId);
        if (party == null) {
            return;
        }

        List<UUID> members = party.getMembers();
        sendProviderSync(player != null ? singletonPlayer(player) : onlinePlayers(members), members);
    }

    public static void syncNames(EntityPlayerMP[] players, UUID[] uuids, String[] names) {
        Set<UUID> ids = collectNameSyncIds(uuids, names);
        sendProviderSync(resolveRecipients(players), ids == null ? null : ids);
    }

    public static void syncParties(EntityPlayerMP[] players, int[] partyIds) {
        if (partyIds != null && partyIds.length == 0) {
            return;
        }
        if (players != null && players.length == 0) {
            return;
        }

        Set<UUID> members = new LinkedHashSet<>();
        for (DBEntry<IParty> entry : partyIds == null ? PartyManager.INSTANCE.getEntries()
            : PartyManager.INSTANCE.bulkLookup(partyIds)) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            members.addAll(
                entry.getValue()
                    .getMembers());
        }

        sendProviderSync(resolveRecipients(players), members);
    }

    private static Set<UUID> collectNameSyncIds(UUID[] uuids, String[] names) {
        if (uuids == null && names == null) {
            return null;
        }

        Set<UUID> ids = new LinkedHashSet<>();
        if (uuids != null) {
            for (UUID uuid : uuids) {
                if (uuid != null) {
                    ids.add(uuid);
                }
            }
        }
        if (names != null) {
            for (String name : names) {
                if (!hasText(name)) {
                    continue;
                }
                UUID uuid = NameCache.INSTANCE.getUUID(name);
                if (uuid != null) {
                    ids.add(uuid);
                }
            }
        }
        return ids;
    }

    private static void sendProviderSync(Collection<EntityPlayerMP> recipients, Collection<UUID> playerIds) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        Map<UUID, String> providers = collectKnownProviders(playerIds);
        if (providers.isEmpty()) {
            return;
        }

        byte[] payload = PlayerProviderSyncPayload.encode(providers);
        int sent = 0;
        for (EntityPlayerMP recipient : recipients) {
            if (recipient == null || recipient.playerNetServerHandler == null) {
                continue;
            }
            try {
                recipient.playerNetServerHandler
                    .sendPacket(new S3FPacketCustomPayload(PlayerProviderSyncPayload.CHANNEL, payload));
                sent++;
            } catch (Exception e) {
                WawelAuth.debug(
                    "[BetterQuesting] Failed to send provider sync to " + recipient.getCommandSenderName()
                        + ": "
                        + e.getMessage());
            }
        }

        if (sent > 0) {
            WawelAuth.debug(
                "[BetterQuesting] Sent provider sync to " + sent
                    + " player(s) with "
                    + providers.size()
                    + " association(s)");
        }
    }

    private static Map<UUID, String> collectKnownProviders(Collection<UUID> playerIds) {
        if (playerIds == null) {
            Map<UUID, String> providers = LocalSessionVerifier.getAllPlayerProviderSessionUrls();
            for (UUID playerId : providers.keySet()) {
                BetterQuestingCommandResolver.rememberProviderBinding(playerId, NameCache.INSTANCE.getName(playerId));
            }
            return providers;
        }

        Map<UUID, String> providers = new LinkedHashMap<>();
        for (UUID playerId : playerIds) {
            String providerSessionUrl = LocalSessionVerifier.getPlayerProviderSessionUrl(playerId);
            if (hasText(providerSessionUrl)) {
                providers.put(playerId, providerSessionUrl);
                BetterQuestingCommandResolver.rememberProviderBinding(playerId, NameCache.INSTANCE.getName(playerId));
            }
        }
        return providers;
    }

    private static Collection<EntityPlayerMP> resolveRecipients(EntityPlayerMP[] players) {
        if (players == null) {
            return allOnlinePlayers();
        }

        List<EntityPlayerMP> recipients = new ArrayList<>();
        for (EntityPlayerMP player : players) {
            if (player != null) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    private static Collection<EntityPlayerMP> singletonPlayer(EntityPlayerMP player) {
        List<EntityPlayerMP> players = new ArrayList<>(1);
        if (player != null) {
            players.add(player);
        }
        return players;
    }

    private static Collection<EntityPlayerMP> onlinePlayers(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<UUID> wanted = new LinkedHashSet<>(playerIds);
        List<EntityPlayerMP> players = new ArrayList<>();
        for (EntityPlayerMP player : allOnlinePlayers()) {
            if (player != null && player.getGameProfile() != null
                && wanted.contains(
                    player.getGameProfile()
                        .getId())) {
                players.add(player);
            }
        }
        return players;
    }

    private static Collection<EntityPlayerMP> allOnlinePlayers() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) {
            return new ArrayList<>();
        }

        List<EntityPlayerMP> players = new ArrayList<>();
        for (Object rawPlayer : server.getConfigurationManager().playerEntityList) {
            if (rawPlayer instanceof EntityPlayerMP) {
                players.add((EntityPlayerMP) rawPlayer);
            }
        }
        return players;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }
}
