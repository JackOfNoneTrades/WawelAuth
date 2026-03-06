package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EntityPlayerMP.class, priority = 999)
public class MixinEntityPlayerMPIpv6 {

    @Shadow
    public NetHandlerPlayServer playerNetServerHandler;

    /**
     * @author WawelAuth
     * @reason IPv6-safe player IP extraction
     */
    @Overwrite
    public String getPlayerIP() {
        return NetworkAddressUtil.extractIp(this.playerNetServerHandler.netManager.getSocketAddress());
    }
}
