package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.network.NetworkManager;
import net.minecraft.server.network.NetHandlerLoginServer;

import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.authlib.GameProfile;

@Mixin(value = NetHandlerLoginServer.class, priority = 999)
public class MixinNetHandlerLoginServerIpv6 {

    @Shadow
    private GameProfile field_147337_i;

    @Shadow
    @Final
    public NetworkManager field_147333_a;

    /**
     * @author WawelAuth
     * @reason IPv6-safe login address formatting
     */
    @Overwrite
    public String func_147317_d() {
        String ip = NetworkAddressUtil.extractIp(this.field_147333_a.getSocketAddress());
        return this.field_147337_i != null ? this.field_147337_i + " (" + ip + ")" : ip;
    }
}
