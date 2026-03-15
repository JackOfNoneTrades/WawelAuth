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

    private boolean wawelAuthEnabled = true;
    private boolean enableLocalAuth = true;
    private String serverName = "A Wawel Auth Server";

    /**
     * Public-facing API root URL (e.g. "https://auth.example.com").
     * Used to construct texture URLs and the ALI metadata response.
     */
    private String apiRoot = "";
    private String publicPagePath = "/";
    private String publicInfoApiPath = "__server-info";

    /**
     * Domains from which clients should accept texture URLs.
     * IMPORTANT: If this is empty and your server serves textures,
     * clients will reject those URLs. Set this to your server's domain
     * (e.g. ["auth.example.com"]). If apiRoot is set, the server module
     * will auto-add its host to this list at runtime.
     */
    private List<String> skinDomains = new ArrayList<>();

    private Meta meta = new Meta();
    private Features features = new Features();
    private Registration registration = new Registration();
    private Invites invites = new Invites();
    private Textures textures = new Textures();
    private Tokens tokens = new Tokens();
    private Http http = new Http();
    private Admin admin = new Admin();
    private List<FallbackServer> fallbackServers = new ArrayList<>();

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

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getApiRoot() {
        return apiRoot;
    }

    public void setApiRoot(String apiRoot) {
        this.apiRoot = apiRoot;
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
     * Route prefix implied by apiRoot's path component.
     * <p>
     * Examples:
     * - "https://auth.example.com" -> ""
     * - "https://auth.example.com/auth" -> "/auth"
     * - "https://auth.example.com/auth/" -> "/auth"
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
     * Ensures the apiRoot's host is in skinDomains. Call this at server startup
     * after config is loaded, so clients accept texture URLs from this server.
     */
    public void ensureApiRootInSkinDomains() {
        if (apiRoot == null || apiRoot.isEmpty()) return;
        try {
            String host = new URI(apiRoot).getHost();
            if (host != null && !getSkinDomains().contains(host)) {
                getSkinDomains().add(host);
            }
        } catch (Exception e) {
            WawelAuth.LOG
                .warn("Invalid apiRoot '{}', could not extract host for skinDomains: {}", apiRoot, e.getMessage());
        }
    }

    public static String normalizeApiRoutePrefix(String rawApiRoot) {
        String raw = rawApiRoot == null ? null : rawApiRoot.trim();
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        try {
            URI uri = new URI(raw);
            return normalizePathPrefix(uri.getPath());
        } catch (Exception ignored) {
            return normalizePathPrefix(raw);
        }
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
        if (httpConfig.getMaxContentLengthBytes() < 1) {
            throw new IllegalStateException("http.maxContentLengthBytes must be >= 1.");
        }
        if (httpConfig.getMaxContentLengthBytes() < texturesConfig.getMaxFileSizeBytes()) {
            throw new IllegalStateException("http.maxContentLengthBytes must be >= textures.maxFileSizeBytes.");
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

        private String implementationName = "Wawel Auth";
        private String serverHomepage = "";
        private String serverRegister = "";
        private String publicDescription = "";

        public String getImplementationName() {
            return implementationName;
        }

        public void setImplementationName(String implementationName) {
            this.implementationName = implementationName;
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
        private boolean allowElytra = true;
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

    /**
     * HTTP pipeline settings. Only affects the HTTP branch after
     * protocol detection; the MC pipeline keeps vanilla timeouts.
     */
    public static class Http {

        /**
         * Read timeout in seconds for HTTP connections. Prevents slowloris.
         */
        private int readTimeoutSeconds = 10;

        /**
         * Maximum HTTP request body size in bytes. Must accommodate texture uploads.
         */
        private int maxContentLengthBytes = 1_048_576;

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
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
