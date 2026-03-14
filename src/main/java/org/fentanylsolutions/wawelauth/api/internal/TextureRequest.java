package org.fentanylsolutions.wawelauth.api.internal;

import org.fentanylsolutions.wawelauth.api.WawelTextureResolver;

/**
 * Caller-controlled flags for {@link WawelTextureResolver#getSkin} / {@link WawelTextureResolver#getCape} requests.
 * <p>
 * {@link #requireSigned}: demand cryptographic signature on the texture property.
 * <p>
 * {@link #allowVanillaFallback}: if no WawelAuth provider is found, try Mojang
 * session service as a last resort.
 */
public final class TextureRequest {

    /**
     * Unsigned textures accepted, Mojang fallback allowed.
     */
    public static final TextureRequest DEFAULT = new TextureRequest(false, true);

    /**
     * Signed textures required, Mojang fallback allowed.
     */
    public static final TextureRequest SIGNED = new TextureRequest(true, true);

    /**
     * Signed textures required, no Mojang fallback.
     */
    public static final TextureRequest STRICT = new TextureRequest(true, false);

    /**
     * Unsigned textures accepted, no Mojang fallback.
     */
    public static final TextureRequest NO_FALLBACK = new TextureRequest(false, false);

    private final boolean requireSigned;
    private final boolean allowVanillaFallback;

    public TextureRequest(boolean requireSigned, boolean allowVanillaFallback) {
        this.requireSigned = requireSigned;
        this.allowVanillaFallback = allowVanillaFallback;
    }

    public boolean requireSigned() {
        return requireSigned;
    }

    public boolean allowVanillaFallback() {
        return allowVanillaFallback;
    }
}
