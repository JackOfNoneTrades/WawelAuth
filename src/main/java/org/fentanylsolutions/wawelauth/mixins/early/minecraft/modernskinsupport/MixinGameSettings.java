package org.fentanylsolutions.wawelauth.mixins.early.minecraft.modernskinsupport;

import net.minecraft.client.settings.GameSettings;

import org.fentanylsolutions.wawelauth.api.modernskinsupport.SkinLayersHelper.SkinLayer;
import org.fentanylsolutions.wawelauth.packet.PacketHandler;
import org.fentanylsolutions.wawelauth.packet.UpdateSkinLayersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameSettings.class, priority = 999)
public class MixinGameSettings {

    /**
     * Packing and sending client flags to server
     */
    @Inject(
        method = "sendSettingsToServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/NetHandlerPlayClient;addToSendQueue(Lnet/minecraft/network/Packet;)V",
            shift = At.Shift.AFTER))
    private void sendSettingsToServer(CallbackInfo ci) {
        int mask = 0;
        for (SkinLayer layer : SkinLayer.VALUES) {
            int shift = layer.ordinal() * 2;
            int bitValue = switch (layer.stateGetter()
                .get()) {
                case DISABLED -> 0;
                case FLAT -> 1;
                case VOLUMETRIC -> 3;
            };
            mask |= (bitValue << shift);
        }
        PacketHandler.sendToServer(new UpdateSkinLayersPacket((short) mask));
    }

}
