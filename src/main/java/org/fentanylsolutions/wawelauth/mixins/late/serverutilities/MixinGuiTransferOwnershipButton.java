package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import net.minecraft.nbt.NBTTagCompound;

import org.fentanylsolutions.wawelauth.wawelclient.compat.ServerUtilitiesTeamGuiPlayerKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "serverutils.client.gui.teams.GuiTransferOwnership$ButtonPlayer", remap = false)
public abstract class MixinGuiTransferOwnershipButton {

    // Regex selector survives synthetic lambda renumbering across SU updates;
    // require = 1 turns any remaining mismatch into a loud startup failure
    // instead of silently sending the plain player name.
    @Redirect(
        method = "/^lambda\\$onClicked\\$\\d+$/",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/NBTTagCompound;setString(Ljava/lang/String;Ljava/lang/String;)V"),
        require = 1,
        remap = false)
    private void wawelauth$sendPlayerUuid(NBTTagCompound data, String key, String value) {
        ServerUtilitiesTeamGuiPlayerKeys.setPlayerKey(data, key, value, this);
    }
}
