package org.fentanylsolutions.wawelauth.wawelcore.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.gtnewhorizon.gtnhlib.config.Config;

/**
 * Client-side configuration for account manager behavior (provider defaults,
 * upload policy, etc).
 */
@Config(modid = "wawelauth", category = "client")
public class ClientConfig {

    @Config.Comment("Provider name to auto-select when none is chosen. Empty = ask user.")
    @Config.DefaultString("")
    public static String defaultProvider = "";

    @Config.Comment("Regex patterns matched against provider name/API root. Skin upload is disabled for matches.")
    @Config.DefaultStringList({ "ely\\.by" })
    public static String[] disableSkinUpload = { "ely\\.by", "littleskin\\.cn" };

    @Config.Comment("Regex patterns matched against provider name/API root. Cape upload is disabled for matches.")
    @Config.DefaultStringList({ "ely\\.by", "^Mojang$" })
    public static String[] disableCapeUpload = { "ely\\.by", "^Mojang$", "littleskin\\.cn" };

    @Config.Comment("Regex patterns matched against provider name/API root. Texture reset is disabled for matches.")
    @Config.DefaultStringList({ "ely\\.by" })
    public static String[] disableTextureReset = { "ely\\.by", "littleskin\\.cn" };

    @Config.Ignore
    private static transient List<Pattern> compiledSkinPatterns;
    @Config.Ignore
    private static transient List<Pattern> compiledCapePatterns;
    @Config.Ignore
    private static transient List<Pattern> compiledResetPatterns;

    public static boolean isSkinUploadDisabled(String providerName, String apiRoot) {
        return matchesAny(providerName, apiRoot, getSkinPatterns());
    }

    public static boolean isCapeUploadDisabled(String providerName, String apiRoot) {
        return matchesAny(providerName, apiRoot, getCapePatterns());
    }

    public static boolean isTextureResetDisabled(String providerName, String apiRoot) {
        return matchesAny(providerName, apiRoot, getResetPatterns());
    }

    /**
     * Invalidate compiled pattern caches so they are rebuilt on next access.
     * Call this after modifying the pattern arrays.
     */
    public static void invalidatePatternCache() {
        compiledSkinPatterns = null;
        compiledCapePatterns = null;
        compiledResetPatterns = null;
    }

    private static boolean matchesAny(String providerName, String apiRoot, List<Pattern> patterns) {
        if (patterns.isEmpty()) return false;
        for (Pattern p : patterns) {
            if (providerName != null && p.matcher(providerName)
                .find()) {
                return true;
            }
            if (apiRoot != null && !apiRoot.isEmpty()
                && p.matcher(apiRoot)
                    .find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> getSkinPatterns() {
        if (compiledSkinPatterns == null) {
            compiledSkinPatterns = compilePatterns(disableSkinUpload);
        }
        return compiledSkinPatterns;
    }

    private static List<Pattern> getCapePatterns() {
        if (compiledCapePatterns == null) {
            compiledCapePatterns = compilePatterns(disableCapeUpload);
        }
        return compiledCapePatterns;
    }

    private static List<Pattern> getResetPatterns() {
        if (compiledResetPatterns == null) {
            compiledResetPatterns = compilePatterns(disableTextureReset);
        }
        return compiledResetPatterns;
    }

    private static List<Pattern> compilePatterns(String[] raw) {
        List<Pattern> result = new ArrayList<>();
        if (raw == null) return result;
        for (String s : raw) {
            if (s != null && !s.trim()
                .isEmpty()) {
                result.add(Pattern.compile(s, Pattern.CASE_INSENSITIVE));
            }
        }
        return result;
    }

    /**
     * Returns the default provider, or null if empty/blank.
     */
    public static String getDefaultProviderOrNull() {
        if (defaultProvider == null || defaultProvider.trim()
            .isEmpty()) {
            return null;
        }
        return defaultProvider;
    }

    /**
     * Returns the disableSkinUpload patterns as a mutable list.
     */
    public static List<String> getDisableSkinUploadList() {
        return new ArrayList<>(Arrays.asList(disableSkinUpload));
    }

    /**
     * Returns the disableCapeUpload patterns as a mutable list.
     */
    public static List<String> getDisableCapeUploadList() {
        return new ArrayList<>(Arrays.asList(disableCapeUpload));
    }

    /**
     * Returns the disableTextureReset patterns as a mutable list.
     */
    public static List<String> getDisableTextureResetList() {
        return new ArrayList<>(Arrays.asList(disableTextureReset));
    }
}
