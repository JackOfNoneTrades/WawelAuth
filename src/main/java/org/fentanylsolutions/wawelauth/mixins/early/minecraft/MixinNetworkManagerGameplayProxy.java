package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.net.InetAddress;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.NetworkManager;

import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerConnectionProxySupport;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mixin(NetworkManager.class)
public class MixinNetworkManagerGameplayProxy {

    @Inject(method = "provideLanClient", at = @At("HEAD"), cancellable = true)
    private static void wawelauth$useGameplayProxy(InetAddress address, int port,
        CallbackInfoReturnable<NetworkManager> cir) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        ServerData serverData = minecraft.func_147104_D();
        if (!(serverData instanceof IServerDataExt)) {
            return;
        }

        ProviderProxySettings settings = ServerConnectionProxySupport
            .normalizeSettings(((IServerDataExt) serverData).getWawelServerProxySettings());
        if (!ServerConnectionProxySupport.shouldUseGameplayProxy(settings)) {
            return;
        }

        ServerAddress serverAddress = ServerAddress.func_78860_a(serverData.serverIP);
        String host = serverAddress != null ? serverAddress.getIP() : serverData.serverIP;
        int targetPort = serverAddress != null ? serverAddress.getPort() : port;
        cir.setReturnValue(ServerConnectionProxySupport.createProxiedLanClient(host, targetPort, settings));
    }
}
