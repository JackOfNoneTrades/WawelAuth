package org.fentanylsolutions.wawelauth.wawelclient;

/**
 * Stable identifiers for special client providers.
 */
public final class BuiltinProviders {

    public static final String MOJANG_PROVIDER_NAME = "Mojang";
    public static final String OFFLINE_PROVIDER_NAME = "Offline Account";
    public static final String LITTLESKIN_PROVIDER_NAME = "LittleSkin";
    public static final String ELY_BY_PROVIDER_NAME = "Ely.by";

    private BuiltinProviders() {}

    public static boolean isMojangProvider(String providerName) {
        return MOJANG_PROVIDER_NAME.equals(providerName);
    }

    public static boolean isOfflineProvider(String providerName) {
        return OFFLINE_PROVIDER_NAME.equals(providerName);
    }

    public static boolean isLittleSkinProvider(String providerName) {
        return LITTLESKIN_PROVIDER_NAME.equals(providerName);
    }

    public static boolean isElyByProvider(String providerName) {
        return ELY_BY_PROVIDER_NAME.equals(providerName);
    }
}
