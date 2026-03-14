package org.fentanylsolutions.wawelauth.wawelcore.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Client-side configuration, stored as client.json in WawelAuth's active data
 * config directory (local or OS-shared depending on local.json).
 * <p>
 * Controls the client account manager behavior (provider defaults, upload
 * policy, etc).
 */
public class ClientConfig {

    /**
     * Provider name to auto-select when none is chosen. Null = ask user. Consumed by wawelclient.
     */
    private String defaultProvider = null;

    /**
     * Regex patterns matched against provider API root URLs.
     * Skin upload is disabled for any provider whose API root matches.
     */
    private List<String> disableSkinUpload = defaultDisableSkinUpload();

    /**
     * Regex patterns matched against provider API root URLs.
     * Cape upload is disabled for any provider whose API root matches.
     */
    private List<String> disableCapeUpload = defaultDisableCapeUpload();

    /**
     * Regex patterns matched against provider name or API root URLs.
     * Texture reset (delete skin/cape) is disabled for any provider that matches.
     */
    private List<String> disableTextureReset = defaultDisableTextureReset();

    private transient List<Pattern> compiledSkinPatterns;
    private transient List<Pattern> compiledCapePatterns;
    private transient List<Pattern> compiledResetPatterns;

    private static List<String> defaultDisableSkinUpload() {
        List<String> list = new ArrayList<>();
        list.add("ely\\.by");
        return list;
    }

    private static List<String> defaultDisableCapeUpload() {
        List<String> list = new ArrayList<>();
        list.add("ely\\.by");
        list.add("^Mojang$");
        return list;
    }

    private static List<String> defaultDisableTextureReset() {
        List<String> list = new ArrayList<>();
        list.add("ely\\.by");
        return list;
    }

    public boolean isSkinUploadDisabled(String providerName, String apiRoot) {
        return matchesAny(providerName, apiRoot, getSkinPatterns());
    }

    public boolean isCapeUploadDisabled(String providerName, String apiRoot) {
        return matchesAny(providerName, apiRoot, getCapePatterns());
    }

    public boolean isTextureResetDisabled(String providerName, String apiRoot) {
        return matchesAny(providerName, apiRoot, getResetPatterns());
    }

    private boolean matchesAny(String providerName, String apiRoot, List<Pattern> patterns) {
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

    private List<Pattern> getSkinPatterns() {
        if (compiledSkinPatterns == null) {
            compiledSkinPatterns = compilePatterns(disableSkinUpload);
        }
        return compiledSkinPatterns;
    }

    private List<Pattern> getCapePatterns() {
        if (compiledCapePatterns == null) {
            compiledCapePatterns = compilePatterns(disableCapeUpload);
        }
        return compiledCapePatterns;
    }

    private List<Pattern> getResetPatterns() {
        if (compiledResetPatterns == null) {
            compiledResetPatterns = compilePatterns(disableTextureReset);
        }
        return compiledResetPatterns;
    }

    private static List<Pattern> compilePatterns(List<String> raw) {
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

    // --- Existing getters/setters ---

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public List<String> getDisableSkinUpload() {
        return disableSkinUpload;
    }

    public void setDisableSkinUpload(List<String> disableSkinUpload) {
        this.disableSkinUpload = disableSkinUpload;
        this.compiledSkinPatterns = null;
    }

    public List<String> getDisableCapeUpload() {
        return disableCapeUpload;
    }

    public void setDisableCapeUpload(List<String> disableCapeUpload) {
        this.disableCapeUpload = disableCapeUpload;
        this.compiledCapePatterns = null;
    }

    public List<String> getDisableTextureReset() {
        return disableTextureReset;
    }

    public void setDisableTextureReset(List<String> disableTextureReset) {
        this.disableTextureReset = disableTextureReset;
        this.compiledResetPatterns = null;
    }
}
