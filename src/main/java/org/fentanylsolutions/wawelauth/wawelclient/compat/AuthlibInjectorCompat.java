package org.fentanylsolutions.wawelauth.wawelclient.compat;

/**
 * Detection utility for authlib-injector presence.
 * Cached at first access since the agent cannot be loaded/unloaded at runtime.
 */
public final class AuthlibInjectorCompat {

    private static final boolean PRESENT;

    static {
        boolean found;
        try {
            Class.forName("moe.yushi.authlibinjector.AuthlibInjector");
            found = true;
        } catch (ClassNotFoundException e) {
            found = false;
        }
        PRESENT = found;
    }

    private AuthlibInjectorCompat() {}

    public static boolean isActive() {
        return PRESENT;
    }
}
