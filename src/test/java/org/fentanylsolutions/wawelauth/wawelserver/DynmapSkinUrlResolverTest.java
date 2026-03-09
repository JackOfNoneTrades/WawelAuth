package org.fentanylsolutions.wawelauth.wawelserver;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Assert;
import org.junit.Test;

public class DynmapSkinUrlResolverTest {

    @Test
    public void extractsSkinUrlFromTexturesProperty() {
        String property = Base64.getEncoder()
            .encodeToString(
                "{\"textures\":{\"SKIN\":{\"url\":\"http://127.0.0.1:25565/textures/hash\"}}}"
                    .getBytes(StandardCharsets.UTF_8));

        Assert.assertEquals(
            "http://127.0.0.1:25565/textures/hash",
            DynmapSkinUrlResolver.extractSkinUrlFromTexturesProperty(property));
    }

    @Test
    public void invalidTexturesPropertyReturnsNull() {
        Assert.assertNull(DynmapSkinUrlResolver.extractSkinUrlFromTexturesProperty("not-base64"));
    }

    @Test
    public void absolutizeSkinUrlExpandsRelativeTexturePath() {
        Assert.assertEquals(
            "http://[::1]:25565/textures/hash",
            DynmapSkinUrlResolver.absolutizeSkinUrl("/textures/hash", "http://[::1]:25565"));
        Assert.assertEquals(
            "http://localhost:25565/textures/hash",
            DynmapSkinUrlResolver.absolutizeSkinUrl("textures/hash", "http://localhost:25565"));
    }

    @Test
    public void absolutizeSkinUrlLeavesAbsoluteUrlsUntouched() {
        Assert.assertEquals(
            "https://textures.minecraft.net/texture/hash",
            DynmapSkinUrlResolver
                .absolutizeSkinUrl("https://textures.minecraft.net/texture/hash", "http://localhost:25565"));
    }
}
