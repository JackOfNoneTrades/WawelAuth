package org.fentanylsolutions.wawelauth.mixins.late.betterquesting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.client.gui2.party.GuiPartyInvite;

@Mixin(value = GuiPartyInvite.class, remap = false)
public abstract class MixinGuiPartyInvite {

    @Redirect(
        method = "initPanel",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/api2/client/gui/controls/PanelTextField;setMaxLength(I)Lbetterquesting/api2/client/gui/controls/PanelTextField;"),
        remap = false)
    private PanelTextField<String> wawelauth$allowProviderQualifiedInviteNames(PanelTextField<String> field, int size) {
        return field.setMaxLength(64);
    }
}
