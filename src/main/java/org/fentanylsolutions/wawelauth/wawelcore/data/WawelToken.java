package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.UUID;

/**
 * An authentication token in the Yggdrasil system.
 * <p>
 * Represents a session between a client and the auth server. Created by the
 * authenticate endpoint, refreshed by the refresh endpoint.
 * <p>
 * Per the Yggdrasil spec:
 * <p>
 * - accessToken is server-generated (typically a random UUID).
 * <p>
 * - clientToken is client-provided and not unique across users.
 * <p>
 * - A token may optionally be bound to a specific {@link WawelProfile}.
 * <p>
 * - A user may hold multiple tokens; the server should cap the count
 * <p>
 * (e.g. 10) and revoke oldest tokens when exceeding the limit.
 * <p>
 * Token states (per spec):
 * - Valid: usable for authentication.
 * - Invalid: permanently unusable (revoked or expired).
 * - Temporarily invalid: currently unusable but may become valid again
 * (e.g. after a profile rename, forcing clients to refresh).
 * <p>
 * The accessToken serves as the primary key.
 */
public class WawelToken {

    /**
     * Server-generated token. Primary key. Typically a random UUID string.
     */
    private String accessToken;

    /**
     * Client-provided token. Not unique: multiple tokens may share a clientToken.
     */
    private String clientToken;

    /**
     * FK: UUID of the owning {@link WawelUser}.
     */
    private UUID userUuid;

    /**
     * FK: UUID of the bound {@link WawelProfile}. Nullable.
     * Set during authenticate (if user has one profile) or during refresh
     * (when client explicitly selects a profile).
     * Once bound, cannot be changed: only a new token can bind a different profile.
     */
    private UUID profileUuid;

    /**
     * Epoch millis when this token was issued (created or last refreshed).
     */
    private long issuedAt;

    /**
     * Epoch millis when this token was last used (validate, join, etc).
     */
    private long lastUsedAt;

    /**
     * Token version, incremented on each refresh. When a token is refreshed,
     * the old accessToken is invalidated and a new one is issued with version + 1.
     * This allows detecting stale tokens.
     */
    private int version = 1;

    /**
     * Current state of this token. See {@link TokenState} for semantics.
     * Defaults to VALID on creation.
     */
    private TokenState state = TokenState.VALID;

    public WawelToken() {}

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public UUID getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(UUID userUuid) {
        this.userUuid = userUuid;
    }

    public UUID getProfileUuid() {
        return profileUuid;
    }

    public void setProfileUuid(UUID profileUuid) {
        this.profileUuid = profileUuid;
    }

    public boolean hasProfile() {
        return profileUuid != null;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public TokenState getState() {
        return state;
    }

    public void setState(TokenState state) {
        this.state = state;
    }

    /**
     * Whether this token can be used for authentication (only VALID tokens).
     */
    public boolean isUsable() {
        return state == TokenState.VALID;
    }

    /**
     * Whether this token can be refreshed (VALID or TEMPORARILY_INVALID).
     */
    public boolean isRefreshable() {
        return state != TokenState.INVALID;
    }
}
