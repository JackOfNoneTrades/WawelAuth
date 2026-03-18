package org.fentanylsolutions.wawelauth.client.render.skinlayers;

import net.minecraft.entity.player.EntityPlayer;

public class SkinLayersHelper {

    public static final int CAPE = 0;
    public static final int JACKET = 1;
    public static final int LEFT_SLEEVE = 2;
    public static final int RIGHT_SLEEVE = 3;
    public static final int LEFT_PANTS = 4;
    public static final int RIGHT_PANTS = 5;
    public static final int HAT = 6;

    public static boolean isSkinLayerVisible(EntityPlayer player, int layerIndex) {
        return (player.getDataWatcher()
            .getWatchableObjectByte(16) & (1 << layerIndex)) != 0;
    }

    public static void setSkinLayerVisible(EntityPlayer player, int layerIndex, boolean visible) {
        byte mask = player.getDataWatcher()
            .getWatchableObjectByte(16);
        if (visible) {
            player.getDataWatcher()
                .updateObject(16, (byte) (mask | (1 << layerIndex)));
        } else {
            player.getDataWatcher()
                .updateObject(16, (byte) (mask & ~(1 << layerIndex)));
        }
    }

}
