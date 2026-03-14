package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.UUID;

/**
 * An invite code for account registration.
 * <p>
 * When the server is configured for invite-only registration, users must
 * provide a valid invite code to create an account. Invites are created
 * by admins and may be single-use or multi-use.
 * <p>
 * Not part of the Yggdrasil spec: this is a WawelAuth extension,
 * following patterns from drasl and similar implementations.
 */
public class WawelInvite {

    /**
     * The invite code string. Primary key.
     */
    private String code;

    /**
     * Epoch millis when this invite was created.
     */
    private long createdAt;

    /**
     * UUID of the admin user who created this invite. Nullable (e.g. system-generated).
     */
    private UUID createdBy;

    /**
     * Number of times this invite can still be used.
     * -1 means unlimited uses. 0 means fully consumed.
     */
    private int usesRemaining = 1;

    public WawelInvite() {}

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public int getUsesRemaining() {
        return usesRemaining;
    }

    public void setUsesRemaining(int usesRemaining) {
        this.usesRemaining = usesRemaining;
    }

    /**
     * Whether this invite can still be used.
     */
    public boolean isValid() {
        return usesRemaining != 0;
    }
}
