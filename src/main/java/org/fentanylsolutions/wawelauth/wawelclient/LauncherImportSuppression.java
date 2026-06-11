package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.JsonConfigIO;
import org.fentanylsolutions.wawelauth.wawelcore.config.LocalConfig;

/**
 * Remembers launcher accounts the user chose not to import ("Don't ask again").
 */
public final class LauncherImportSuppression {

    private static final String FILE_NAME = "launcher_import_suppression.json";
    private static SuppressionConfig config;

    private LauncherImportSuppression() {}

    public static synchronized boolean isSuppressed(String providerName, UUID profileUuid) {
        return getConfig().suppressed.contains(key(providerName, profileUuid));
    }

    public static synchronized void suppress(String providerName, UUID profileUuid) {
        SuppressionConfig current = getConfig();
        String key = key(providerName, profileUuid);
        if (!current.suppressed.contains(key)) {
            current.suppressed.add(key);
            save(current);
        }
    }

    private static String key(String providerName, UUID profileUuid) {
        return (providerName == null ? "" : providerName) + "|" + (profileUuid == null ? "" : profileUuid.toString());
    }

    private static SuppressionConfig getConfig() {
        if (config == null) {
            config = JsonConfigIO.load(resolveStorageFile(), SuppressionConfig.class);
            if (config.suppressed == null) {
                config.suppressed = new ArrayList<>();
            }
        }
        return config;
    }

    private static void save(SuppressionConfig current) {
        config = current;
        JsonConfigIO.save(resolveStorageFile(), current);
    }

    private static File resolveStorageFile() {
        File baseDir;
        LocalConfig local = Config.local();
        if (local != null && local.isUseOsConfigDir()) {
            baseDir = Config.getDataConfigDir();
        } else {
            baseDir = new File(Config.getConfigDir(), "client");
        }
        if (baseDir != null && !baseDir.exists() && !baseDir.mkdirs()) {
            WawelAuth.LOG.warn("Failed to create launcher import suppression directory: {}", baseDir);
        }
        return new File(baseDir, FILE_NAME);
    }

    public static final class SuppressionConfig {

        public List<String> suppressed = new ArrayList<>();

        public SuppressionConfig() {}
    }
}
