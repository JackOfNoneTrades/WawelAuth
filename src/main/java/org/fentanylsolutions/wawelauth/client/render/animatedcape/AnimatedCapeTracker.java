package org.fentanylsolutions.wawelauth.client.render.animatedcape;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.fentanylsolutions.wawelauth.WawelAuth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side singleton that maps player UUIDs to their animated cape textures.
 * <p>
 * Used by the AbstractClientPlayer mixin to override getLocationCape() for
 * players with animated capes.
 */
@SideOnly(Side.CLIENT)
public final class AnimatedCapeTracker {

    private static final Map<UUID, AnimatedCapeTexture> CAPES = new ConcurrentHashMap<>();

    private AnimatedCapeTracker() {}

    public static void register(UUID uuid, AnimatedCapeTexture texture) {
        CAPES.put(uuid, texture);
        WawelAuth.debug("Registered animated cape for " + uuid);
    }

    public static void remove(UUID uuid) {
        if (CAPES.remove(uuid) != null) {
            WawelAuth.debug("Removed animated cape for " + uuid);
        }
    }

    public static AnimatedCapeTexture get(UUID uuid) {
        return CAPES.get(uuid);
    }

    public static boolean has(UUID uuid) {
        return CAPES.containsKey(uuid);
    }

    /** Ticks all active animated cape textures. Call once per client tick. */
    public static void tickAll() {
        for (AnimatedCapeTexture tex : CAPES.values()) {
            tex.tick();
        }
    }

    /** Clears all tracked capes. Call on world unload. */
    public static void clearAll() {
        if (!CAPES.isEmpty()) {
            WawelAuth.debug("Clearing " + CAPES.size() + " animated cape(s)");
            CAPES.clear();
        }
    }
}
