package org.fentanylsolutions.wawelauth.wawelcore.storage;

import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.wawelauth.wawelcore.data.WawelUser;

/**
 * Data access interface for {@link WawelUser} entities.
 * <p>
 * Used by:
 * <p>
 * - POST /authserver/authenticate (findByUsername)
 * <p>
 * - POST /authserver/signout (findByUsername)
 * <p>
 * - Authenticate/refresh responses with requestUser=true (findByUuid)
 * <p>
 * - Registration (create)
 * <p>
 * - Admin user management (all methods)
 */
public interface UserDAO {

    /**
     * Find a user by their UUID. Returns null if not found.
     */
    WawelUser findByUuid(UUID uuid);

    /**
     * Find a user by their login identifier (email or player name).
     * Used by the authenticate and signout endpoints.
     * Returns null if not found.
     */
    WawelUser findByUsername(String username);

    /**
     * Persist a new user. Throws if UUID or username already exists.
     */
    void create(WawelUser user);

    /**
     * Update an existing user. Throws if user does not exist.
     */
    void update(WawelUser user);

    /**
     * Delete a user by UUID.
     */
    void delete(UUID uuid);

    /**
     * Return all users. For admin listing.
     */
    List<WawelUser> listAll();

    /**
     * Return total user count. For server metadata/stats.
     */
    long count();
}
