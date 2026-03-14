package org.fentanylsolutions.wawelauth.wawelcore.storage;

import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;

/**
 * Data access interface for {@link WawelProfile} entities.
 * <p>
 * Used by:
 * <p>
 * - GET /sessionserver/session/minecraft/profile/{uuid} (findByUuid)
 * <p>
 * - GET /sessionserver/session/minecraft/hasJoined?username= (findByName)
 * <p>
 * - POST /api/profiles/minecraft (findByNames)
 * <p>
 * - POST /authserver/authenticate: availableProfiles (findByOwner)
 * <p>
 * - PUT/DELETE /api/user/profile/{uuid}/{type}: texture upload (findByUuid)
 * <p>
 * - Registration (create)
 */
public interface ProfileDAO {

    /**
     * Find a profile by its game UUID. Returns null if not found.
     */
    WawelProfile findByUuid(UUID uuid);

    /**
     * Find a profile by player name (case-insensitive lookup).
     * Names are stored preserving their original case for display, but all lookups
     * and uniqueness checks are case-insensitive. Implementations must normalize
     * accordingly (e.g. COLLATE NOCASE in SQLite, or a lowercase index column).
     * <p>
     * Used by hasJoined (username query param) and non-email login.
     * Returns null if not found.
     */
    WawelProfile findByName(String name);

    /**
     * Find all profiles owned by a user. Used for availableProfiles in authenticate.
     */
    List<WawelProfile> findByOwner(UUID userUuid);

    /**
     * Bulk lookup profiles by name (case-insensitive).
     * Used by POST /api/profiles/minecraft.
     * Returns only profiles that exist: missing names are silently omitted.
     */
    List<WawelProfile> findByNames(List<String> names);

    /**
     * Persist a new profile. Throws if UUID or name (case-insensitive) already exists.
     * The name is stored in its original case but must be unique when compared
     * case-insensitively.
     */
    void create(WawelProfile profile);

    /**
     * Update an existing profile. Throws if profile does not exist.
     */
    void update(WawelProfile profile);

    /**
     * Delete a profile by UUID.
     */
    void delete(UUID uuid);

    /**
     * Check if any profile still references the given texture hash
     * (across skin_hash, cape_hash, elytra_hash columns).
     * Used to safely delete orphaned texture files.
     */
    boolean isTextureHashReferenced(String hash);

    /**
     * Return total profile count. For server stats.
     */
    long count();
}
