package org.fentanylsolutions.wawelauth.wawelcore.storage;

import java.util.List;

import org.fentanylsolutions.wawelauth.wawelcore.data.WawelInvite;

/**
 * Data access interface for {@link WawelInvite} entities.
 * <p>
 * Not part of the Yggdrasil spec: WawelAuth extension for controlling registration.
 * <p>
 * Used by:
 * <p>
 * - Registration flow (findByCode, consume)
 * <p>
 * - Admin invite management (create, delete, listAll)
 */
public interface InviteDAO {

    /**
     * Find an invite by its code. Returns null if not found.
     */
    WawelInvite findByCode(String code);

    /**
     * Persist a new invite. Throws if code already exists.
     */
    void create(WawelInvite invite);

    /**
     * Atomically consume one use of an invite.
     * Decrements usesRemaining if > 0, or succeeds without decrement if -1 (unlimited).
     * If the invite reaches 0 remaining uses, it should be deleted immediately.
     * Returns true if the invite was valid and consumed, false if not found or exhausted.
     * <p>
     * Implementations must be atomic (e.g. UPDATE ... WHERE uses_remaining > 0 OR uses_remaining = -1)
     * to prevent two concurrent registrations from both consuming a single-use invite.
     */
    boolean consume(String code);

    /**
     * Delete an invite by code.
     */
    void delete(String code);

    /**
     * Return all invites. For admin listing.
     */
    List<WawelInvite> listAll();

    /**
     * Delete all fully consumed invites (usesRemaining == 0).
     */
    void purgeConsumed();
}
