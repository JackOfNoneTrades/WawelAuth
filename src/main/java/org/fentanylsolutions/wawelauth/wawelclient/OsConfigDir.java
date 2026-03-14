package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.File;

/**
 * Resolves the OS-specific configuration directory for WawelAuth's
 * client account database.
 * <p>
 * When local bootstrap config (local.json) has {@code useOsConfigDir=true},
 * the account database and other data configs are stored in a shared OS
 * location instead of inside the .minecraft directory, allowing multiple
 * Minecraft instances to share accounts/config.
 */
public final class OsConfigDir {

    private static final String APP_DIR_NAME = "wawelauth";

    private OsConfigDir() {}

    /**
     * Returns the OS-specific config directory for WawelAuth.
     *
     * <ul>
     * <li>Linux: {@code $XDG_DATA_HOME/wawelauth/} or {@code ~/.local/share/wawelauth/}</li>
     * <li>macOS: {@code ~/Library/Application Support/wawelauth/}</li>
     * <li>Windows: {@code %APPDATA%/wawelauth/}</li>
     * </ul>
     *
     * @return the resolved directory (may not exist yet)
     */
    public static File resolve() {
        String os = System.getProperty("os.name", "")
            .toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                return new File(appdata, APP_DIR_NAME);
            }
            return new File(System.getProperty("user.home"), "AppData/Roaming/" + APP_DIR_NAME);
        } else if (os.contains("mac")) {
            return new File(System.getProperty("user.home"), "Library/Application Support/" + APP_DIR_NAME);
        } else {
            // Linux / other Unix
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null && !xdgData.isEmpty()) {
                return new File(xdgData, APP_DIR_NAME);
            }
            return new File(System.getProperty("user.home"), ".local/share/" + APP_DIR_NAME);
        }
    }
}
