package org.fentanylsolutions.wawelauth.wawelserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.WawelFaceRendererServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.FallbackServer;
import org.fentanylsolutions.wawelauth.wawelcore.config.RegistrationPolicy;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelnet.BinaryResponse;
import org.fentanylsolutions.wawelauth.wawelnet.HttpRouter;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Serves a public site from config/wawelauth/public-page.
 * <p>
 * Files are frozen at startup into an in-memory route map and only those exact
 * route keys are served. If no custom top-level index exists yet, the bundled
 * default public site is copied into that directory first.
 */
public final class PublicPageService {

    private static final String MINECRAFT_VERSION = "1.7.10";
    private static final String SITE_DIR_NAME = "public-page";
    private static final String ICON_PNG_NAME = "__server-icon.png";
    private static final String ICON_GIF_NAME = "__server-icon.gif";
    private static final String PLAYER_AVATAR_NAME = "__player-avatar.png";
    private static final String PUBLIC_INFO_API_PATH_TOKEN = "__WAWEL_PUBLIC_INFO_API_PATH__";
    private static final int PUBLIC_INFO_API_VERSION = 1;
    private static final int LIVE_STATUS_CACHE_TTL_SECONDS = 5;
    private static final long LIVE_STATUS_CACHE_TTL_MS = LIVE_STATUS_CACHE_TTL_SECONDS * 1000L;
    private static final int MAX_HTTP_BYTES = 4_194_304;
    private static final long PLAYER_AVATAR_CACHE_TTL_MS = 30_000L;

    private static final List<ManagedSeedResource> MANAGED_TEMPLATE_RESOURCES;
    private static final Map<String, String> STATIC_SEED_RESOURCES;

    static {
        List<ManagedSeedResource> templates = new ArrayList<>();
        templates.add(new ManagedSeedResource("/assets/wawelauth/web/public/index.htm", "index.htm"));
        templates.add(new ManagedSeedResource("/assets/wawelauth/web/public/styles.css", "styles.css"));
        templates.add(new ManagedSeedResource("/assets/wawelauth/web/public/app.js", "app.js"));
        MANAGED_TEMPLATE_RESOURCES = Collections.unmodifiableList(templates);

        Map<String, String> staticFiles = new LinkedHashMap<>();
        staticFiles.put("/assets/wawelauth/Logo_Dragon_Outline.png", "logo-dragon-outline.png");
        staticFiles.put("/assets/wawelauth/web/admin/pack-fallback.png", "pack-fallback.png");
        staticFiles.put(
            "/assets/wawelauth/web/admin/fonts/nerd-fonts/SymbolsNerdFont-Subset.woff2",
            "fonts/nerd-fonts/SymbolsNerdFont-Subset.woff2");
        STATIC_SEED_RESOURCES = Collections.unmodifiableMap(staticFiles);
    }

    private final ServerConfig serverConfig;
    private final String publicPath;
    private final String apiPrefix;
    private final String publicInfoApiPath;
    private final File siteDir;
    private volatile Map<String, FrozenAsset> frozenAssets = Collections.emptyMap();
    private volatile StaticPublicInfo staticPublicInfo;
    private final ConcurrentMap<String, CachedAvatar> playerAvatarCache = new ConcurrentHashMap<>();
    private volatile CachedLiveStatus cachedLiveStatus;

    public PublicPageService(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.publicPath = serverConfig.getPublicPagePath();
        this.apiPrefix = serverConfig.getApiRoutePrefix();
        this.publicInfoApiPath = serverConfig.getPublicInfoApiPath();
        this.siteDir = ensureDirectory(new File(Config.getConfigDir(), SITE_DIR_NAME));
        refreshAllCaches();

        WawelAuth.LOG.info(
            "Public page mount: {} (files: {}, info API: {}, directory: {})",
            publicPath,
            Integer.valueOf(frozenAssets.size()),
            publicInfoApiPath.isEmpty() ? "<disabled>" : publicInfoApiPath,
            siteDir.getAbsolutePath());
    }

    public void registerRoutes(HttpRouter router) {
        warnOnOverlaps();

        if (serverConfig.isPublicInfoApiEnabled() && !publicInfoApiPath.isEmpty()) {
            router.get(publicInfoApiPath, ctx -> servePublicInfo());
        }

        if (!serverConfig.isPublicPageEnabled()) {
            return;
        }

        router.get(joinRoute(publicPath, ICON_PNG_NAME), ctx -> serveServerIconPng());
        router.get(joinRoute(publicPath, ICON_GIF_NAME), ctx -> serveServerIconGif());
        router.get(joinRoute(publicPath, PLAYER_AVATAR_NAME), ctx -> servePlayerAvatar(ctx.getQueryParam("uuid")));

        for (String routePath : new ArrayList<>(frozenAssets.keySet())) {
            router.get(routePath, ctx -> serveFrozenAsset(routePath));
        }
    }

    public synchronized void refreshAllCaches() {
        seedDefaultSiteIfNeeded();
        this.frozenAssets = Collections.unmodifiableMap(snapshotPublicSite(siteDir));
        this.staticPublicInfo = buildStaticPublicInfo();
        this.cachedLiveStatus = null;
        this.playerAvatarCache.clear();
    }

    private void warnOnOverlaps() {
        if ((apiPrefix == null || apiPrefix.isEmpty()) && "/".equals(publicPath)) {
            WawelAuth.LOG.warn(
                "Public page path '/' conflicts with the auth API root. Set apiRoot to a prefixed URL like '/auth' to free '/'.");
        } else if (publicPath.equals(apiPrefix) || (!"/".equals(publicPath)
            && (publicPath.startsWith("/admin") || publicPath.startsWith("/api/wawelauth/admin")))) {
                WawelAuth.LOG.warn(
                    "Public page path '{}' overlaps an important route prefix. Earlier routes will take priority.",
                    publicPath);
            }

        if (!publicInfoApiPath.isEmpty()) {
            if (publicInfoApiPath.equals(apiPrefix) || publicInfoApiPath.startsWith("/admin")
                || publicInfoApiPath.startsWith("/api/wawelauth/admin")) {
                WawelAuth.LOG.warn(
                    "Public info API path '{}' overlaps an important route prefix. Earlier routes will take priority.",
                    publicInfoApiPath);
            }
            if (frozenAssets.containsKey(publicInfoApiPath)) {
                WawelAuth.LOG.warn(
                    "Public info API path '{}' conflicts with a frozen public-page file. The API route will take priority.",
                    publicInfoApiPath);
            }
        }
    }

    private BinaryResponse servePublicInfo() {
        long now = System.currentTimeMillis();
        StaticPublicInfo staticInfo = staticPublicInfo;
        if (staticInfo == null) {
            refreshAllCaches();
            staticInfo = staticPublicInfo;
        }

        CachedLiveStatus liveStatus = resolveCachedLiveStatus(now);
        String json = JsonSupport.toJson(buildPublicInfoPayload(now, staticInfo, liveStatus));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "public, max-age=" + LIVE_STATUS_CACHE_TTL_SECONDS);
        headers.put("X-Wawel-Public-Info-Version", String.valueOf(PUBLIC_INFO_API_VERSION));
        return new BinaryResponse(json.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8", headers);
    }

    private BinaryResponse serveFrozenAsset(String routePath) {
        FrozenAsset asset = frozenAssets.get(routePath);
        if (asset == null) {
            return textResponse("Frozen public-page asset not found.");
        }
        return asset.toResponse();
    }

    private BinaryResponse serveServerIconPng() {
        StaticPublicInfo staticInfo = staticPublicInfo;
        byte[] iconBytes = staticInfo == null ? null : staticInfo.serverIconPng;
        if (iconBytes == null) {
            return textResponse("Static server icon not found.");
        }
        return cacheableBinary(iconBytes, "image/png");
    }

    private BinaryResponse serveServerIconGif() {
        StaticPublicInfo staticInfo = staticPublicInfo;
        byte[] iconBytes = staticInfo == null ? null : staticInfo.serverIconGif;
        if (iconBytes == null) {
            return textResponse("Animated server icon not found.");
        }
        return cacheableBinary(iconBytes, "image/gif");
    }

    private BinaryResponse servePlayerAvatar(String uuidRaw) {
        UUID uuid = parseUuidFlexible(uuidRaw);
        if (uuid == null) {
            return textResponse("Connected player avatar not found.");
        }

        String uuidUnsigned = UuidUtil.toUnsigned(uuid);
        CachedAvatar cached = playerAvatarCache.get(uuidUnsigned);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMs > now) {
            return buildAvatarResponse(cached.pngBytes);
        }
        playerAvatarCache.remove(uuidUnsigned);

        byte[] pngBytes = renderConnectedPlayerAvatar(uuid);
        if (pngBytes == null) {
            return textResponse("Connected player avatar not found.");
        }

        playerAvatarCache.put(uuidUnsigned, new CachedAvatar(pngBytes, now + PLAYER_AVATAR_CACHE_TTL_MS));
        return buildAvatarResponse(pngBytes);
    }

    private BinaryResponse textResponse(String message) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Pragma", "no-cache");
        return new BinaryResponse(message.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8", headers);
    }

    private BinaryResponse cacheableBinary(byte[] bytes, String contentType) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "public, max-age=300");
        return new BinaryResponse(bytes, contentType, headers);
    }

    private CachedLiveStatus resolveCachedLiveStatus(long now) {
        CachedLiveStatus cached = cachedLiveStatus;
        if (cached != null && cached.expiresAtMs > now) {
            return cached;
        }

        synchronized (this) {
            cached = cachedLiveStatus;
            if (cached != null && cached.expiresAtMs > now) {
                return cached;
            }

            List<Map<String, Object>> connectedPlayers = resolveConnectedPlayers();
            cachedLiveStatus = new CachedLiveStatus(connectedPlayers, now + LIVE_STATUS_CACHE_TTL_MS);
            return cachedLiveStatus;
        }
    }

    private StaticPublicInfo buildStaticPublicInfo() {
        RegistrationPolicy registrationPolicy = serverConfig.getRegistration()
            .getPolicy();
        return new StaticPublicInfo(
            trimToNull(serverConfig.getServerName()),
            trimToNull(
                serverConfig.getMeta()
                    .getImplementationName()),
            trimToNull(
                serverConfig.getMeta()
                    .getImplementationVersion()),
            trimToNull(
                serverConfig.getMeta()
                    .getPublicDescription()),
            resolveMotd(),
            resolveMaxPlayers(),
            registrationPolicy == null ? null : registrationPolicy.name(),
            formatRegistrationPolicy(registrationPolicy),
            registrationDescription(registrationPolicy),
            Collections.unmodifiableList(new ArrayList<>(resolveFallbacks())),
            trimToNull(serverConfig.getApiRoot()),
            trimToNull(
                serverConfig.getMeta()
                    .getServerHomepage()),
            trimToNull(
                serverConfig.getMeta()
                    .getServerRegister()),
            serverConfig.getAdmin()
                .isEnabled(),
            isDynmapInstalled(),
            Collections.unmodifiableList(new ArrayList<>(resolveInstalledMods())),
            readExistingFileBytes(resolveServerDirectoryFile("server-icon.png")),
            readExistingFileBytes(resolveServerDirectoryFile("server-icon.gif")));
    }

    private static boolean isDynmapInstalled() {
        return Loader.isModLoaded("dynmap") || Loader.isModLoaded("Dynmap") || Loader.isModLoaded("gtnh-web-map");
    }

    private Map<String, Object> buildPublicInfoPayload(long now, StaticPublicInfo staticInfo,
        CachedLiveStatus liveStatus) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apiVersion", Integer.valueOf(PUBLIC_INFO_API_VERSION));
        out.put("generatedAt", Long.valueOf(now));
        out.put("cacheTtlSeconds", Integer.valueOf(LIVE_STATUS_CACHE_TTL_SECONDS));

        Map<String, Object> branding = new LinkedHashMap<>();
        branding.put("implementationName", staticInfo.implementationName);
        branding.put("implementationVersion", staticInfo.implementationVersion);
        branding.put("minecraftVersion", MINECRAFT_VERSION);
        out.put("branding", branding);

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", staticInfo.serverName);
        server.put("description", staticInfo.description);
        server.put("motd", staticInfo.motd);
        server.put("playersOnline", Integer.valueOf(liveStatus.playersOnline));
        server.put("maxPlayers", staticInfo.maxPlayers);
        server.put("connectedPlayers", liveStatus.connectedPlayers);

        Map<String, Object> registration = new LinkedHashMap<>();
        registration.put("id", staticInfo.registrationId);
        registration.put("label", staticInfo.registrationLabel);
        registration.put("description", staticInfo.registrationDescription);
        server.put("registration", registration);
        server.put("fallbacks", staticInfo.fallbacks);
        out.put("server", server);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("apiRoot", staticInfo.apiRoot);
        links.put("homepage", staticInfo.homepage);
        links.put("register", staticInfo.register);
        links.put("admin", staticInfo.adminEnabled ? "/admin" : null);
        links.put("dynmap", staticInfo.dynmapInstalled ? "/dynmap" : null);
        links.put("publicPagePath", publicPath);
        links.put("publicInfoApiPath", publicInfoApiPath);
        links.put("authlibInjectorHeader", "X-Authlib-Injector-API-Location");
        out.put("links", links);

        Map<String, Object> icons = new LinkedHashMap<>();
        icons.put("hasStatic", Boolean.valueOf(staticInfo.serverIconPng != null));
        icons.put("hasAnimated", Boolean.valueOf(staticInfo.serverIconGif != null));
        icons.put("staticUrl", staticInfo.serverIconPng != null ? joinRoute(publicPath, ICON_PNG_NAME) : null);
        icons.put("animatedUrl", staticInfo.serverIconGif != null ? joinRoute(publicPath, ICON_GIF_NAME) : null);
        icons.put(
            "preferred",
            staticInfo.serverIconGif != null ? "animated" : (staticInfo.serverIconPng != null ? "static" : "fallback"));
        out.put("icons", icons);

        out.put("modlist", staticInfo.modlist);

        return out;
    }

    private void seedDefaultSiteIfNeeded() {
        if (!isDirectoryEmpty(siteDir)) {
            return;
        }

        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(PUBLIC_INFO_API_PATH_TOKEN, escapeJsStringValue(publicInfoApiPath));

        for (ManagedSeedResource resource : MANAGED_TEMPLATE_RESOURCES) {
            writeSeedTemplate(resource, replacements);
        }
        for (Map.Entry<String, String> entry : STATIC_SEED_RESOURCES.entrySet()) {
            copyBundledFile(entry.getKey(), new File(siteDir, entry.getValue()));
        }
    }

    private void writeSeedTemplate(ManagedSeedResource resource, Map<String, String> replacements) {
        File destination = new File(siteDir, resource.targetName);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create public-page directory '{}'.", parent.getAbsolutePath());
            return;
        }

        String text = loadClasspathText(resource.resourcePath);
        if (text == null) {
            WawelAuth.LOG.warn("Missing bundled public-page text resource '{}'.", resource.resourcePath);
            return;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        try {
            Files.write(destination.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to seed public-page file {}: {}", destination.getAbsolutePath(), e.getMessage());
        }
    }

    private static boolean isDirectoryEmpty(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return true;
        }
        String[] children = dir.list();
        return children == null || children.length == 0;
    }

    private static void copyBundledFile(String resourcePath, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create public-page directory '{}'.", parent.getAbsolutePath());
            return;
        }

        byte[] bytes = loadClasspathBytes(resourcePath);
        if (bytes == null) {
            WawelAuth.LOG.warn("Missing bundled public-page resource '{}'.", resourcePath);
            return;
        }
        try {
            Files.write(destination.toPath(), bytes);
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to seed public-page file {}: {}", destination.getAbsolutePath(), e.getMessage());
        }
    }

    private Map<String, FrozenAsset> snapshotPublicSite(File rootDir) {
        Map<String, FrozenAsset> out = new LinkedHashMap<>();
        if (rootDir == null || !rootDir.isDirectory()) {
            return out;
        }

        try {
            Path rootPath = rootDir.toPath();
            Files.walk(rootPath)
                .filter(Files::isRegularFile)
                .sorted()
                .forEach(path -> freezePath(rootPath, path, out));
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to snapshot public-site directory '{}': {}", rootDir, e.getMessage());
        }

        return out;
    }

    private void freezePath(Path rootPath, Path filePath, Map<String, FrozenAsset> out) {
        String relative = rootPath.relativize(filePath)
            .toString()
            .replace(File.separatorChar, '/');
        if (relative.isEmpty()) {
            return;
        }

        String routePath = joinRoute(publicPath, relative);
        if (isReservedRoute(routePath)) {
            return;
        }

        byte[] bytes = readExistingFileBytes(filePath.toFile());
        if (bytes == null) {
            return;
        }

        FrozenAsset asset = new FrozenAsset(bytes, guessContentType(relative), isCacheable(relative));
        putAsset(out, routePath, asset);

        if (isIndexFile(relative)) {
            String directory = directoryOf(relative);
            String baseRoute = directory.isEmpty() ? publicPath : joinRoute(publicPath, directory);
            if (!isReservedRoute(baseRoute)) {
                putAsset(out, baseRoute, asset);
                if (!"/".equals(baseRoute) && !isReservedRoute(baseRoute + "/")) {
                    putAsset(out, baseRoute + "/", asset);
                }
            }
        }
    }

    private boolean isReservedRoute(String routePath) {
        return routePath.equals(joinRoute(publicPath, ICON_PNG_NAME))
            || routePath.equals(joinRoute(publicPath, ICON_GIF_NAME))
            || routePath.equals(joinRoute(publicPath, PLAYER_AVATAR_NAME))
            || (!publicInfoApiPath.isEmpty() && routePath.equals(publicInfoApiPath));
    }

    private static void putAsset(Map<String, FrozenAsset> out, String routePath, FrozenAsset asset) {
        if (!out.containsKey(routePath)) {
            out.put(routePath, asset);
        }
    }

    private static boolean isIndexFile(String relativePath) {
        String name = relativePath.toLowerCase(Locale.ROOT);
        return name.endsWith("/index.htm") || name.endsWith("/index.html")
            || "index.htm".equals(name)
            || "index.html".equals(name);
    }

    private static String directoryOf(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(0, slash) : "";
    }

    private static String joinRoute(String base, String relative) {
        if (relative == null || relative.isEmpty()) {
            return base;
        }
        return "/".equals(base) ? "/" + relative : base + "/" + relative;
    }

    private static String guessContentType(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".htm") || lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static boolean isCacheable(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return !(lower.endsWith(".htm") || lower.endsWith(".html"));
    }

    private static String resolveMotd() {
        String live = trimToNull(invokeString(MinecraftServer.getServer(), "getMOTD", "getServerMOTD"));
        if (live != null) {
            return stripFormattingCodes(live);
        }
        return stripFormattingCodes(readServerProperty("motd", "Minecraft Server"));
    }

    private static Integer resolveMaxPlayers() {
        Object configManager = null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            configManager = server.getConfigurationManager();
        }
        Integer live = invokeInteger(configManager, "getMaxPlayers", "func_72352_l");
        if (live != null && live.intValue() > 0) {
            return live;
        }
        String property = trimToNull(readServerProperty("max-players", null));
        if (property == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(property);
            return parsed > 0 ? Integer.valueOf(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> resolveConnectedPlayers() {
        List<Map<String, Object>> rows = new ArrayList<>();
        MinecraftServer server = MinecraftServer.getServer();
        ServerConfigurationManager configManager = server == null ? null : server.getConfigurationManager();
        if (configManager == null) {
            return rows;
        }

        for (Object rawPlayer : configManager.playerEntityList) {
            if (!(rawPlayer instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP) rawPlayer;
            GameProfile profile = player.getGameProfile();
            if (profile == null || profile.getId() == null) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put(
                "uuid",
                profile.getId()
                    .toString());
            row.put("name", trimToNull(profile.getName()));
            row.put(
                "avatarUrl",
                joinRoute(publicPath, PLAYER_AVATAR_NAME) + "?uuid=" + UuidUtil.toUnsigned(profile.getId()));
            rows.add(row);
        }
        rows.sort(
            (a, b) -> String.valueOf(a.get("name"))
                .compareToIgnoreCase(String.valueOf(b.get("name"))));
        return rows;
    }

    private byte[] renderConnectedPlayerAvatar(UUID uuid) {
        GameProfile profile = findConnectedPlayerProfile(uuid);
        if (profile == null) {
            return null;
        }

        String skinUrl = DynmapSkinUrlResolver.resolveFromProfile(profile);
        if (skinUrl == null) {
            skinUrl = resolveLocalSkinUrl(uuid);
        }
        if (skinUrl == null) {
            return null;
        }

        byte[] skinBytes = fetchBinary(skinUrl, MAX_HTTP_BYTES);
        return skinBytes == null ? null : renderFacePng(skinBytes);
    }

    private static GameProfile findConnectedPlayerProfile(UUID uuid) {
        MinecraftServer server = MinecraftServer.getServer();
        ServerConfigurationManager configManager = server == null ? null : server.getConfigurationManager();
        if (configManager == null) {
            return null;
        }

        for (Object rawPlayer : configManager.playerEntityList) {
            if (!(rawPlayer instanceof EntityPlayerMP)) {
                continue;
            }
            GameProfile profile = ((EntityPlayerMP) rawPlayer).getGameProfile();
            if (profile != null && uuid.equals(profile.getId())) {
                return profile;
            }
        }
        return null;
    }

    private static String resolveLocalSkinUrl(UUID uuid) {
        WawelServer server = WawelServer.instance();
        if (server == null || server.getProfileDAO() == null) {
            return null;
        }
        WawelProfile profile = server.getProfileDAO()
            .findByUuid(uuid);
        if (profile == null || trimToNull(profile.getSkinHash()) == null) {
            return null;
        }
        String apiRoot = trimToNull(
            server.getServerConfig()
                .getApiRoot());
        return apiRoot == null ? null : apiRoot + "/textures/" + profile.getSkinHash();
    }

    private static String readServerProperty(String key, String fallback) {
        File file = resolveServerPropertiesFile();
        if (file == null || !file.isFile()) {
            return fallback;
        }
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to read server.properties for public page: {}", e.getMessage());
            return fallback;
        }
        String value = trimToNull(properties.getProperty(key));
        return value != null ? value : fallback;
    }

    private List<Map<String, Object>> resolveFallbacks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (FallbackServer fallback : serverConfig.getFallbackServers()) {
            if (fallback == null) continue;
            String label = trimToNull(fallback.getName());
            if (label == null) {
                label = extractHost(fallback.getSessionServerUrl());
            }
            if (label == null) {
                label = extractHost(fallback.getAccountUrl());
            }
            if (label == null) {
                label = extractHost(fallback.getServicesUrl());
            }
            if (label == null) {
                label = "fallback";
            }
            if (seen.add(label)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", label);
                row.put("accountUrl", trimToNull(fallback.getAccountUrl()));
                row.put("sessionServerUrl", trimToNull(fallback.getSessionServerUrl()));
                row.put("servicesUrl", trimToNull(fallback.getServicesUrl()));
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> resolveInstalledMods() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ModContainer> mods = Loader.instance()
            .getActiveModList();
        if (mods == null) {
            return rows;
        }

        for (ModContainer mod : mods) {
            if (mod == null) {
                continue;
            }

            String name = trimToNull(mod.getName());
            String version = trimToNull(mod.getVersion());
            File source = mod.getSource();
            String filename = source == null ? null : trimToNull(source.getName());

            if (name == null) {
                name = trimToNull(mod.getModId());
            }
            if (name == null) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("version", version);
            row.put("filename", filename);
            rows.add(row);
        }

        rows.sort(
            (a, b) -> String.valueOf(a.get("name"))
                .compareToIgnoreCase(String.valueOf(b.get("name"))));
        return rows;
    }

    private static String extractHost(String rawUrl) {
        String value = trimToNull(rawUrl);
        if (value == null) {
            return null;
        }
        try {
            return trimToNull(new java.net.URI(value).getHost());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatRegistrationPolicy(RegistrationPolicy policy) {
        if (policy == null) {
            return "Unknown";
        }
        switch (policy) {
            case OPEN:
                return "Open";
            case INVITE_ONLY:
                return "Invite Only";
            case CLOSED:
                return "Closed";
            default:
                return policy.name();
        }
    }

    private static String registrationDescription(RegistrationPolicy policy) {
        if (policy == null) {
            return "Registration settings are unavailable.";
        }
        switch (policy) {
            case OPEN:
                return "Anybody can create a local Wawel Auth account.";
            case INVITE_ONLY:
                return "Registration requires a valid invite token.";
            case CLOSED:
                return "New local accounts cannot be registered right now.";
            default:
                return "Registration settings are unavailable.";
        }
    }

    private static File resolveServerPropertiesFile() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null ? server.getFile("server.properties") : null;
    }

    private static File resolveServerDirectoryFile(String name) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null ? server.getFile(name) : null;
    }

    private static File ensureDirectory(File dir) {
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create public-page directory: {}", dir.getAbsolutePath());
        }
        return dir;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stripFormattingCodes(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean skip = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (skip) {
                skip = false;
                continue;
            }
            if (c == '\u00A7') {
                skip = true;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String invokeString(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Object value = target.getClass()
                    .getMethod(methodName)
                    .invoke(target);
                if (value instanceof String) {
                    return (String) value;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static UUID parseUuidFlexible(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {}
        try {
            return UUID.fromString(
                value.replaceFirst(
                    "^(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})$",
                    "$1-$2-$3-$4-$5"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static byte[] fetchBinary(String url, int maxBytes) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
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

    private static byte[] renderFacePng(byte[] skinBytes) {
        return WawelFaceRendererServer.renderFacePng(skinBytes, 64);
    }

    private static BinaryResponse buildAvatarResponse(byte[] pngBytes) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "public, max-age=30");
        return new BinaryResponse(pngBytes, "image/png", headers);
    }

    private static Integer invokeInteger(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Object value = target.getClass()
                    .getMethod(methodName)
                    .invoke(target);
                if (value instanceof Number) {
                    return Integer.valueOf(((Number) value).intValue());
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String loadClasspathText(String path) {
        byte[] bytes = loadClasspathBytes(path);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static String escapeJsStringValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static byte[] loadClasspathBytes(String path) {
        InputStream in = PublicPageService.class.getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to read bundled public-page resource {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static byte[] readExistingFileBytes(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            WawelAuth.LOG.warn("Failed to read public-page asset {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private static final class ManagedSeedResource {

        private final String resourcePath;
        private final String targetName;

        private ManagedSeedResource(String resourcePath, String targetName) {
            this.resourcePath = resourcePath;
            this.targetName = targetName;
        }
    }

    private static final class FrozenAsset {

        private final byte[] data;
        private final String contentType;
        private final boolean cacheable;

        private FrozenAsset(byte[] data, String contentType, boolean cacheable) {
            this.data = data;
            this.contentType = contentType;
            this.cacheable = cacheable;
        }

        private BinaryResponse toResponse() {
            Map<String, String> headers = new LinkedHashMap<>();
            if (cacheable) {
                headers.put("Cache-Control", "public, max-age=300");
            } else {
                headers.put("Cache-Control", "no-store");
                headers.put("Pragma", "no-cache");
            }
            return new BinaryResponse(data, contentType, headers);
        }
    }

    private static final class StaticPublicInfo {

        private final String serverName;
        private final String implementationName;
        private final String implementationVersion;
        private final String description;
        private final String motd;
        private final Integer maxPlayers;
        private final String registrationId;
        private final String registrationLabel;
        private final String registrationDescription;
        private final List<Map<String, Object>> fallbacks;
        private final String apiRoot;
        private final String homepage;
        private final String register;
        private final boolean adminEnabled;
        private final boolean dynmapInstalled;
        private final List<Map<String, Object>> modlist;
        private final byte[] serverIconPng;
        private final byte[] serverIconGif;

        private StaticPublicInfo(String serverName, String implementationName, String implementationVersion,
            String description, String motd, Integer maxPlayers, String registrationId, String registrationLabel,
            String registrationDescription, List<Map<String, Object>> fallbacks, String apiRoot, String homepage,
            String register, boolean adminEnabled, boolean dynmapInstalled, List<Map<String, Object>> modlist,
            byte[] serverIconPng, byte[] serverIconGif) {
            this.serverName = serverName;
            this.implementationName = implementationName;
            this.implementationVersion = implementationVersion;
            this.description = description;
            this.motd = motd;
            this.maxPlayers = maxPlayers;
            this.registrationId = registrationId;
            this.registrationLabel = registrationLabel;
            this.registrationDescription = registrationDescription;
            this.fallbacks = fallbacks;
            this.apiRoot = apiRoot;
            this.homepage = homepage;
            this.register = register;
            this.adminEnabled = adminEnabled;
            this.dynmapInstalled = dynmapInstalled;
            this.modlist = modlist;
            this.serverIconPng = serverIconPng;
            this.serverIconGif = serverIconGif;
        }
    }

    private static final class CachedLiveStatus {

        private final List<Map<String, Object>> connectedPlayers;
        private final int playersOnline;
        private final long expiresAtMs;

        private CachedLiveStatus(List<Map<String, Object>> connectedPlayers, long expiresAtMs) {
            this.connectedPlayers = Collections.unmodifiableList(new ArrayList<>(connectedPlayers));
            this.playersOnline = connectedPlayers.size();
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class CachedAvatar {

        private final byte[] pngBytes;
        private final long expiresAtMs;

        private CachedAvatar(byte[] pngBytes, long expiresAtMs) {
            this.pngBytes = pngBytes;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class JsonSupport {

        private JsonSupport() {}

        private static String toJson(Object value) {
            return new com.google.gson.Gson().toJson(value);
        }
    }
}
