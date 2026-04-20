package org.fentanylsolutions.wawelauth.wawelclient.oauth;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

public final class ProviderOAuthClients {

    private static final ProviderOAuthClient[] CLIENTS = { new ElyByOAuthClient(), new LittleSkinOAuthClient() };

    private ProviderOAuthClients() {}

    public static ProviderOAuthClient resolve(String providerName) {
        if (providerName == null) {
            return null;
        }
        for (ProviderOAuthClient client : CLIENTS) {
            if (client.supports(providerName)) {
                return client;
            }
        }
        return null;
    }

    public static ProviderOAuthClient resolve(ClientProvider provider) {
        return provider != null ? resolve(provider.getName()) : null;
    }

    public static boolean supports(String providerName) {
        return resolve(providerName) != null;
    }
}
