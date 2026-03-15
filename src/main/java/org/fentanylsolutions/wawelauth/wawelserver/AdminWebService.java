package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListWhitelist;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.WawelFaceRendererServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.JsonConfigIO;
import org.fentanylsolutions.wawelauth.wawelcore.config.RegistrationPolicy;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.KeyManager;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.PasswordHasher;
import org.fentanylsolutions.wawelauth.wawelcore.data.AdminPlayerListType;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelInvite;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelUser;
import org.fentanylsolutions.wawelauth.wawelcore.storage.AdminPlayerListProviderBindingDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.InviteDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TokenDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.UserDAO;
import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.fentanylsolutions.wawelauth.wawelcore.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelnet.BinaryResponse;
import org.fentanylsolutions.wawelauth.wawelnet.HttpRouter;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;
import org.fentanylsolutions.wawelauth.wawelnet.RequestContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

/**
 * Admin web UI and API endpoints.
 * <p>
 * Auth:
 * <p>
 * - Admin secret comes from server.admin.tokenEnvVar (preferred) or server.admin.token.
 * <p>
 * - POST /api/wawelauth/admin/login mints short-lived session tokens.
 * <p>
 * - Non-HTTPS transport requires encrypted login payload (RSA-OAEP with server public key).
 */
public class AdminWebService {

    private static final String ADMIN_HEADER = "X-WawelAuth-Admin-Session";
    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int HTTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int HTTP_READ_TIMEOUT_MS = 10_000;
    private static final int MAX_HTTP_BYTES = 4_194_304;
    private static final long AVATAR_CACHE_TTL_MS = 60_000L;
    private static final long MAIN_THREAD_WAIT_MS = 15_000L;

    private final ServerConfig serverConfig;
    private final KeyManager keyManager;
    private final UserDAO userDAO;
    private final ProfileDAO profileDAO;
    private final TokenDAO tokenDAO;
    private final InviteDAO inviteDAO;
    private final AdminPlayerListProviderBindingDAO adminPlayerListProviderBindingDAO;
    private final ConcurrentMap<String, AdminSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedAvatar> avatarCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<MainThreadTask<?>> mainThreadQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mainThreadPumpInstalled = new AtomicBoolean(false);

    private final byte[] indexHtml;
    private final byte[] appJs;
    private final byte[] stylesCss;
    private final byte[] logoPng;
    private final byte[] packFallbackPng;
    private final byte[] nerdSymbolsSubsetWoff2;

    public AdminWebService(ServerConfig serverConfig, KeyManager keyManager, UserDAO userDAO, ProfileDAO profileDAO,
        TokenDAO tokenDAO, InviteDAO inviteDAO, AdminPlayerListProviderBindingDAO adminPlayerListProviderBindingDAO) {
        this.serverConfig = serverConfig;
        this.keyManager = keyManager;
        this.userDAO = userDAO;
        this.profileDAO = profileDAO;
        this.tokenDAO = tokenDAO;
        this.inviteDAO = inviteDAO;
        this.adminPlayerListProviderBindingDAO = adminPlayerListProviderBindingDAO;
        this.indexHtml = loadResourceBytes("/assets/wawelauth/web/admin/index.html");
        this.appJs = loadResourceBytes("/assets/wawelauth/web/admin/app.js");
        this.stylesCss = loadResourceBytes("/assets/wawelauth/web/admin/styles.css");
        this.logoPng = loadResourceBytes("/assets/wawelauth/Logo_Dragon_Outline.png");
        this.packFallbackPng = loadResourceBytes("/assets/wawelauth/web/admin/pack-fallback.png");
        this.nerdSymbolsSubsetWoff2 = loadResourceBytes(
            "/assets/wawelauth/web/admin/fonts/nerd-fonts/SymbolsNerdFont-Subset.woff2");

        if (serverConfig.getAdmin()
            .isEnabled() && resolveConfiguredAdminToken() == null) {
            WawelAuth.LOG.warn("Admin web UI enabled but no admin token configured.");
            WawelAuth.LOG.warn(
                "Set server.admin.token or export {} to enable admin login.",
                serverConfig.getAdmin()
                    .getTokenEnvVar());
        }
    }

    public void registerRoutes(HttpRouter router) {
        router.get("/admin", this::serveIndex);
        router.get("/admin/", this::serveIndex);
        router.get("/admin/app.js", this::serveAppJs);
        router.get("/admin/styles.css", this::serveStylesCss);
        router.get("/admin/logo-dragon-outline.png", this::serveLogoPng);
        router.get("/admin/server-icon.png", this::serveServerIconPng);
        router.get("/admin/server-icon.gif", this::serveServerIconGif);
        router.get("/admin/fonts/nerd-fonts/SymbolsNerdFont-Subset.woff2", this::serveNerdSymbolsSubsetWoff2);

        router.get("/api/wawelauth/admin/bootstrap", this::bootstrap);
        router.post("/api/wawelauth/admin/login", this::login);
        router.post("/api/wawelauth/admin/logout", this::logout);
        router.get("/api/wawelauth/admin/session", this::session);

        router.get("/api/wawelauth/admin/stats", this::stats);
        router.get("/api/wawelauth/admin/users", this::users);
        router.post("/api/wawelauth/admin/users/{uuid}/delete", this::deleteUser);
        router.post("/api/wawelauth/admin/users/{uuid}/reset-password", this::resetUserPassword);
        router.post("/api/wawelauth/admin/users/{uuid}/reset-textures", this::resetUserTextures);
        router.post("/api/wawelauth/admin/profiles/{uuid}/set-uuid", this::setProfileUuid);
        router.post("/api/wawelauth/admin/profiles/{uuid}/use-offline-uuid", this::useOfflineProfileUuid);
        router.get("/api/wawelauth/admin/providers", this::providers);
        router.post("/api/wawelauth/admin/resolve-profile", this::resolveProfileForProvider);
        router.get("/api/wawelauth/admin/avatar", this::avatar);
        router.get("/api/wawelauth/admin/whitelist", this::whitelistState);
        router.post("/api/wawelauth/admin/whitelist/add", this::whitelistAdd);
        router.post("/api/wawelauth/admin/whitelist/remove", this::whitelistRemove);
        router.post("/api/wawelauth/admin/whitelist/enabled", this::whitelistEnabled);
        router.get("/api/wawelauth/admin/ops", this::opsState);
        router.post("/api/wawelauth/admin/ops/add", this::opsAdd);
        router.post("/api/wawelauth/admin/ops/remove", this::opsRemove);
        router.get("/api/wawelauth/admin/invites", this::invites);
        router.post("/api/wawelauth/admin/invites", this::createInvite);
        router.delete("/api/wawelauth/admin/invites/{code}", this::deleteInvite);
        router.post("/api/wawelauth/admin/invites/purge", this::purgeInvites);
        router.get("/api/wawelauth/admin/config/server", this::getServerConfig);
        router.post("/api/wawelauth/admin/config/server", this::updateServerConfig);
        router.get("/api/wawelauth/admin/config/server-properties", this::getServerProperties);
        router.post("/api/wawelauth/admin/config/server-properties", this::updateServerProperties);
    }

    private Object serveIndex(RequestContext ctx) {
        return staticResponse(indexHtml, "text/html; charset=utf-8");
    }

    private Object serveAppJs(RequestContext ctx) {
        return staticResponse(appJs, "application/javascript; charset=utf-8");
    }

    private Object serveStylesCss(RequestContext ctx) {
        return staticResponse(stylesCss, "text/css; charset=utf-8");
    }

    private Object serveLogoPng(RequestContext ctx) {
        return staticResponse(logoPng, "image/png");
    }

    private Object serveServerIconPng(RequestContext ctx) {
        byte[] iconBytes = readExistingFileBytes(resolveServerDirectoryFile("server-icon.png"));
        return staticResponse(iconBytes != null ? iconBytes : packFallbackPng, "image/png");
    }

    private Object serveServerIconGif(RequestContext ctx) {
        byte[] iconBytes = readExistingFileBytes(resolveServerDirectoryFile("server-icon.gif"));
        if (iconBytes == null) {
            throw NetException.notFound("Animated server icon not found.");
        }
        return staticResponse(iconBytes, "image/gif");
    }

    private Object serveNerdSymbolsSubsetWoff2(RequestContext ctx) {
        return staticResponse(nerdSymbolsSubsetWoff2, "font/woff2");
    }

    private Object bootstrap(RequestContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = isAdminEnabled();
        boolean tokenConfigured = resolveConfiguredAdminToken() != null;
        boolean requireEncryption = requiresEncryptedLogin(ctx);

        out.put("enabled", enabled);
        out.put("tokenConfigured", tokenConfigured);
        out.put("requireEncryption", requireEncryption);
        out.put("publicKeyBase64", requireEncryption ? keyManager.getPublicKeyBase64() : null);
        out.put("serverName", serverConfig.getServerName());
        out.put("apiRoot", serverConfig.getApiRoot());
        out.put(
            "implementationName",
            serverConfig.getMeta()
                .getImplementationName());
        out.put(
            "modVersion",
            serverConfig.getMeta()
                .getImplementationVersion());
        out.put(
            "registrationPolicy",
            serverConfig.getRegistration()
                .getPolicy()
                .name());
        out.put("serverIconStaticAvailable", hasServerDirectoryFile("server-icon.png"));
        out.put("serverIconAnimatedAvailable", hasServerDirectoryFile("server-icon.gif"));
        out.put(
            "sessionTtlMs",
            sanitizeSessionTtl(
                serverConfig.getAdmin()
                    .getSessionTtlMs()));
        return out;
    }

    private Object login(RequestContext ctx) {
        ensureOperational();

        String configuredToken = resolveConfiguredAdminToken();
        if (configuredToken == null) {
            throw NetException.forbidden("Admin token is not configured.");
        }

        String providedToken;
        if (requiresEncryptedLogin(ctx)) {
            String encryptedToken = ctx.optJsonString("encryptedToken");
            if (encryptedToken == null) {
                throw NetException.illegalArgument("Missing encryptedToken.");
            }
            providedToken = decryptEncryptedToken(encryptedToken);
        } else {
            providedToken = ctx.optJsonString("token");
            if (providedToken == null) {
                throw NetException.illegalArgument("Missing token.");
            }
        }

        if (!constantTimeEquals(configuredToken, providedToken)) {
            throw NetException.forbidden("Invalid admin token.");
        }

        long now = System.currentTimeMillis();
        long ttl = sanitizeSessionTtl(
            serverConfig.getAdmin()
                .getSessionTtlMs());
        String sessionToken = randomSessionToken();
        sessions.put(sessionToken, new AdminSession(now + ttl));
        cleanupExpiredSessions(now);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionToken", sessionToken);
        out.put("expiresAt", now + ttl);
        return out;
    }

    private Object logout(RequestContext ctx) {
        ensureOperational();
        String token = extractSessionToken(ctx);
        if (token != null) {
            sessions.remove(token);
        }
        return null; // 204
    }

    private Object session(RequestContext ctx) {
        AdminSession adminSession = requireSession(ctx);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expiresAt", adminSession.expiresAt);
        return out;
    }

    private Object stats(RequestContext ctx) {
        requireSession(ctx);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", userDAO.count());
        out.put("profiles", profileDAO.count());
        out.put("tokens", tokenDAO.count());
        out.put(
            "invites",
            inviteDAO.listAll()
                .size());
        out.put("activeAdminSessions", sessions.size());

        File stateDir = new File(Config.getConfigDir(), "data");
        out.put("databaseSizeBytes", computeDatabaseSize(stateDir));
        out.put("textureStorageSizeBytes", computeTextureStorageSize(stateDir));
        out.put("textureFileCount", countTextureFiles(stateDir));

        return out;
    }

    private static long computeDatabaseSize(File stateDir) {
        long total = 0;
        String[] dbFiles = { "wawelauth.db", "wawelauth.db-wal", "wawelauth.db-shm" };
        for (String name : dbFiles) {
            File f = new File(stateDir, name);
            if (f.exists()) total += f.length();
        }
        return total;
    }

    private static long computeTextureStorageSize(File stateDir) {
        File textureDir = new File(stateDir, "textures");
        if (!textureDir.exists() || !textureDir.isDirectory()) return 0;
        long total = 0;
        File[] files = textureDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) total += f.length();
            }
        }
        return total;
    }

    private static int countTextureFiles(File stateDir) {
        File textureDir = new File(stateDir, "textures");
        if (!textureDir.exists() || !textureDir.isDirectory()) return 0;
        File[] files = textureDir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.isFile()) count++;
        }
        return count;
    }

    private Object users(RequestContext ctx) {
        requireSession(ctx);
        List<WawelUser> allUsers = new ArrayList<>(userDAO.listAll());
        Collections.sort(allUsers, Comparator.comparing(WawelUser::getUsername, String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> result = new ArrayList<>();
        for (WawelUser user : allUsers) {
            List<WawelProfile> profiles = profileDAO.findByOwner(user.getUuid());
            Collections.sort(profiles, Comparator.comparing(WawelProfile::getName, String.CASE_INSENSITIVE_ORDER));

            List<Map<String, Object>> profileJson = new ArrayList<>();
            for (WawelProfile profile : profiles) {
                profileJson.add(toAdminProfileJson(profile));
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", UuidUtil.toUnsigned(user.getUuid()));
            entry.put("username", user.getUsername());
            entry.put("admin", user.isAdmin());
            entry.put("locked", user.isLocked());
            entry.put("createdAt", user.getCreatedAt());
            entry.put("profiles", profileJson);
            result.add(entry);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", result);
        return out;
    }

    private Object deleteUser(RequestContext ctx) {
        requireSession(ctx);
        WawelUser user = requireUserByPathParam(ctx, "uuid");

        runInServerTransaction(() -> {
            tokenDAO.deleteByUser(user.getUuid());
            List<WawelProfile> profiles = profileDAO.findByOwner(user.getUuid());
            for (WawelProfile profile : profiles) {
                profileDAO.delete(profile.getUuid());
            }
            userDAO.delete(user.getUuid());
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", true);
        out.put("username", user.getUsername());
        return out;
    }

    private Object resetUserPassword(RequestContext ctx) {
        requireSession(ctx);
        WawelUser user = requireUserByPathParam(ctx, "uuid");

        String newPassword = trimToNull(ctx.optJsonString("newPassword"));
        if (newPassword == null) {
            throw NetException.illegalArgument("newPassword is required.");
        }

        PasswordHasher.HashResult hash = PasswordHasher.hash(newPassword);
        user.setPasswordHash(hash.getHash());
        user.setPasswordSalt(hash.getSalt());

        runInServerTransaction(() -> {
            userDAO.update(user);
            tokenDAO.deleteByUser(user.getUuid());
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updated", true);
        out.put("username", user.getUsername());
        return out;
    }

    private Object resetUserTextures(RequestContext ctx) {
        requireSession(ctx);
        WawelUser user = requireUserByPathParam(ctx, "uuid");
        List<WawelProfile> profiles = profileDAO.findByOwner(user.getUuid());

        int changed = 0;
        for (WawelProfile profile : profiles) {
            boolean modified = false;
            if (profile.getSkinHash() != null) {
                profile.setSkinHash(null);
                modified = true;
            }
            if (profile.getCapeHash() != null) {
                profile.setCapeHash(null);
                modified = true;
            }
            if (modified) {
                profileDAO.update(profile);
                changed++;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updatedProfiles", changed);
        out.put("username", user.getUsername());
        return out;
    }

    private Object setProfileUuid(RequestContext ctx) {
        requireSession(ctx);
        WawelProfile profile = requireProfileByPathParam(ctx, "uuid");
        String newUuidRaw = trimToNull(ctx.optJsonString("newUuid"));
        if (newUuidRaw == null) {
            throw NetException.illegalArgument("newUuid is required.");
        }
        return changeProfileUuid(profile, parseUuidFlexible(newUuidRaw), false);
    }

    private Object useOfflineProfileUuid(RequestContext ctx) {
        requireSession(ctx);
        WawelProfile profile = requireProfileByPathParam(ctx, "uuid");
        String name = trimToNull(profile.getName());
        if (name == null) {
            throw NetException.illegalArgument("Profile name is missing.");
        }
        return changeProfileUuid(profile, WawelProfile.computeOfflineUuid(name), true);
    }

    private Object changeProfileUuid(WawelProfile profile, UUID newUuid, boolean useOfflineUuid) {
        UUID oldUuid = profile.getUuid();
        if (oldUuid == null) {
            throw NetException.illegalArgument("Profile UUID is missing.");
        }
        String profileName = trimToNull(profile.getName());
        if (profileName == null) {
            throw NetException.illegalArgument("Profile name is missing.");
        }

        WawelUser owner = requireUser(userDAO, profile.getOwnerUuid());
        WawelProfile existing = profileDAO.findByUuid(newUuid);
        if (existing != null && !oldUuid.equals(existing.getUuid())) {
            throw NetException.illegalArgument("UUID is already assigned to profile " + existing.getName() + ".");
        }

        UUID offlineUuid = WawelProfile.computeOfflineUuid(profileName);
        if (newUuid.equals(oldUuid)) {
            return buildProfileUuidChangeResponse(
                owner,
                profile,
                oldUuid,
                newUuid,
                offlineUuid,
                useOfflineUuid,
                false,
                0,
                0);
        }

        final List<WawelProfile> ownerProfiles = new ArrayList<>(profileDAO.findByOwner(owner.getUuid()));
        final int invalidatedTokens = tokenDAO.findByUser(owner.getUuid())
            .size();
        final LinkedHashSet<UUID> kickUuids = new LinkedHashSet<>();
        final LinkedHashSet<String> kickNames = new LinkedHashSet<>();
        for (WawelProfile ownedProfile : ownerProfiles) {
            if (ownedProfile.getUuid() != null) {
                kickUuids.add(ownedProfile.getUuid());
            }
            String ownedName = trimToNull(ownedProfile.getName());
            if (ownedName != null) {
                kickNames.add(ownedName);
            }
        }
        kickUuids.add(oldUuid);
        kickUuids.add(newUuid);

        runInServerTransaction(() -> {
            tokenDAO.deleteByUser(owner.getUuid());
            profileDAO.delete(oldUuid);
            profile.setUuid(newUuid);
            profile.updateOfflineUuid();
            profileDAO.create(profile);
        });

        int kickedPlayers = callOnServerThread(
            () -> kickOnlineProfiles(
                kickUuids,
                kickNames,
                "Your Wawel Auth account was changed by an administrator. Please sign in again."));
        WawelProfile updatedProfile = requireProfile(profileDAO, newUuid);
        return buildProfileUuidChangeResponse(
            owner,
            updatedProfile,
            oldUuid,
            newUuid,
            updatedProfile.getOfflineUuid(),
            useOfflineUuid,
            true,
            invalidatedTokens,
            kickedPlayers);
    }

    private Object providers(RequestContext ctx) {
        requireSession(ctx);
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();

        for (ProviderChoice provider : getProviderChoices()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", provider.key);
            entry.put("label", provider.label);
            entry.put("type", provider.type);
            list.add(entry);
        }

        out.put("providers", list);
        return out;
    }

    private Object resolveProfileForProvider(RequestContext ctx) {
        requireSession(ctx);
        String username = trimToNull(ctx.optJsonString("username"));
        String providerKey = trimToNull(ctx.optJsonString("provider"));
        if (username == null) {
            throw NetException.illegalArgument("username is required.");
        }
        if (providerKey == null) {
            throw NetException.illegalArgument("provider is required.");
        }

        ProviderChoice provider = requireProviderChoice(providerKey);
        ResolvedProfile resolved = resolveProfileForProvider(username, provider);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", toProfileJson(resolved));
        return out;
    }

    private Object avatar(RequestContext ctx) {
        requireSession(ctx);
        String providerKey = trimToNull(ctx.getQueryParam("provider"));
        String uuidRaw = trimToNull(ctx.getQueryParam("uuid"));
        if (providerKey == null || uuidRaw == null) {
            throw NetException.illegalArgument("provider and uuid query params are required.");
        }

        ProviderChoice provider = requireProviderChoice(providerKey);
        UUID uuid = parseUuidFlexible(uuidRaw);
        String uuidUnsigned = UuidUtil.toUnsigned(uuid);
        String cacheKey = provider.key.toLowerCase(Locale.ROOT) + "|" + uuidUnsigned;

        CachedAvatar cached = avatarCache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return buildAvatarResponse(cached.pngBytes);
        }
        avatarCache.remove(cacheKey);

        String skinUrl = resolveSkinUrl(provider, uuid, uuidUnsigned);
        if (skinUrl == null) {
            throw NetException.notFound("No skin available.");
        }

        byte[] skinBytes = fetchBinary(skinUrl, MAX_HTTP_BYTES);
        byte[] avatarPng = renderFacePng(skinBytes);
        avatarCache.put(cacheKey, new CachedAvatar(avatarPng, System.currentTimeMillis() + AVATAR_CACHE_TTL_MS));

        return buildAvatarResponse(avatarPng);
    }

    private Object whitelistState(RequestContext ctx) {
        requireSession(ctx);
        return callOnServerThread(() -> {
            ServerConfigurationManager scm = requireServerConfigManager();
            UserListWhitelist whitelist = scm.func_152599_k() /* getWhiteListedPlayers */;
            Map<UUID, String> providerKeys = adminPlayerListProviderBindingDAO
                .findAllProviderKeys(AdminPlayerListType.WHITELIST);
            List<Map<String, Object>> entries = new ArrayList<>();
            List<GameProfile> profiles = readUserListProfiles(whitelist.func_152691_c());
            for (GameProfile profile : profiles) {
                if (profile == null || profile.getId() == null) continue;
                entries.add(toListEntryJson(profile, providerKeys.get(profile.getId())));
            }

            Collections.sort(
                entries,
                (a, b) -> String.valueOf(a.get("name"))
                    .compareToIgnoreCase(String.valueOf(b.get("name"))));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("enabled", scm.isWhiteListEnabled());
            out.put("entries", entries);
            return out;
        });
    }

    private Object whitelistEnabled(RequestContext ctx) {
        requireSession(ctx);
        Boolean enabled = readOptionalBoolean(ctx.getJsonBody(), "enabled");
        if (enabled == null) {
            throw NetException.illegalArgument("enabled is required.");
        }

        return callOnServerThread(() -> {
            ServerConfigurationManager scm = requireServerConfigManager();
            scm.setWhiteListEnabled(enabled.booleanValue());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("enabled", scm.isWhiteListEnabled());
            return out;
        });
    }

    private Object whitelistAdd(RequestContext ctx) {
        requireSession(ctx);
        String username = trimToNull(ctx.optJsonString("username"));
        String providerKey = trimToNull(ctx.optJsonString("provider"));
        if (username == null) {
            throw NetException.illegalArgument("username is required.");
        }
        if (providerKey == null) {
            throw NetException.illegalArgument("provider is required.");
        }

        ProviderChoice provider = requireProviderChoice(providerKey);
        ResolvedProfile resolved = resolveProfileForProvider(username, provider);

        callOnServerThread(() -> {
            ServerConfigurationManager scm = requireServerConfigManager();
            scm.func_152601_d(/* addWhitelistedPlayer */resolved.profile);
            return null;
        });
        adminPlayerListProviderBindingDAO
            .putProviderKey(AdminPlayerListType.WHITELIST, resolved.profile.getId(), resolved.providerKey);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", true);
        out.put("entry", toListEntryJson(resolved.profile, resolved.providerKey));
        return out;
    }

    private Object whitelistRemove(RequestContext ctx) {
        requireSession(ctx);
        GameProfile target = callOnServerThread(() -> {
            GameProfile found = requireExistingWhitelistEntry(ctx);
            ServerConfigurationManager scm = requireServerConfigManager();
            scm.func_152597_c(/* removePlayerFromWhitelist */found);
            return found;
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", true);
        out.put("name", target.getName());
        if (target.getId() != null) {
            adminPlayerListProviderBindingDAO.delete(AdminPlayerListType.WHITELIST, target.getId());
        }
        return out;
    }

    private Object opsState(RequestContext ctx) {
        requireSession(ctx);
        return callOnServerThread(() -> {
            ServerConfigurationManager scm = requireServerConfigManager();
            UserListOps ops = scm.func_152603_m() /* getOppedPlayers */;
            Map<UUID, String> providerKeys = adminPlayerListProviderBindingDAO
                .findAllProviderKeys(AdminPlayerListType.OPS);
            List<Map<String, Object>> entries = new ArrayList<>();
            List<GameProfile> profiles = readUserListProfiles(ops.func_152691_c());
            for (GameProfile profile : profiles) {
                if (profile == null || profile.getId() == null) continue;
                entries.add(toListEntryJson(profile, providerKeys.get(profile.getId())));
            }

            Collections.sort(
                entries,
                (a, b) -> String.valueOf(a.get("name"))
                    .compareToIgnoreCase(String.valueOf(b.get("name"))));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("entries", entries);
            return out;
        });
    }

    private Object opsAdd(RequestContext ctx) {
        requireSession(ctx);
        String username = trimToNull(ctx.optJsonString("username"));
        String providerKey = trimToNull(ctx.optJsonString("provider"));
        if (username == null) {
            throw NetException.illegalArgument("username is required.");
        }
        if (providerKey == null) {
            throw NetException.illegalArgument("provider is required.");
        }

        ProviderChoice provider = requireProviderChoice(providerKey);
        ResolvedProfile resolved = resolveProfileForProvider(username, provider);

        callOnServerThread(() -> {
            ServerConfigurationManager scm = requireServerConfigManager();
            scm.func_152605_a(/* addOp */resolved.profile);
            return null;
        });
        adminPlayerListProviderBindingDAO
            .putProviderKey(AdminPlayerListType.OPS, resolved.profile.getId(), resolved.providerKey);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", true);
        out.put("entry", toListEntryJson(resolved.profile, resolved.providerKey));
        return out;
    }

    private Object opsRemove(RequestContext ctx) {
        requireSession(ctx);
        GameProfile target = callOnServerThread(() -> {
            GameProfile found = requireExistingOpEntry(ctx);
            ServerConfigurationManager scm = requireServerConfigManager();
            scm.func_152610_b(/* removeOp */found);
            return found;
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", true);
        out.put("name", target.getName());
        if (target.getId() != null) {
            adminPlayerListProviderBindingDAO.delete(AdminPlayerListType.OPS, target.getId());
        }
        return out;
    }

    private Object invites(RequestContext ctx) {
        requireSession(ctx);
        List<WawelInvite> all = new ArrayList<>(inviteDAO.listAll());
        Collections.sort(all, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (WawelInvite invite : all) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", invite.getCode());
            entry.put("usesRemaining", invite.getUsesRemaining());
            entry.put("createdAt", invite.getCreatedAt());
            entry.put("createdBy", invite.getCreatedBy() == null ? null : UuidUtil.toUnsigned(invite.getCreatedBy()));
            result.add(entry);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invites", result);
        return out;
    }

    private Object createInvite(RequestContext ctx) {
        requireSession(ctx);
        JsonObject body = ctx.getJsonBody();
        String requestedCode = trimToNull(ctx.optJsonString("code"));

        int uses = serverConfig.getInvites()
            .getDefaultUses();
        JsonElement usesElement = body.get("uses");
        if (usesElement != null && !usesElement.isJsonNull()) {
            try {
                uses = usesElement.getAsInt();
            } catch (Exception e) {
                throw NetException.illegalArgument("uses must be an integer.");
            }
        }

        if (uses == 0 || uses < -1) {
            throw NetException.illegalArgument("uses must be -1 (unlimited) or a positive integer.");
        }

        String code = requestedCode != null ? requestedCode : createUniqueInviteCode();
        if (inviteDAO.findByCode(code) != null) {
            throw NetException.illegalArgument("Invite already exists: " + code);
        }

        WawelInvite invite = new WawelInvite();
        invite.setCode(code);
        invite.setCreatedAt(System.currentTimeMillis());
        invite.setCreatedBy((UUID) null);
        invite.setUsesRemaining(uses);
        inviteDAO.create(invite);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", invite.getCode());
        out.put("usesRemaining", invite.getUsesRemaining());
        out.put("createdAt", invite.getCreatedAt());
        return out;
    }

    private Object deleteInvite(RequestContext ctx) {
        requireSession(ctx);
        String code = trimToNull(ctx.getPathParam("code"));
        if (code == null) {
            throw NetException.illegalArgument("Invite code is required.");
        }
        if (inviteDAO.findByCode(code) == null) {
            throw NetException.notFound("Invite not found: " + code);
        }
        inviteDAO.delete(code);
        return null; // 204
    }

    private Object purgeInvites(RequestContext ctx) {
        requireSession(ctx);
        int before = inviteDAO.listAll()
            .size();
        inviteDAO.purgeConsumed();
        int after = inviteDAO.listAll()
            .size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("purged", before - after);
        return out;
    }

    private Object getServerConfig(RequestContext ctx) {
        requireSession(ctx);
        return buildServerConfigResponse();
    }

    private Object updateServerConfig(RequestContext ctx) {
        requireSession(ctx);
        JsonObject body = ctx.getJsonBody();

        String value = readOptionalString(body, "serverName");
        if (value != null) {
            serverConfig.setServerName(value);
        }

        value = readOptionalString(body, "apiRoot");
        if (value != null) {
            serverConfig.setApiRoot(value);
        }

        value = readOptionalString(body, "publicPagePath");
        if (value != null) {
            serverConfig.setPublicPagePath(value);
        }

        value = readOptionalString(body, "publicInfoApiPath");
        if (value != null) {
            serverConfig.setPublicInfoApiPath(value);
        }

        JsonArray skinDomainsArr = readOptionalArray(body, "skinDomains");
        if (skinDomainsArr != null) {
            List<String> domains = new ArrayList<>();
            for (JsonElement el : skinDomainsArr) {
                String d = el.isJsonNull() ? null : el.getAsString();
                if (d != null && !d.trim()
                    .isEmpty()) domains.add(d.trim());
            }
            serverConfig.setSkinDomains(domains);
        }

        JsonObject meta = readOptionalObject(body, "meta");
        if (meta != null) {
            value = readOptionalString(meta, "implementationName");
            if (value != null) {
                serverConfig.getMeta()
                    .setImplementationName(value);
            }
            value = readOptionalString(meta, "serverHomepage");
            if (value != null) {
                serverConfig.getMeta()
                    .setServerHomepage(value);
            }
            value = readOptionalString(meta, "serverRegister");
            if (value != null) {
                serverConfig.getMeta()
                    .setServerRegister(value);
            }
            value = readOptionalString(meta, "publicDescription");
            if (value != null) {
                serverConfig.getMeta()
                    .setPublicDescription(value);
            }
        }

        JsonObject features = readOptionalObject(body, "features");
        if (features != null) {
            Boolean bool = readOptionalBoolean(features, "legacySkinApi");
            if (bool != null) {
                serverConfig.getFeatures()
                    .setLegacySkinApi(bool);
            }
            bool = readOptionalBoolean(features, "noMojangNamespace");
            if (bool != null) {
                serverConfig.getFeatures()
                    .setNoMojangNamespace(bool);
            }
            bool = readOptionalBoolean(features, "usernameCheck");
            if (bool != null) {
                serverConfig.getFeatures()
                    .setUsernameCheck(bool);
            }
        }

        JsonObject registration = readOptionalObject(body, "registration");
        if (registration != null) {
            value = readOptionalString(registration, "policy");
            if (value != null) {
                try {
                    serverConfig.getRegistration()
                        .setPolicy(RegistrationPolicy.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw NetException.illegalArgument("registration.policy must be OPEN, INVITE_ONLY, or CLOSED.");
                }
            }

            value = readOptionalString(registration, "playerNameRegex");
            if (value != null) {
                serverConfig.getRegistration()
                    .setPlayerNameRegex(value);
            }

            JsonArray uploadableArr = readOptionalArray(registration, "defaultUploadableTextures");
            if (uploadableArr != null) {
                List<String> texTypes = new ArrayList<>();
                for (JsonElement el : uploadableArr) {
                    String t = el.isJsonNull() ? null : el.getAsString();
                    if (t != null && !t.trim()
                        .isEmpty()) texTypes.add(
                            t.trim()
                                .toLowerCase());
                }
                serverConfig.getRegistration()
                    .setDefaultUploadableTextures(texTypes);
            }
        }

        JsonObject invites = readOptionalObject(body, "invites");
        if (invites != null) {
            Integer defaultUses = readOptionalInt(invites, "defaultUses");
            if (defaultUses != null) {
                if (defaultUses == 0 || defaultUses < -1) {
                    throw NetException
                        .illegalArgument("invites.defaultUses must be -1 (unlimited) or a positive integer.");
                }
                serverConfig.getInvites()
                    .setDefaultUses(defaultUses);
            }
        }

        JsonObject tokens = readOptionalObject(body, "tokens");
        if (tokens != null) {
            Integer maxPerUser = readOptionalInt(tokens, "maxPerUser");
            if (maxPerUser != null) {
                if (maxPerUser < 1) {
                    throw NetException.illegalArgument("tokens.maxPerUser must be >= 1.");
                }
                serverConfig.getTokens()
                    .setMaxPerUser(maxPerUser);
            }

            Long timeoutMs = readOptionalLong(tokens, "sessionTimeoutMs");
            if (timeoutMs != null) {
                if (timeoutMs < 1) {
                    throw NetException.illegalArgument("tokens.sessionTimeoutMs must be >= 1.");
                }
                serverConfig.getTokens()
                    .setSessionTimeoutMs(timeoutMs);
            }
        }

        JsonObject textures = readOptionalObject(body, "textures");
        if (textures != null) {
            Integer maxSkinWidth = readOptionalInt(textures, "maxSkinWidth");
            if (maxSkinWidth != null) {
                if (maxSkinWidth < 1) throw NetException.illegalArgument("textures.maxSkinWidth must be >= 1.");
                serverConfig.getTextures()
                    .setMaxSkinWidth(maxSkinWidth);
            }

            Integer maxSkinHeight = readOptionalInt(textures, "maxSkinHeight");
            if (maxSkinHeight != null) {
                if (maxSkinHeight < 1) throw NetException.illegalArgument("textures.maxSkinHeight must be >= 1.");
                serverConfig.getTextures()
                    .setMaxSkinHeight(maxSkinHeight);
            }

            Integer maxCapeWidth = readOptionalInt(textures, "maxCapeWidth");
            if (maxCapeWidth != null) {
                if (maxCapeWidth < 1) throw NetException.illegalArgument("textures.maxCapeWidth must be >= 1.");
                serverConfig.getTextures()
                    .setMaxCapeWidth(maxCapeWidth);
            }

            Integer maxCapeHeight = readOptionalInt(textures, "maxCapeHeight");
            if (maxCapeHeight != null) {
                if (maxCapeHeight < 1) throw NetException.illegalArgument("textures.maxCapeHeight must be >= 1.");
                serverConfig.getTextures()
                    .setMaxCapeHeight(maxCapeHeight);
            }

            Integer maxFileSizeBytes = readOptionalInt(textures, "maxFileSizeBytes");
            if (maxFileSizeBytes != null) {
                if (maxFileSizeBytes < 1) throw NetException.illegalArgument("textures.maxFileSizeBytes must be >= 1.");
                serverConfig.getTextures()
                    .setMaxFileSizeBytes(maxFileSizeBytes);
            }

            Boolean allowElytra = readOptionalBoolean(textures, "allowElytra");
            if (allowElytra != null) {
                serverConfig.getTextures()
                    .setAllowElytra(allowElytra);
            }

            Boolean allowAnimatedCapes = readOptionalBoolean(textures, "allowAnimatedCapes");
            if (allowAnimatedCapes != null) {
                serverConfig.getTextures()
                    .setAllowAnimatedCapes(allowAnimatedCapes);
            }
            Integer maxCapeFrameCount = readOptionalInt(textures, "maxCapeFrameCount");
            if (maxCapeFrameCount != null) {
                if (maxCapeFrameCount < 2)
                    throw NetException.illegalArgument("textures.maxCapeFrameCount must be >= 2.");
                serverConfig.getTextures()
                    .setMaxCapeFrameCount(maxCapeFrameCount);
            }
            Integer maxAnimatedCapeFileSize = readOptionalInt(textures, "maxAnimatedCapeFileSizeBytes");
            if (maxAnimatedCapeFileSize != null) {
                if (maxAnimatedCapeFileSize < 1)
                    throw NetException.illegalArgument("textures.maxAnimatedCapeFileSizeBytes must be >= 1.");
                serverConfig.getTextures()
                    .setMaxAnimatedCapeFileSizeBytes(maxAnimatedCapeFileSize);
            }
        }

        JsonObject http = readOptionalObject(body, "http");
        if (http != null) {
            Integer readTimeoutSeconds = readOptionalInt(http, "readTimeoutSeconds");
            if (readTimeoutSeconds != null) {
                if (readTimeoutSeconds < 1) {
                    throw NetException.illegalArgument("http.readTimeoutSeconds must be >= 1.");
                }
                serverConfig.getHttp()
                    .setReadTimeoutSeconds(readTimeoutSeconds);
            }

            Integer maxContentLengthBytes = readOptionalInt(http, "maxContentLengthBytes");
            if (maxContentLengthBytes != null) {
                if (maxContentLengthBytes < 1) {
                    throw NetException.illegalArgument("http.maxContentLengthBytes must be >= 1.");
                }
                serverConfig.getHttp()
                    .setMaxContentLengthBytes(maxContentLengthBytes);
            }
        }

        serverConfig.ensureApiRootInSkinDomains();
        try {
            serverConfig.validateOrThrow();
        } catch (IllegalStateException e) {
            throw NetException.illegalArgument(e.getMessage());
        }
        persistServerConfig();
        return buildServerConfigResponse();
    }

    private Object getServerProperties(RequestContext ctx) {
        requireSession(ctx);
        return callOnServerThread(() -> buildServerPropertiesResponse(null));
    }

    private Object updateServerProperties(RequestContext ctx) {
        requireSession(ctx);
        JsonArray propsArray = readRequiredArray(ctx.getJsonBody(), "properties");
        List<ServerPropertyEntry> entries = parseServerPropertyEntries(propsArray);

        return callOnServerThread(() -> {
            File file = resolveServerPropertiesFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw NetException.illegalArgument("Failed to create directory for server.properties.");
            }

            Properties properties = new Properties();
            for (ServerPropertyEntry entry : entries) {
                properties.setProperty(entry.key, entry.value);
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                properties.store(out, "Managed by WawelAuth admin");
            } catch (IOException e) {
                throw NetException.illegalArgument("Failed to write server.properties: " + e.getMessage());
            }

            return buildServerPropertiesResponse(
                "Saved server.properties. Reload/restart server for changes to take effect.");
        });
    }

    private static List<ServerPropertyEntry> parseServerPropertyEntries(JsonArray propsArray) {
        List<ServerPropertyEntry> entries = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (JsonElement element : propsArray) {
            if (element == null || !element.isJsonObject()) {
                throw NetException.illegalArgument("Each server.properties entry must be an object.");
            }

            JsonObject object = element.getAsJsonObject();
            String key = trimToNull(readOptionalString(object, "key"));
            if (key == null) {
                throw NetException.illegalArgument("server.properties key cannot be empty.");
            }
            if (key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
                throw NetException.illegalArgument("server.properties keys cannot contain newlines.");
            }
            if (!seen.add(key)) {
                throw NetException.illegalArgument("Duplicate server.properties key: " + key);
            }

            String value = readOptionalString(object, "value");
            if (value == null) {
                value = "";
            }
            entries.add(new ServerPropertyEntry(key, value));
        }

        return entries;
    }

    private Map<String, Object> buildServerPropertiesResponse(String statusMessage) {
        File file = resolveServerPropertiesFile();
        Properties properties = new Properties();

        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                throw NetException.illegalArgument("Failed to read server.properties: " + e.getMessage());
            }
        }

        List<String> keys = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            keys.add(String.valueOf(entry.getKey()));
        }
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);

        List<Map<String, Object>> entries = new ArrayList<>();
        for (String key : keys) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("value", properties.getProperty(key, ""));
            entries.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", file.getAbsolutePath());
        out.put("exists", file.exists());
        out.put("properties", entries);
        out.put("reloadRequired", true);
        out.put(
            "statusMessage",
            statusMessage != null ? statusMessage
                : "Most server.properties changes require reload/restart to take effect.");
        return out;
    }

    private File resolveServerPropertiesFile() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            throw NetException.illegalArgument("Minecraft server is not running.");
        }
        return server.getFile("server.properties");
    }

    private File resolveServerDirectoryFile(String name) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        return server.getFile(name);
    }

    private boolean hasServerDirectoryFile(String name) {
        File file = resolveServerDirectoryFile(name);
        return file != null && file.isFile();
    }

    private BinaryResponse staticResponse(byte[] body, String contentType) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Pragma", "no-cache");
        return new BinaryResponse(body, contentType, headers);
    }

    private void ensureOperational() {
        if (!isAdminEnabled()) {
            throw NetException.forbidden("Admin web UI is disabled in configuration.");
        }
        if (resolveConfiguredAdminToken() == null) {
            throw NetException.forbidden("Admin token is not configured.");
        }
    }

    private boolean isAdminEnabled() {
        return serverConfig.getAdmin()
            .isEnabled();
    }

    private AdminSession requireSession(RequestContext ctx) {
        ensureOperational();
        cleanupExpiredSessions(System.currentTimeMillis());
        String token = extractSessionToken(ctx);
        if (token == null) {
            throw NetException.forbidden("Missing admin session token.");
        }

        AdminSession session = sessions.get(token);
        if (session == null) {
            throw NetException.forbidden("Invalid or expired admin session.");
        }

        long now = System.currentTimeMillis();
        if (session.expiresAt <= now) {
            sessions.remove(token);
            throw NetException.forbidden("Invalid or expired admin session.");
        }

        long newExpiry = now + sanitizeSessionTtl(
            serverConfig.getAdmin()
                .getSessionTtlMs());
        session.expiresAt = newExpiry;
        return session;
    }

    private static String extractSessionToken(RequestContext ctx) {
        String bearer = trimToNull(ctx.getBearerToken());
        if (bearer != null) return bearer;
        String headerToken = trimToNull(
            ctx.getRequest()
                .headers()
                .get(ADMIN_HEADER));
        if (headerToken != null) return headerToken;

        String cookieHeader = trimToNull(
            ctx.getRequest()
                .headers()
                .get("Cookie"));
        if (cookieHeader != null) {
            String fromCookie = parseCookie(cookieHeader, "wawelauth_admin_session");
            if (fromCookie != null) {
                return fromCookie;
            }
        }
        return null;
    }

    private static String parseCookie(String cookieHeader, String name) {
        if (cookieHeader == null || name == null) return null;
        String[] pairs = cookieHeader.split(";");
        String prefix = name + "=";
        for (String pair : pairs) {
            String part = pair.trim();
            if (!part.startsWith(prefix)) continue;
            String raw = part.substring(prefix.length());
            if (raw.isEmpty()) return null;
            try {
                return trimToNull(URLDecoder.decode(raw, "UTF-8"));
            } catch (Exception ignored) {
                return trimToNull(raw);
            }
        }
        return null;
    }

    private void cleanupExpiredSessions(long now) {
        for (Map.Entry<String, AdminSession> entry : sessions.entrySet()) {
            if (entry.getValue().expiresAt <= now) {
                sessions.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private String decryptEncryptedToken(String encryptedTokenB64) {
        byte[] encryptedBytes;
        try {
            encryptedBytes = Base64.getDecoder()
                .decode(encryptedTokenB64);
        } catch (IllegalArgumentException e) {
            throw NetException.illegalArgument("encryptedToken is not valid base64.");
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, keyManager.getPrivateKey());
            byte[] plain = cipher.doFinal(encryptedBytes);
            String token = new String(plain, StandardCharsets.UTF_8);
            if (token.isEmpty()) {
                throw NetException.illegalArgument("Encrypted token payload is empty.");
            }
            return token;
        } catch (NetException e) {
            throw e;
        } catch (Exception e) {
            throw NetException.illegalArgument("Failed to decrypt encryptedToken payload.");
        }
    }

    private boolean requiresEncryptedLogin(RequestContext ctx) {
        return !isHttpsRequest(ctx);
    }

    private static boolean isHttpsRequest(RequestContext ctx) {
        String forwarded = trimToNull(
            ctx.getRequest()
                .headers()
                .get("X-Forwarded-Proto"));
        if (forwarded != null) {
            String lower = forwarded.toLowerCase(Locale.ROOT);
            if (lower.contains("https")) {
                return true;
            }
        }

        String frontEndHttps = trimToNull(
            ctx.getRequest()
                .headers()
                .get("Front-End-Https"));
        if ("on".equalsIgnoreCase(frontEndHttps) || "1".equals(frontEndHttps)) {
            return true;
        }

        String forwardedSsl = trimToNull(
            ctx.getRequest()
                .headers()
                .get("X-Forwarded-Ssl"));
        return "on".equalsIgnoreCase(forwardedSsl) || "1".equals(forwardedSsl);
    }

    private String resolveConfiguredAdminToken() {
        ServerConfig.Admin admin = serverConfig.getAdmin();
        String envVar = admin.getTokenEnvVar();
        if (envVar != null && !envVar.trim()
            .isEmpty()) {
            String envToken = System.getenv(envVar);
            if (envToken != null && !envToken.isEmpty()) {
                return envToken;
            }
        }
        String configured = admin.getToken();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return null;
    }

    private static WawelUser requireUser(UserDAO userDAO, UUID userUuid) {
        WawelUser user = userDAO.findByUuid(userUuid);
        if (user == null) {
            throw NetException.notFound("User not found.");
        }
        return user;
    }

    private static WawelProfile requireProfile(ProfileDAO profileDAO, UUID profileUuid) {
        WawelProfile profile = profileDAO.findByUuid(profileUuid);
        if (profile == null) {
            throw NetException.notFound("Profile not found.");
        }
        return profile;
    }

    private WawelUser requireUserByPathParam(RequestContext ctx, String field) {
        String raw = trimToNull(ctx.getPathParam(field));
        if (raw == null) {
            throw NetException.illegalArgument("Missing user identifier.");
        }
        UUID uuid = parseUuidFlexible(raw);
        return requireUser(userDAO, uuid);
    }

    private WawelProfile requireProfileByPathParam(RequestContext ctx, String field) {
        String raw = trimToNull(ctx.getPathParam(field));
        if (raw == null) {
            throw NetException.illegalArgument("Missing profile identifier.");
        }
        UUID uuid = parseUuidFlexible(raw);
        return requireProfile(profileDAO, uuid);
    }

    private static UUID parseUuidFlexible(String value) {
        try {
            if (value.contains("-")) {
                return UUID.fromString(value);
            }
            return UuidUtil.fromUnsigned(value);
        } catch (Exception e) {
            throw NetException.illegalArgument("Invalid UUID: " + value);
        }
    }

    private void runInServerTransaction(Runnable action) {
        WawelServer server = WawelServer.instance();
        if (server != null) {
            server.runInTransaction(action);
        } else {
            action.run();
        }
    }

    private <T> T callOnServerThread(Callable<T> callable) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || isServerThread()) {
            return callUnchecked(callable);
        }

        ensureMainThreadPumpInstalled(server);

        MainThreadTask<T> task = new MainThreadTask<>(callable);
        mainThreadQueue.add(task);
        try {
            boolean ok = task.latch.await(MAIN_THREAD_WAIT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                throw NetException.illegalArgument("Timed out waiting for main-thread server operation.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            throw NetException.illegalArgument("Interrupted while waiting for main-thread server operation.");
        }

        if (task.error != null) {
            if (task.error instanceof RuntimeException) {
                throw (RuntimeException) task.error;
            }
            throw new RuntimeException(task.error);
        }
        return task.result;
    }

    private static <T> T callUnchecked(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isServerThread() {
        return "Server thread".equals(
            Thread.currentThread()
                .getName());
    }

    private void ensureMainThreadPumpInstalled(MinecraftServer server) {
        if (!mainThreadPumpInstalled.compareAndSet(false, true)) {
            return;
        }
        server.func_82010_a(/* addTickable */new IUpdatePlayerListBox() {

            @Override
            public void update() {
                MainThreadTask<?> task;
                while ((task = mainThreadQueue.poll()) != null) {
                    task.run();
                }
            }
        });
    }

    private void persistServerConfig() {
        File configDir = Config.getConfigDir();
        if (configDir == null) {
            throw NetException.illegalArgument("Config directory is not initialized.");
        }
        JsonConfigIO.save(new File(configDir, "server.json"), serverConfig);
    }

    private Map<String, Object> buildServerConfigResponse() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverName", serverConfig.getServerName());
        out.put("apiRoot", serverConfig.getApiRoot());
        out.put("publicPagePath", serverConfig.getPublicPagePath());
        out.put("publicInfoApiPath", serverConfig.getPublicInfoApiPath());
        out.put("skinDomains", serverConfig.getSkinDomains());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(
            "implementationName",
            serverConfig.getMeta()
                .getImplementationName());
        meta.put(
            "serverHomepage",
            serverConfig.getMeta()
                .getServerHomepage());
        meta.put(
            "serverRegister",
            serverConfig.getMeta()
                .getServerRegister());
        meta.put(
            "publicDescription",
            serverConfig.getMeta()
                .getPublicDescription());
        out.put("meta", meta);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put(
            "legacySkinApi",
            serverConfig.getFeatures()
                .isLegacySkinApi());
        features.put(
            "noMojangNamespace",
            serverConfig.getFeatures()
                .isNoMojangNamespace());
        features.put(
            "usernameCheck",
            serverConfig.getFeatures()
                .isUsernameCheck());
        out.put("features", features);

        Map<String, Object> registration = new LinkedHashMap<>();
        registration.put(
            "policy",
            serverConfig.getRegistration()
                .getPolicy()
                .name());
        registration.put(
            "playerNameRegex",
            serverConfig.getRegistration()
                .getPlayerNameRegex());
        registration.put(
            "defaultUploadableTextures",
            serverConfig.getRegistration()
                .getDefaultUploadableTextures());
        out.put("registration", registration);

        Map<String, Object> invites = new LinkedHashMap<>();
        invites.put(
            "defaultUses",
            serverConfig.getInvites()
                .getDefaultUses());
        out.put("invites", invites);

        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put(
            "maxPerUser",
            serverConfig.getTokens()
                .getMaxPerUser());
        tokens.put(
            "sessionTimeoutMs",
            serverConfig.getTokens()
                .getSessionTimeoutMs());
        out.put("tokens", tokens);

        Map<String, Object> textures = new LinkedHashMap<>();
        textures.put(
            "maxSkinWidth",
            serverConfig.getTextures()
                .getMaxSkinWidth());
        textures.put(
            "maxSkinHeight",
            serverConfig.getTextures()
                .getMaxSkinHeight());
        textures.put(
            "maxCapeWidth",
            serverConfig.getTextures()
                .getMaxCapeWidth());
        textures.put(
            "maxCapeHeight",
            serverConfig.getTextures()
                .getMaxCapeHeight());
        textures.put(
            "maxFileSizeBytes",
            serverConfig.getTextures()
                .getMaxFileSizeBytes());
        textures.put(
            "allowElytra",
            serverConfig.getTextures()
                .isAllowElytra());
        textures.put(
            "allowAnimatedCapes",
            serverConfig.getTextures()
                .isAllowAnimatedCapes());
        textures.put(
            "maxCapeFrameCount",
            serverConfig.getTextures()
                .getMaxCapeFrameCount());
        textures.put(
            "maxAnimatedCapeFileSizeBytes",
            serverConfig.getTextures()
                .getMaxAnimatedCapeFileSizeBytes());
        out.put("textures", textures);

        Map<String, Object> http = new LinkedHashMap<>();
        http.put(
            "readTimeoutSeconds",
            serverConfig.getHttp()
                .getReadTimeoutSeconds());
        http.put(
            "maxContentLengthBytes",
            serverConfig.getHttp()
                .getMaxContentLengthBytes());
        out.put("http", http);

        return out;
    }

    private static Map<String, Object> toAdminProfileJson(WawelProfile profile) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", UuidUtil.toUnsigned(profile.getUuid()));
        out.put("name", profile.getName());
        out.put("avatarUrl", buildAvatarUrl("local", profile.getUuid()));
        UUID offlineUuid = profile.getOfflineUuid();
        if (offlineUuid == null) {
            String name = trimToNull(profile.getName());
            if (name != null) {
                offlineUuid = WawelProfile.computeOfflineUuid(name);
            }
        }
        out.put("offlineUuid", offlineUuid == null ? null : UuidUtil.toUnsigned(offlineUuid));
        return out;
    }

    private Map<String, Object> buildProfileUuidChangeResponse(WawelUser owner, WawelProfile profile, UUID oldUuid,
        UUID newUuid, UUID offlineUuid, boolean usedOfflineUuid, boolean changed, int invalidatedTokens,
        int kickedPlayers) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changed", changed);
        out.put("usedOfflineUuid", usedOfflineUuid);
        out.put("username", owner.getUsername());
        out.put("profile", toAdminProfileJson(profile));
        out.put("oldUuid", UuidUtil.toUnsigned(oldUuid));
        out.put("newUuid", UuidUtil.toUnsigned(newUuid));
        out.put("offlineUuid", offlineUuid == null ? null : UuidUtil.toUnsigned(offlineUuid));
        out.put("invalidatedTokens", invalidatedTokens);
        out.put("kickedPlayers", kickedPlayers);
        return out;
    }

    private static int kickOnlineProfiles(LinkedHashSet<UUID> profileUuids, LinkedHashSet<String> profileNames,
        String reason) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return 0;
        }

        ServerConfigurationManager scm = server.getConfigurationManager();
        if (scm == null) {
            return 0;
        }

        LinkedHashSet<String> normalizedNames = new LinkedHashSet<>();
        for (String profileName : profileNames) {
            String normalized = trimToNull(profileName);
            if (normalized != null) {
                normalizedNames.add(normalized.toLowerCase(Locale.ROOT));
            }
        }

        Object rawList;
        try {
            rawList = scm.getClass()
                .getField("playerEntityList")
                .get(scm);
        } catch (Throwable ignored) {
            return 0;
        }
        if (!(rawList instanceof List<?>)) {
            return 0;
        }

        LinkedHashSet<EntityPlayerMP> toKick = new LinkedHashSet<>();
        for (Object rawPlayer : (List<?>) rawList) {
            if (!(rawPlayer instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP player = (EntityPlayerMP) rawPlayer;
            GameProfile gameProfile = player.getGameProfile();
            UUID onlineUuid = gameProfile == null ? null : gameProfile.getId();
            String onlineName = gameProfile == null ? trimToNull(player.getCommandSenderName())
                : trimToNull(gameProfile.getName());
            boolean uuidMatch = onlineUuid != null && profileUuids.contains(onlineUuid);
            boolean nameMatch = onlineName != null && normalizedNames.contains(onlineName.toLowerCase(Locale.ROOT));
            if (uuidMatch || nameMatch) {
                toKick.add(player);
            }
        }

        int kicked = 0;
        for (EntityPlayerMP player : toKick) {
            if (player.playerNetServerHandler == null) {
                continue;
            }
            player.playerNetServerHandler.kickPlayerFromServer(reason);
            kicked++;
        }
        return kicked;
    }

    private List<ProviderChoice> getProviderChoices() {
        List<ProviderChoice> providers = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        if (serverConfig.isLocalAuthEnabled()) {
            providers.add(new ProviderChoice("local", "Local (Wawel Auth)", "local", null));
            seen.add("local");
        }

        for (FallbackServer fallback : serverConfig.getFallbackServers()) {
            if (fallback == null) continue;
            String name = trimToNull(fallback.getName());
            if (name == null) continue;
            String key = name.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                providers.add(new ProviderChoice(name, name, "fallback", fallback));
            }
        }
        return providers;
    }

    private ProviderChoice requireProviderChoice(String providerKey) {
        ProviderChoice provider = findProviderChoice(providerKey);
        if (provider != null) {
            return provider;
        }
        throw NetException.illegalArgument("Unknown provider: " + providerKey);
    }

    private ProviderChoice findProviderChoice(String providerKey) {
        String wanted = trimToNull(providerKey);
        if (wanted == null) {
            return null;
        }

        for (ProviderChoice provider : getProviderChoices()) {
            if (provider.key.equalsIgnoreCase(wanted)) {
                return provider;
            }
        }
        return null;
    }

    private ResolvedProfile resolveProfileForProvider(String username, ProviderChoice provider) {
        String qualified = username + "@" + provider.key;
        GameProfile profile = FallbackWhitelistLookup.resolveQualifiedProfile(qualified);
        if (profile == null || profile.getId() == null) {
            throw NetException.notFound("Profile not found for " + qualified);
        }

        String avatarUrl = buildAvatarUrl(provider.key, profile.getId());
        return new ResolvedProfile(profile, provider.key, provider.label, avatarUrl);
    }

    private Map<String, Object> toProfileJson(ResolvedProfile resolved) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", UuidUtil.toUnsigned(resolved.profile.getId()));
        out.put("name", resolved.profile.getName());
        out.put("provider", resolved.providerKey);
        out.put("providerLabel", resolved.providerLabel);
        out.put("avatarUrl", resolved.avatarUrl);
        return out;
    }

    private Map<String, Object> toListEntryJson(GameProfile profile, String storedProviderKey) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", UuidUtil.toUnsigned(profile.getId()));
        out.put("name", profile.getName());

        boolean isLocal = profileDAO.findByUuid(profile.getId()) != null;
        ProviderChoice boundProvider = findProviderChoice(storedProviderKey);
        if (boundProvider != null) {
            out.put("provider", boundProvider.key);
            out.put("providerLabel", boundProvider.label);
            out.put("providerKnown", true);
            out.put("avatarUrl", buildAvatarUrl(boundProvider.key, profile.getId()));
            return out;
        }

        String rawStoredProviderKey = trimToNull(storedProviderKey);
        if (rawStoredProviderKey != null) {
            out.put("provider", rawStoredProviderKey);
            out.put("providerLabel", rawStoredProviderKey);
            out.put("providerKnown", false);
            out.put("avatarUrl", null);
            return out;
        }

        out.put("provider", isLocal ? "local" : null);
        out.put("providerLabel", isLocal ? "Local" : "Unknown");
        out.put("providerKnown", isLocal);
        out.put("avatarUrl", isLocal ? buildAvatarUrl("local", profile.getId()) : null);
        return out;
    }

    private static String buildAvatarUrl(String providerKey, UUID uuid) {
        return "/api/wawelauth/admin/avatar?provider=" + urlEncode(providerKey)
            + "&uuid="
            + urlEncode(UuidUtil.toUnsigned(uuid));
    }

    private ServerConfigurationManager requireServerConfigManager() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            throw NetException.illegalArgument("Minecraft server is not running.");
        }
        ServerConfigurationManager scm = server.getConfigurationManager();
        if (scm == null) {
            throw NetException.illegalArgument("Server configuration manager is not available.");
        }
        return scm;
    }

    private GameProfile requireExistingWhitelistEntry(RequestContext ctx) {
        ServerConfigurationManager scm = requireServerConfigManager();
        UserListWhitelist whitelist = scm.func_152599_k() /* getWhiteListedPlayers */;
        List<GameProfile> profiles = readUserListProfiles(whitelist.func_152691_c());

        String uuidRaw = trimToNull(ctx.optJsonString("uuid"));
        if (uuidRaw != null) {
            UUID uuid = parseUuidFlexible(uuidRaw);
            for (GameProfile profile : profiles) {
                if (profile != null && uuid.equals(profile.getId())) {
                    return profile;
                }
            }
        }

        String name = trimToNull(ctx.optJsonString("name"));
        if (name != null) {
            for (GameProfile profile : profiles) {
                if (profile != null && name.equalsIgnoreCase(profile.getName())) {
                    return profile;
                }
            }
        }

        throw NetException.notFound("Whitelist entry not found.");
    }

    private GameProfile requireExistingOpEntry(RequestContext ctx) {
        ServerConfigurationManager scm = requireServerConfigManager();
        UserListOps ops = scm.func_152603_m() /* getOppedPlayers */;
        List<GameProfile> profiles = readUserListProfiles(ops.func_152691_c());

        String uuidRaw = trimToNull(ctx.optJsonString("uuid"));
        if (uuidRaw != null) {
            UUID uuid = parseUuidFlexible(uuidRaw);
            for (GameProfile profile : profiles) {
                if (profile != null && uuid.equals(profile.getId())) {
                    return profile;
                }
            }
        }

        String name = trimToNull(ctx.optJsonString("name"));
        if (name != null) {
            for (GameProfile profile : profiles) {
                if (profile != null && name.equalsIgnoreCase(profile.getName())) {
                    return profile;
                }
            }
        }

        throw NetException.notFound("Op entry not found.");
    }

    private List<GameProfile> readUserListProfiles(File listFile) {
        List<GameProfile> profiles = new ArrayList<>();
        if (listFile == null || !listFile.exists()) {
            return profiles;
        }

        try (FileInputStream in = new FileInputStream(listFile)) {
            byte[] raw = readAll(in, MAX_HTTP_BYTES);
            if (raw.length == 0) {
                return profiles;
            }

            JsonElement root = new JsonParser().parse(new String(raw, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonArray()) {
                return profiles;
            }

            JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();

                String uuidRaw = trimToNull(readOptionalString(object, "uuid"));
                if (uuidRaw == null) continue;

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidRaw);
                } catch (Exception e) {
                    continue;
                }

                String name = trimToNull(readOptionalString(object, "name"));
                if (name == null) {
                    name = uuid.toString();
                }
                profiles.add(new GameProfile(uuid, name));
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to read user list file {}: {}", listFile, e.getMessage());
        }

        return profiles;
    }

    private String resolveSkinUrl(ProviderChoice provider, UUID uuid, String uuidUnsigned) {
        if ("local".equals(provider.type)) {
            WawelProfile profile = profileDAO.findByUuid(uuid);
            if (profile == null) return null;
            String hash = trimToNull(profile.getSkinHash());
            if (hash == null) return null;
            String base = resolveLocalApiRoot();
            if (base == null) return null;
            return appendPath(base, "/textures/" + hash);
        }

        if (provider.fallback == null) {
            return null;
        }
        return fetchFallbackSkinUrl(provider.fallback, uuidUnsigned);
    }

    private String fetchFallbackSkinUrl(FallbackServer fallback, String uuidUnsigned) {
        String base = resolveSessionMinecraftBase(normalizeUrl(fallback.getSessionServerUrl()));
        if (base == null) {
            return null;
        }
        String url = appendPath(base, "/profile/" + uuidUnsigned + "?unsigned=true");
        JsonObject profile = fetchJson(url, MAX_HTTP_BYTES);
        if (profile == null) {
            return null;
        }
        return parseSkinUrlFromProfileResponse(profile);
    }

    private String resolveSessionMinecraftBase(String rawSessionServerUrl) {
        String base = normalizeUrl(rawSessionServerUrl);
        if (base == null) return null;

        if (base.endsWith("/session/minecraft")) {
            return base;
        }
        if (base.endsWith("/sessionserver")) {
            return base + "/session/minecraft";
        }
        if (base.endsWith("/session")) {
            return base + "/minecraft";
        }

        try {
            java.net.URI uri = java.net.URI.create(base);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host != null && "sessionserver.mojang.com".equalsIgnoreCase(host) && (path == null || path.isEmpty())) {
                return base + "/session/minecraft";
            }
        } catch (Exception ignored) {}

        return base + "/session/minecraft";
    }

    private String resolveLocalApiRoot() {
        String configured = normalizeUrl(serverConfig.getApiRoot());
        if (configured != null) {
            return configured;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;

        int port = server.getServerPort();
        if (port <= 0) {
            port = server.getPort();
        }
        if (port <= 0) return null;
        InetAddress loopback = InetAddress.getLoopbackAddress();
        String host = loopback == null ? "127.0.0.1" : loopback.getHostAddress();
        return "http://" + NetworkAddressUtil.formatHostPort(host, port);
    }

    private static String parseSkinUrlFromProfileResponse(JsonObject profile) {
        if (profile == null) return null;
        JsonArray properties;
        try {
            properties = profile.getAsJsonArray("properties");
        } catch (Exception e) {
            return null;
        }
        if (properties == null) return null;

        for (JsonElement element : properties) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject prop = element.getAsJsonObject();
            String name = safeString(prop, "name");
            if (!"textures".equals(name)) continue;
            String value = safeString(prop, "value");
            if (value == null) continue;
            String skin = extractSkinUrlFromTextureProperty(value);
            if (skin != null) {
                return skin;
            }
        }
        return null;
    }

    private static String extractSkinUrlFromTextureProperty(String texturesBase64) {
        try {
            byte[] jsonBytes = Base64.getDecoder()
                .decode(texturesBase64);
            JsonObject root = new JsonParser().parse(new String(jsonBytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) return null;
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) return null;
            return safeString(skin, "url");
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeString(JsonObject object, String key) {
        if (object == null || key == null
            || !object.has(key)
            || object.get(key)
                .isJsonNull()) {
            return null;
        }
        try {
            return object.get(key)
                .getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeUrl(String raw) {
        return StringUtil.normalizeHttpUrl(raw);
    }

    private static String appendPath(String base, String suffix) {
        if (base == null || suffix == null) return null;
        return base + suffix;
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private JsonObject fetchJson(String url, int maxBytes) {
        byte[] bytes = fetchBinary(url, maxBytes);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] fetchBinary(String url, int maxBytes) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "*/*");
            int status = conn.getResponseCode();
            if (status != 200) {
                return null;
            }
            InputStream stream = conn.getInputStream();
            return readAll(stream, maxBytes);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Response too large");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private BinaryResponse buildAvatarResponse(byte[] pngBytes) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "public, max-age=30");
        return new BinaryResponse(pngBytes, "image/png", headers);
    }

    private static byte[] renderFacePng(byte[] skinBytes) {
        return WawelFaceRendererServer.renderFacePng(skinBytes, 64);
    }

    private static JsonObject readOptionalObject(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) {
            return null;
        }
        try {
            return parent.getAsJsonObject(field);
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be an object.");
        }
    }

    private static JsonArray readOptionalArray(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) return null;
        try {
            return parent.getAsJsonArray(field);
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be an array.");
        }
    }

    private static JsonArray readRequiredArray(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) {
            throw NetException.illegalArgument("Field '" + field + "' is required and must be an array.");
        }
        try {
            return parent.getAsJsonArray(field);
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be an array.");
        }
    }

    private static String readOptionalString(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) {
            return null;
        }
        try {
            return parent.get(field)
                .getAsString();
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be a string.");
        }
    }

    private static Integer readOptionalInt(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) {
            return null;
        }
        try {
            return Integer.valueOf(
                parent.get(field)
                    .getAsInt());
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be an integer.");
        }
    }

    private static Long readOptionalLong(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) {
            return null;
        }
        try {
            return Long.valueOf(
                parent.get(field)
                    .getAsLong());
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be a long integer.");
        }
    }

    private static Boolean readOptionalBoolean(JsonObject parent, String field) {
        if (!parent.has(field) || parent.get(field)
            .isJsonNull()) {
            return null;
        }
        try {
            return Boolean.valueOf(
                parent.get(field)
                    .getAsBoolean());
        } catch (Exception e) {
            throw NetException.illegalArgument("Field '" + field + "' must be a boolean.");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static long sanitizeSessionTtl(long rawTtlMs) {
        if (rawTtlMs < 60_000L) return 60_000L;
        if (rawTtlMs > 86_400_000L) return 86_400_000L;
        return rawTtlMs;
    }

    private static String randomSessionToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw);
    }

    private String createUniqueInviteCode() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String code = randomInviteCode(4, 5);
            if (inviteDAO.findByCode(code) == null) {
                return code;
            }
        }
        throw NetException.illegalArgument("Failed to generate unique invite code.");
    }

    private static String randomInviteCode(int groups, int charsPerGroup) {
        StringBuilder out = new StringBuilder(groups * charsPerGroup + (groups - 1));
        for (int g = 0; g < groups; g++) {
            if (g > 0) {
                out.append('-');
            }
            for (int i = 0; i < charsPerGroup; i++) {
                int idx = RANDOM.nextInt(INVITE_ALPHABET.length());
                out.append(INVITE_ALPHABET.charAt(idx));
            }
        }
        return out.toString();
    }

    private static String trimToNull(String value) {
        return StringUtil.trimToNull(value);
    }

    private static byte[] loadResourceBytes(String path) {
        InputStream in = AdminWebService.class.getResourceAsStream(path);
        if (in == null) {
            throw new RuntimeException("Missing classpath resource: " + path);
        }

        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read classpath resource: " + path, e);
        }
    }

    private static byte[] readExistingFileBytes(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to read admin asset {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static final class ProviderChoice {

        final String key;
        final String label;
        final String type;
        final FallbackServer fallback;

        ProviderChoice(String key, String label, String type, FallbackServer fallback) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.fallback = fallback;
        }
    }

    private static final class ResolvedProfile {

        final GameProfile profile;
        final String providerKey;
        final String providerLabel;
        final String avatarUrl;

        ResolvedProfile(GameProfile profile, String providerKey, String providerLabel, String avatarUrl) {
            this.profile = profile;
            this.providerKey = providerKey;
            this.providerLabel = providerLabel;
            this.avatarUrl = avatarUrl;
        }
    }

    private static final class CachedAvatar {

        final byte[] pngBytes;
        final long expiresAt;

        CachedAvatar(byte[] pngBytes, long expiresAt) {
            this.pngBytes = pngBytes;
            this.expiresAt = expiresAt;
        }
    }

    private static final class MainThreadTask<T> {

        final Callable<T> callable;
        final CountDownLatch latch = new CountDownLatch(1);
        volatile T result;
        volatile Throwable error;

        MainThreadTask(Callable<T> callable) {
            this.callable = callable;
        }

        void run() {
            try {
                result = callable.call();
            } catch (Throwable t) {
                error = t;
            } finally {
                latch.countDown();
            }
        }
    }

    private static final class ServerPropertyEntry {

        final String key;
        final String value;

        ServerPropertyEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class AdminSession {

        volatile long expiresAt;

        AdminSession(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
