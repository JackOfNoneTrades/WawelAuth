package org.fentanylsolutions.wawelauth;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.fentanylsolutions.wawelauth.wawelclient.OsConfigDir;
import org.fentanylsolutions.wawelauth.wawelcore.config.ClientConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.JsonConfigIO;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Top-level config loader.
 * <p>
 * Local bootstrap config (always local):
 * <p>
 * - local.json (debug mode + useOsConfigDir)
 * <p>
 * Data configs (local or OS-shared based on local.json):
 * <p>
 * - server.json (Yggdrasil server module settings)
 * <p>
 * - client.json (client account manager settings)
 */
public class Config {

    private static final String CONFIG_DIR_NAME = "wawelauth";
    private static final String LOCAL_CONFIG_FILE = "local.json";
    private static final String SERVER_CONFIG_FILE = "server.json";
    private static final String CLIENT_CONFIG_FILE = "client.json";

    private static File minecraftConfigRoot;
    private static File localConfigDir;
    private static File dataConfigDir;

    // JSON configs
    private static LocalConfig localConfig;
    private static ServerConfig serverConfig;
    private static ClientConfig clientConfig;

    /**
     * Load all configs from config/wawelauth/.
     *
     * @param mcConfigDir Minecraft's config/ directory
     */
    public static void loadAll(File mcConfigDir) {
        minecraftConfigRoot = mcConfigDir;
        localConfigDir = ensureDirectory(new File(mcConfigDir, CONFIG_DIR_NAME));
        File localConfigFile = new File(localConfigDir, LOCAL_CONFIG_FILE);
        boolean localConfigExisted = localConfigFile.exists();
        localConfig = JsonConfigIO.load(localConfigFile, LocalConfig.class);
        if (!localConfigExisted) {
            importLegacyBootstrapSettings(localConfig);
            JsonConfigIO.save(localConfigFile, localConfig);
        }

        dataConfigDir = ensureDirectory(resolveDataConfigDir(localConfig.isUseOsConfigDir()));

        serverConfig = JsonConfigIO.load(new File(dataConfigDir, SERVER_CONFIG_FILE), ServerConfig.class);
        serverConfig.validateOrThrow();
        serverConfig.ensureApiRootInSkinDomains();
        clientConfig = JsonConfigIO.load(new File(dataConfigDir, CLIENT_CONFIG_FILE), ClientConfig.class);
    }

    public static void reload() {
        if (minecraftConfigRoot == null) {
            WawelAuth.LOG.warn("Config.reload() called before initial loadAll().");
            return;
        }
        loadAll(minecraftConfigRoot);
    }

    /**
     * Resolves the directory used for non-bootstrap JSON configs.
     * On dedicated server this always resolves to local config/wawelauth/.
     */
    public static File resolveDataConfigDir(boolean useOsConfigDir) {
        if (useOsConfigDir && FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            return OsConfigDir.resolve();
        }
        if (localConfigDir != null) {
            return localConfigDir;
        }
        if (minecraftConfigRoot != null) {
            return new File(minecraftConfigRoot, CONFIG_DIR_NAME);
        }
        return new File(CONFIG_DIR_NAME);
    }

    private static File ensureDirectory(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            WawelAuth.LOG.error("Failed to create config directory: {}", dir);
        }
        return dir;
    }

    private static void importLegacyBootstrapSettings(LocalConfig target) {
        if (target == null || localConfigDir == null) {
            return;
        }
        boolean legacyUseOs = target.isUseOsConfigDir();
        boolean legacyDebug = target.isDebugMode();

        JsonObject legacyClient = parseJsonObject(new File(localConfigDir, CLIENT_CONFIG_FILE));
        if (legacyClient != null) {
            if (legacyClient.has("useOsConfigDir")) {
                legacyUseOs = getBoolean(legacyClient, "useOsConfigDir", legacyUseOs);
            }
            if (legacyClient.has("debugMode")) {
                legacyDebug = legacyDebug || getBoolean(legacyClient, "debugMode", false);
            }
        }

        JsonObject legacyServer = parseJsonObject(new File(localConfigDir, SERVER_CONFIG_FILE));
        if (legacyServer != null && legacyServer.has("debugMode")) {
            legacyDebug = legacyDebug || getBoolean(legacyServer, "debugMode", false);
        }

        target.setUseOsConfigDir(legacyUseOs);
        target.setDebugMode(legacyDebug);
    }

    private static JsonObject parseJsonObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            WawelAuth.LOG.warn("Failed to parse legacy config '{}': {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        try {
            return obj.get(key)
                .getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static LocalConfig local() {
        return localConfig;
    }

    public static ServerConfig server() {
        return serverConfig;
    }

    public static ClientConfig client() {
        return clientConfig;
    }

    public static File getLocalConfigDir() {
        return localConfigDir;
    }

    public static File getDataConfigDir() {
        return dataConfigDir;
    }

    public static File getMinecraftConfigRoot() {
        return minecraftConfigRoot;
    }

    /**
     * Compatibility alias: returns the active data config dir.
     */
    public static File getConfigDir() {
        return dataConfigDir;
    }
}
