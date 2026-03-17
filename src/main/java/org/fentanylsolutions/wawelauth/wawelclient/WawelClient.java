package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.File;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite.ClientDatabase;
import org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite.SqliteClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite.SqliteClientProviderDAO;

/**
 * Client module singleton. Owns the SQLite DB, provider registry, account manager,
 * and background token refresh. Start from ClientProxy.init(), stop on shutdown.
 */
public class WawelClient {

    private static WawelClient instance;
    private static Thread shutdownHook;

    private final ClientDatabase database;
    private final ClientProviderDAO providerDAO;
    private final ClientAccountDAO accountDAO;
    private final YggdrasilHttpClient httpClient;
    private final ProviderRegistry providerRegistry;
    private final LocalAuthProviderResolver localAuthProviderResolver;
    private final AccountManager accountManager;
    private final SessionBridge sessionBridge;
    private final ConnectionProviderCache connectionProviderCache;
    private final WawelTextureResolver textureResolver;

    private WawelClient(File dataDir) {
        WawelAuth.LOG.info("Starting WawelAuth client module...");

        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new RuntimeException("Failed to create client data directory: " + dataDir);
        }

        // Database
        database = new ClientDatabase(new File(dataDir, "accounts.db"));
        database.initialize();

        // DAOs
        providerDAO = new SqliteClientProviderDAO(database);
        accountDAO = new SqliteClientAccountDAO(database);

        // HTTP client
        httpClient = new YggdrasilHttpClient();

        // Provider registry
        providerRegistry = new ProviderRegistry(providerDAO, httpClient);
        providerRegistry.ensureDefaultProviders();
        localAuthProviderResolver = new LocalAuthProviderResolver(providerDAO);

        // Account manager
        accountManager = new AccountManager(accountDAO, providerDAO, httpClient);
        accountManager.startBackgroundRefresh();

        // Session bridge
        sessionBridge = new SessionBridge(httpClient, providerDAO, accountDAO, accountManager);
        sessionBridge.tryImportLauncherSession();

        // Connection provider cache
        connectionProviderCache = new ConnectionProviderCache();

        // Skin resolver
        textureResolver = new WawelTextureResolver(sessionBridge);

        int prunedBindings = ServerBindingPersistence.clearMissingAccountBindings(accountManager);
        if (prunedBindings > 0) {
            WawelAuth.LOG.info("Cleared {} stale per-server account bindings on startup", prunedBindings);
        }
        if (SingleplayerAccountPersistence.clearMissingSelection(accountManager)) {
            WawelAuth.LOG.info("Cleared stale singleplayer account selection on startup");
        }

        WawelAuth.LOG.info(
            "WawelAuth client module started. {} accounts across {} providers.",
            accountDAO.count(),
            providerDAO.count());
    }

    public static synchronized void start(File dataDir) {
        if (instance != null) {
            WawelAuth.LOG.warn("WawelClient already running, ignoring start()");
            return;
        }
        instance = new WawelClient(dataDir);

        // JVM shutdown hook for crash safety
        shutdownHook = new Thread(() -> {
            WawelClient local = instance;
            if (local != null) {
                local.doStop();
            }
        }, "WawelAuth-ClientShutdown");
        Runtime.getRuntime()
            .addShutdownHook(shutdownHook);
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.doStop();
        instance = null;

        // Clean shutdown, remove hook
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime()
                    .removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // Already shutting down
            }
            shutdownHook = null;
        }
    }

    private void doStop() {
        WawelAuth.LOG.info("Stopping WawelAuth client module...");
        try {
            accountManager.shutdown();
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error shutting down account manager: {}", e.getMessage());
        }
        try {
            textureResolver.shutdown();
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error shutting down skin resolver: {}", e.getMessage());
        }
        try {
            sessionBridge.shutdown();
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error shutting down session bridge: {}", e.getMessage());
        }
        try {
            database.close();
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error closing client database: {}", e.getMessage());
        }
    }

    public static WawelClient instance() {
        return instance;
    }

    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public LocalAuthProviderResolver getLocalAuthProviderResolver() {
        return localAuthProviderResolver;
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public ClientProviderDAO getProviderDAO() {
        return providerDAO;
    }

    public SessionBridge getSessionBridge() {
        return sessionBridge;
    }

    public WawelTextureResolver getTextureResolver() {
        return textureResolver;
    }

    public YggdrasilHttpClient getHttpClient() {
        return httpClient;
    }

    public ConnectionProviderCache getConnectionProviderCache() {
        return connectionProviderCache;
    }

    /**
     * Resolve the provider for a player UUID.
     * Checks: connection cache, active account, local accounts.
     * Returns null if unknown (caller shows Steve).
     */
    public ClientProvider resolvePlayerProvider(UUID playerUuid) {
        // In-world on WA server: check connection cache
        if (Minecraft.getMinecraft().theWorld != null && connectionProviderCache.isActive()) {
            ClientProvider connectionProvider = connectionProviderCache.getPlayerProvider(playerUuid);
            if (connectionProvider != null) {
                return connectionProvider;
            }
        }

        // In-world but no per-player match: use active account's provider
        if (Minecraft.getMinecraft().theWorld != null) {
            return sessionBridge.getActiveProvider();
        }

        // Not in-world: check local accounts
        if (playerUuid != null && accountDAO != null) {
            for (org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount account : accountDAO.listAll()) {
                if (account != null && playerUuid.equals(account.getProfileUuid())) {
                    String providerName = account.getProviderName();
                    if (providerName != null && !providerName.trim()
                        .isEmpty()) {
                        ClientProvider provider = providerDAO.findByName(providerName);
                        if (provider != null) {
                            return provider;
                        }
                    }
                }
            }
        }

        return null;
    }

    /** Resolve provider by name, or null. */
    public ClientProvider resolveProviderByName(String providerName) {
        if (providerName != null && !providerName.trim()
            .isEmpty()) {
            return providerDAO.findByName(providerName);
        }
        return null;
    }

    /** Mojang provider. Only for singleplayer with no explicit account. */
    public ClientProvider getMojangProvider() {
        return providerDAO.findByName(BuiltinProviders.MOJANG_PROVIDER_NAME);
    }
}
