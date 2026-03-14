package org.fentanylsolutions.wawelauth.wawelcore.config;

/**
 * Local bootstrap configuration, always stored in the local Minecraft config
 * directory (config/wawelauth/local.json).
 * <p>
 * This config decides where the rest of WawelAuth's JSON configs live and
 * controls global debug logging.
 */
public class LocalConfig {

    /**
     * Enable verbose WawelAuth debug logging.
     */
    private boolean debugMode = false;

    /**
     * When true on client side, use OS shared config directory for data configs
     * (client.json, server.json, etc). Instance-specific rendering config stays local.
     */
    private boolean useOsConfigDir = true;

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isUseOsConfigDir() {
        return useOsConfigDir;
    }

    public void setUseOsConfigDir(boolean useOsConfigDir) {
        this.useOsConfigDir = useOsConfigDir;
    }
}
