package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.File;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite.ClientDatabase;
import org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite.SqliteClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite.SqliteClientProviderDAO;

/**
 * Singleton composition root for the client account manager module.
 * <p>
 * Manages the client-side SQLite database, provider registry, account manager,
 * and background token refresh.
 * <p>
 * Lifecycle:
 * <ul>
 * <li>{@link #start(File)} called from {@code ClientProxy.init()} (client-side only)</li>
 * <li>{@link #stop()} called on game shutdown (JVM hook + explicit call)</li>
 * </ul>
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
        providerRegistry.ensureBuiltinProviders();
        localAuthProviderResolver = new LocalAuthProviderResolver(providerDAO);

        // Account manager
        accountManager = new AccountManager(accountDAO, providerDAO, httpClient);
        accountManager.startBackgroundRefresh();

        // Session bridge: coordinates mixin layer with account system
        sessionBridge = new SessionBridge(httpClient, providerDAO, accountDAO, accountManager);
        sessionBridge.tryImportLauncherSession();

        // Skin resolver: unified skin resolution API
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

        // Register JVM shutdown hook for crash safety
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

        // Remove the shutdown hook since we're stopping cleanly
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
}
