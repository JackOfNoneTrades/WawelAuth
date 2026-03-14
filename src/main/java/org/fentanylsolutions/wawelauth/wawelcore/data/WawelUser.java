package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A user account in the Yggdrasil system.
 * <p>
 * Maps to the Yggdrasil "user" concept. A user authenticates with a username
 * (player name) and password.
 * A user owns one or more {@link WawelProfile}s (game characters).
 * <p>
 * Serialized user form (Yggdrasil spec):
 *
 * <pre>
 * {
 *   "id": "unsigned user uuid",
 *   "properties": [{"name": "preferredLanguage", "value": "en"}]
 * }
 * </pre>
 * <p>
 * The user UUID is distinct from profile UUIDs. It identifies the account,
 * not any specific game character.
 */
public class WawelUser {

    /**
     * Unique user ID. Not the same as any profile UUID.
     */
    private UUID uuid;

    /**
     * Login identifier (player name).
     * Must be unique.
     */
    private String username;

    /**
     * PBKDF2-hashed password, hex-encoded.
     */
    private String passwordHash;

    /**
     * PBKDF2 salt, hex-encoded.
     */
    private String passwordSalt;

    /**
     * Whether this user has admin privileges (manage users, invites, etc).
     */
    private boolean admin;

    /**
     * Whether this account is locked (cannot authenticate).
     */
    private boolean locked;

    /**
     * User's preferred language, e.g. "en", "zh_CN". Yggdrasil user property.
     */
    private String preferredLanguage;

    /**
     * Epoch millis when this user was created.
     */
    private long createdAt;

    /**
     * Transient: the user's profiles. Not persisted inline: loaded via DAO.
     */
    private transient List<WawelProfile> profiles;

    public WawelUser() {
        this.profiles = new ArrayList<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<WawelProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<WawelProfile> profiles) {
        this.profiles = profiles;
    }

}
