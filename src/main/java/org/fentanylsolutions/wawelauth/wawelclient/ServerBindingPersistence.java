package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelPingPayload;

/**
 * Persistence and cleanup helpers for per-server WawelAuth account bindings.
 */
public final class ServerBindingPersistence {

    private static final int MAX_SERVER_LIST_BACKUPS = 10;
    private static final String SERVER_LIST_BACKUP_DIR = "backups";
    private static final String SERVER_LIST_BACKUP_PREFIX = "servers.dat.wawelauth-";
    private static final String SERVER_LIST_BACKUP_SUFFIX = ".bak";

    private static volatile WeakReference<ServerList> activeServerListRef = new WeakReference<>(null);

    private ServerBindingPersistence() {}

    /**
     * Register the currently active multiplayer server list so in-memory rows
     * can be healed immediately when accounts/providers are removed.
     */
    public static void setActiveServerList(ServerList serverList) {
        activeServerListRef = new WeakReference<>(serverList);
    }

    /**
     * Persist one modified ServerData entry to servers.dat.
     */
    public static void persistServerSelection(ServerData selected) {
        if (selected == null) {
            return;
        }

        if (persistInActiveServerList(selected)) {
            return;
        }

        try {
            // Fallback for non-multiplayer contexts where no active list was registered.
            ServerList serverList = new ServerList(Minecraft.getMinecraft());
            serverList.loadServerList();
            if (!persistInServerList(serverList, selected, true)) {
                WawelAuth.debug("No saved server entry matched per-server account selection for " + selected.serverIP);
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to persist per-server account selection: {}", e.getMessage());
        }
    }

    /**
     * If the selected account does not exist anymore, clear this server binding
     * and persist the change.
     *
     * @return true if a stale binding was removed
     */
    public static boolean clearMissingBinding(ServerData serverData, AccountManager accountManager) {
        if (serverData == null || accountManager == null) return false;
        IServerDataExt ext = (IServerDataExt) serverData;
        long accountId = ext.getWawelAccountId();
        if (accountId < 0) return false;

        if (accountManager.getAccount(accountId) != null) {
            return false;
        }

        ext.setWawelAccountId(-1L);
        ext.setWawelProviderName(null);
        persistServerSelection(serverData);
        return true;
    }

    /**
     * Remember the current server address as the origin for this binding/local
     * auth identity.
     */
    public static void markServerBindingOrigin(ServerData serverData) {
        if (!(serverData instanceof IServerDataExt)) {
            return;
        }

        IServerDataExt ext = (IServerDataExt) serverData;
        if (!hasTrackedBinding(ext)) {
            ext.setWawelOriginalServerIp(null);
            return;
        }

        ext.setWawelOriginalServerIp(normalizeAddress(serverData.serverIP));
    }

    /**
     * Effective local-auth metadata for one server entry: live ping/join
     * capabilities if available, otherwise the persisted local-auth snapshot.
     */
    public static ServerCapabilities getEffectiveLocalAuthCapabilities(ServerData serverData) {
        if (!(serverData instanceof IServerDataExt)) {
            return ServerCapabilities.empty();
        }

        IServerDataExt ext = (IServerDataExt) serverData;
        ServerCapabilities live = ext.getWawelCapabilities();
        if (live != null && live.isLocalAuthSupported()) {
            return live;
        }

        return ServerCapabilities.persistedLocalAuth(
            ext.getWawelLocalAuthApiRoot(),
            ext.getWawelLocalAuthFingerprint(),
            ext.getWawelLocalAuthPublicKeyBase64());
    }

    /**
     * Remove all server bindings whose account IDs do not exist anymore.
     *
     * @return number of bindings removed
     */
    public static int clearMissingAccountBindings(AccountManager accountManager) {
        if (accountManager == null) return 0;

        List<ClientAccount> accounts = accountManager.listAccounts();
        Set<Long> validIds = new HashSet<>();
        for (ClientAccount account : accounts) {
            validIds.add(account.getId());
        }

        try {
            ServerList serverList = new ServerList(Minecraft.getMinecraft());
            serverList.loadServerList();

            int removed = clearMissingInServerList(serverList, validIds, true);

            ServerList active = activeServerListRef.get();
            if (active != null && active != serverList) {
                removed += clearMissingInServerList(active, validIds, true);
            }
            return removed;
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to clean stale server bindings: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Persist or clear the remembered local-auth fingerprint for one server
     * entry based on the latest advertised capabilities.
     */
    public static void persistLocalAuthMetadata(ServerData serverData, ServerCapabilities capabilities) {
        if (!(serverData instanceof IServerDataExt)) {
            return;
        }

        IServerDataExt ext = (IServerDataExt) serverData;
        String current = normalize(ext.getWawelLocalAuthFingerprint());
        String currentApiRoot = WawelPingPayload.normalizeUrl(ext.getWawelLocalAuthApiRoot());
        String currentPublicKeyBase64 = normalizeRaw(ext.getWawelLocalAuthPublicKeyBase64());
        String currentOrigin = normalizeAddress(ext.getWawelOriginalServerIp());
        String currentAddress = normalizeAddress(serverData.serverIP);
        String next = null;
        String nextApiRoot = currentApiRoot;
        String nextPublicKeyBase64 = currentPublicKeyBase64;

        if (capabilities != null && capabilities.isWawelAuthAdvertised() && capabilities.isLocalAuthSupported()) {
            next = normalize(capabilities.getLocalAuthPublicKeyFingerprint());
            nextApiRoot = WawelPingPayload.normalizeUrl(capabilities.getLocalAuthApiRoot());
            nextPublicKeyBase64 = normalizeRaw(capabilities.getLocalAuthPublicKeyBase64());
        } else if (capabilities != null && capabilities.isWawelAuthAdvertised()) {
            next = null;
            nextApiRoot = null;
            nextPublicKeyBase64 = null;
        } else {
            return;
        }

        String nextOrigin = currentOrigin;
        if (next != null) {
            if (currentOrigin == null || equalsNullable(currentOrigin, currentAddress)) {
                nextOrigin = currentAddress;
            }
        } else if (!hasTrackedBinding(ext)) {
            nextOrigin = null;
        }

        if (equalsNullable(current, next) && equalsNullable(currentApiRoot, nextApiRoot)
            && equalsNullable(currentPublicKeyBase64, nextPublicKeyBase64)
            && equalsNullable(currentOrigin, nextOrigin)) {
            return;
        }

        ext.setWawelLocalAuthFingerprint(next);
        ext.setWawelLocalAuthApiRoot(nextApiRoot);
        ext.setWawelLocalAuthPublicKeyBase64(nextPublicKeyBase64);
        ext.setWawelOriginalServerIp(nextOrigin);
        persistServerSelection(serverData);
    }

    /**
     * Clear bindings for entries whose current address no longer matches the
     * address they were originally bound against.
     *
     * @return number of entries cleared
     */
    public static int clearRetargetedServerBindings(WawelClient client) {
        try {
            ServerList serverList = new ServerList(Minecraft.getMinecraft());
            serverList.loadServerList();

            int cleared = clearRetargetedInServerList(serverList, client, true);

            ServerList active = activeServerListRef.get();
            if (active != null && active != serverList) {
                cleared += clearRetargetedInServerList(active, client, true);
            }
            return cleared;
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to clean retargeted server bindings: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Remove managed local-auth providers whose fingerprint/provider identity
     * is no longer referenced by any saved server entry.
     *
     * Not wired to any automatic trigger: "referenced" is evaluated against the
     * current instance's servers.dat, but the provider registry can be shared
     * across instances (e.g. symlinked config dir). Opening this instance with
     * a fresh server list would happily nuke providers another instance still
     * uses. Retained for an explicit user-driven path only.
     *
     * @return number of providers removed
     */
    public static int clearOrphanedLocalProviders(WawelClient client) {
        if (client == null) return 0;

        try {
            ServerList serverList = new ServerList(Minecraft.getMinecraft());
            serverList.loadServerList();

            ReferencedLocalAuths referenced = new ReferencedLocalAuths();
            collectReferencedLocalAuths(serverList, referenced);

            ServerList active = activeServerListRef.get();
            if (active != null && active != serverList) {
                collectReferencedLocalAuths(active, referenced);
            }

            List<ClientProvider> providers = new ArrayList<>(
                client.getProviderRegistry()
                    .listProviders());
            int removed = 0;
            for (ClientProvider provider : providers) {
                if (!isManagedLocalProvider(provider)) {
                    continue;
                }
                if (referenced.contains(provider)) {
                    continue;
                }

                client.getProviderRegistry()
                    .removeProvider(provider.getName());
                removed++;
            }
            return removed;
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to clean orphaned local auth providers: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Look for a managed local provider that was referenced by the just-removed
     * server entry and is now referenced by no other entry in the live server
     * list. Used to ask the user whether to also delete the provider.
     *
     * Scope is point-in-time and local to this instance only; the caller is
     * expected to surface the cross-instance caveat in the prompt.
     */
    public static ClientProvider findOrphanCandidateFromDeletedEntry(WawelClient client, ServerData deletedEntry) {
        if (client == null || !(deletedEntry instanceof IServerDataExt)) {
            return null;
        }
        IServerDataExt deletedExt = (IServerDataExt) deletedEntry;
        String deletedProviderName = normalize(deletedExt.getWawelProviderName());
        String deletedFingerprint = normalize(deletedExt.getWawelLocalAuthFingerprint());
        if (deletedProviderName == null && deletedFingerprint == null) {
            return null;
        }

        try {
            ServerList serverList = new ServerList(Minecraft.getMinecraft());
            serverList.loadServerList();

            ReferencedLocalAuths referenced = new ReferencedLocalAuths();
            collectReferencedLocalAuths(serverList, referenced);

            ServerList active = activeServerListRef.get();
            if (active != null && active != serverList) {
                collectReferencedLocalAuths(active, referenced);
            }

            for (ClientProvider provider : client.getProviderRegistry()
                .listProviders()) {
                if (!isManagedLocalProvider(provider)) {
                    continue;
                }
                String providerName = normalize(provider.getName());
                String fingerprint = normalize(provider.getPublicKeyFingerprint());
                boolean matchesDeleted = (deletedProviderName != null && deletedProviderName.equals(providerName))
                    || (deletedFingerprint != null && deletedFingerprint.equals(fingerprint));
                if (!matchesDeleted) {
                    continue;
                }
                if (referenced.contains(provider)) {
                    continue;
                }
                return provider;
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to detect orphaned local provider after delete: {}", e.getMessage());
        }
        return null;
    }

    private static boolean sameServer(ServerData a, ServerData b) {
        if (a == null || b == null) return false;
        if (a.serverIP == null || b.serverIP == null) return false;
        return a.serverIP.equals(b.serverIP);
    }

    private static boolean persistInActiveServerList(ServerData selected) {
        ServerList active = activeServerListRef.get();
        if (active == null) {
            return false;
        }

        try {
            return persistInServerList(active, selected, true);
        } catch (Throwable t) {
            WawelAuth.debug("Active server list save failed, using fallback save path: " + t.getMessage());
            return false;
        }
    }

    private static boolean persistInServerList(ServerList serverList, ServerData selected, boolean save) {
        if (serverList == null || selected == null) {
            return false;
        }

        for (int i = 0; i < serverList.countServers(); i++) {
            ServerData existing = serverList.getServerData(i);
            if (existing == selected || sameServer(existing, selected)) {
                serverList.func_147413_a(i, selected); // ServerList.setServer
                if (save) {
                    saveServerListWithBackup(serverList);
                }
                return true;
            }
        }

        return false;
    }

    private static int clearMissingInServerList(ServerList serverList, Set<Long> validIds, boolean save) {
        int removed = 0;
        for (int i = 0; i < serverList.countServers(); i++) {
            ServerData serverData = serverList.getServerData(i);
            if (!(serverData instanceof IServerDataExt)) continue;

            IServerDataExt ext = (IServerDataExt) serverData;
            long accountId = ext.getWawelAccountId();
            if (accountId >= 0 && !validIds.contains(accountId)) {
                ext.setWawelAccountId(-1L);
                ext.setWawelProviderName(null);
                serverList.func_147413_a(i, serverData); // ServerList.setServer
                removed++;
            }
        }

        if (save && removed > 0) {
            saveServerListWithBackup(serverList);
        }
        return removed;
    }

    private static int clearRetargetedInServerList(ServerList serverList, WawelClient client, boolean save) {
        int cleared = 0;
        for (int i = 0; i < serverList.countServers(); i++) {
            ServerData serverData = serverList.getServerData(i);
            if (!(serverData instanceof IServerDataExt)) {
                continue;
            }

            IServerDataExt ext = (IServerDataExt) serverData;
            if (ext.getWawelOriginalServerIp() == null && hasTrackedBinding(ext)) {
                if (isLegacyRetargetedLocalEntry(serverData, client)) {
                    clearBindingState(serverData, true);
                    serverList.func_147413_a(i, serverData); // ServerList.setServer
                    cleared++;
                    continue;
                }
                ext.setWawelOriginalServerIp(normalizeAddress(serverData.serverIP));
                serverList.func_147413_a(i, serverData); // ServerList.setServer
                cleared++;
                continue;
            }

            if (!isRetargetedServerEntry(serverData)) {
                continue;
            }

            clearBindingState(serverData, true);
            serverList.func_147413_a(i, serverData); // ServerList.setServer
            cleared++;
        }

        if (save && cleared > 0) {
            saveServerListWithBackup(serverList);
        }
        return cleared;
    }

    private static void saveServerListWithBackup(ServerList serverList) {
        backupServerListFile();
        serverList.saveServerList();
    }

    private static void backupServerListFile() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.mcDataDir == null) {
                return;
            }

            File source = new File(minecraft.mcDataDir, "servers.dat");
            if (!source.isFile() || source.length() <= 19L) {
                return;
            }

            File backupDir = resolveServerListBackupDir(minecraft);
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                WawelAuth.debug("Failed to create servers.dat backup directory: " + backupDir.getAbsolutePath());
                return;
            }

            File backup = nextServerListBackupFile(backupDir);
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            pruneServerListBackups(backupDir);
        } catch (IOException e) {
            WawelAuth.debug("Failed to back up servers.dat before save: " + e.getMessage());
        } catch (Throwable t) {
            WawelAuth.debug("Failed to back up servers.dat before save: " + t.getMessage());
        }
    }

    private static File resolveServerListBackupDir(Minecraft minecraft) {
        File localConfigDir = Config.getLocalConfigDir();
        if (localConfigDir != null) {
            return new File(localConfigDir, SERVER_LIST_BACKUP_DIR);
        }

        File minecraftConfigRoot = Config.getMinecraftConfigRoot();
        if (minecraftConfigRoot != null) {
            return new File(new File(minecraftConfigRoot, "wawelauth"), SERVER_LIST_BACKUP_DIR);
        }

        return new File(new File(new File(minecraft.mcDataDir, "config"), "wawelauth"), SERVER_LIST_BACKUP_DIR);
    }

    private static File nextServerListBackupFile(File backupDir) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        File backup = new File(backupDir, SERVER_LIST_BACKUP_PREFIX + timestamp + SERVER_LIST_BACKUP_SUFFIX);
        for (int i = 1; backup.exists(); i++) {
            backup = new File(backupDir, SERVER_LIST_BACKUP_PREFIX + timestamp + "-" + i + SERVER_LIST_BACKUP_SUFFIX);
        }
        return backup;
    }

    private static void pruneServerListBackups(File backupDir) {
        File[] backups = backupDir.listFiles(
            file -> file.isFile() && file.getName()
                .startsWith(SERVER_LIST_BACKUP_PREFIX)
                && file.getName()
                    .endsWith(SERVER_LIST_BACKUP_SUFFIX));
        if (backups == null || backups.length <= MAX_SERVER_LIST_BACKUPS) {
            return;
        }

        Arrays.sort(
            backups,
            Comparator.comparing(File::getName)
                .reversed());

        for (int i = MAX_SERVER_LIST_BACKUPS; i < backups.length; i++) {
            if (!backups[i].delete()) {
                WawelAuth.debug("Failed to delete old servers.dat backup: " + backups[i].getAbsolutePath());
            }
        }
    }

    private static void collectReferencedLocalAuths(ServerList serverList, ReferencedLocalAuths referenced) {
        if (serverList == null || referenced == null) {
            return;
        }

        for (int i = 0; i < serverList.countServers(); i++) {
            ServerData serverData = serverList.getServerData(i);
            if (!(serverData instanceof IServerDataExt)) {
                continue;
            }

            IServerDataExt ext = (IServerDataExt) serverData;
            if (isRetargetedServerEntry(serverData)) {
                continue;
            }
            referenced.providerNames.add(normalize(ext.getWawelProviderName()));
            referenced.fingerprints.add(normalize(ext.getWawelLocalAuthFingerprint()));
        }
    }

    public static boolean isRetargetedServerAddress(ServerData serverData, String candidateServerIp) {
        if (!(serverData instanceof IServerDataExt)) {
            return false;
        }

        String original = normalizeAddress(((IServerDataExt) serverData).getWawelOriginalServerIp());
        if (original == null) {
            return false;
        }

        return !equalsNullable(original, normalizeAddress(candidateServerIp));
    }

    public static boolean isRetargetedServerEntry(ServerData serverData) {
        return isRetargetedServerAddress(serverData, serverData != null ? serverData.serverIP : null);
    }

    public static void clearBindingState(ServerData serverData, boolean clearCapabilities) {
        if (!(serverData instanceof IServerDataExt)) {
            return;
        }

        IServerDataExt ext = (IServerDataExt) serverData;
        ext.setWawelAccountId(-1L);
        ext.setWawelProviderName(null);
        ext.setWawelLocalAuthFingerprint(null);
        ext.setWawelLocalAuthApiRoot(null);
        ext.setWawelLocalAuthPublicKeyBase64(null);
        ext.setWawelOriginalServerIp(null);
        if (clearCapabilities) {
            ext.setWawelCapabilities(ServerCapabilities.empty());
        }
    }

    private static boolean isManagedLocalProvider(ClientProvider provider) {
        return provider != null && provider.getType() == ProviderType.CUSTOM && !provider.isManualEntry();
    }

    private static boolean hasTrackedBinding(IServerDataExt ext) {
        return ext != null && (ext.getWawelAccountId() >= 0 || normalize(ext.getWawelProviderName()) != null
            || normalize(ext.getWawelLocalAuthFingerprint()) != null);
    }

    private static boolean isLegacyRetargetedLocalEntry(ServerData serverData, WawelClient client) {
        if (!(serverData instanceof IServerDataExt) || client == null) {
            return false;
        }

        ClientProvider provider = resolveManagedLocalProviderReference((IServerDataExt) serverData, client);
        if (!isManagedLocalProvider(provider)) {
            return false;
        }

        String providerHost = extractHost(provider.getApiRoot());
        String serverHost = extractServerHost(serverData.serverIP);
        return providerHost != null && serverHost != null && !providerHost.equals(serverHost);
    }

    private static ClientProvider resolveManagedLocalProviderReference(IServerDataExt ext, WawelClient client) {
        if (ext == null || client == null) {
            return null;
        }

        String providerName = normalize(ext.getWawelProviderName());
        if (providerName != null) {
            ClientProvider provider = client.getProviderRegistry()
                .getProvider(ext.getWawelProviderName());
            if (provider != null) {
                return provider;
            }
        }

        String fingerprint = normalize(ext.getWawelLocalAuthFingerprint());
        if (fingerprint == null) {
            return null;
        }

        for (ClientProvider provider : client.getProviderRegistry()
            .listProviders()) {
            if (!isManagedLocalProvider(provider)) {
                continue;
            }
            if (fingerprint.equals(normalize(provider.getPublicKeyFingerprint()))) {
                return provider;
            }
        }
        return null;
    }

    private static String extractHost(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        try {
            String host = new URI(rawUrl).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractServerHost(String serverIp) {
        if (serverIp == null) {
            return null;
        }
        ServerAddress address = ServerAddress.func_78860_a(serverIp);
        if (address == null || address.getIP() == null) {
            return null;
        }
        String host = address.getIP()
            .trim();
        return host.isEmpty() ? null : host.toLowerCase();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private static String normalizeAddress(String value) {
        return normalize(value);
    }

    private static String normalizeRaw(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static final class ReferencedLocalAuths {

        private final Set<String> providerNames = new HashSet<>();
        private final Set<String> fingerprints = new HashSet<>();

        private boolean contains(ClientProvider provider) {
            if (provider == null) {
                return false;
            }

            String providerName = normalize(provider.getName());
            if (providerName != null && providerNames.contains(providerName)) {
                return true;
            }

            String fingerprint = normalize(provider.getPublicKeyFingerprint());
            return fingerprint != null && fingerprints.contains(fingerprint);
        }
    }
}
