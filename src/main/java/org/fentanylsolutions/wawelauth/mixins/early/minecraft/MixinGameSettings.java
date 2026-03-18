package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.settings.GameSettings;

import org.fentanylsolutions.wawelauth.api.SkinLayersHelper;
import org.fentanylsolutions.wawelauth.packet.PacketHandler;
import org.fentanylsolutions.wawelauth.packet.UpdateSkinLayersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameSettings.class, priority = 999)
public class MixinGameSettings {

    @Inject(
        method = "sendSettingsToServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/NetHandlerPlayClient;addToSendQueue(Lnet/minecraft/network/Packet;)V",
            shift = At.Shift.AFTER))
    private void sendSettingsToServer(CallbackInfo ci) {
        byte mask = 0;
        for (SkinLayersHelper.EnumPlayerModelParts part : SkinLayersHelper.EnumPlayerModelParts.values()) {
            if (part.getPartHidden()) {
                mask |= part.getPartMask();
            }
        }
        PacketHandler.sendToServer(new UpdateSkinLayersPacket(mask));
    }

}
