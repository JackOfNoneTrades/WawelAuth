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
 * The cache's expireAfterWrite (fixed at construction) bounds memory; the
 * timeoutMs params reflect the live config value and are checked explicitly.
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
        ConcurrentMap<String, PendingSession> map = sessions.asMap();
        PendingSession session = map.get(serverId);
        if (session == null) return null;

        // The cache's expireAfterWrite uses the boot-time timeout; the param reflects
        // the live config value, which the admin UI can change at runtime.
        if (session.isExpired(timeoutMs)) {
            map.remove(serverId, session);
            return null;
        }

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
        sessions.asMap()
            .values()
            .removeIf(s -> s.isExpired(timeoutMs));
        sessions.cleanUp();
    }
}
