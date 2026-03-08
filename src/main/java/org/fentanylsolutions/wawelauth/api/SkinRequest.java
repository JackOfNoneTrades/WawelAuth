package org.fentanylsolutions.wawelauth.api;

/**
 * Caller-controlled flags for {@link WawelSkinResolver#getSkin} requests.
 *
 * {@link #requireSigned}: demand cryptographic signature on the texture property.
 * {@link #allowVanillaFallback}: if no WawelAuth provider is found, try Mojang
 * session service as a last resort.
 */
public final class SkinRequest {

    /** Unsigned textures accepted, Mojang fallback allowed. */
    public static final SkinRequest DEFAULT = new SkinRequest(false, true);

    /** Signed textures required, Mojang fallback allowed. */
    public static final SkinRequest SIGNED = new SkinRequest(true, true);

    /** Signed textures required, no Mojang fallback. */
    public static final SkinRequest STRICT = new SkinRequest(true, false);

    /** Unsigned textures accepted, no Mojang fallback. */
    public static final SkinRequest NO_FALLBACK = new SkinRequest(false, false);

    private final boolean requireSigned;
    private final boolean allowVanillaFallback;

    public SkinRequest(boolean requireSigned, boolean allowVanillaFallback) {
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
