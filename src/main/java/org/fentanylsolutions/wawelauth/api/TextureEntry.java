package org.fentanylsolutions.wawelauth.api;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;

final class TextureEntry {

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = { 2_000L, 8_000L, 30_000L };
    private static final long TEXTURE_TTL_MS = 20 * 60 * 1_000L;
    private static final long FAILED_RETRY_MS = 60_000L;
    private static final long REGISTERED_LOADING_TIMEOUT_MS = 30_000L;

    final TextureKind kind;
    final String cacheKey;
    final long generation;
    final UUID profileId;
    final String displayName;
    final ClientProvider provider;
    final boolean allowUnsigned;
    final AtomicBoolean fetchInFlight = new AtomicBoolean(false);

    volatile TextureFetchState state = TextureFetchState.PENDING;
    volatile ResourceLocation texLocation;
    volatile long resolvedAtMs;
    volatile long lastAttemptMs;
    volatile int retryCount;

    TextureEntry(TextureKind kind, String cacheKey, long generation, UUID profileId, String displayName,
        ClientProvider provider, boolean allowUnsigned) {
        this.kind = kind;
        this.cacheKey = cacheKey;
        this.generation = generation;
        this.profileId = profileId;
        this.displayName = displayName;
        this.provider = provider;
        this.allowUnsigned = allowUnsigned;
    }

    boolean isExpired() {
        return isExpired(System.currentTimeMillis());
    }

    boolean isExpired(long now) {
        return state == TextureFetchState.RESOLVED && resolvedAtMs > 0 && now - resolvedAtMs > TEXTURE_TTL_MS;
    }

    boolean shouldRetry() {
        long now = System.currentTimeMillis();
        if (state == TextureFetchState.FAILED) {
            return now - lastAttemptMs > FAILED_RETRY_MS;
        }
        if (state == TextureFetchState.PLACEHOLDER) {
            long delay = retryCount < RETRY_DELAYS_MS.length ? RETRY_DELAYS_MS[retryCount]
                : RETRY_DELAYS_MS[RETRY_DELAYS_MS.length - 1];
            return now - lastAttemptMs > delay;
        }
        return false;
    }

    boolean isRegisteredLoadingTimedOut() {
        return lastAttemptMs > 0 && System.currentTimeMillis() - lastAttemptMs > REGISTERED_LOADING_TIMEOUT_MS;
    }

    void markResolved(ResourceLocation texLocation) {
        this.texLocation = texLocation;
        this.state = TextureFetchState.RESOLVED;
        this.resolvedAtMs = System.currentTimeMillis();
        this.retryCount = 0;
    }

    void markRegisteredLoading(ResourceLocation texLocation) {
        this.texLocation = texLocation;
        this.state = TextureFetchState.REGISTERED_LOADING;
        this.lastAttemptMs = System.currentTimeMillis();
    }

    /** @return true exactly once, on the attempt that exhausts the retry budget. */
    boolean markFailure() {
        retryCount++;
        lastAttemptMs = System.currentTimeMillis();
        state = retryCount >= MAX_RETRIES ? TextureFetchState.FAILED : TextureFetchState.PLACEHOLDER;
        return retryCount == MAX_RETRIES;
    }
}
