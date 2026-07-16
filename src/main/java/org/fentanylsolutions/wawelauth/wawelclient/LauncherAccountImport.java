package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.util.Session;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.client.gui.LauncherImportPromptHandler;
import org.fentanylsolutions.wawelauth.wawelclient.compat.AuthlibInjectorCompat;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilRequestException;
import org.fentanylsolutions.wawelauth.wawelclient.oauth.MicrosoftOAuthClient;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.google.gson.JsonObject;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Detects a usable launcher session and offers to import it into WawelAuth.
 *
 * <p>
 * This integration is omitted from privacy-restricted distribution builds.
 */
public final class LauncherAccountImport {

    private static LauncherAccountImport instance;

    private final YggdrasilHttpClient httpClient;
    private final ClientProviderDAO providerDAO;
    private final ClientAccountDAO accountDAO;
    private final AccountManager accountManager;
    private final Session launcherSession;
    private final ExecutorService executor;
    private final LauncherImportPromptHandler promptHandler;

    private volatile Candidate pendingImport;

    private LauncherAccountImport(YggdrasilHttpClient httpClient, ClientProviderDAO providerDAO,
        ClientAccountDAO accountDAO, AccountManager accountManager, Session launcherSession) {
        this.httpClient = httpClient;
        this.providerDAO = providerDAO;
        this.accountDAO = accountDAO;
        this.accountManager = accountManager;
        this.launcherSession = launcherSession;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WawelAuth-LauncherImport");
            thread.setDaemon(true);
            return thread;
        });
        this.promptHandler = new LauncherImportPromptHandler();
    }

    public static synchronized void start(YggdrasilHttpClient httpClient, ClientProviderDAO providerDAO,
        ClientAccountDAO accountDAO, AccountManager accountManager, Session launcherSession) {
        if (instance != null) {
            return;
        }
        LauncherAccountImport created = new LauncherAccountImport(
            httpClient,
            providerDAO,
            accountDAO,
            accountManager,
            launcherSession);
        instance = created;
        FMLCommonHandler.instance()
            .bus()
            .register(created.promptHandler);
        created.executor.submit(created::inspectLauncherSession);
    }

    public static synchronized void stop() {
        LauncherAccountImport current = instance;
        instance = null;
        if (current == null) {
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .unregister(current.promptHandler);
        current.pendingImport = null;
        current.executor.shutdownNow();
    }

    public static synchronized LauncherAccountImport instance() {
        return instance;
    }

    public Candidate getPendingImport() {
        return pendingImport;
    }

    public void confirmImport() {
        Candidate candidate = pendingImport;
        pendingImport = null;
        if (candidate != null) {
            executor.submit(() -> createImportedAccount(candidate));
        }
    }

    public void declineImport() {
        pendingImport = null;
    }

    public void suppressImport() {
        Candidate candidate = pendingImport;
        pendingImport = null;
        if (candidate != null) {
            LauncherImportSuppression.suppress(candidate.getProviderName(), candidate.getProfileUuid());
        }
    }

    private void inspectLauncherSession() {
        try {
            String token = launcherSession != null ? launcherSession.getToken() : null;

            WawelAuth.debug(
                "[launcher-import] session user=" + (launcherSession != null ? launcherSession.getUsername() : "")
                    + " hasToken="
                    + (token != null && !token.isEmpty()));

            if (!isUsableLauncherSession(launcherSession)) {
                WawelAuth.debug("[launcher-import] token not usable, skipping");
                return;
            }

            WawelAuth.debug("[launcher-import] authlib-injector active=" + AuthlibInjectorCompat.isActive());

            ClientProvider provider = resolveLauncherSessionProvider();
            WawelAuth.debug("[launcher-import] resolved provider=" + (provider != null ? provider.getName() : "null"));
            boolean tokenAlreadyValidated = false;
            if (provider == null) {
                if (!AuthlibInjectorCompat.isActive()) {
                    WawelAuth.debug("[launcher-import] no provider and no authlib-injector, giving up");
                    return;
                }
                provider = probeProviderByTokenValidation(token);
                WawelAuth.debug("[launcher-import] probe result=" + (provider != null ? provider.getName() : "null"));
                if (provider == null) {
                    WawelAuth.debug("[launcher-import] no provider accepted the token");
                    return;
                }
                tokenAlreadyValidated = true;
            }

            UUID profileUuid;
            String username;
            if (BuiltinProviders.isMojangProvider(provider.getName())) {
                MicrosoftOAuthClient.MinecraftProfile profile;
                try {
                    profile = new MicrosoftOAuthClient().fetchMinecraftProfile(token, provider);
                } catch (IOException e) {
                    WawelAuth.debug("Microsoft launcher token validation failed: " + e.getMessage());
                    return;
                }
                profileUuid = profile.getUuid();
                username = profile.getName();
            } else {
                if (!tokenAlreadyValidated) {
                    JsonObject validateBody = new JsonObject();
                    validateBody.addProperty("accessToken", token);
                    try {
                        httpClient.postJson(provider, provider.authUrl("/validate"), validateBody);
                    } catch (YggdrasilRequestException e) {
                        WawelAuth.debug("Launcher session token validation failed: " + e.getMessage());
                        return;
                    } catch (IOException | IllegalStateException e) {
                        WawelAuth.debug(
                            "Could not validate launcher session against " + provider.getName()
                                + ": "
                                + e.getMessage());
                        return;
                    }
                }
                profileUuid = parseSessionUuid(launcherSession.getPlayerID());
                username = launcherSession.getUsername();
            }

            WawelAuth.debug("[launcher-import] uuid=" + profileUuid + " username=" + username);
            if (profileUuid == null) {
                WawelAuth.debug("[launcher-import] no usable profile UUID");
                return;
            }

            ClientAccount existing = accountDAO.findByProviderAndProfile(provider.getName(), profileUuid);
            if (existing != null) {
                WawelAuth.debug("[launcher-import] account already exists, resyncing");
                resyncImportedAccount(existing, token, username);
                return;
            }

            if (LauncherImportSuppression.isSuppressed(provider.getName(), profileUuid)) {
                WawelAuth.debug("[launcher-import] suppressed, skipping");
                return;
            }

            pendingImport = new Candidate(provider.getName(), profileUuid, username, token);
            WawelAuth.debug("[launcher-import] pending import created for '" + username + "'");
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to inspect launcher session: {}", e.getMessage());
        }
    }

    private void createImportedAccount(Candidate candidate) {
        try {
            ClientProvider provider = providerDAO.findByName(candidate.getProviderName());
            if (provider == null) {
                WawelAuth.LOG.warn(
                    "Cannot import launcher session: provider '{}' no longer exists",
                    candidate.getProviderName());
                return;
            }

            ClientAccount existing = accountDAO
                .findByProviderAndProfile(provider.getName(), candidate.getProfileUuid());
            if (existing != null) {
                resyncImportedAccount(existing, candidate.getToken(), candidate.getUsername());
                return;
            }

            long now = System.currentTimeMillis();
            ClientAccount account = new ClientAccount();
            account.setProviderName(provider.getName());
            account.setUserUuid(UuidUtil.toUnsigned(candidate.getProfileUuid()));
            account.setProfileUuid(candidate.getProfileUuid());
            account.setProfileName(candidate.getUsername());
            account.setAccessToken(candidate.getToken());
            account.setClientToken(null);
            account.setStatus(AccountStatus.VALID);
            account.setConsecutiveFailures(0);
            account.setCreatedAt(now);
            account.setLastValidatedAt(now);
            account.setTokenIssuedAt(now);

            long id = accountDAO.create(account);
            account.setId(id);
            accountManager.cacheStatus(id, AccountStatus.VALID);

            WawelAuth.LOG
                .info("Imported launcher session as {} account: {}", provider.getName(), candidate.getUsername());
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to import launcher session: {}", e.getMessage());
        }
    }

    private void resyncImportedAccount(ClientAccount existing, String token, String username) {
        if (StringUtil.trimToNull(existing.getRefreshToken()) != null) {
            WawelAuth.debug("Account '" + username + "' is WawelAuth-managed, leaving launcher session untouched");
            return;
        }
        long now = System.currentTimeMillis();
        existing.setAccessToken(token);
        existing.setStatus(AccountStatus.VALID);
        existing.setConsecutiveFailures(0);
        existing.setLastValidatedAt(now);
        existing.setTokenIssuedAt(now);
        existing.setLastError(null);
        accountDAO.update(existing);
        accountManager.cacheStatus(existing.getId(), AccountStatus.VALID);
        WawelAuth.LOG.info("Re-synced imported account from launcher session: {}", username);
    }

    private ClientProvider resolveLauncherSessionProvider() {
        String backendHost = detectLauncherAuthBackendHost();
        WawelAuth.debug("[launcher-import] detectLauncherAuthBackendHost=" + backendHost);
        if (backendHost == null) {
            if (AuthlibInjectorCompat.isActive()) {
                WawelAuth.debug("authlib-injector detected but backend host unknown, skipping Mojang default");
                return null;
            }
            return providerDAO.findByName(BuiltinProviders.MOJANG_PROVIDER_NAME);
        }
        boolean authlibInjectorActive = AuthlibInjectorCompat.isActive();
        for (ClientProvider provider : providerDAO.listAll()) {
            if (BuiltinProviders.isOfflineProvider(provider.getName())) {
                continue;
            }
            if (authlibInjectorActive && BuiltinProviders.isMojangProvider(provider.getName())) {
                continue;
            }
            if (backendHost.equals(extractHost(provider.getApiRoot()))
                || backendHost.equals(extractHost(provider.getAuthServerUrl()))
                || backendHost.equals(extractHost(provider.getSessionServerUrl()))) {
                return provider;
            }
        }
        WawelAuth.debug("Launcher auth backend '" + backendHost + "' is not a configured provider, skipping import");
        return null;
    }

    private static String detectLauncherAuthBackendHost() {
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean()
                .getInputArguments()) {
                if (arg == null) {
                    continue;
                }
                String lower = arg.toLowerCase();
                if (lower.startsWith("-javaagent:")
                    && (lower.contains("authlib-injector") || lower.contains("authlibinjector"))) {
                    int eq = arg.indexOf('=');
                    if (eq >= 0 && eq + 1 < arg.length()) {
                        String host = extractHost(
                            arg.substring(eq + 1)
                                .trim());
                        if (host != null) {
                            return host;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        for (String key : new String[] { "minecraft.api.authHost", "minecraft.api.accountsHost",
            "minecraft.api.sessionHost", "minecraft.api.servicesHost" }) {
            String value = System.getProperty(key);
            if (value != null) {
                WawelAuth.debug("[launcher-import] system property " + key + "=" + value);
            }
            String host = extractHost(value);
            if (host != null) {
                return host;
            }
        }
        return null;
    }

    private ClientProvider probeProviderByTokenValidation(String token) {
        for (ClientProvider provider : providerDAO.listAll()) {
            if (BuiltinProviders.isOfflineProvider(provider.getName())
                || BuiltinProviders.isMojangProvider(provider.getName())) {
                continue;
            }
            JsonObject validateBody = new JsonObject();
            validateBody.addProperty("accessToken", token);
            try {
                httpClient.postJson(provider, provider.authUrl("/validate"), validateBody);
                WawelAuth.debug("Token accepted by provider '" + provider.getName() + "'");
                return provider;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean isUsableLauncherSession(Session session) {
        if (session == null) {
            return false;
        }
        String token = session.getToken();
        return token != null && !token.isEmpty()
            && !"NotValid".equals(token)
            && !"0".equals(token)
            && !"FML".equals(token);
    }

    private static String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.trim()
            .isEmpty()) {
            return null;
        }
        try {
            String host = new URI(rawUrl).getHost();
            return host != null ? host.toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID parseSessionUuid(String playerId) {
        if (playerId == null || playerId.trim()
            .isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(playerId);
        } catch (IllegalArgumentException e) {
            try {
                return UuidUtil.fromUnsigned(playerId);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static final class Candidate {

        private final String providerName;
        private final UUID profileUuid;
        private final String username;
        private final String token;

        private Candidate(String providerName, UUID profileUuid, String username, String token) {
            this.providerName = providerName;
            this.profileUuid = profileUuid;
            this.username = username;
            this.token = token;
        }

        public String getProviderName() {
            return providerName;
        }

        public UUID getProfileUuid() {
            return profileUuid;
        }

        public String getUsername() {
            return username;
        }

        private String getToken() {
            return token;
        }
    }
}
