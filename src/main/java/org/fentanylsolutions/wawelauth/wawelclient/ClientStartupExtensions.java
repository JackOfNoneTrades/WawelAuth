package org.fentanylsolutions.wawelauth.wawelclient;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;

/**
 * Optional client integrations that are not present in every distribution build.
 */
public final class ClientStartupExtensions {

    private ClientStartupExtensions() {}

    public static void start(YggdrasilHttpClient httpClient, ClientProviderDAO providerDAO, ClientAccountDAO accountDAO,
        AccountManager accountManager) {
        LauncherAccountImport.start(
            httpClient,
            providerDAO,
            accountDAO,
            accountManager,
            Minecraft.getMinecraft()
                .getSession());
    }

    public static void stop() {
        LauncherAccountImport.stop();
    }
}
