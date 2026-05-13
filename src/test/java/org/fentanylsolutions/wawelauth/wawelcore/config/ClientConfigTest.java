package org.fentanylsolutions.wawelauth.wawelcore.config;

import org.junit.Assert;
import org.junit.Test;

public class ClientConfigTest {

    @Test
    public void credentialsAreDisabledForDefaultExternalProviders() {
        ClientConfig.invalidatePatternCache();

        Assert.assertTrue(ClientConfig.isCredentialsDisabled("Mojang", "https://api.minecraftservices.com"));
        Assert.assertTrue(ClientConfig.isCredentialsDisabled("Ely.by", "https://account.ely.by/api/authlib-injector"));
        Assert.assertTrue(ClientConfig.isCredentialsDisabled("LittleSkin", "https://littleskin.cn/api/yggdrasil"));
    }

    @Test
    public void credentialsRemainAvailableForUnmatchedProviders() {
        ClientConfig.invalidatePatternCache();

        Assert.assertFalse(ClientConfig.isCredentialsDisabled("WawelAuth@example.org", "https://example.org/auth"));
    }
}
