package org.fentanylsolutions.wawelauth.wawelclient;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderType;
import org.junit.Assert;
import org.junit.Test;

public class LocalAuthProviderResolverTest {

    @Test
    public void detectsManagedLocalAuthProviderShape() {
        ClientProvider provider = provider("WawelAuth@example.org", "https://example.org/auth/");
        provider.setPublicKeyFingerprint("ABCDEF");
        provider.setAuthServerUrl("https://example.org/auth/authserver/");
        provider.setSessionServerUrl("https://example.org/auth/sessionserver/");

        Assert.assertTrue(LocalAuthProviderResolver.isLocalAuthProvider(provider));
    }

    @Test
    public void generatedNameAloneDoesNotMakeProviderLocalAuth() {
        ClientProvider provider = provider("WawelAuth@example.org", "https://example.org/auth");

        Assert.assertFalse(LocalAuthProviderResolver.isLocalAuthProvider(provider));
    }

    @Test
    public void mismatchedEndpointShapeIsNotLocalAuth() {
        ClientProvider provider = provider("WawelAuth@example.org", "https://example.org/auth");
        provider.setPublicKeyFingerprint("abcdef");
        provider.setAuthServerUrl("https://example.org/other/authserver");
        provider.setSessionServerUrl("https://example.org/auth/sessionserver");

        Assert.assertFalse(LocalAuthProviderResolver.isLocalAuthProvider(provider));
    }

    private static ClientProvider provider(String name, String apiRoot) {
        ClientProvider provider = new ClientProvider();
        provider.setName(name);
        provider.setType(ProviderType.CUSTOM);
        provider.setApiRoot(apiRoot);
        return provider;
    }
}
