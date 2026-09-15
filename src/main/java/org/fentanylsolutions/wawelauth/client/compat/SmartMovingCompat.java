package org.fentanylsolutions.wawelauth.client.compat;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Static field access in render loop instead of hashmap lookup, that's it.
 */
@SideOnly(Side.CLIENT)
public class SmartMovingCompat {

    private static boolean IS_LOADED;

    public static void init() {
        IS_LOADED = Loader.isModLoaded("SmartMoving");
    }

    public static boolean isLoaded() {
        return IS_LOADED;
    }

}
