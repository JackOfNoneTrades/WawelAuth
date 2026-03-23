package org.fentanylsolutions.wawelauth.client.gui;

import org.fentanylsolutions.fentlib.util.GuiText;
import org.fentanylsolutions.wawelauth.wawelclient.BuiltinProviders;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ProviderDisplayName {

    private static final String MICROSOFT_DISPLAY_NAME_KEY = "wawelauth.gui.common.microsoft";
    private static final String OFFLINE_DISPLAY_NAME_KEY = "wawelauth.gui.common.offline_account";

    private ProviderDisplayName() {}

    public static boolean isMicrosoftProvider(String providerName) {
        return BuiltinProviders.isMojangProvider(providerName);
    }

    public static boolean isOfflineProvider(String providerName) {
        return BuiltinProviders.isOfflineProvider(providerName);
    }

    public static String displayName(String providerName) {
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return "?";
        }
        if (isMicrosoftProvider(providerName)) {
            return GuiText.tr(MICROSOFT_DISPLAY_NAME_KEY);
        }
        if (isOfflineProvider(providerName)) {
            return GuiText.tr(OFFLINE_DISPLAY_NAME_KEY);
        }
        return providerName;
    }
}
