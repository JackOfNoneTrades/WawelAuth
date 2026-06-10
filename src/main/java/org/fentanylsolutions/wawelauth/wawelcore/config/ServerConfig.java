package org.fentanylsolutions.wawelauth.wawelcore.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.fentanylsolutions.wawelauth.Tags;
import org.fentanylsolutions.wawelauth.WawelAuth;

import com.google.gson.annotations.SerializedName;

/**
 * Server-side configuration, stored as server.json in WawelAuth's active data
 * config directory.
 * <p>
 * Controls the Yggdrasil server module: registration policy, token limits,
 * texture constraints, fallback servers, etc.
 * <p>
 * All fields have sensible defaults. GSON deserializes over the default
 * instance, so missing fields in the JSON keep their defaults.
 * Getters for nested objects and lists are null-safe: if a user sets
 * a section to null in their JSON, a fresh default is re-created.
 */
public class ServerConfig {

    public static final int MIN_TLS_HANDSHAKE_TIMEOUT_SECONDS = 1;
    public static final int MAX_TLS_HANDSHAKE_TIMEOUT_SECONDS = 300;

    private boolean wawelAuthEnabled = true;
    private boolean enableLocalAuth = true;
    private String serverName = "A Wawel Auth Server";

    /**
     * Public-facing base URL used to build the auth API root
     * (e.g. "auth.example.com:25565" or "https://auth.example.com").
     */
    private String publicBaseUrl = "";

    /**
     * Relative auth API mount path under {@link #publicBaseUrl}
     * (e.g. "auth" -> "/auth").
     */
    private String apiRoot = "auth";
    private boolean enablePublicPage = true;
    private boolean enablePublicInfoApi = true;
    private String publicPagePath = "/";
    private String publicInfoApiPath = "__server-info";
    @SerializedName("server-address")
    private String serverAddress = "localhost:25565";

    /**
     * Domains from which clients should accept texture URLs.
     * IMPORTANT: If this is empty and your server serves textures,
     * clients will reject those URLs. Set this to your server's domain
     * (e.g. ["auth.example.com"]). If publicBaseUrl is set, the server
     * module will auto-add its host to this list at runtime.
     */
    private List<String> skinDomains = new ArrayList<>();

    private Meta meta = new Meta();
    private Features features = new Features();
    private Registration registration = new Registration();
    private Invites invites = new Invites();
    private Textures textures = new Textures();
    private Tokens tokens = new Tokens();
    private RateLimits rateLimits = new RateLimits();
    private Http http = new Http();
    private Admin admin = new Admin();
    // Runtime-injected from fallback-servers.json at load; transient so the
    // enabled-filtered list and discovered signature keys never persist into server.json.
    private transient List<FallbackServer> fallbackServers = new ArrayList<>();

    // Host auto-added to skinDomains at startup; stripped again on persist.
    private transient String autoAddedSkinDomain;

    public boolean isWawelAuthEnabled() {
        return wawelAuthEnabled;
    }

    public void setWawelAuthEnabled(boolean wawelAuthEnabled) {
        this.wawelAuthEnabled = wawelAuthEnabled;
    }

    public boolean isLocalAuthEnabled() {
        return enableLocalAuth;
    }

    public void setLocalAuthEnabled(boolean enableLocalAuth) {
        this.enableLocalAuth = enableLocalAuth;
    }

    public boolean isPublicPageEnabled() {
        return enablePublicPage;
    }

    public void setPublicPageEnabled(boolean enablePublicPage) {
        this.enablePublicPage = enablePublicPage;
    }

    public boolean isPublicInfoApiEnabled() {
        return enablePublicInfoApi;
    }

    public void setPublicInfoApiEnabled(boolean enablePublicInfoApi) {
        this.enablePublicInfoApi = enablePublicInfoApi;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getPublicBaseUrl() {
        return normalizePublicBaseUrl(publicBaseUrl);
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
    }

    public String getApiRoot() {
        return normalizeConfiguredApiRoot(apiRoot);
    }

    public void setApiRoot(String apiRoot) {
        this.apiRoot = normalizeConfiguredApiRoot(apiRoot);
    }

    /**
     * Public-facing full API root URL for authlib-injector and texture URLs.
     * Built from publicBaseUrl + apiRoot.
     */
    public String getEffectiveApiRoot() {
        if (hasAbsoluteLikeApiRoot()) {
            return null;
        }

        String base = getPublicBaseUrl();
        if (base.isEmpty()) {
            return null;
        }

        String prefix = getApiRoutePrefix();
        return prefix.isEmpty() ? base : base + prefix;
    }

    public boolean hasAbsoluteLikeApiRoot() {
        return looksLikeAbsoluteUrl(apiRoot);
    }

    /**
     * Public landing page mount path.
     * <p>
     * Examples:
     * - "" -> "/"
     * - "/" -> "/"
     * - "/info" -> "/info"
     * - "/info/" -> "/info"
     */
    public String getPublicPagePath() {
        return normalizePublicPagePath(publicPagePath);
    }

    public void setPublicPagePath(String publicPagePath) {
        this.publicPagePath = publicPagePath;
    }

    /**
     * Public server-info API path consumed by the default landing page.
     * <p>
     * Empty disables the endpoint entirely.
     * <p>
     * Relative paths are resolved under {@link #getPublicPagePath()} so the
     * bundled static page can call it directly.
     */
    public String getPublicInfoApiPath() {
        return normalizePublicInfoApiPath(publicInfoApiPath, getPublicPagePath());
    }

    public void setPublicInfoApiPath(String publicInfoApiPath) {
        this.publicInfoApiPath = publicInfoApiPath;
    }

    /**
     * Public-facing advertised Minecraft server address shown on the landing
     * page. Unlike server.properties server-ip, this is intended for humans and
     * client launchers, not socket binding.
     */
    public String getServerAddress() {
        return serverAddress == null ? "" : serverAddress.trim();
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    /**
     * Route prefix implied by the relative apiRoot config.
     * <p>
     * Examples:
     * - "" -> ""
     * - "auth" -> "/auth"
     * - "auth/v2" -> "/auth/v2"
     */
    public String getApiRoutePrefix() {
        return normalizeApiRoutePrefix(apiRoot);
    }

    public List<String> getSkinDomains() {
        if (skinDomains == null) skinDomains = new ArrayList<>();
        return skinDomains;
    }

    public void setSkinDomains(List<String> skinDomains) {
        this.skinDomains = skinDomains;
        // The new list is pure declared intent; the auto-added host (if any) was replaced.
        this.autoAddedSkinDomain = null;
    }

    public Meta getMeta() {
        if (meta == null) meta = new Meta();
        return meta;
    }

    public Features getFeatures() {
        if (features == null) features = new Features();
        return features;
    }

    public Registration getRegistration() {
        if (registration == null) registration = new Registration();
        return registration;
    }

    public Invites getInvites() {
        if (invites == null) invites = new Invites();
        return invites;
    }

    public Textures getTextures() {
        if (textures == null) textures = new Textures();
        return textures;
    }

    public Tokens getTokens() {
        if (tokens == null) tokens = new Tokens();
        return tokens;
    }

    public RateLimits getRateLimits() {
        if (rateLimits == null) rateLimits = new RateLimits();
        return rateLimits;
    }

    public Http getHttp() {
        if (http == null) http = new Http();
        return http;
    }

    public Admin getAdmin() {
        if (admin == null) admin = new Admin();
        return admin;
    }

    public List<FallbackServer> getFallbackServers() {
        if (fallbackServers == null) fallbackServers = new ArrayList<>();
        return fallbackServers;
    }

    public void setFallbackServers(List<FallbackServer> fallbackServers) {
        this.fallbackServers = fallbackServers;
    }

    /**
     * Ensures the effective API root host is in skinDomains. Call this at
     * server startup
     * after config is loaded, so clients accept texture URLs from this server.
     */
    public void ensureApiRootInSkinDomains() {
        String effectiveApiRoot = getEffectiveApiRoot();
        if (effectiveApiRoot == null || effectiveApiRoot.isEmpty()) return;
        try {
            String host = new URI(effectiveApiRoot).getHost();
            if (host != null && !getSkinDomains().contains(host)) {
                getSkinDomains().add(host);
                autoAddedSkinDomain = host;
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn(
                "Invalid effective API root '{}', could not extract host for skinDomains: {}",
                effectiveApiRoot,
                e.getMessage());
        }
    }

    /**
     * Returns a copy suitable for writing to server.json: declared values only,
     * with runtime-injected state stripped. Transient fields (fallbackServers,
     * autoAddedSkinDomain) are already excluded by serialization.
     */
    public ServerConfig toPersistable() {
        ServerConfig copy = JsonConfigIO.deepCopy(this, ServerConfig.class);
        if (autoAddedSkinDomain != null) {
            copy.getSkinDomains()
                .remove(autoAddedSkinDomain);
        }
        return copy;
    }

    public static String normalizeApiRoutePrefix(String rawApiRoot) {
        String raw = normalizeConfiguredApiRoot(rawApiRoot);
        if (raw.isEmpty()) {
            return "";
        }

        if (looksLikeAbsoluteUrl(raw)) {
            try {
                URI uri = new URI(raw);
                return normalizePathPrefix(uri.getPath());
            } catch (Exception ignored) {
                return "";
            }
        }

        return "/" + raw;
    }

    public static String normalizePublicBaseUrl(String rawBaseUrl) {
        String value = rawBaseUrl == null ? "" : rawBaseUrl.trim();
        while (value.endsWith("/") && !value.isEmpty()) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return "";
        }
        if (!looksLikeAbsoluteUrl(value)) {
            value = "http://" + value;
        }
        return value;
    }

    private static String normalizeConfiguredApiRoot(String rawApiRoot) {
        String raw = rawApiRoot == null ? "" : rawApiRoot.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (looksLikeAbsoluteUrl(raw)) {
            return raw;
        }
        return normalizeRelativePath(raw);
    }

    private static String normalizeRelativePath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }

        String[] segments = path.split("/+");
        StringBuilder normalized = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private static boolean looksLikeAbsoluteUrl(String value) {
        return value != null && value.contains("://");
    }

    public static String normalizePublicPagePath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    public static String normalizePublicInfoApiPath(String rawPath, String publicPagePath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty()) {
            return "";
        }
        if (path.startsWith("/")) {
            return normalizePublicPagePath(path);
        }

        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }

        String base = normalizePublicPagePath(publicPagePath);
        return "/".equals(base) ? "/" + path : base + "/" + path;
    }

    private static String normalizePathPrefix(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String path = rawPath.trim();
        if (path.isEmpty() || "/".equals(path)) {
            return "";
        }
        if (!path.startsWith("/")) {
            return "";
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return "/".equals(path) ? "" : path;
    }

    /**
     * Strict config validation. Throws on invalid operator input.
     */
    public void validateOrThrow() {
        Textures texturesConfig = getTextures();
        if (texturesConfig.getMaxSkinWidth() < 1) {
            throw new IllegalStateException("textures.maxSkinWidth must be >= 1.");
        }
        if (texturesConfig.getMaxSkinHeight() < 1) {
            throw new IllegalStateException("textures.maxSkinHeight must be >= 1.");
        }
        if (texturesConfig.getMaxCapeWidth() < 1) {
            throw new IllegalStateException("textures.maxCapeWidth must be >= 1.");
        }
        if (texturesConfig.getMaxCapeHeight() < 1) {
            throw new IllegalStateException("textures.maxCapeHeight must be >= 1.");
        }
        if (texturesConfig.getMaxFileSizeBytes() < 1) {
            throw new IllegalStateException("textures.maxFileSizeBytes must be >= 1.");
        }
        if (texturesConfig.getMaxCapeFrameCount() < 2) {
            throw new IllegalStateException("textures.maxCapeFrameCount must be >= 2.");
        }
        if (texturesConfig.getMaxAnimatedCapeFileSizeBytes() < 1) {
            throw new IllegalStateException("textures.maxAnimatedCapeFileSizeBytes must be >= 1.");
        }

        Http httpConfig = getHttp();
        if (httpConfig.getReadTimeoutSeconds() < 1) {
            throw new IllegalStateException("http.readTimeoutSeconds must be >= 1.");
        }
        if (httpConfig.getTlsHandshakeTimeoutSeconds() < MIN_TLS_HANDSHAKE_TIMEOUT_SECONDS
            || httpConfig.getTlsHandshakeTimeoutSeconds() > MAX_TLS_HANDSHAKE_TIMEOUT_SECONDS) {
            throw new IllegalStateException(
                "http.tlsHandshakeTimeoutSeconds must be between " + MIN_TLS_HANDSHAKE_TIMEOUT_SECONDS
                    + " and "
                    + MAX_TLS_HANDSHAKE_TIMEOUT_SECONDS
                    + ".");
        }
        if (httpConfig.getMaxContentLengthBytes() < 1) {
            throw new IllegalStateException("http.maxContentLengthBytes must be >= 1.");
        }
        if (httpConfig.getMaxContentLengthBytes() < texturesConfig.getMaxFileSizeBytes()) {
            throw new IllegalStateException("http.maxContentLengthBytes must be >= textures.maxFileSizeBytes.");
        }

        RateLimits rateLimitsConfig = getRateLimits();
        if (rateLimitsConfig.getAdminLoginAttempts() < 1) {
            throw new IllegalStateException("rateLimits.adminLoginAttempts must be >= 1.");
        }
        if (rateLimitsConfig.getAdminLoginWindowSeconds() < 1) {
            throw new IllegalStateException("rateLimits.adminLoginWindowSeconds must be >= 1.");
        }
        if (rateLimitsConfig.getPasswordIpAttempts() < 1) {
            throw new IllegalStateException("rateLimits.passwordIpAttempts must be >= 1.");
        }
        if (rateLimitsConfig.getPasswordSubjectAttempts() < 1) {
            throw new IllegalStateException("rateLimits.passwordSubjectAttempts must be >= 1.");
        }
        if (rateLimitsConfig.getPasswordWindowSeconds() < 1) {
            throw new IllegalStateException("rateLimits.passwordWindowSeconds must be >= 1.");
        }
        if (rateLimitsConfig.getTokenIpAttempts() < 1) {
            throw new IllegalStateException("rateLimits.tokenIpAttempts must be >= 1.");
        }
        if (rateLimitsConfig.getTokenWindowSeconds() < 1) {
            throw new IllegalStateException("rateLimits.tokenWindowSeconds must be >= 1.");
        }
        if (rateLimitsConfig.getRegistrationIpAttempts() < 1) {
            throw new IllegalStateException("rateLimits.registrationIpAttempts must be >= 1.");
        }
        if (rateLimitsConfig.getRegistrationSubjectAttempts() < 1) {
            throw new IllegalStateException("rateLimits.registrationSubjectAttempts must be >= 1.");
        }
        if (rateLimitsConfig.getRegistrationWindowSeconds() < 1) {
            throw new IllegalStateException("rateLimits.registrationWindowSeconds must be >= 1.");
        }

        List<FallbackServer> fallbacks = getFallbackServers();
        for (int i = 0; i < fallbacks.size(); i++) {
            FallbackServer fallback = fallbacks.get(i);
            if (fallback == null) continue;

            String name = fallback.getName();
            if (name == null) continue;

            for (int j = 0; j < name.length(); j++) {
                if (Character.isWhitespace(name.charAt(j))) {
                    throw new IllegalStateException(
                        "Invalid fallbackServers[" + i
                            + "].name '"
                            + name
                            + "': provider names must not contain whitespace.");
                }
            }
        }
    }

    /**
     * Metadata fields for the Yggdrasil API root response (GET /).
     * Per spec: implementationName, implementationVersion, links.
     * signaturePublickey is populated at runtime from KeyManager.
     */
    public static class Meta {

        private String serverHomepage = "";
        private String serverRegister = "";
        private String publicDescription = "";

        public String getImplementationName() {
            return WawelAuth.MODNAME;
        }

        /**
         * Returns the mod version from Tags. Not configurable.
         */
        public String getImplementationVersion() {
            return Tags.VERSION;
        }

        public String getServerHomepage() {
            return serverHomepage;
        }

        public void setServerHomepage(String serverHomepage) {
            this.serverHomepage = serverHomepage;
        }

        public String getServerRegister() {
            return serverRegister;
        }

        public void setServerRegister(String serverRegister) {
            this.serverRegister = serverRegister;
        }

        public String getPublicDescription() {
            return publicDescription;
        }

        public void setPublicDescription(String publicDescription) {
            this.publicDescription = publicDescription;
        }
    }

    /**
     * Yggdrasil feature flags, reported in the meta.feature object of the
     * API metadata response. Field names match the spec keys exactly
     * (snake_case in JSON via @SerializedName).
     */
    public static class Features {

        /**
         * Whether the legacy skin API (/skins/MinecraftSkins/) is supported.
         */
        @SerializedName("legacy_skin_api")
        private boolean legacySkinApi = false;

        /**
         * When true, the server uses its own UUID namespace instead of Mojang's.
         */
        @SerializedName("no_mojang_namespace")
        private boolean noMojangNamespace = true;

        /**
         * Whether the server validates usernames against a regex.
         */
        @SerializedName("username_check")
        private boolean usernameCheck = true;

        public boolean isLegacySkinApi() {
            return legacySkinApi;
        }

        public void setLegacySkinApi(boolean legacySkinApi) {
            this.legacySkinApi = legacySkinApi;
        }

        public boolean isNoMojangNamespace() {
            return noMojangNamespace;
        }

        public void setNoMojangNamespace(boolean noMojangNamespace) {
            this.noMojangNamespace = noMojangNamespace;
        }

        public boolean isUsernameCheck() {
            return usernameCheck;
        }

        public void setUsernameCheck(boolean usernameCheck) {
            this.usernameCheck = usernameCheck;
        }
    }

    public static class Registration {

        private RegistrationPolicy policy = RegistrationPolicy.INVITE_ONLY;
        private String playerNameRegex = "^[a-zA-Z0-9_]{3,16}$";
        private List<String> defaultUploadableTextures = Arrays.asList("skin", "cape");

        public RegistrationPolicy getPolicy() {
            return policy;
        }

        public void setPolicy(RegistrationPolicy policy) {
            this.policy = policy;
        }

        public String getPlayerNameRegex() {
            return playerNameRegex;
        }

        public void setPlayerNameRegex(String playerNameRegex) {
            this.playerNameRegex = playerNameRegex;
        }

        public List<String> getDefaultUploadableTextures() {
            return defaultUploadableTextures;
        }

        public void setDefaultUploadableTextures(List<String> defaultUploadableTextures) {
            this.defaultUploadableTextures = defaultUploadableTextures;
        }
    }

    public static class Invites {

        private int defaultUses = 1;

        public int getDefaultUses() {
            return defaultUses;
        }

        public void setDefaultUses(int defaultUses) {
            this.defaultUses = defaultUses;
        }
    }

    public static class Textures {

        private int maxSkinWidth = 64;
        private int maxSkinHeight = 64;
        private int maxCapeWidth = 64;
        private int maxCapeHeight = 32;
        private int maxFileSizeBytes = 1_048_576;
        private boolean allowElytra = false;
        private boolean allowAnimatedCapes = true;
        private int maxCapeFrameCount = 256;
        private int maxAnimatedCapeFileSizeBytes = 10_485_760;

        public int getMaxSkinWidth() {
            return maxSkinWidth;
        }

        public void setMaxSkinWidth(int maxSkinWidth) {
            this.maxSkinWidth = maxSkinWidth;
        }

        public int getMaxSkinHeight() {
            return maxSkinHeight;
        }

        public void setMaxSkinHeight(int maxSkinHeight) {
            this.maxSkinHeight = maxSkinHeight;
        }

        public int getMaxCapeWidth() {
            return maxCapeWidth;
        }

        public void setMaxCapeWidth(int maxCapeWidth) {
            this.maxCapeWidth = maxCapeWidth;
        }

        public int getMaxCapeHeight() {
            return maxCapeHeight;
        }

        public void setMaxCapeHeight(int maxCapeHeight) {
            this.maxCapeHeight = maxCapeHeight;
        }

        public int getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(int maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public boolean isAllowElytra() {
            return allowElytra;
        }

        public void setAllowElytra(boolean allowElytra) {
            this.allowElytra = allowElytra;
        }

        public boolean isAllowAnimatedCapes() {
            return allowAnimatedCapes;
        }

        public void setAllowAnimatedCapes(boolean allowAnimatedCapes) {
            this.allowAnimatedCapes = allowAnimatedCapes;
        }

        public int getMaxCapeFrameCount() {
            return maxCapeFrameCount;
        }

        public void setMaxCapeFrameCount(int maxCapeFrameCount) {
            this.maxCapeFrameCount = maxCapeFrameCount;
        }

        public int getMaxAnimatedCapeFileSizeBytes() {
            return maxAnimatedCapeFileSizeBytes;
        }

        public void setMaxAnimatedCapeFileSizeBytes(int maxAnimatedCapeFileSizeBytes) {
            this.maxAnimatedCapeFileSizeBytes = maxAnimatedCapeFileSizeBytes;
        }
    }

    public static class Tokens {

        private int maxPerUser = 10;
        private long sessionTimeoutMs = 30_000;

        public int getMaxPerUser() {
            return maxPerUser;
        }

        public void setMaxPerUser(int maxPerUser) {
            this.maxPerUser = maxPerUser;
        }

        public long getSessionTimeoutMs() {
            return sessionTimeoutMs;
        }

        public void setSessionTimeoutMs(long sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
        }
    }

    public static class RateLimits {

        private boolean enabled = true;
        private int adminLoginAttempts = 5;
        private int adminLoginWindowSeconds = 60;
        private int passwordIpAttempts = 60;
        private int passwordSubjectAttempts = 10;
        private int passwordWindowSeconds = 60;
        private int tokenIpAttempts = 300;
        private int tokenWindowSeconds = 60;
        private int registrationIpAttempts = 10;
        private int registrationSubjectAttempts = 3;
        private int registrationWindowSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getAdminLoginAttempts() {
            return adminLoginAttempts;
        }

        public void setAdminLoginAttempts(int adminLoginAttempts) {
            this.adminLoginAttempts = adminLoginAttempts;
        }

        public int getAdminLoginWindowSeconds() {
            return adminLoginWindowSeconds;
        }

        public void setAdminLoginWindowSeconds(int adminLoginWindowSeconds) {
            this.adminLoginWindowSeconds = adminLoginWindowSeconds;
        }

        public int getPasswordIpAttempts() {
            return passwordIpAttempts;
        }

        public void setPasswordIpAttempts(int passwordIpAttempts) {
            this.passwordIpAttempts = passwordIpAttempts;
        }

        public int getPasswordSubjectAttempts() {
            return passwordSubjectAttempts;
        }

        public void setPasswordSubjectAttempts(int passwordSubjectAttempts) {
            this.passwordSubjectAttempts = passwordSubjectAttempts;
        }

        public int getPasswordWindowSeconds() {
            return passwordWindowSeconds;
        }

        public void setPasswordWindowSeconds(int passwordWindowSeconds) {
            this.passwordWindowSeconds = passwordWindowSeconds;
        }

        public int getTokenIpAttempts() {
            return tokenIpAttempts;
        }

        public void setTokenIpAttempts(int tokenIpAttempts) {
            this.tokenIpAttempts = tokenIpAttempts;
        }

        public int getTokenWindowSeconds() {
            return tokenWindowSeconds;
        }

        public void setTokenWindowSeconds(int tokenWindowSeconds) {
            this.tokenWindowSeconds = tokenWindowSeconds;
        }

        public int getRegistrationIpAttempts() {
            return registrationIpAttempts;
        }

        public void setRegistrationIpAttempts(int registrationIpAttempts) {
            this.registrationIpAttempts = registrationIpAttempts;
        }

        public int getRegistrationSubjectAttempts() {
            return registrationSubjectAttempts;
        }

        public void setRegistrationSubjectAttempts(int registrationSubjectAttempts) {
            this.registrationSubjectAttempts = registrationSubjectAttempts;
        }

        public int getRegistrationWindowSeconds() {
            return registrationWindowSeconds;
        }

        public void setRegistrationWindowSeconds(int registrationWindowSeconds) {
            this.registrationWindowSeconds = registrationWindowSeconds;
        }
    }

    /**
     * HTTP pipeline settings. Only affects the HTTP branch after
     * protocol detection; the MC pipeline keeps vanilla timeouts.
     */
    public static class Http {

        /**
         * Enables HTTPS on the unified Minecraft/HTTP port.
         * <p>
         * When enabled, the protocol switcher accepts TLS ClientHello records on
         * the same socket as Minecraft and plaintext HTTP.
         */
        private boolean httpsEnabled = true;

        /**
         * Read timeout in seconds for HTTP connections. Prevents slowloris.
         */
        private int readTimeoutSeconds = 10;

        /**
         * TLS handshake timeout in seconds for same-port HTTPS connections.
         */
        private int tlsHandshakeTimeoutSeconds = 10;

        /**
         * Maximum HTTP request body size in bytes. Must accommodate texture uploads.
         */
        private int maxContentLengthBytes = 1_048_576;

        public boolean isHttpsEnabled() {
            return httpsEnabled;
        }

        public void setHttpsEnabled(boolean httpsEnabled) {
            this.httpsEnabled = httpsEnabled;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public int getTlsHandshakeTimeoutSeconds() {
            return tlsHandshakeTimeoutSeconds;
        }

        public void setTlsHandshakeTimeoutSeconds(int tlsHandshakeTimeoutSeconds) {
            this.tlsHandshakeTimeoutSeconds = tlsHandshakeTimeoutSeconds;
        }

        public int getMaxContentLengthBytes() {
            return maxContentLengthBytes;
        }

        public void setMaxContentLengthBytes(int maxContentLengthBytes) {
            this.maxContentLengthBytes = maxContentLengthBytes;
        }
    }

    /**
     * Admin web UI authentication settings.
     */
    public static class Admin {

        /**
         * Enables /admin UI and /api/wawelauth/admin/* endpoints.
         */
        private boolean enabled = true;

        /**
         * Static admin token. Prefer using tokenEnvVar in production.
         */
        private String token = "";

        /**
         * Optional environment variable name for the admin token.
         * If present and non-empty at runtime, it overrides {@link #token}.
         */
        private String tokenEnvVar = "WAWELAUTH_ADMIN_TOKEN";

        /**
         * Session lifetime for admin API bearer sessions.
         */
        private long sessionTtlMs = 30L * 60L * 1000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getTokenEnvVar() {
            return tokenEnvVar;
        }

        public void setTokenEnvVar(String tokenEnvVar) {
            this.tokenEnvVar = tokenEnvVar;
        }

        public long getSessionTtlMs() {
            return sessionTtlMs;
        }

        public void setSessionTtlMs(long sessionTtlMs) {
            this.sessionTtlMs = sessionTtlMs;
        }
    }
}
