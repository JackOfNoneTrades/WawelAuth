package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.File;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.KeyManager;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.PropertySigner;
import org.fentanylsolutions.wawelauth.wawelcore.storage.AdminPlayerListProviderBindingDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.InviteDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.SessionDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TokenDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.UserDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.memory.InMemorySessionDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteAdminPlayerListProviderBindingDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteInviteDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteTokenDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteUserDAO;
import org.fentanylsolutions.wawelauth.wawelnet.HttpRouter;

/**
 * Singleton composition root for the Yggdrasil server module.
 * <p>
 * Initializes database, crypto, DAOs, services, and the HTTP router.
 */
public class WawelServer {

    private static WawelServer instance;

    private final ServerConfig serverConfig;
    private final SqliteDatabase database;
    private final KeyManager keyManager;
    private final HttpRouter router;
    private final UserDAO userDAO;
    private final ProfileDAO profileDAO;
    private final TokenDAO tokenDAO;
    private final InviteDAO inviteDAO;
    private final AdminPlayerListProviderBindingDAO adminPlayerListProviderBindingDAO;
    private final PublicPageService publicPageService;

    private WawelServer(File stateDir) {
        WawelAuth.LOG.info("Starting Wawel Auth server module...");

        if (!stateDir.exists() && !stateDir.mkdirs()) {
            throw new RuntimeException("Failed to create state directory: " + stateDir.getAbsolutePath());
        }

        ServerConfig config = Config.server();
        this.serverConfig = config;

        // Database
        database = new SqliteDatabase(new File(stateDir, "wawelauth.db"));
        database.initialize();

        // Discover fallback providers' public signing keys
        FallbackKeyDiscovery.discoverKeys(config);

        // Crypto
        keyManager = new KeyManager(stateDir);
        keyManager.loadOrGenerate();
        PropertySigner signer = new PropertySigner(keyManager);

        // DAOs
        userDAO = new SqliteUserDAO(database);
        tokenDAO = new SqliteTokenDAO(database);
        profileDAO = new SqliteProfileDAO(database);
        inviteDAO = new SqliteInviteDAO(database);
        adminPlayerListProviderBindingDAO = new SqliteAdminPlayerListProviderBindingDAO(database);
        SessionDAO sessionDAO = new InMemorySessionDAO(
            config.getTokens()
                .getSessionTimeoutMs(),
            10_000);

        // File stores
        TextureFileStore textureFileStore = new TextureFileStore(stateDir);

        // Services
        ProfileService profileService = new ProfileService(signer);
        AuthService authService = new AuthService(userDAO, tokenDAO, profileDAO, inviteDAO, profileService);
        FallbackProxyService fallbackProxyService = new FallbackProxyService(config);
        SessionService sessionService = new SessionService(
            tokenDAO,
            profileDAO,
            sessionDAO,
            profileService,
            fallbackProxyService);
        TextureService textureService = new TextureService(tokenDAO, profileDAO, textureFileStore);
        AdminWebService adminWebService = new AdminWebService(
            config,
            keyManager,
            userDAO,
            profileDAO,
            tokenDAO,
            inviteDAO,
            adminPlayerListProviderBindingDAO);
        publicPageService = new PublicPageService(config);

        // Router
        router = new HttpRouter();
        YggdrasilRoutes.register(
            router,
            config,
            keyManager,
            authService,
            sessionService,
            fallbackProxyService,
            textureService,
            profileService,
            profileDAO,
            textureFileStore);
        adminWebService.registerRoutes(router);
        publicPageService.registerRoutes(router);

        WawelAuth.LOG.info(
            "WawelAuth server module started. Public key: {}...{}",
            keyManager.getPublicKeyBase64()
                .substring(0, 16),
            keyManager.getPublicKeyBase64()
                .substring(
                    keyManager.getPublicKeyBase64()
                        .length() - 8));
    }

    public static synchronized void start(File stateDir) {
        if (instance != null) {
            WawelAuth.LOG.warn("WawelServer already running, ignoring start()");
            return;
        }
        instance = new WawelServer(stateDir);
    }

    public static synchronized void stop() {
        if (instance == null) return;
        WawelAuth.LOG.info("Stopping WawelAuth server module...");
        try {
            instance.database.close();
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error closing database: {}", e.getMessage());
        }
        instance = null;
    }

    public static WawelServer instance() {
        return instance;
    }

    public HttpRouter getRouter() {
        return router;
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public String getApiLocationHeaderValue() {
        String prefix = serverConfig.getApiRoutePrefix();
        return prefix.isEmpty() ? "/" : prefix;
    }

    public void refreshPublicPageCaches() {
        publicPageService.refreshAllCaches();
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public UserDAO getUserDAO() {
        return userDAO;
    }

    public ProfileDAO getProfileDAO() {
        return profileDAO;
    }

    public TokenDAO getTokenDAO() {
        return tokenDAO;
    }

    public InviteDAO getInviteDAO() {
        return inviteDAO;
    }

    public AdminPlayerListProviderBindingDAO getAdminPlayerListProviderBindingDAO() {
        return adminPlayerListProviderBindingDAO;
    }

    /**
     * Executes the given action inside a database transaction.
     * Rolls back on any exception.
     */
    public void runInTransaction(Runnable action) {
        database.runInTransaction(action);
    }
}
