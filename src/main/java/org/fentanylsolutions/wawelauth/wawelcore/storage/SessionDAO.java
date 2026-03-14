package org.fentanylsolutions.wawelauth.wawelcore.storage;

import org.fentanylsolutions.wawelauth.wawelcore.data.PendingSession;

/**
 * Data access interface for {@link PendingSession} entries.
 * <p>
 * Manages the short-lived join/hasJoined handshake state.
 * Implementations may be in-memory (recommended, since sessions are ephemeral
 * and expire in seconds) or persistent.
 * <p>
 * Used by:
 * <p>
 * - POST /sessionserver/session/minecraft/join (create)
 * <p>
 * - GET /sessionserver/session/minecraft/hasJoined (consume)
 * <p>
 * Sessions are one-time use: consumed by hasJoined and then deleted.
 * <p>
 * Sessions expire after a configurable timeout (typically 15-30 seconds).
 * <p>
 * Sessions are keyed by serverId alone. Each connection attempt produces a unique
 * serverId (SHA-1 hash of server ID string + shared secret + server public key),
 * so there is at most one pending session per serverId at any time.
 */
public interface SessionDAO {

    /**
     * Store a pending session, keyed by serverId.
     * If a session already exists for the same serverId, it is replaced.
     */
    void create(PendingSession session);

    /**
     * Atomically look up, verify, and consume a pending session.
     * Only removes the session if all criteria match. Returns null without
     * consuming if any check fails, leaving the session intact for the
     * legitimate hasJoined call.
     *
     * @param serverId    the server hash from the hasJoined query
     * @param profileName the username from the hasJoined query (must match, case-insensitive)
     * @param clientIp    the ip from the hasJoined query; null to skip IP verification
     * @param timeoutMs   maximum age in milliseconds before a session is considered expired
     * @return the consumed session, or null if not found / expired / mismatch
     */
    PendingSession consume(String serverId, String profileName, String clientIp, long timeoutMs);

    /**
     * Remove all sessions older than the given timeout. Periodic cleanup.
     */
    void purgeExpired(long timeoutMs);
}
