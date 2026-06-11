package org.fentanylsolutions.wawelauth.api;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;

final class TextureResolverCache {

    private static final int CLEANUP_INTERVAL_TICKS = 20 * 60;

    private final ConcurrentHashMap<String, TextureEntry> skinEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TextureEntry> capeEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkinModel> resolvedSkinModels = new ConcurrentHashMap<>();
    private final AtomicLong generationCounter = new AtomicLong(0);
    private final Object completionLock = new Object();
    private int cleanupTicks;

    TextureEntry get(TextureKind kind, String cacheKey) {
        return entries(kind).get(cacheKey);
    }

    TextureEntry getOrCreate(TextureKind kind, String cacheKey, UUID profileId, String displayName,
        ClientProvider provider, boolean allowUnsigned) {
        TextureEntry created = new TextureEntry(
            kind,
            cacheKey,
            generationCounter.incrementAndGet(),
            profileId,
            displayName,
            provider,
            allowUnsigned);
        TextureEntry existing = cacheEntry(created);
        return existing != null ? existing : created;
    }

    TextureEntry cacheEntry(TextureEntry entry) {
        return entries(entry.kind).putIfAbsent(entry.cacheKey, entry);
    }

    boolean isCurrent(TextureEntry entry) {
        if (entry == null) {
            return false;
        }
        TextureEntry current = entries(entry.kind).get(entry.cacheKey);
        return current == entry && current.generation == entry.generation;
    }

    boolean beginFetchIfCurrent(TextureEntry entry) {
        synchronized (completionLock) {
            if (!isCurrent(entry)) {
                return false;
            }
            entry.state = TextureFetchState.FETCHING;
            return true;
        }
    }

    SkinModel getResolvedSkinModel(String cacheKey) {
        return resolvedSkinModels.get(cacheKey);
    }

    void putResolvedSkinModelIfCurrent(TextureEntry entry, SkinModel model) {
        if (model == null) {
            return;
        }
        synchronized (completionLock) {
            if (isCurrent(entry)) {
                resolvedSkinModels.put(entry.cacheKey, model);
            }
        }
    }

    void completeNoTextureIfCurrent(TextureEntry entry, ResourceLocation noTextureLocation) {
        synchronized (completionLock) {
            if (!isCurrent(entry)) {
                return;
            }
            entry.markResolved(noTextureLocation);
        }
    }

    TextureFetchState completeRegisteredTextureIfCurrent(TextureEntry entry,
        Supplier<ResourceLocation> textureRegistration, Predicate<ResourceLocation> isReadyForRender) {
        synchronized (completionLock) {
            if (!isCurrent(entry)) {
                return null;
            }
            ResourceLocation registeredLocation = textureRegistration.get();
            if (isReadyForRender.test(registeredLocation)) {
                entry.markResolved(registeredLocation);
            } else {
                entry.markRegisteredLoading(registeredLocation);
            }
            return entry.state;
        }
    }

    TextureFetchState completeReadyTextureIfCurrent(TextureEntry entry, Supplier<ResourceLocation> textureRegistration,
        Predicate<ResourceLocation> isReadyForRender) {
        synchronized (completionLock) {
            if (!isCurrent(entry)) {
                return null;
            }
            ResourceLocation registeredLocation = textureRegistration.get();
            if (isReadyForRender.test(registeredLocation)) {
                entry.markResolved(registeredLocation);
            } else {
                entry.markFailure();
            }
            return entry.state;
        }
    }

    boolean handleFetchFailureIfCurrent(TextureEntry entry) {
        synchronized (completionLock) {
            return isCurrent(entry) && entry.markFailure();
        }
    }

    // Render-thread transitions take the same lock as worker completions and
    // re-check the state observed before the lock was acquired.

    void restartIfCurrent(TextureEntry entry, TextureFetchState expectedState, ResourceLocation fallbackLocation) {
        synchronized (completionLock) {
            if (isCurrent(entry) && entry.state == expectedState) {
                entry.texLocation = fallbackLocation;
                entry.state = TextureFetchState.PENDING;
            }
        }
    }

    void retryIfCurrent(TextureEntry entry, TextureFetchState expectedState) {
        synchronized (completionLock) {
            if (isCurrent(entry) && entry.state == expectedState) {
                entry.state = TextureFetchState.PENDING;
            }
        }
    }

    void resolveIfCurrent(TextureEntry entry, TextureFetchState expectedState, ResourceLocation location) {
        synchronized (completionLock) {
            if (isCurrent(entry) && entry.state == expectedState) {
                entry.markResolved(location);
            }
        }
    }

    void failIfCurrent(TextureEntry entry, TextureFetchState expectedState, ResourceLocation fallbackLocation) {
        synchronized (completionLock) {
            if (isCurrent(entry) && entry.state == expectedState) {
                entry.texLocation = fallbackLocation;
                entry.markFailure();
            }
        }
    }

    void invalidate(UUID profileId) {
        if (profileId == null) return;
        String suffix = "|" + profileId.toString();
        synchronized (completionLock) {
            skinEntries.keySet()
                .removeIf(k -> k.endsWith(suffix));
            capeEntries.keySet()
                .removeIf(k -> k.endsWith(suffix));
            resolvedSkinModels.keySet()
                .removeIf(k -> k.endsWith(suffix));
        }
    }

    void clear() {
        synchronized (completionLock) {
            skinEntries.clear();
            capeEntries.clear();
            resolvedSkinModels.clear();
        }
    }

    void tick() {
        cleanupTicks++;
        if (cleanupTicks < CLEANUP_INTERVAL_TICKS) {
            return;
        }
        cleanupTicks = 0;

        long now = System.currentTimeMillis();
        synchronized (completionLock) {
            skinEntries.entrySet()
                .removeIf(entry -> {
                    boolean expired = entry.getValue()
                        .isExpired(now);
                    if (expired) {
                        resolvedSkinModels.remove(entry.getKey());
                    }
                    return expired;
                });
            capeEntries.values()
                .removeIf(entry -> entry.isExpired(now));
        }
    }

    static String buildCacheKey(ClientProvider provider, UUID profileId) {
        String providerName = provider.getName();
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            providerName = "unknown";
        }
        return providerName.trim()
            .toLowerCase() + "|"
            + profileId.toString();
    }

    private ConcurrentHashMap<String, TextureEntry> entries(TextureKind kind) {
        return kind.isSkin() ? skinEntries : capeEntries;
    }
}
