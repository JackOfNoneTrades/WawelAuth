package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelCapabilitySyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonObject;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClientJoinSync {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void wawelauth$handleJoinCapabilities(S3FPacketCustomPayload packetIn, CallbackInfo ci) {
        if (!WawelCapabilitySyncPayload.CHANNEL.equals(packetIn.func_149169_c())) {
            return;
        }

        JsonObject payload = WawelCapabilitySyncPayload.decodePayload(packetIn.func_149168_d());
        if (payload == null) {
            WawelAuth.LOG.warn("[JoinSync] Received invalid {} payload", WawelCapabilitySyncPayload.CHANNEL);
            ci.cancel();
            return;
        }

        ServerCapabilities capabilities = ServerCapabilities.fromPayload(payload, System.currentTimeMillis());
        ServerData serverData = Minecraft.getMinecraft()
            .func_147104_D();
        String serverLabel = serverData != null ? serverData.serverIP : "(unknown)";

        if (serverData instanceof IServerDataExt ext) {
            ext.setWawelCapabilities(capabilities);
        }

        WawelClient client = WawelClient.instance();
        if (client != null) {
            SessionBridge bridge = client.getSessionBridge();
            bridge.applyServerCapabilities(capabilities);
        }

        WawelAuth.LOG.info(
            "[JoinSync] Received {} from {} (advertised={}, localAuth={}, authUrls={}, fallbackProviders={}, skinDomains={})",
            WawelCapabilitySyncPayload.CHANNEL,
            serverLabel,
            capabilities.isWawelAuthAdvertised(),
            capabilities.isLocalAuthSupported(),
            capabilities.getAcceptedAuthServerUrls()
                .size(),
            capabilities.getAcceptedProviders()
                .size(),
            capabilities.getLocalAuthSkinDomains()
                .size());
        WawelAuth.debug("[JoinSync] Raw payload: " + capabilities.getRawPayloadJson());
        ci.cancel();
    }
}
