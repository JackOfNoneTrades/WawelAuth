package org.fentanylsolutions.wawelauth.wawelclient;

import org.fentanylsolutions.wawelauth.wawelclient.http.YggdrasilHttpClient;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;

/**
 * CurseForge distribution: privacy-sensitive launcher integrations are omitted.
 */
public final class ClientStartupExtensions {

    private ClientStartupExtensions() {}

    public static void start(YggdrasilHttpClient httpClient, ClientProviderDAO providerDAO, ClientAccountDAO accountDAO,
        AccountManager accountManager) {}

    public static void stop() {}
}
