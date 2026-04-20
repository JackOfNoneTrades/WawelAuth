package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilRequestException;
import org.fentanylsolutions.wawelauth.wawelclient.oauth.MicrosoftOAuthClient;
import org.fentanylsolutions.wawelauth.wawelclient.oauth.ProviderOAuthClient;
import org.fentanylsolutions.wawelauth.wawelclient.oauth.ProviderOAuthClients;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Core service managing client-side accounts and their token lifecycle.
 * <p>
 * Handles: authenticate, validate, refresh, offline degradation,
 * background token refresh with exponential backoff.
 * <p>
 * All network-touching methods return {@link CompletableFuture} and run on
 * a background thread. Synchronous methods (local DB only) are safe to call
 * from any thread.
 */
public class AccountManager {

    private static final Pattern OFFLINE_ACCOUNT_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String OFFLINE_ACCESS_TOKEN_PREFIX = "offline:";
    private static final long STALE_THRESHOLD_MS = 25 * 60_000; // 25 minutes
    private static final long CHECK_INTERVAL_SECONDS = 60;
    private static final long UNVERIFIED_RETRY_INTERVAL_MS = 30_000; // retry quickly after offline startup

    private final ClientAccountDAO accountDAO;
    private final ClientProviderDAO providerDAO;
    private final YggdrasilHttpClient httpClient;
    private final MicrosoftOAuthClient microsoftOAuthClient;
    private final ScheduledExecutorService scheduler;

    /**
     * In-memory account status cache. Render code reads from this, never from DB.
     */
    private final ConcurrentHashMap<Long, AccountStatus> statusCache = new ConcurrentHashMap<>();

    public AccountManager(ClientAccountDAO accountDAO, ClientProviderDAO providerDAO, YggdrasilHttpClient httpClient) {

        this.accountDAO = accountDAO;
        this.providerDAO = providerDAO;
        this.httpClient = httpClient;
        this.microsoftOAuthClient = new MicrosoftOAuthClient();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WawelAuth-TokenRefresh");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start background token refresh. Called once from WawelClient.
     */
    public void startBackgroundRefresh() {
        // Immediate: validate all accounts
        scheduler.submit(this::validateAllAccounts);
        // Periodic: check every 60 seconds
        scheduler
            .scheduleAtFixedRate(this::periodicCheck, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stop background refresh. Called from WawelClient.stop().
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread()
                .interrupt();
        }
    }

    // =========================================================================
    // Async public API (network-touching)
    // =========================================================================

    /**
     * Authenticate a new account against a provider.
     * Runs on the background thread.
     *
     * @param providerName which provider to authenticate against
     * @param username     login username
     * @param password     login password
     * @return future resolving to the created/updated account
     */
    public CompletableFuture<ClientAccount> authenticate(String providerName, String username, String password) {
        CompletableFuture<ClientAccount> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount result = doAuthenticate(providerName, username, password);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Register a new account on a custom provider.
     * <p>
     * This is a WawelAuth extension endpoint (not Yggdrasil standard):
     * POST /api/wawelauth/register
     */
    public CompletableFuture<Void> register(String providerName, String username, String password, String inviteToken) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                doRegister(providerName, username, password, inviteToken);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Change password for a WawelAuth-managed account.
     * <p>
     * This is a WawelAuth extension endpoint:
     * POST /api/wawelauth/change-password
     */
    public CompletableFuture<Void> changePassword(long accountId, String currentPassword, String newPassword) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount account = accountDAO.findById(accountId);
                if (account == null) {
                    throw new IllegalArgumentException("Account not found: " + accountId);
                }
                doChangePassword(account, currentPassword, newPassword);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Delete a WawelAuth-managed account on the provider (self-service).
     * <p>
     * This is a WawelAuth extension endpoint:
     * POST /api/wawelauth/delete-account
     */
    public CompletableFuture<Void> deleteWawelAuthAccount(long accountId, String currentPassword) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount account = accountDAO.findById(accountId);
                if (account == null) {
                    throw new IllegalArgumentException("Account not found: " + accountId);
                }
                doDeleteWawelAuthAccount(account, currentPassword);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Probe whether a provider supports WawelAuth registration extension.
     * <p>
     * Current rule: GET {services/api root}/ and require
     * meta.implementationName == "WawelAuth" (case-insensitive).
     */
    public CompletableFuture<Boolean> probeSupportsWawelRegister(String providerName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                future.complete(doProbeSupportsWawelRegister(providerName));
            } catch (Exception e) {
                WawelAuth.debug("Register capability probe failed for '" + providerName + "': " + e.getMessage());
                future.complete(false);
            }
        });
        return future;
    }

    /**
     * Authenticate a Microsoft-backed Mojang account via browser OAuth.
     * Uses a dedicated worker so the refresh scheduler thread is not blocked
     * while waiting for user interaction in the browser.
     */
    public CompletableFuture<ClientAccount> authenticateMicrosoft(String providerName, Consumer<String> statusSink) {
        CompletableFuture<ClientAccount> future = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                ClientAccount result = doAuthenticateMicrosoft(providerName, statusSink);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, "WawelAuth-MicrosoftLogin");
        worker.setDaemon(true);
        worker.start();
        return future;
    }

    public CompletableFuture<ClientAccount> authenticateOAuth(String providerName, String loginHint,
        Consumer<String> statusSink) {
        return authenticateOAuth(providerName, loginHint, statusSink, null);
    }

    public CompletableFuture<ClientAccount> authenticateOAuth(String providerName, String loginHint,
        Consumer<String> statusSink, Consumer<String> deviceCodeSink) {
        CompletableFuture<ClientAccount> future = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                ClientAccount result = doAuthenticateOAuth(providerName, loginHint, statusSink, deviceCodeSink);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, "WawelAuth-ProviderOAuthLogin");
        worker.setDaemon(true);
        worker.start();
        return future;
    }

    /**
     * Manually refresh a specific account's token.
     * Runs on the background thread.
     */
    public CompletableFuture<AccountStatus> refreshAccount(long id) {
        CompletableFuture<AccountStatus> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount account = accountDAO.findById(id);
                if (account == null) {
                    future.completeExceptionally(new IllegalArgumentException("Account not found: " + id));
                    return;
                }
                AccountStatus status = doValidateOrRefresh(account);
                future.complete(status);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Remove an account. Attempts to invalidate the token on the server first,
     * then deletes locally regardless.
     * Runs on the background thread.
     */
    public CompletableFuture<Void> removeAccount(long id) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount account = accountDAO.findById(id);
                if (account == null) {
                    future.completeExceptionally(new IllegalArgumentException("Account not found: " + id));
                    return;
                }

                // Best-effort invalidation on the server
                try {
                    ClientProvider provider = providerDAO.findByName(account.getProviderName());
                    if (provider != null && !BuiltinProviders.isOfflineProvider(provider.getName())) {
                        String token = account.getAccessToken();
                        JsonObject body = new JsonObject();
                        body.addProperty("accessToken", token);
                        body.addProperty("clientToken", account.getClientToken());
                        httpClient.postJson(provider, provider.authUrl("/invalidate"), body);
                    }
                } catch (Exception e) {
                    WawelAuth.debug("Failed to invalidate token on server (non-fatal): " + e.getMessage());
                }

                accountDAO.delete(id);
                statusCache.remove(id);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Delete (reset) skin and cape textures for an account profile.
     */
    public CompletableFuture<String> deleteTextures(long accountId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount account = accountDAO.findById(accountId);
                if (account == null) {
                    throw new IllegalArgumentException("Account not found: " + accountId);
                }
                String result = doDeleteTextures(account);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Upload skin/cape files for an account profile.
     *
     * @param skinSlim whether skin should be uploaded as slim model (ignored if no skin is uploaded)
     */
    public CompletableFuture<String> uploadTextures(long accountId, File skinFile, File capeFile, boolean skinSlim) {
        CompletableFuture<String> future = new CompletableFuture<>();
        scheduler.submit(() -> {
            try {
                ClientAccount account = accountDAO.findById(accountId);
                if (account == null) {
                    throw new IllegalArgumentException("Account not found: " + accountId);
                }
                String result = doUploadTextures(account, skinFile, capeFile, skinSlim);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public String applyOfflineTextures(long accountId, File skinFile, File capeFile, boolean skinSlim)
        throws IOException {
        ClientAccount account = accountDAO.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (!BuiltinProviders.isOfflineProvider(account.getProviderName())) {
            throw new IllegalArgumentException("Account is not an offline account: " + accountId);
        }
        return doUploadOfflineTextures(account, skinFile, capeFile, skinSlim);
    }

    public String resetOfflineTextures(long accountId) {
        ClientAccount account = accountDAO.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (!BuiltinProviders.isOfflineProvider(account.getProviderName())) {
            throw new IllegalArgumentException("Account is not an offline account: " + accountId);
        }
        return doDeleteOfflineTextures(account);
    }

    // =========================================================================
    // Synchronous public API (no network)
    // =========================================================================

    public List<ClientAccount> listAccounts() {
        return accountDAO.listAll();
    }

    public List<ClientAccount> listAccounts(String providerName) {
        return accountDAO.findByProvider(providerName);
    }

    public ClientAccount getAccount(long id) {
        return accountDAO.findById(id);
    }

    /**
     * Get the usable access token for an account.
     * Currently a plain pass-through; would add decryption if token-at-rest
     * encryption is added later.
     * Synchronous: no network, local DB read only.
     */
    public String getUsableAccessToken(ClientAccount account) {
        return account.getAccessToken();
    }

    /**
     * Get the cached account status. Safe to call from the render thread:
     * reads only from an in-memory map, never the DB.
     *
     * @return the cached status, or null if unknown
     */
    public AccountStatus getAccountStatus(long id) {
        return statusCache.get(id);
    }

    /**
     * Update the in-memory status cache entry.
     */
    public void cacheStatus(long id, AccountStatus status) {
        statusCache.put(id, status);
    }

    // =========================================================================
    // Background refresh internals
    // =========================================================================

    private void validateAllAccounts() {
        try {
            List<ClientAccount> accounts = accountDAO.listAll();
            // Seed status cache before validation so render code has data immediately
            for (ClientAccount account : accounts) {
                statusCache.put(account.getId(), account.getStatus());
            }
            WawelAuth.debug("Validating " + accounts.size() + " accounts on startup");
            for (ClientAccount account : accounts) {
                try {
                    doValidateOrRefresh(account);
                } catch (Exception e) {
                    WawelAuth.LOG.warn(
                        "Error validating account {} ({}): {}",
                        account.getId(),
                        account.getProfileName(),
                        e.getMessage());
                }
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error during startup account validation: {}", e.getMessage());
        }
    }

    private void periodicCheck() {
        try {
            List<ClientAccount> accounts = accountDAO.listAll();
            long now = System.currentTimeMillis();

            for (ClientAccount account : accounts) {
                try {
                    AccountStatus status = account.getStatus();

                    if (status == AccountStatus.EXPIRED) {
                        // Needs user intervention, skip
                        continue;
                    }

                    if (status == AccountStatus.UNAUTHED) {
                        continue;
                    }

                    if (status == AccountStatus.UNVERIFIED) {
                        // Keep retrying UNVERIFIED accounts on a short cadence so they
                        // recover quickly when internet comes back after startup.
                        long delay = UNVERIFIED_RETRY_INTERVAL_MS;
                        long elapsed = now - account.getLastRefreshAttemptAt();
                        if (elapsed >= delay) {
                            doValidateOrRefresh(account);
                        }
                        continue;
                    }

                    // VALID or REFRESHED: check if stale
                    if ((now - account.getLastValidatedAt()) > STALE_THRESHOLD_MS) {
                        doValidateOrRefresh(account);
                    }
                } catch (Exception e) {
                    WawelAuth.debug("Error in periodic check for account " + account.getId() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error during periodic account check: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Token lifecycle
    // =========================================================================

    private ClientAccount doAuthenticate(String providerName, String username, String password)
        throws IOException, YggdrasilRequestException {

        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName);
        }
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            return doAuthenticateOffline(provider, username);
        }

        String clientToken = UUID.randomUUID()
            .toString()
            .replace("-", "");

        JsonObject agentObj = new JsonObject();
        agentObj.addProperty("name", "Minecraft");
        agentObj.addProperty("version", 1);

        JsonObject body = new JsonObject();
        body.add("agent", agentObj);
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("clientToken", clientToken);
        body.addProperty("requestUser", true);

        JsonObject response = httpClient.postJson(provider, provider.authUrl("/authenticate"), body);

        // Parse response
        String accessToken = response.get("accessToken")
            .getAsString();
        String returnedClientToken = response.get("clientToken")
            .getAsString();

        // Extract user UUID
        String userUuid = null;
        if (response.has("user") && !response.get("user")
            .isJsonNull()) {
            JsonObject user = response.getAsJsonObject("user");
            if (user.has("id")) {
                userUuid = user.get("id")
                    .getAsString();
            }
        }
        if (userUuid == null) {
            // Fallback: some servers don't return user block; use a hash of provider+username
            userUuid = UUID.nameUUIDFromBytes((providerName + ":" + username).getBytes())
                .toString()
                .replace("-", "");
        }

        // Extract selected profile
        UUID profileUuid = null;
        String profileName = null;
        boolean needsProfileBind = false;
        if (response.has("selectedProfile") && !response.get("selectedProfile")
            .isJsonNull()) {
            JsonObject profile = response.getAsJsonObject("selectedProfile");
            profileUuid = UuidUtil.fromUnsigned(
                profile.get("id")
                    .getAsString());
            profileName = profile.get("name")
                .getAsString();
        } else if (response.has("availableProfiles") && response.get("availableProfiles")
            .isJsonArray()) {
                JsonArray available = response.getAsJsonArray("availableProfiles");
                if (available.size() > 0) {
                    JsonObject first = available.get(0)
                        .getAsJsonObject();
                    profileUuid = UuidUtil.fromUnsigned(
                        first.get("id")
                            .getAsString());
                    profileName = first.get("name")
                        .getAsString();
                    // Token is not bound to this profile yet: must call /refresh to bind
                    needsProfileBind = true;
                }
            }

        // Bind profile via refresh if authenticate only returned availableProfiles
        if (needsProfileBind && profileUuid != null) {
            WawelAuth.debug("Binding profile " + profileName + " via /refresh for unbound token");
            JsonObject refreshBody = new JsonObject();
            refreshBody.addProperty("accessToken", accessToken);
            refreshBody.addProperty("clientToken", returnedClientToken);

            JsonObject selectedProfileObj = new JsonObject();
            selectedProfileObj.addProperty("id", UuidUtil.toUnsigned(profileUuid));
            selectedProfileObj.addProperty("name", profileName);
            refreshBody.add("selectedProfile", selectedProfileObj);

            JsonObject refreshResp = httpClient.postJson(provider, provider.authUrl("/refresh"), refreshBody);

            // Update tokens from refresh response
            accessToken = refreshResp.get("accessToken")
                .getAsString();
            returnedClientToken = refreshResp.get("clientToken")
                .getAsString();

            // Update profile from refresh response if present
            if (refreshResp.has("selectedProfile") && !refreshResp.get("selectedProfile")
                .isJsonNull()) {
                JsonObject boundProfile = refreshResp.getAsJsonObject("selectedProfile");
                profileUuid = UuidUtil.fromUnsigned(
                    boundProfile.get("id")
                        .getAsString());
                profileName = boundProfile.get("name")
                    .getAsString();
            }
        }

        // Extract user properties
        String userPropsJson = null;
        if (response.has("user") && !response.get("user")
            .isJsonNull()) {
            JsonObject user = response.getAsJsonObject("user");
            if (user.has("properties") && user.get("properties")
                .isJsonArray()) {
                userPropsJson = user.getAsJsonArray("properties")
                    .toString();
            }
        }

        // Build account
        long now = System.currentTimeMillis();
        ClientAccount account = new ClientAccount();
        account.setProviderName(providerName);
        account.setUserUuid(userUuid);
        account.setProfileUuid(profileUuid);
        account.setProfileName(profileName);
        account.setAccessToken(accessToken);
        account.setRefreshToken(null);
        account.setClientToken(returnedClientToken);
        account.setUserPropertiesJson(userPropsJson);
        account.setStatus(AccountStatus.VALID);
        account.setConsecutiveFailures(0);
        account.setCreatedAt(now);
        account.setLastValidatedAt(now);
        account.setTokenIssuedAt(now);

        // Upsert: match by profile when bound, by unbound user when not
        ClientAccount existing = null;
        if (profileUuid != null) {
            existing = accountDAO.findByProviderAndProfile(providerName, profileUuid);
        }
        if (existing == null) {
            // Only match an unbound entry (profile_uuid IS NULL) for this user.
            // Never match a row that already has a different profile bound,
            // otherwise logging into profile P2 would overwrite profile P1.
            existing = accountDAO.findUnboundByProviderAndUser(providerName, userUuid);
        }
        if (existing != null) {
            account.setId(existing.getId());
            account.setCreatedAt(existing.getCreatedAt());
            accountDAO.update(account);
        } else {
            long id = accountDAO.create(account);
            account.setId(id);
        }
        statusCache.put(account.getId(), account.getStatus());

        WawelAuth.LOG.info("Authenticated account '{}' on provider '{}'", profileName, providerName);
        return account;
    }

    private ClientAccount doAuthenticateOffline(ClientProvider provider, String username) {
        String profileName = normalizeOfflineAccountName(username);
        UUID profileUuid = UuidUtil.offlinePlayerUuid(profileName);
        String profileUuidUnsigned = UuidUtil.toUnsigned(profileUuid);
        long now = System.currentTimeMillis();

        ClientAccount account = new ClientAccount();
        account.setProviderName(provider.getName());
        account.setUserUuid(profileUuidUnsigned);
        account.setProfileUuid(profileUuid);
        account.setProfileName(profileName);
        account.setAccessToken(OFFLINE_ACCESS_TOKEN_PREFIX + profileUuidUnsigned);
        account.setRefreshToken(null);
        account.setClientToken(null);
        account.setUserPropertiesJson(null);
        account.setStatus(AccountStatus.UNAUTHED);
        account.setConsecutiveFailures(0);
        account.setCreatedAt(now);
        account.setLastValidatedAt(now);
        account.setTokenIssuedAt(now);
        account.setLastError(null);
        account.setLastErrorAt(0L);
        account.setLastRefreshAttemptAt(0L);

        ClientAccount existing = accountDAO.findByProviderAndProfile(provider.getName(), profileUuid);
        if (existing != null) {
            account.setId(existing.getId());
            account.setCreatedAt(existing.getCreatedAt());
            account.setLocalSkinPath(existing.getLocalSkinPath());
            account.setLocalSkinModel(existing.getLocalSkinModel());
            account.setLocalCapePath(existing.getLocalCapePath());
            accountDAO.update(account);
        } else {
            long id = accountDAO.create(account);
            account.setId(id);
        }
        statusCache.put(account.getId(), AccountStatus.UNAUTHED);

        WawelAuth.LOG.info("Created offline account '{}' with UUID {}", profileName, profileUuidUnsigned);
        return account;
    }

    private void doRegister(String providerName, String username, String password, String inviteToken)
        throws IOException, YggdrasilRequestException {
        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName);
        }
        if (provider.getType() == ProviderType.BUILTIN) {
            throw new IllegalArgumentException("Registration is not supported for built-in providers.");
        }

        String cleanUsername = username == null ? null : username.trim();
        if (cleanUsername == null || cleanUsername.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("username", cleanUsername);
        body.addProperty("password", password);
        if (inviteToken != null && !inviteToken.trim()
            .isEmpty()) {
            body.addProperty("inviteToken", inviteToken.trim());
        }

        String baseUrl = resolveServicesBase(provider);
        String registerUrl = baseUrl + "/api/wawelauth/register";
        httpClient.postJson(provider, registerUrl, body);
        WawelAuth.LOG.info("Registered account '{}' on provider '{}'", cleanUsername, providerName);
    }

    private void doChangePassword(ClientAccount account, String currentPassword, String newPassword)
        throws IOException, YggdrasilRequestException {
        if (account == null) {
            throw new IllegalArgumentException("Account is required.");
        }

        String current = trimToNull(currentPassword);
        String next = trimToNull(newPassword);
        if (current == null) {
            throw new IllegalArgumentException("Current password is required.");
        }
        if (next == null) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (current.equals(next)) {
            throw new IllegalArgumentException("New password must differ from current password.");
        }

        ClientProvider provider = providerDAO.findByName(account.getProviderName());
        if (!supportsWawelAuthCredentialManagement(provider)) {
            throw new IllegalArgumentException("Credential management is only available for WawelAuth providers.");
        }

        AccountStatus status = doValidateOrRefresh(account);
        if (status == AccountStatus.EXPIRED) {
            throw new IllegalArgumentException("Account token expired. Please re-authenticate first.");
        }
        if (status == AccountStatus.UNVERIFIED) {
            throw new IOException("Account token could not be verified (provider unreachable).");
        }

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", account.getAccessToken());
        if (account.getClientToken() != null && !account.getClientToken()
            .trim()
            .isEmpty()) {
            body.addProperty("clientToken", account.getClientToken());
        }
        body.addProperty("currentPassword", current);
        body.addProperty("newPassword", next);

        String baseUrl = resolveServicesBase(provider);
        httpClient.postJson(provider, baseUrl + "/api/wawelauth/change-password", body);
        WawelAuth.LOG.info(
            "Changed password for account '{}' on provider '{}'",
            account.getProfileName(),
            account.getProviderName());
    }

    private void doDeleteWawelAuthAccount(ClientAccount account, String currentPassword)
        throws IOException, YggdrasilRequestException {
        if (account == null) {
            throw new IllegalArgumentException("Account is required.");
        }

        String current = trimToNull(currentPassword);
        if (current == null) {
            throw new IllegalArgumentException("Current password is required.");
        }

        ClientProvider provider = providerDAO.findByName(account.getProviderName());
        if (!supportsWawelAuthCredentialManagement(provider)) {
            throw new IllegalArgumentException("Credential management is only available for WawelAuth providers.");
        }

        AccountStatus status = doValidateOrRefresh(account);
        if (status == AccountStatus.EXPIRED) {
            throw new IllegalArgumentException("Account token expired. Please re-authenticate first.");
        }
        if (status == AccountStatus.UNVERIFIED) {
            throw new IOException("Account token could not be verified (provider unreachable).");
        }

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", account.getAccessToken());
        if (account.getClientToken() != null && !account.getClientToken()
            .trim()
            .isEmpty()) {
            body.addProperty("clientToken", account.getClientToken());
        }
        body.addProperty("currentPassword", current);

        String baseUrl = resolveServicesBase(provider);
        httpClient.postJson(provider, baseUrl + "/api/wawelauth/delete-account", body);

        String targetProvider = account.getProviderName();
        String targetUserUuid = trimToNull(account.getUserUuid());
        List<ClientAccount> providerAccounts = accountDAO.findByProvider(targetProvider);
        int removed = 0;
        for (ClientAccount candidate : providerAccounts) {
            if (shouldRemoveLocalAccountAfterServerDelete(candidate, account, targetUserUuid)) {
                accountDAO.delete(candidate.getId());
                statusCache.remove(candidate.getId());
                removed++;
            }
        }

        if (removed == 0) {
            accountDAO.delete(account.getId());
            statusCache.remove(account.getId());
        }

        WawelAuth.LOG.info(
            "Deleted server account '{}' on provider '{}'; removed {} local account row(s)",
            account.getProfileName(),
            account.getProviderName(),
            removed > 0 ? removed : 1);
    }

    private boolean doProbeSupportsWawelRegister(String providerName) throws IOException, YggdrasilRequestException {
        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            return false;
        }
        if (provider.getType() == ProviderType.BUILTIN) {
            return false;
        }

        String baseUrl = resolveMetadataBase(provider);
        JsonObject metadata = httpClient.getJson(provider, baseUrl);
        if (metadata == null || !metadata.has("meta")
            || metadata.get("meta")
                .isJsonNull()
            || !metadata.get("meta")
                .isJsonObject()) {
            return false;
        }

        JsonObject meta = metadata.getAsJsonObject("meta");
        if (!meta.has("implementationName") || meta.get("implementationName")
            .isJsonNull()) {
            return false;
        }
        String implementationName;
        try {
            implementationName = meta.get("implementationName")
                .getAsString();
        } catch (Exception e) {
            return false;
        }
        return "WawelAuth".equalsIgnoreCase(implementationName.trim());
    }

    private ClientAccount doAuthenticateMicrosoft(String providerName, Consumer<String> statusSink) throws IOException {
        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName);
        }
        provider = applyRuntimeProviderOverrides(provider);
        if (!BuiltinProviders.isMojangProvider(provider.getName())) {
            throw new IllegalArgumentException("Microsoft login is only supported for the Microsoft provider");
        }

        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        MicrosoftOAuthClient.LoginResult result = microsoftOAuthClient.loginInteractive(provider, status);

        long now = System.currentTimeMillis();
        ClientAccount account = new ClientAccount();
        account.setProviderName(providerName);
        account.setUserUuid(UuidUtil.toUnsigned(result.getProfileUuid()));
        account.setProfileUuid(result.getProfileUuid());
        account.setProfileName(result.getProfileName());
        account.setAccessToken(result.getMinecraftAccessToken());
        account.setRefreshToken(result.getMicrosoftRefreshToken());
        account.setClientToken(null);
        account.setUserPropertiesJson(null);
        account.setStatus(AccountStatus.VALID);
        account.setConsecutiveFailures(0);
        account.setCreatedAt(now);
        account.setLastValidatedAt(now);
        account.setTokenIssuedAt(now);
        account.setLastError(null);
        account.setLastErrorAt(0L);
        account.setLastRefreshAttemptAt(now);

        ClientAccount existing = accountDAO.findByProviderAndProfile(providerName, result.getProfileUuid());
        if (existing != null) {
            account.setId(existing.getId());
            account.setCreatedAt(existing.getCreatedAt());
            accountDAO.update(account);
        } else {
            long id = accountDAO.create(account);
            account.setId(id);
        }
        statusCache.put(account.getId(), account.getStatus());

        WawelAuth.LOG
            .info("Authenticated Microsoft account '{}' on provider '{}'", account.getProfileName(), providerName);
        return account;
    }

    private ClientAccount doAuthenticateOAuth(String providerName, String loginHint, Consumer<String> statusSink,
        Consumer<String> deviceCodeSink) throws IOException {
        ClientProvider provider = providerDAO.findByName(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName);
        }
        provider = applyRuntimeProviderOverrides(provider);
        ProviderOAuthClient oauthClient = ProviderOAuthClients.resolve(provider);
        if (oauthClient == null) {
            throw new IllegalArgumentException("OAuth login is not supported for provider: " + providerName);
        }

        Consumer<String> status = statusSink != null ? statusSink : s -> {};
        Consumer<String> deviceCode = deviceCodeSink != null ? deviceCodeSink : s -> {};
        ProviderOAuthClient.LoginResult result = oauthClient.loginInteractive(provider, loginHint, status, deviceCode);

        long now = System.currentTimeMillis();
        ClientAccount account = new ClientAccount();
        account.setProviderName(providerName);
        account.setUserUuid(UuidUtil.toUnsigned(result.getProfileUuid()));
        account.setProfileUuid(result.getProfileUuid());
        account.setProfileName(result.getProfileName());
        account.setAccessToken(result.getAccessToken());
        account.setRefreshToken(result.getRefreshToken());
        account.setClientToken(result.getClientToken());
        account.setUserPropertiesJson(null);
        account.setStatus(AccountStatus.VALID);
        account.setConsecutiveFailures(0);
        account.setCreatedAt(now);
        account.setLastValidatedAt(now);
        account.setTokenIssuedAt(now);
        account.setLastError(null);
        account.setLastErrorAt(0L);
        account.setLastRefreshAttemptAt(now);

        ClientAccount existing = accountDAO.findByProviderAndProfile(providerName, result.getProfileUuid());
        if (existing != null) {
            account.setId(existing.getId());
            account.setCreatedAt(existing.getCreatedAt());
            accountDAO.update(account);
        } else {
            long id = accountDAO.create(account);
            account.setId(id);
        }
        statusCache.put(account.getId(), account.getStatus());

        WawelAuth.LOG.info("Authenticated OAuth account '{}' on provider '{}'", account.getProfileName(), providerName);
        return account;
    }

    /**
     * Validate → Refresh lifecycle for a single account.
     * Returns the resulting status.
     */
    private AccountStatus doValidateOrRefresh(ClientAccount account) {
        ClientProvider provider = providerDAO.findByName(account.getProviderName());
        if (provider == null) {
            WawelAuth.LOG.warn("Provider '{}' not found for account {}", account.getProviderName(), account.getId());
            markStatus(account, AccountStatus.EXPIRED, "Provider not found");
            return AccountStatus.EXPIRED;
        }
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            markUnauthed(account);
            return AccountStatus.UNAUTHED;
        }

        if (isMicrosoftManagedMojangAccount(provider, account)) {
            return doValidateOrRefreshMicrosoft(account, provider);
        }
        if (isProviderOauthManagedAccount(provider, account)) {
            return doValidateOrRefreshProviderOauth(account, provider);
        }

        long now = System.currentTimeMillis();
        account.setLastRefreshAttemptAt(now);

        WawelAuth.debug(
            "Validating account " + account.getId()
                + " ("
                + account.getProfileName()
                + ") on provider '"
                + provider.getName()
                + "' using proxy "
                + org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport
                    .describeProxySettings(provider.getProxySettings()));

        // Step 1: Validate
        try {
            String accessToken = account.getAccessToken();
            JsonObject validateBody = new JsonObject();
            validateBody.addProperty("accessToken", accessToken);
            if (account.getClientToken() != null) {
                validateBody.addProperty("clientToken", account.getClientToken());
            }

            httpClient.postJson(provider, provider.authUrl("/validate"), validateBody);

            // 204: token is valid
            account.setStatus(AccountStatus.VALID);
            account.setLastValidatedAt(now);
            account.setConsecutiveFailures(0);
            account.setLastError(null);
            accountDAO.update(account);
            statusCache.put(account.getId(), AccountStatus.VALID);
            WawelAuth.debug("Account " + account.getId() + " (" + account.getProfileName() + ") validated OK");
            return AccountStatus.VALID;

        } catch (YggdrasilRequestException e) {
            WawelAuth.debug("Validate failed for account " + account.getId() + ": " + e.getMessage());
            if (!isAuthFailureStatus(e.getHttpStatus())) {
                markUnverified(account, e.getMessage());
                return AccountStatus.UNVERIFIED;
            }
            // Fall through to refresh
        } catch (IOException e) {
            // Network error: offline degradation
            WawelAuth.debug("Validate network error for account " + account.getId() + ": " + e.getMessage());
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        }

        // Step 2: Refresh
        try {
            String accessToken = account.getAccessToken();
            JsonObject refreshBody = new JsonObject();
            refreshBody.addProperty("accessToken", accessToken);
            if (account.getClientToken() != null) {
                refreshBody.addProperty("clientToken", account.getClientToken());
            }
            refreshBody.addProperty("requestUser", true);

            // Include selectedProfile to maintain binding (per Yggdrasil spec)
            if (account.getProfileUuid() != null) {
                JsonObject selectedProfileObj = new JsonObject();
                selectedProfileObj.addProperty("id", UuidUtil.toUnsigned(account.getProfileUuid()));
                if (account.getProfileName() != null) {
                    selectedProfileObj.addProperty("name", account.getProfileName());
                }
                refreshBody.add("selectedProfile", selectedProfileObj);
            }

            JsonObject response = httpClient.postJson(provider, provider.authUrl("/refresh"), refreshBody);

            // 200: token refreshed
            String newAccessToken = response.get("accessToken")
                .getAsString();
            String newClientToken = response.get("clientToken")
                .getAsString();

            account.setAccessToken(newAccessToken);
            account.setClientToken(newClientToken);

            // Update profile if returned
            if (response.has("selectedProfile") && !response.get("selectedProfile")
                .isJsonNull()) {
                JsonObject profile = response.getAsJsonObject("selectedProfile");
                account.setProfileUuid(
                    UuidUtil.fromUnsigned(
                        profile.get("id")
                            .getAsString()));
                account.setProfileName(
                    profile.get("name")
                        .getAsString());
            }

            account.setStatus(AccountStatus.REFRESHED);
            account.setLastValidatedAt(now);
            account.setConsecutiveFailures(0);
            account.setLastError(null);
            account.setTokenIssuedAt(now);
            accountDAO.update(account);
            statusCache.put(account.getId(), AccountStatus.REFRESHED);
            WawelAuth.debug("Account " + account.getId() + " (" + account.getProfileName() + ") refreshed OK");
            return AccountStatus.REFRESHED;

        } catch (YggdrasilRequestException e) {
            WawelAuth.debug("Refresh failed for account " + account.getId() + ": " + e.getMessage());
            if (!isAuthFailureStatus(e.getHttpStatus())) {
                markUnverified(account, e.getMessage());
                return AccountStatus.UNVERIFIED;
            }
            // Both validate and refresh failed with auth-failure status: token is expired
            markStatus(account, AccountStatus.EXPIRED, e.getMessage());
            WawelAuth.LOG.warn(
                "Account '{}' on '{}' expired: re-authentication required",
                account.getProfileName(),
                account.getProviderName());
            return AccountStatus.EXPIRED;

        } catch (IOException e) {
            // Network error during refresh: offline degradation
            WawelAuth.debug("Refresh network error for account " + account.getId() + ": " + e.getMessage());
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        }
    }

    private AccountStatus doValidateOrRefreshMicrosoft(ClientAccount account, ClientProvider provider) {
        provider = applyRuntimeProviderOverrides(provider);
        long now = System.currentTimeMillis();
        account.setLastRefreshAttemptAt(now);

        WawelAuth.debug(
            "Validating Microsoft account " + account.getId()
                + " ("
                + account.getProfileName()
                + ") on provider '"
                + provider.getName()
                + "' using proxy "
                + org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport
                    .describeProxySettings(provider.getProxySettings()));

        // Step 1: validate by calling Minecraft profile endpoint with current token.
        try {
            MicrosoftOAuthClient.MinecraftProfile profile = microsoftOAuthClient
                .fetchMinecraftProfile(account.getAccessToken(), provider);
            account.setUserUuid(UuidUtil.toUnsigned(profile.getUuid()));
            account.setProfileUuid(profile.getUuid());
            account.setProfileName(profile.getName());
            account.setStatus(AccountStatus.VALID);
            account.setLastValidatedAt(now);
            account.setConsecutiveFailures(0);
            account.setLastError(null);
            accountDAO.update(account);
            statusCache.put(account.getId(), AccountStatus.VALID);
            WawelAuth
                .debug("Microsoft account " + account.getId() + " (" + account.getProfileName() + ") validated OK");
            return AccountStatus.VALID;
        } catch (MicrosoftOAuthClient.HttpStatusException e) {
            if (!isAuthFailureStatus(e.getStatusCode())) {
                WawelAuth.debug(
                    "Microsoft validate HTTP " + e
                        .getStatusCode() + " for account " + account.getId() + ": " + e.getMessage());
                markUnverified(account, e.getMessage());
                return AccountStatus.UNVERIFIED;
            }
            // auth failure -> try refresh
            WawelAuth.debug("Microsoft token rejected for account " + account.getId() + ", attempting refresh");
        } catch (IOException e) {
            WawelAuth.debug("Microsoft validate network error for account " + account.getId() + ": " + e.getMessage());
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        }

        // Step 2: refresh via Microsoft refresh token.
        if (account.getRefreshToken() == null || account.getRefreshToken()
            .trim()
            .isEmpty()) {
            markStatus(account, AccountStatus.EXPIRED, "Missing Microsoft refresh token");
            return AccountStatus.EXPIRED;
        }

        try {
            MicrosoftOAuthClient.LoginResult refreshed = microsoftOAuthClient
                .refreshFromToken(account.getRefreshToken(), provider, s -> {});

            account.setAccessToken(refreshed.getMinecraftAccessToken());
            account.setRefreshToken(refreshed.getMicrosoftRefreshToken());
            account.setUserUuid(UuidUtil.toUnsigned(refreshed.getProfileUuid()));
            account.setProfileUuid(refreshed.getProfileUuid());
            account.setProfileName(refreshed.getProfileName());
            account.setStatus(AccountStatus.REFRESHED);
            account.setLastValidatedAt(now);
            account.setConsecutiveFailures(0);
            account.setLastError(null);
            account.setTokenIssuedAt(now);
            accountDAO.update(account);
            statusCache.put(account.getId(), AccountStatus.REFRESHED);
            WawelAuth
                .debug("Microsoft account " + account.getId() + " (" + account.getProfileName() + ") refreshed OK");
            return AccountStatus.REFRESHED;
        } catch (MicrosoftOAuthClient.HttpStatusException e) {
            if (isAuthFailureStatus(e.getStatusCode())) {
                markStatus(account, AccountStatus.EXPIRED, e.getMessage());
                WawelAuth.LOG.warn(
                    "Microsoft account '{}' on '{}' expired: re-authentication required",
                    account.getProfileName(),
                    account.getProviderName());
                return AccountStatus.EXPIRED;
            }
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        } catch (IOException e) {
            WawelAuth.debug("Microsoft refresh network error for account " + account.getId() + ": " + e.getMessage());
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        }
    }

    private AccountStatus doValidateOrRefreshProviderOauth(ClientAccount account, ClientProvider provider) {
        provider = applyRuntimeProviderOverrides(provider);
        ProviderOAuthClient oauthClient = ProviderOAuthClients.resolve(provider);
        if (oauthClient == null) {
            markStatus(account, AccountStatus.EXPIRED, "OAuth provider handler not found");
            return AccountStatus.EXPIRED;
        }

        long now = System.currentTimeMillis();
        account.setLastRefreshAttemptAt(now);

        WawelAuth.debug(
            "Validating OAuth account " + account.getId()
                + " ("
                + account.getProfileName()
                + ") on provider '"
                + provider.getName()
                + "' using proxy "
                + org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport
                    .describeProxySettings(provider.getProxySettings()));

        try {
            if (oauthClient.supportsProfileValidation()) {
                ProviderOAuthClient.MinecraftProfile profile = oauthClient
                    .fetchProfile(account.getAccessToken(), provider);
                account.setUserUuid(UuidUtil.toUnsigned(profile.getUuid()));
                account.setProfileUuid(profile.getUuid());
                account.setProfileName(profile.getName());
            } else {
                JsonObject validateBody = new JsonObject();
                validateBody.addProperty("accessToken", account.getAccessToken());
                if (account.getClientToken() != null) {
                    validateBody.addProperty("clientToken", account.getClientToken());
                }
                httpClient.postJson(provider, provider.authUrl("/validate"), validateBody);
            }
            account.setStatus(AccountStatus.VALID);
            account.setLastValidatedAt(now);
            account.setConsecutiveFailures(0);
            account.setLastError(null);
            accountDAO.update(account);
            statusCache.put(account.getId(), AccountStatus.VALID);
            WawelAuth.debug("OAuth account " + account.getId() + " (" + account.getProfileName() + ") validated OK");
            return AccountStatus.VALID;
        } catch (ProviderOAuthClient.HttpStatusException e) {
            if (!isAuthFailureStatus(e.getStatusCode())) {
                markUnverified(account, e.getMessage());
                return AccountStatus.UNVERIFIED;
            }
        } catch (YggdrasilRequestException e) {
            if (!isAuthFailureStatus(e.getHttpStatus())) {
                markUnverified(account, e.getMessage());
                return AccountStatus.UNVERIFIED;
            }
        } catch (IOException e) {
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        }

        if (account.getRefreshToken() == null || account.getRefreshToken()
            .trim()
            .isEmpty()) {
            markStatus(account, AccountStatus.EXPIRED, "Missing OAuth refresh token");
            return AccountStatus.EXPIRED;
        }

        try {
            ProviderOAuthClient.LoginResult refreshed = oauthClient.refreshFromToken(
                account.getRefreshToken(),
                provider,
                account.getProfileUuid(),
                account.getProfileName(),
                account.getAccessToken(),
                s -> {});

            account.setAccessToken(refreshed.getAccessToken());
            account.setRefreshToken(refreshed.getRefreshToken());
            account.setClientToken(refreshed.getClientToken());
            account.setUserUuid(UuidUtil.toUnsigned(refreshed.getProfileUuid()));
            account.setProfileUuid(refreshed.getProfileUuid());
            account.setProfileName(refreshed.getProfileName());
            account.setStatus(AccountStatus.REFRESHED);
            account.setLastValidatedAt(now);
            account.setConsecutiveFailures(0);
            account.setLastError(null);
            account.setTokenIssuedAt(now);
            accountDAO.update(account);
            statusCache.put(account.getId(), AccountStatus.REFRESHED);
            WawelAuth.debug("OAuth account " + account.getId() + " (" + account.getProfileName() + ") refreshed OK");
            return AccountStatus.REFRESHED;
        } catch (ProviderOAuthClient.HttpStatusException e) {
            if (isAuthFailureStatus(e.getStatusCode())) {
                markStatus(account, AccountStatus.EXPIRED, e.getMessage());
                WawelAuth.LOG.warn(
                    "OAuth account '{}' on '{}' expired: re-authentication required",
                    account.getProfileName(),
                    account.getProviderName());
                return AccountStatus.EXPIRED;
            }
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        } catch (IOException e) {
            markUnverified(account, e.getMessage());
            return AccountStatus.UNVERIFIED;
        }
    }

    private static boolean isMicrosoftManagedMojangAccount(ClientProvider provider, ClientAccount account) {
        return provider != null && account != null
            && BuiltinProviders.isMojangProvider(provider.getName())
            && account.getRefreshToken() != null
            && !account.getRefreshToken()
                .trim()
                .isEmpty();
    }

    private static boolean isProviderOauthManagedAccount(ClientProvider provider, ClientAccount account) {
        return provider != null && account != null
            && ProviderOAuthClients.resolve(provider) != null
            && account.getRefreshToken() != null
            && !account.getRefreshToken()
                .trim()
                .isEmpty();
    }

    private static ClientProvider applyRuntimeProviderOverrides(ClientProvider provider) {
        WawelClient client = WawelClient.instance();
        if (client == null || client.getProviderRegistry() == null) {
            return provider;
        }
        return client.getProviderRegistry()
            .applyRuntimeOverrides(provider);
    }

    private static boolean isAuthFailureStatus(int status) {
        return status == 400 || status == 401 || status == 403;
    }

    // =========================================================================
    // Token helpers (read/write are plain pass-through for now; a future
    // encryption layer would hook in here)
    // =========================================================================

    private String doUploadTexturesMicrosoft(ClientAccount account, ClientProvider provider, File skinFile,
        File capeFile, boolean skinSlim) throws IOException, YggdrasilRequestException {
        if (skinFile == null && capeFile != null) {
            throw new IllegalArgumentException("Microsoft accounts do not support custom cape upload.");
        }
        if (skinFile == null) {
            throw new IllegalArgumentException("Select a skin file. Microsoft cape upload is not supported.");
        }

        ensureReadablePngFile(skinFile, "Skin");

        // Mojang/Microsoft accounts use Minecraft Services tokens; validate against profile endpoint.
        AccountStatus status = doValidateOrRefreshMicrosoft(account, provider);
        if (status == AccountStatus.EXPIRED) {
            throw new IllegalArgumentException("Account token expired. Please re-authenticate first.");
        }
        if (status == AccountStatus.UNVERIFIED) {
            throw new IOException("Account token could not be verified (provider unreachable).");
        }

        String accessToken = account.getAccessToken();
        String baseUrl = resolveServicesBase(provider);
        String skinUrl = baseUrl + "/minecraft/profile/skins";

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("variant", skinSlim ? "slim" : "classic");
        httpClient.postMultipart(provider, skinUrl, accessToken, "file", skinFile, "image/png", fields);

        String result = capeFile != null ? "Uploaded skin. Microsoft API does not support custom cape upload."
            : "Uploaded skin.";
        WawelAuth.debug("Texture upload completed for Microsoft account " + account.getId() + ": " + result);
        return result;
    }

    private String doDeleteTextures(ClientAccount account) throws IOException, YggdrasilRequestException {
        if (account.getProfileUuid() == null) {
            throw new IllegalArgumentException("Selected account has no bound profile UUID.");
        }

        ClientProvider provider = providerDAO.findByName(account.getProviderName());
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + account.getProviderName());
        }
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            return doDeleteOfflineTextures(account);
        }
        if (BuiltinProviders.isMojangProvider(provider.getName())) {
            return doDeleteTexturesMicrosoft(account, provider);
        }

        // Ensure the account token is usable before delete.
        AccountStatus status = doValidateOrRefresh(account);
        if (status == AccountStatus.EXPIRED) {
            throw new IllegalArgumentException("Account token expired. Please re-authenticate first.");
        }
        if (status == AccountStatus.UNVERIFIED) {
            throw new IOException("Account token could not be verified (provider unreachable).");
        }

        String accessToken = account.getAccessToken();
        String baseUrl = resolveServicesBase(provider);
        String profileId = UuidUtil.toUnsigned(account.getProfileUuid());

        httpClient.deleteWithAuth(provider, baseUrl + "/api/user/profile/" + profileId + "/skin", accessToken);
        httpClient.deleteWithAuth(provider, baseUrl + "/api/user/profile/" + profileId + "/cape", accessToken);

        String result = "Skin and cape reset.";
        WawelAuth.debug("Texture delete completed for account " + account.getId() + ": " + result);
        return result;
    }

    private String doDeleteTexturesMicrosoft(ClientAccount account, ClientProvider provider)
        throws IOException, YggdrasilRequestException {
        // Validate/refresh Microsoft token.
        AccountStatus status = doValidateOrRefreshMicrosoft(account, provider);
        if (status == AccountStatus.EXPIRED) {
            throw new IllegalArgumentException("Account token expired. Please re-authenticate first.");
        }
        if (status == AccountStatus.UNVERIFIED) {
            throw new IOException("Account token could not be verified (provider unreachable).");
        }

        String accessToken = account.getAccessToken();
        String baseUrl = resolveServicesBase(provider);
        httpClient.deleteWithAuth(provider, baseUrl + "/minecraft/profile/skins/active", accessToken);

        String result = "Skin reset.";
        WawelAuth.debug("Texture delete completed for Microsoft account " + account.getId() + ": " + result);
        return result;
    }

    private String doDeleteOfflineTextures(ClientAccount account) {
        account.setLocalSkinPath(null);
        account.setLocalSkinModel(null);
        account.setLocalCapePath(null);
        accountDAO.update(account);
        return "Cleared local offline skin and cape.";
    }

    private static String resolveServicesBase(ClientProvider provider) {
        String base = provider.getServicesUrl();
        if (base == null || base.trim()
            .isEmpty()) {
            base = provider.getApiRoot();
        }
        if (base == null || base.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Provider has no services/api base URL.");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String resolveMetadataBase(ClientProvider provider) {
        String base = provider.getApiRoot();
        if (base == null || base.trim()
            .isEmpty()) {
            base = resolveServicesBase(provider);
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private boolean supportsWawelAuthCredentialManagement(ClientProvider provider)
        throws IOException, YggdrasilRequestException {
        if (provider == null) {
            return false;
        }
        if (provider.getType() == ProviderType.BUILTIN) {
            return false;
        }
        if (BuiltinProviders.isMojangProvider(provider.getName())) {
            return false;
        }
        return doProbeSupportsWawelRegister(provider.getName());
    }

    private static boolean shouldRemoveLocalAccountAfterServerDelete(ClientAccount candidate, ClientAccount anchor,
        String anchorUserUuid) {
        if (candidate == null || anchor == null) {
            return false;
        }

        if (anchorUserUuid != null) {
            String candidateUserUuid = trimToNull(candidate.getUserUuid());
            return anchorUserUuid.equalsIgnoreCase(candidateUserUuid);
        }

        if (anchor.getProfileUuid() != null && candidate.getProfileUuid() != null) {
            return anchor.getProfileUuid()
                .equals(candidate.getProfileUuid());
        }

        return candidate.getId() == anchor.getId();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void ensureReadablePngFile(File file, String label) {
        ensureReadableImageFile(file, label);
        String lower = file.getName()
            .toLowerCase();
        if (!lower.endsWith(".png")) {
            throw new IllegalArgumentException(label + " file must be a .png image.");
        }
    }

    private static void ensureReadableImageFile(File file, String label) {
        if (file == null) return;
        if (!file.isFile()) {
            throw new IllegalArgumentException(label + " file does not exist: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException(label + " file is not readable: " + file.getAbsolutePath());
        }
        String lower = file.getName()
            .toLowerCase();
        if (!lower.endsWith(".png") && !lower.endsWith(".gif")) {
            throw new IllegalArgumentException(label + " file must be a .png or .gif image.");
        }
    }

    private static String imageContentTypeFor(File file) {
        String lower = file.getName()
            .toLowerCase();
        return lower.endsWith(".gif") ? "image/gif" : "image/png";
    }

    // =========================================================================
    // Status update helpers
    // =========================================================================

    private void markStatus(ClientAccount account, AccountStatus status, String error) {
        long now = System.currentTimeMillis();
        account.setStatus(status);
        account.setLastError(error);
        account.setLastErrorAt(now);
        account.setLastRefreshAttemptAt(now);
        accountDAO.update(account);
        statusCache.put(account.getId(), status);
    }

    private void markUnverified(ClientAccount account, String error) {
        long now = System.currentTimeMillis();
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setLastError(error);
        account.setLastErrorAt(now);
        account.setLastRefreshAttemptAt(now);
        account.setConsecutiveFailures(account.getConsecutiveFailures() + 1);
        accountDAO.update(account);
        statusCache.put(account.getId(), AccountStatus.UNVERIFIED);
    }

    private void markUnauthed(ClientAccount account) {
        account.setStatus(AccountStatus.UNAUTHED);
        account.setLastError(null);
        account.setLastErrorAt(0L);
        account.setLastRefreshAttemptAt(0L);
        account.setConsecutiveFailures(0);
        accountDAO.update(account);
        statusCache.put(account.getId(), AccountStatus.UNAUTHED);
    }

    private static String normalizeOfflineAccountName(String username) {
        String clean = trimToNull(username);
        if (clean == null) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (!OFFLINE_ACCOUNT_NAME_PATTERN.matcher(clean)
            .matches()) {
            throw new IllegalArgumentException("Offline account names must use 1-16 letters, digits, or underscores.");
        }
        return clean;
    }

    private String doUploadTextures(ClientAccount account, File skinFile, File capeFile, boolean skinSlim)
        throws IOException, YggdrasilRequestException {
        if (account.getProfileUuid() == null) {
            throw new IllegalArgumentException("Selected account has no bound profile UUID.");
        }

        ClientProvider provider = providerDAO.findByName(account.getProviderName());
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + account.getProviderName());
        }
        if (BuiltinProviders.isOfflineProvider(provider.getName())) {
            return doUploadOfflineTextures(account, skinFile, capeFile, skinSlim);
        }
        if (BuiltinProviders.isMojangProvider(provider.getName())) {
            return doUploadTexturesMicrosoft(account, provider, skinFile, capeFile, skinSlim);
        }

        // Ensure the account token is usable before upload.
        AccountStatus status = doValidateOrRefresh(account);
        if (status == AccountStatus.EXPIRED) {
            throw new IllegalArgumentException("Account token expired. Please re-authenticate first.");
        }
        if (status == AccountStatus.UNVERIFIED) {
            throw new IOException("Account token could not be verified (provider unreachable).");
        }

        if (skinFile == null && capeFile == null) {
            throw new IllegalArgumentException("Select a skin or cape file.");
        }
        if (skinFile != null) {
            ensureReadableImageFile(skinFile, "Skin");
        }
        if (capeFile != null) {
            ensureReadableImageFile(capeFile, "Cape");
        }

        String accessToken = account.getAccessToken();
        String baseUrl = resolveServicesBase(provider);
        String profileId = UuidUtil.toUnsigned(account.getProfileUuid());

        int uploaded = 0;

        if (skinFile != null) {
            String skinUrl = baseUrl + "/api/user/profile/" + profileId + "/skin";
            Map<String, String> skinFields = new LinkedHashMap<>();
            skinFields.put("model", skinSlim ? "slim" : "");
            httpClient.putMultipart(
                provider,
                skinUrl,
                accessToken,
                "file",
                skinFile,
                imageContentTypeFor(skinFile),
                skinFields);
            uploaded++;
        }

        if (capeFile != null) {
            String capeUrl = baseUrl + "/api/user/profile/" + profileId + "/cape";
            Map<String, String> capeFields = new LinkedHashMap<>();
            httpClient.putMultipart(
                provider,
                capeUrl,
                accessToken,
                "file",
                capeFile,
                imageContentTypeFor(capeFile),
                capeFields);
            uploaded++;
        }

        String result;
        if (uploaded == 2) {
            result = "Uploaded skin and cape.";
        } else if (skinFile != null) {
            result = "Uploaded skin.";
        } else {
            result = "Uploaded cape.";
        }
        WawelAuth.debug("Texture upload completed for account " + account.getId() + ": " + result);
        return result;
    }

    private String doUploadOfflineTextures(ClientAccount account, File skinFile, File capeFile, boolean skinSlim)
        throws IOException {
        if (skinFile == null && capeFile == null) {
            throw new IllegalArgumentException("Select a skin or cape file.");
        }

        if (skinFile != null) {
            ensureReadablePngFile(skinFile, "Skin");
            account.setLocalSkinPath(skinFile.getAbsolutePath());
            account.setLocalSkinModel(skinSlim ? SkinModel.SLIM : SkinModel.CLASSIC);
        }
        if (capeFile != null) {
            ensureReadableImageFile(capeFile, "Cape");
            account.setLocalCapePath(capeFile.getAbsolutePath());
        }
        accountDAO.update(account);
        if (skinFile != null && capeFile != null) {
            return "Applied local offline skin and cape.";
        }
        if (skinFile != null) {
            return "Applied local offline skin.";
        }
        return "Applied local offline cape.";
    }

}
