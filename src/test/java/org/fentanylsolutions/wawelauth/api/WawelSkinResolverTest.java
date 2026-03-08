package org.fentanylsolutions.wawelauth.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

public class WawelSkinResolverTest {

    @Test
    public void buildCacheKeyKeepsProviderScopesSeparate() {
        UUID profileId = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

        String alphaKey = WawelSkinResolver.buildCacheKey(WawelSkinResolver.buildProviderScope("Alpha"), profileId);
        String betaKey = WawelSkinResolver.buildCacheKey(WawelSkinResolver.buildProviderScope("Beta"), profileId);

        Assert.assertEquals("provider:alpha|12345678-1234-1234-1234-1234567890ab", alphaKey);
        Assert.assertEquals("provider:beta|12345678-1234-1234-1234-1234567890ab", betaKey);
        Assert.assertNotEquals(alphaKey, betaKey);
    }

    @Test
    public void normalizeSkinDomainsInfersSessionHostWhenDomainsMissing() {
        List<String> domains = WawelSkinResolver
            .normalizeSkinDomains("https://session.example.com/sessionserver/", Collections.<String>emptyList());

        Assert.assertEquals(Collections.singletonList("session.example.com"), domains);
    }

    @Test
    public void normalizeSkinDomainsDeduplicatesAndNormalizesHosts() {
        List<String> domains = WawelSkinResolver.normalizeSkinDomains(
            "https://session.example.com/sessionserver",
            Arrays.asList(
                "TEXTURES.EXAMPLE.COM",
                ".example.com",
                "https://textures.example.com/skins/hash.png",
                "session.example.com"));

        Assert.assertEquals(Arrays.asList("session.example.com", "textures.example.com", ".example.com"), domains);
    }
}
