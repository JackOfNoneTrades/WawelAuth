package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import net.minecraft.nbt.NBTTagCompound;

import org.fentanylsolutions.wawelauth.wawelclient.compat.ServerUtilitiesTeamGuiPlayerKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
    targets = {
        "serverutils.client.gui.teams.GuiManageMembers$ButtonPlayer",
        "serverutils.client.gui.teams.GuiManageAllies$ButtonPlayer",
        "serverutils.client.gui.teams.GuiManageModerators$ButtonPlayer",
        "serverutils.client.gui.teams.GuiManageEnemies$ButtonPlayer"
    },
    remap = false)
public abstract class MixinGuiManagePlayerButtons {

    @Redirect(
        method = "onClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/NBTTagCompound;setString(Ljava/lang/String;Ljava/lang/String;)V",
            ordinal = 0),
        remap = false)
    private void wawelauth$sendPlayerUuid(NBTTagCompound data, String key, String value) {
        ServerUtilitiesTeamGuiPlayerKeys.setPlayerKey(data, key, value, this);
    }
}
