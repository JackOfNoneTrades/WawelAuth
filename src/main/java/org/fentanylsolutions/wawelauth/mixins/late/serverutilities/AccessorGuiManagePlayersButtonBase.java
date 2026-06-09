package org.fentanylsolutions.wawelauth.mixins.late.serverutilities;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import serverutils.client.gui.teams.GuiManagePlayersBase;
import serverutils.net.MessageMyTeamPlayerList;

@Mixin(value = GuiManagePlayersBase.ButtonPlayerBase.class, remap = false)
public interface AccessorGuiManagePlayersButtonBase {

    @Accessor("entry")
    MessageMyTeamPlayerList.Entry wawelauth$getEntry();
}
