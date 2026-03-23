package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.net.SocketAddress;

import net.minecraft.server.management.BanList;

import org.fentanylsolutions.fentlib.util.NetworkAddressUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = BanList.class, priority = 999)
public class MixinBanListIpv6 {

    /**
     * @author WawelAuth
     * @reason IPv6-safe IP extraction from socket addresses
     */
    @Overwrite
    private String func_152707_c(SocketAddress address) {
        return NetworkAddressUtil.extractIp(address);
    }
}
