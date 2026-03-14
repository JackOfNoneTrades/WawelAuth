package org.fentanylsolutions.wawelauth.wawelcore.data;

import java.util.Locale;

/**
 * Texture types as defined by the Yggdrasil specification.
 * Used as keys in the "textures" map of a profile's textures property.
 * <p>
 * The spec defines SKIN and CAPE. ELYTRA is a non-standard extension
 * supported by some custom auth servers.
 */
public enum TextureType {

    SKIN,
    CAPE,
    ELYTRA;

    /**
     * Returns the lowercase name used in texture upload/delete API paths.
     * e.g. PUT /api/user/profile/{uuid}/skin
     */
    public String getApiName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TextureType fromApiName(String name) {
        if (name == null) return null;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "skin":
                return SKIN;
            case "cape":
                return CAPE;
            case "elytra":
                return ELYTRA;
            default:
                return null;
        }
    }
}
