package org.fentanylsolutions.wawelauth.wawelserver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.fentanylsolutions.fentlib.util.StringUtil;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Small fixed-window limiter for public auth/admin endpoints.
 * <p>
 * The map is bounded and entries expire after inactivity, so high-cardinality
 * usernames/IPs cannot grow memory without limit.
 */
public class RequestRateLimiter {

    private static final long DEFAULT_MAX_KEYS = 100_000L;
    private static final long EXPIRE_AFTER_ACCESS_MINUTES = 30L;

    private final Cache<String, Bucket> buckets;

    public RequestRateLimiter() {
        this(DEFAULT_MAX_KEYS);
    }

    RequestRateLimiter(long maxKeys) {
        buckets = CacheBuilder.newBuilder()
            .maximumSize(maxKeys)
            .expireAfterAccess(EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
            .build();
    }

    public void check(boolean enabled, String key, int maxAttempts, int windowSeconds, String message) {
        if (!enabled) {
            return;
        }
        long windowMs = TimeUnit.SECONDS.toMillis(windowSeconds);
        long nowMs = now();
        long retryAfterMs = checkLimit(key, maxAttempts, windowMs, nowMs);
        if (retryAfterMs > 0L) {
            throwRateLimited(message, retryAfterMs);
        }
        record(key, windowMs, nowMs);
    }

    public void checkFailures(boolean enabled, String key, int maxAttempts, int windowSeconds, String message) {
        if (!enabled) {
            return;
        }
        long retryAfterMs = checkLimit(key, maxAttempts, TimeUnit.SECONDS.toMillis(windowSeconds), now());
        if (retryAfterMs > 0L) {
            throwRateLimited(message, retryAfterMs);
        }
    }

    public void recordFailure(boolean enabled, String key, int windowSeconds) {
        if (!enabled) {
            return;
        }
        record(key, TimeUnit.SECONDS.toMillis(windowSeconds), now());
    }

    public void reset(String key) {
        buckets.invalidate(key);
    }

    long tryAcquire(String key, int maxAttempts, long windowMs, long nowMs) {
        if (maxAttempts < 1 || windowMs < 1L) {
            return 0L;
        }
        long retryAfterMs = checkLimit(key, maxAttempts, windowMs, nowMs);
        if (retryAfterMs > 0L) {
            return retryAfterMs;
        }
        record(key, windowMs, nowMs);
        return 0L;
    }

    long checkLimit(String key, int maxAttempts, long windowMs, long nowMs) {
        if (maxAttempts < 1 || windowMs < 1L) {
            return 0L;
        }
        Bucket bucket = buckets.getIfPresent(key);
        if (bucket == null) {
            return 0L;
        }

        synchronized (bucket) {
            long elapsed = nowMs - bucket.windowStartedAtMs;
            if (elapsed >= windowMs || elapsed < 0L) {
                return 0L;
            }

            if (bucket.count >= maxAttempts) {
                return Math.max(1L, windowMs - (nowMs - bucket.windowStartedAtMs));
            }

            return 0L;
        }
    }

    private void record(String key, long windowMs, long nowMs) {
        Bucket bucket = buckets.getIfPresent(key);
        if (bucket == null) {
            Bucket created = new Bucket(nowMs);
            Bucket existing = buckets.asMap()
                .putIfAbsent(key, created);
            bucket = existing == null ? created : existing;
        }

        synchronized (bucket) {
            long elapsed = nowMs - bucket.windowStartedAtMs;
            if (elapsed >= windowMs || elapsed < 0L) {
                bucket.windowStartedAtMs = nowMs;
                bucket.count = 0;
            }
            bucket.count++;
        }
    }

    public static String keyPart(String value) {
        String normalized = StringUtil.trimToNull(value);
        if (normalized == null) {
            return "unknown";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() <= 96 && normalized.indexOf('|') < 0) {
            return normalized;
        }
        return "sha256:" + sha256Hex(normalized);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static void throwRateLimited(String message, long retryAfterMs) {
        long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999L) / 1000L);
        throw NetException.tooManyRequests(message + " Try again in " + retryAfterSeconds + "s.");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                int v = b & 0xff;
                if (v < 16) {
                    out.append('0');
                }
                out.append(Integer.toHexString(v));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static class Bucket {

        long windowStartedAtMs;
        int count;

        Bucket(long windowStartedAtMs) {
            this.windowStartedAtMs = windowStartedAtMs;
        }
    }
}
