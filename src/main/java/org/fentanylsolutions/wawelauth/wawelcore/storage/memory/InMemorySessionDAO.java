package org.fentanylsolutions.wawelauth.wawelcore.storage.memory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.fentanylsolutions.wawelauth.wawelcore.data.PendingSession;
import org.fentanylsolutions.wawelauth.wawelcore.storage.SessionDAO;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * In-memory implementation of {@link SessionDAO} backed by Guava Cache.
 * Pending sessions are ephemeral (15-30 second lifetime) and don't need
 * persistence across server restarts.
 * <p>
 * Guava Cache handles automatic expiry via expireAfterWrite, so purgeExpired
 * is a no-op: expired entries are evicted lazily on access or by cache maintenance.
 */
public class InMemorySessionDAO implements SessionDAO {

    private final Cache<String, PendingSession> sessions;

    /**
     * @param timeoutMs session expiry time in milliseconds (e.g. 30000 for 30 seconds)
     * @param maxSize   upper bound on concurrent pending sessions
     */
    public InMemorySessionDAO(long timeoutMs, long maxSize) {
        sessions = CacheBuilder.newBuilder()
            .expireAfterWrite(timeoutMs, TimeUnit.MILLISECONDS)
            .maximumSize(maxSize)
            .concurrencyLevel(4)
            .build();
    }

    @Override
    public void create(PendingSession session) {
        sessions.put(session.getServerId(), session);
    }

    @Override
    public PendingSession consume(String serverId, String profileName, String clientIp, long timeoutMs) {
        // timeoutMs param is ignored here: expiry is handled by the cache's expireAfterWrite.
        ConcurrentMap<String, PendingSession> map = sessions.asMap();
        PendingSession session = map.get(serverId);
        if (session == null) return null;

        // Verify profile name (case-insensitive)
        if (!session.getProfileName()
            .equalsIgnoreCase(profileName)) {
            return null;
        }

        // Verify IP if requested
        if (clientIp != null && session.getClientIp() != null && !clientIp.equals(session.getClientIp())) {
            return null;
        }

        // All checks passed: atomically remove and return.
        if (map.remove(serverId, session)) {
            return session;
        }
        return null;
    }

    @Override
    public void purgeExpired(long timeoutMs) {
        // Guava Cache handles expiry automatically. Trigger maintenance cleanup.
        sessions.cleanUp();
    }
}
