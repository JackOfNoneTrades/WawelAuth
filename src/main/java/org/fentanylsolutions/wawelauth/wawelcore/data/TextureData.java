package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The decoded value of a profile's "textures" property.
 * <p>
 * In the Yggdrasil API, this structure is JSON-encoded, then base64-encoded,
 * and placed in a {@link ProfileProperty} with name "textures".
 * <p>
 * Spec format:
 *
 * <pre>
 * {
 *   "timestamp": 1234567890000,
 *   "profileId": "unsigneduuid",
 *   "profileName": "Steve",
 *   "textures": {
 *     "SKIN": {"url": "https://...", "metadata": {"model": "slim"}},
 *     "CAPE": {"url": "https://..."}
 *   }
 * }
 * </pre>
 * <p>
 * Note: profileId is serialized as an unsigned UUID (no dashes) in the JSON.
 */
public class TextureData {

    private long timestamp;
    private UUID profileId;
    private String profileName;
    private Map<TextureType, TextureRef> textures;

    public TextureData() {
        this.textures = new EnumMap<>(TextureType.class);
    }

    public TextureData(long timestamp, UUID profileId, String profileName) {
        this.timestamp = timestamp;
        this.profileId = profileId;
        this.profileName = profileName;
        this.textures = new EnumMap<>(TextureType.class);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public Map<TextureType, TextureRef> getTextures() {
        return textures;
    }

    public void setTextures(Map<TextureType, TextureRef> textures) {
        this.textures = textures;
    }

    public void putTexture(TextureType type, TextureRef ref) {
        textures.put(type, ref);
    }

    public TextureRef getTexture(TextureType type) {
        return textures.get(type);
    }

    /**
     * A reference to a single texture within the textures map.
     * Contains the URL where the texture can be fetched, and optional metadata.
     * <p>
     * The texture hash is derived from the URL filename (substring after last '/'),
     * which Minecraft uses for client-side caching.
     */
    public static class TextureRef {

        private String url;
        private Map<String, String> metadata;

        public TextureRef() {}

        public TextureRef(String url) {
            this.url = url;
        }

        public TextureRef(String url, SkinModel model) {
            this.url = url;
            if (model == SkinModel.SLIM) {
                this.metadata = new HashMap<>();
                this.metadata.put("model", model.getYggdrasilName());
            }
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * Metadata map. For SKIN textures, may contain {"model": "slim"}. Null or absent means classic.
         */
        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        /**
         * Convenience: extract the SkinModel from metadata, defaulting to CLASSIC.
         */
        public SkinModel getSkinModel() {
            if (metadata == null) return SkinModel.CLASSIC;
            return SkinModel.fromYggdrasil(metadata.get("model"));
        }
    }
}
