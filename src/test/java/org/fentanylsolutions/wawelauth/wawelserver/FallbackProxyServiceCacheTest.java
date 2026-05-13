package org.fentanylsolutions.wawelauth.wawelserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.cache.Cache;
import com.google.gson.JsonObject;

public class FallbackProxyServiceCacheTest {

    @Test
    public void profileCacheIsBoundedByWeight() throws Exception {
        FallbackProxyService service = new FallbackProxyService(new ServerConfig(), 6500L, 1024L * 1024L);

        for (int i = 0; i < 10; i++) {
            JsonObject profile = new JsonObject();
            profile.addProperty("id", "profile-" + i);
            profile.addProperty("name", "player" + i);
            cacheProfile(service, "profile-" + i, profile, 60_000L);
        }

        Assert.assertTrue(cacheSize(service, "profileCache") <= 3L);
    }

    @Test
    public void textureCacheIsBoundedByWeight() throws Exception {
        FallbackProxyService service = new FallbackProxyService(new ServerConfig(), 1024L * 1024L, 120_000L);

        for (int i = 0; i < 10; i++) {
            cacheTexture(service, "texture-" + i, new byte[40_000], "image/png", 60_000L);
        }

        Assert.assertTrue(cacheSize(service, "textureCache") <= 2L);
    }

    @Test
    public void profileCacheKeepsPerEntryTtl() throws Exception {
        FallbackProxyService service = new FallbackProxyService(new ServerConfig(), 6500L, 1024L * 1024L);
        JsonObject profile = new JsonObject();
        profile.addProperty("id", "expired");

        cacheProfile(service, "expired", profile, 1L);
        Thread.sleep(10L);

        Assert.assertNull(getCachedProfile(service, "expired"));
    }

    @Test
    public void textureCacheKeepsPerEntryTtl() throws Exception {
        FallbackProxyService service = new FallbackProxyService(new ServerConfig(), 1024L * 1024L, 120_000L);

        cacheTexture(service, "expired", new byte[128], "image/png", 1L);
        Thread.sleep(10L);

        Assert.assertNull(getCachedTexture(service, "expired"));
    }

    private static void cacheProfile(FallbackProxyService service, String key, JsonObject profile, long ttlMs)
        throws Exception {
        Method method = FallbackProxyService.class
            .getDeclaredMethod("cacheProfile", String.class, JsonObject.class, long.class);
        method.setAccessible(true);
        method.invoke(service, key, profile, Long.valueOf(ttlMs));
    }

    private static JsonObject getCachedProfile(FallbackProxyService service, String key) throws Exception {
        Method method = FallbackProxyService.class.getDeclaredMethod("getCachedProfile", String.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(service, key);
    }

    private static void cacheTexture(FallbackProxyService service, String key, byte[] data, String contentType,
        long ttlMs) throws Exception {
        Method method = FallbackProxyService.class
            .getDeclaredMethod("cacheTexture", String.class, byte[].class, String.class, long.class);
        method.setAccessible(true);
        method.invoke(service, key, data, contentType, Long.valueOf(ttlMs));
    }

    private static Object getCachedTexture(FallbackProxyService service, String key) throws Exception {
        Method method = FallbackProxyService.class.getDeclaredMethod("getCachedTexture", String.class);
        method.setAccessible(true);
        return method.invoke(service, key);
    }

    private static long cacheSize(FallbackProxyService service, String fieldName) throws Exception {
        Field field = FallbackProxyService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Cache<?, ?> cache = (Cache<?, ?>) field.get(service);
        cache.cleanUp();
        return cache.size();
    }
}
