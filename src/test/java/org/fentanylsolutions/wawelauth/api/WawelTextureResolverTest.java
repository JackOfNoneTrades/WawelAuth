package org.fentanylsolutions.wawelauth.api;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.junit.Assert;
import org.junit.Test;

public class WawelTextureResolverTest {

    @Test
    public void buildCacheKeyKeepsProvidersSeparate() {
        UUID profileId = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

        ClientProvider alpha = provider("Alpha");
        ClientProvider beta = provider("Beta");

        String alphaKey = WawelTextureResolver.buildCacheKey(alpha, profileId);
        String betaKey = WawelTextureResolver.buildCacheKey(beta, profileId);

        Assert.assertEquals("alpha|12345678-1234-1234-1234-1234567890ab", alphaKey);
        Assert.assertEquals("beta|12345678-1234-1234-1234-1234567890ab", betaKey);
        Assert.assertNotEquals(alphaKey, betaKey);
    }

    @Test
    public void staleEntryCannotCompleteAfterInvalidation() {
        ThreadPoolExecutor executor = executor();
        try {
            WawelTextureResolver resolver = new WawelTextureResolver(null, executor);
            UUID profileId = UUID.fromString("22222222-3333-4444-5555-666666666666");
            ClientProvider provider = provider("Alpha");
            String cacheKey = WawelTextureResolver.buildCacheKey(provider, profileId);

            WawelTextureResolver.SkinEntry oldEntry = new WawelTextureResolver.SkinEntry(
                cacheKey,
                1L,
                profileId,
                "Player",
                provider,
                false);

            Assert.assertNull(resolver.cacheEntry(oldEntry));
            Assert.assertTrue(resolver.isCurrent(oldEntry));

            resolver.putResolvedSkinModelIfCurrent(oldEntry, SkinModel.SLIM);
            Assert.assertEquals(SkinModel.SLIM, resolver.getResolvedSkinModel(profileId, provider));

            resolver.invalidate(profileId);

            Assert.assertFalse(resolver.isCurrent(oldEntry));
            Assert.assertNull(resolver.getResolvedSkinModel(profileId, provider));

            resolver.putResolvedSkinModelIfCurrent(oldEntry, SkinModel.CLASSIC);
            resolver.completeNoTextureIfCurrent(oldEntry);

            Assert.assertNull(resolver.getResolvedSkinModel(profileId, provider));
            Assert.assertEquals(WawelTextureResolver.FetchState.PENDING, oldEntry.state);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void replacementEntryIsOnlyCompletedByItsOwnGeneration() {
        ThreadPoolExecutor executor = executor();
        try {
            WawelTextureResolver resolver = new WawelTextureResolver(null, executor);
            UUID profileId = UUID.fromString("77777777-8888-4999-aaaa-bbbbbbbbbbbb");
            ClientProvider provider = provider("Alpha");
            String cacheKey = WawelTextureResolver.buildCacheKey(provider, profileId);

            WawelTextureResolver.SkinEntry oldEntry = new WawelTextureResolver.SkinEntry(
                cacheKey,
                1L,
                profileId,
                "Player",
                provider,
                false);
            WawelTextureResolver.SkinEntry newEntry = new WawelTextureResolver.SkinEntry(
                cacheKey,
                2L,
                profileId,
                "Player",
                provider,
                false);

            resolver.cacheEntry(oldEntry);
            resolver.invalidate(profileId);
            resolver.cacheEntry(newEntry);

            Assert.assertFalse(resolver.isCurrent(oldEntry));
            Assert.assertTrue(resolver.isCurrent(newEntry));

            resolver.completeNoTextureIfCurrent(oldEntry);
            Assert.assertEquals(WawelTextureResolver.FetchState.PENDING, newEntry.state);

            resolver.completeNoTextureIfCurrent(newEntry);
            Assert.assertEquals(WawelTextureResolver.FetchState.RESOLVED, newEntry.state);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectedFetchSubmissionDoesNotLeaveEntryFetchingForever() {
        ThreadPoolExecutor executor = executor();
        executor.shutdownNow();

        WawelTextureResolver resolver = new WawelTextureResolver(null, executor);
        UUID profileId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        ClientProvider provider = provider("Alpha");
        String cacheKey = WawelTextureResolver.buildCacheKey(provider, profileId);

        resolver.getSkin(profileId, "Player", provider, false);

        WawelTextureResolver.TextureEntry entry = resolver.getSkinEntry(cacheKey);
        Assert.assertNotNull(entry);
        Assert.assertFalse(entry.fetchInFlight.get());
        Assert.assertEquals(WawelTextureResolver.FetchState.PLACEHOLDER, entry.state);
        Assert.assertEquals(1, entry.retryCount);
    }

    @Test
    public void registeredLoadingEntryCanTimeOut() {
        UUID profileId = UUID.fromString("dddddddd-1111-4222-8333-eeeeeeeeeeee");
        ClientProvider provider = provider("Alpha");
        String cacheKey = WawelTextureResolver.buildCacheKey(provider, profileId);
        WawelTextureResolver.SkinEntry entry = new WawelTextureResolver.SkinEntry(
            cacheKey,
            1L,
            profileId,
            "Player",
            provider,
            false);

        entry.state = WawelTextureResolver.FetchState.REGISTERED_LOADING;
        entry.lastAttemptMs = System.currentTimeMillis();
        Assert.assertFalse(entry.isRegisteredLoadingTimedOut());

        entry.lastAttemptMs = 1L;
        Assert.assertTrue(entry.isRegisteredLoadingTimedOut());
    }

    @Test
    public void tickRemovesSkinModelWhenResolvedSkinEntryExpires() {
        ThreadPoolExecutor executor = executor();
        try {
            WawelTextureResolver resolver = new WawelTextureResolver(null, executor);
            UUID profileId = UUID.fromString("eeeeeeee-1111-4222-8333-ffffffffffff");
            ClientProvider provider = provider("Alpha");
            String cacheKey = WawelTextureResolver.buildCacheKey(provider, profileId);
            WawelTextureResolver.SkinEntry entry = new WawelTextureResolver.SkinEntry(
                cacheKey,
                1L,
                profileId,
                "Player",
                provider,
                false);

            resolver.cacheEntry(entry);
            resolver.putResolvedSkinModelIfCurrent(entry, SkinModel.SLIM);
            resolver.completeNoTextureIfCurrent(entry);
            entry.resolvedAtMs = 1L;

            tickUntilCleanup(resolver);

            Assert.assertNull(resolver.getSkinEntry(cacheKey));
            Assert.assertNull(resolver.getResolvedSkinModel(profileId, provider));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void tickDoesNotSweepOnEveryClientTick() {
        ThreadPoolExecutor executor = executor();
        try {
            WawelTextureResolver resolver = new WawelTextureResolver(null, executor);
            UUID profileId = UUID.fromString("ffffffff-1111-4222-8333-aaaaaaaaaaaa");
            ClientProvider provider = provider("Alpha");
            String cacheKey = WawelTextureResolver.buildCacheKey(provider, profileId);
            WawelTextureResolver.SkinEntry entry = new WawelTextureResolver.SkinEntry(
                cacheKey,
                1L,
                profileId,
                "Player",
                provider,
                false);

            resolver.cacheEntry(entry);
            resolver.putResolvedSkinModelIfCurrent(entry, SkinModel.SLIM);
            resolver.completeNoTextureIfCurrent(entry);
            entry.resolvedAtMs = 1L;

            resolver.tick();

            Assert.assertSame(entry, resolver.getSkinEntry(cacheKey));
            Assert.assertEquals(SkinModel.SLIM, resolver.getResolvedSkinModel(profileId, provider));
        } finally {
            executor.shutdownNow();
        }
    }

    private static ClientProvider provider(String name) {
        ClientProvider provider = new ClientProvider();
        provider.setName(name);
        return provider;
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private static void tickUntilCleanup(WawelTextureResolver resolver) {
        for (int i = 0; i < 20 * 60; i++) {
            resolver.tick();
        }
    }
}
