package org.fentanylsolutions.wawelauth.api;

import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.junit.Assert;
import org.junit.Test;

public class WawelTextureResolverTest {

    @Test
    public void buildCacheKeyKeepsProvidersSeparate() {
        UUID profileId = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

        ClientProvider alpha = new ClientProvider();
        alpha.setName("Alpha");
        ClientProvider beta = new ClientProvider();
        beta.setName("Beta");

        String alphaKey = WawelTextureResolver.buildCacheKey(alpha, profileId);
        String betaKey = WawelTextureResolver.buildCacheKey(beta, profileId);

        Assert.assertEquals("alpha|12345678-1234-1234-1234-1234567890ab", alphaKey);
        Assert.assertEquals("beta|12345678-1234-1234-1234-1234567890ab", betaKey);
        Assert.assertNotEquals(alphaKey, betaKey);
    }
}
