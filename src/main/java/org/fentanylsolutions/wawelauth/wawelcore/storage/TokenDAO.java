package org.fentanylsolutions.wawelauth.wawelcore.storage;

import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.WawelToken;

/**
 * Data access interface for {@link WawelToken} entities.
 * <p>
 * Used by:
 * <p>
 * - POST /authserver/authenticate (create)
 * <p>
 * - POST /authserver/refresh (findByAccessToken, create new, delete old)
 * <p>
 * - POST /authserver/validate (findByAccessToken)
 * <p>
 * - POST /authserver/invalidate (findByAccessToken, delete)
 * <p>
 * - POST /authserver/signout (deleteByUser)
 * <p>
 * - POST /sessionserver/session/minecraft/join (findByAccessToken)
 * <p>
 * Tokens use accessToken as their primary key.
 * <p>
 * The server should cap tokens per user and evict oldest when exceeded.
 */
public interface TokenDAO {

    /**
     * Find a token by its access token string. Returns null if not found.
     */
    WawelToken findByAccessToken(String accessToken);

    /**
     * Find a token by access token, optionally verifying the client token.
     * <p>
     * If {@code clientToken} is non-null, both must match: returns null if the
     * stored clientToken differs. If {@code clientToken} is null, matches on
     * accessToken alone (some clients omit clientToken in practice).
     * <p>
     * Used by validate, refresh, invalidate.
     */
    WawelToken findByTokenPair(String accessToken, String clientToken);

    /**
     * Find all tokens belonging to a user. Ordered by issuedAt descending.
     */
    List<WawelToken> findByUser(UUID userUuid);

    /**
     * Count tokens belonging to a user. Used for cap enforcement.
     */
    long countByUser(UUID userUuid);

    /**
     * Persist a new token. Throws if accessToken already exists.
     */
    void create(WawelToken token);

    /**
     * Update an existing token (state changes, lastUsedAt, etc).
     */
    void update(WawelToken token);

    /**
     * Delete a token by its access token string.
     */
    void delete(String accessToken);

    /**
     * Delete all tokens belonging to a user. Used by signout.
     */
    void deleteByUser(UUID userUuid);

    /**
     * Mark all tokens bound to a profile as temporarily invalid.
     * Used when a profile is renamed, forcing clients to refresh.
     */
    void markTemporarilyInvalid(UUID profileUuid);

    /**
     * Evict oldest tokens for a user, keeping at most {@code keepCount}.
     * Deletes tokens with the oldest issuedAt first.
     */
    void evictOldest(UUID userUuid, int keepCount);

    /**
     * Delete all tokens in INVALID state. Periodic cleanup.
     */
    void purgeInvalid();

    /**
     * Return total token count across all users. For server stats.
     */
    long count();
}
