package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.UUID;

/**
 * Metadata for a texture file stored on disk.
 * <p>
 * Textures are stored using content-addressed storage: the filename is the
 * content hash of the PNG file. This deduplicates identical textures across
 * profiles automatically.
 * <p>
 * The hash is also placed in the texture URL path, which Minecraft uses for
 * client-side cache keying (per the Yggdrasil spec, the hash is the substring
 * after the last '/' in the texture URL).
 * <p>
 * Storage layout: {stateDir}/textures/{hash}.png
 * <p>
 * Security: uploaded textures must be validated and re-encoded before storage
 * to prevent PNG bombs and metadata-based exploits (see spec).
 */
public class StoredTexture {

    /**
     * Content hash of the PNG file. Primary key and filename.
     */
    private String hash;

    /**
     * What kind of texture this is.
     */
    private TextureType textureType;

    /**
     * UUID of the profile that originally uploaded this texture.
     */
    private UUID uploadedBy;

    /**
     * Epoch millis when this texture was first stored.
     */
    private long uploadedAt;

    /**
     * File size in bytes of the stored PNG.
     */
    private long contentLength;

    /**
     * Image width in pixels.
     */
    private int width;

    /**
     * Image height in pixels.
     */
    private int height;

    public StoredTexture() {}

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public TextureType getTextureType() {
        return textureType;
    }

    public void setTextureType(TextureType textureType) {
        this.textureType = textureType;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
