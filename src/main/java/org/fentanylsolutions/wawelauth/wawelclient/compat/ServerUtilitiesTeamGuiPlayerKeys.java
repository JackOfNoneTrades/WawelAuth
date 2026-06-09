package org.fentanylsolutions.wawelauth.wawelclient.compat;

import net.minecraft.nbt.NBTTagCompound;

import org.fentanylsolutions.wawelauth.mixins.late.serverutilities.AccessorGuiManagePlayersButtonBase;

import serverutils.net.MessageMyTeamPlayerList;

public final class ServerUtilitiesTeamGuiPlayerKeys {

    private ServerUtilitiesTeamGuiPlayerKeys() {}

    public static void setPlayerKey(NBTTagCompound data, String key, String fallbackValue, Object button) {
        MessageMyTeamPlayerList.Entry entry = ((AccessorGuiManagePlayersButtonBase) button).wawelauth$getEntry();
        if (entry != null && entry.uuid != null) {
            data.setString(key, entry.uuid.toString());
        } else {
            data.setString(key, fallbackValue);
        }
    }
}
