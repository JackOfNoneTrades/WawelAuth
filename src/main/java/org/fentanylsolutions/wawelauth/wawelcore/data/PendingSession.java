package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.UUID;

/**
 * A pending server-join session, created by the "join" endpoint and consumed
 * by the "hasJoined" endpoint.
 * <p>
 * When a client calls POST /sessionserver/session/minecraft/join, a PendingSession
 * is created. When the server calls GET /sessionserver/session/minecraft/hasJoined,
 * the matching PendingSession is looked up and consumed (one-time use).
 * <p>
 * This is a short-lived entry: pending sessions should expire after a configurable
 * window (typically 15-30 seconds) and be cleaned up periodically.
 * <p>
 * Using a dedicated model instead of a field on WawelProfile allows:
 * <p>
 * - Concurrent joins (different servers)
 * <p>
 * - IP verification
 * <p>
 * - Expiry tracking
 * <p>
 * - One-time consume semantics
 * <p>
 * - Clean separation from persistent profile data
 */
public class PendingSession {

    /**
     * The server hash that the client is attempting to join.
     * Computed by the client from the server's public key and a shared secret.
     */
    private String serverId;

    /**
     * UUID of the profile that is joining.
     */
    private UUID profileUuid;

    /**
     * Name of the profile at join time (used for hasJoined lookup by username).
     */
    private String profileName;

    /**
     * The access token used to authenticate the join.
     */
    private String accessToken;

    /**
     * IP address of the joining client. Null if IP checking is disabled.
     */
    private String clientIp;

    /**
     * Epoch millis when this pending session was created.
     */
    private long createdAt;

    public PendingSession() {}

    public PendingSession(String serverId, UUID profileUuid, String profileName, String accessToken, String clientIp) {
        this.serverId = serverId;
        this.profileUuid = profileUuid;
        this.profileName = profileName;
        this.accessToken = accessToken;
        this.clientIp = clientIp;
        this.createdAt = System.currentTimeMillis();
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public UUID getProfileUuid() {
        return profileUuid;
    }

    public void setProfileUuid(UUID profileUuid) {
        this.profileUuid = profileUuid;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Check if this pending session has expired given a timeout in milliseconds.
     */
    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - createdAt > timeoutMs;
    }
}
