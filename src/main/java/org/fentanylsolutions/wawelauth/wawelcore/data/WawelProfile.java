package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.Set;
import java.util.UUID;

/**
 * A game profile (character) in the Yggdrasil system.
 * <p>
 * Maps to the Yggdrasil "profile" concept. A profile belongs to exactly one
 * {@link WawelUser} and represents a playable game identity with a unique UUID
 * and unique name.
 * <p>
 * Serialized profile form (Yggdrasil spec):
 *
 * <pre>
 * {
 *   "id": "unsigned profile uuid",
 *   "name": "player name",
 *   "properties": [
 *     {"name": "textures", "value": "base64...", "signature": "base64..."}
 *   ]
 * }
 * </pre>
 * <p>
 * The properties array (including textures) is only present when the endpoint
 * requires it (e.g. hasJoined, profile query). Simple profile references
 * in authenticate/refresh responses only include id and name.
 */
public class WawelProfile {

    /**
     * The profile UUID used in-game. Globally unique.
     */
    private UUID uuid;

    /**
     * Player name. Globally unique but may change. Do not use as persistent identity.
     */
    private String name;

    /**
     * FK: UUID of the owning {@link WawelUser}.
     */
    private UUID ownerUuid;

    /**
     * Offline-mode compatible UUID, derived from the player name.
     * Computed as UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8)).
     * Useful for servers migrating from offline mode to preserve player data.
     */
    private UUID offlineUuid;

    /**
     * Skin arm model. CLASSIC (4px) or SLIM (3px).
     */
    private SkinModel skinModel = SkinModel.CLASSIC;

    /**
     * Content hash of the skin texture PNG file. Null if no skin is set.
     * Used as the filename in content-addressed texture storage.
     * Also placed in the texture URL for client-side cache keying.
     */
    private String skinHash;

    /**
     * Content hash of the cape texture PNG file. Null if no cape is set.
     */
    private String capeHash;

    /**
     * Content hash of the elytra texture PNG file. Null if no elytra is set.
     * Non-standard extension.
     */
    private String elytraHash;

    /**
     * Whether the current cape is an animated GIF.
     */
    private boolean capeAnimated;

    /**
     * Epoch millis when this profile was created.
     */
    private long createdAt;

    /**
     * Which texture types this profile is allowed to upload.
     * Serialized as the authlib-injector "uploadableTextures" profile property
     * (comma-separated, e.g. "skin,cape").
     * Empty set means no uploads allowed. Null means use server default.
     */
    private Set<TextureType> uploadableTextures = null;

    public WawelProfile() {}

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOfflineUuid() {
        return offlineUuid;
    }

    public void setOfflineUuid(UUID offlineUuid) {
        this.offlineUuid = offlineUuid;
    }

    public SkinModel getSkinModel() {
        return skinModel;
    }

    public void setSkinModel(SkinModel skinModel) {
        this.skinModel = skinModel;
    }

    public String getSkinHash() {
        return skinHash;
    }

    public void setSkinHash(String skinHash) {
        this.skinHash = skinHash;
    }

    public String getCapeHash() {
        return capeHash;
    }

    public void setCapeHash(String capeHash) {
        this.capeHash = capeHash;
    }

    public String getElytraHash() {
        return elytraHash;
    }

    public void setElytraHash(String elytraHash) {
        this.elytraHash = elytraHash;
    }

    public boolean isCapeAnimated() {
        return capeAnimated;
    }

    public void setCapeAnimated(boolean capeAnimated) {
        this.capeAnimated = capeAnimated;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Set<TextureType> getUploadableTextures() {
        return uploadableTextures;
    }

    public void setUploadableTextures(Set<TextureType> uploadableTextures) {
        this.uploadableTextures = uploadableTextures;
    }

    /**
     * Whether this profile can upload the given texture type.
     */
    public boolean canUpload(TextureType type) {
        return uploadableTextures != null && uploadableTextures.contains(type);
    }

    /**
     * Build the authlib-injector "uploadableTextures" property value.
     * Returns comma-separated lowercase type names, e.g. "skin,cape".
     * Returns null if no uploads are allowed.
     */
    public String toUploadableTexturesValue() {
        if (uploadableTextures == null || uploadableTextures.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (TextureType type : uploadableTextures) {
            if (type == TextureType.ELYTRA) continue; // non-standard, skip in property
            if (sb.length() > 0) sb.append(',');
            sb.append(type.getApiName());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Returns the texture hash for the given type, or null if not set.
     */
    public String getTextureHash(TextureType type) {
        switch (type) {
            case SKIN:
                return skinHash;
            case CAPE:
                return capeHash;
            case ELYTRA:
                return elytraHash;
            default:
                return null;
        }
    }

    /**
     * Sets the texture hash for the given type. Pass null to clear.
     */
    public void setTextureHash(TextureType type, String hash) {
        switch (type) {
            case SKIN:
                this.skinHash = hash;
                break;
            case CAPE:
                this.capeHash = hash;
                if (hash == null) {
                    this.capeAnimated = false;
                }
                break;
            case ELYTRA:
                this.elytraHash = hash;
                break;
        }
    }

    /**
     * Compute the offline-mode UUID for a player name.
     * Uses the same algorithm as vanilla Minecraft offline mode:
     * UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8))
     */
    public static UUID computeOfflineUuid(String playerName) {
        return UuidUtil.offlinePlayerUuid(playerName);
    }

    /**
     * Recompute and set offlineUuid from the current name.
     */
    public void updateOfflineUuid() {
        if (name != null) {
            this.offlineUuid = computeOfflineUuid(name);
        }
    }
}
