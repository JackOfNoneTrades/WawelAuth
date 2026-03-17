package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.ConnectionProviderCache;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelcore.ping.PlayerProviderSyncPayload;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelCapabilitySyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonObject;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClientJoinSync {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void wawelauth$handleJoinCapabilities(S3FPacketCustomPayload packetIn, CallbackInfo ci) {
        String channel = packetIn.func_149169_c();

        if (WawelCapabilitySyncPayload.CHANNEL.equals(channel)) {
            wawelauth$handleCapabilities(packetIn, ci);
            return;
        }

        if (PlayerProviderSyncPayload.CHANNEL.equals(channel)) {
            wawelauth$handlePlayerProviders(packetIn, ci);
        }
    }

    @Unique
    private void wawelauth$handleCapabilities(S3FPacketCustomPayload packetIn, CallbackInfo ci) {
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
            ServerBindingPersistence.persistLocalAuthMetadata(serverData, capabilities);
        }

        WawelClient client = WawelClient.instance();
        if (client != null) {
            SessionBridge bridge = client.getSessionBridge();
            bridge.applyServerCapabilities(capabilities);
            java.util.List<org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider> trusted = bridge
                .getTrustedProviders();
            client.getConnectionProviderCache()
                .setProviders(trusted);

            // Log each trusted provider with key availability
            for (org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider tp : trusted) {
                boolean hasKey = tp.getPublicKeyBase64() != null && !tp.getPublicKeyBase64()
                    .trim()
                    .isEmpty();
                WawelAuth.LOG.info(
                    "[JoinSync] Trusted provider: name='{}' session='{}' hasKey={}",
                    tp.getName(),
                    tp.getSessionServerUrl(),
                    hasKey);
            }
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

    @Unique
    private void wawelauth$handlePlayerProviders(S3FPacketCustomPayload packetIn, CallbackInfo ci) {
        Map<UUID, String> playerProviders = PlayerProviderSyncPayload.decode(packetIn.func_149168_d());
        if (playerProviders.isEmpty()) {
            ci.cancel();
            return;
        }

        WawelClient client = WawelClient.instance();
        if (client == null) {
            ci.cancel();
            return;
        }

        ConnectionProviderCache cache = client.getConnectionProviderCache();
        int associated = 0;
        for (Map.Entry<UUID, String> entry : playerProviders.entrySet()) {
            ClientProvider provider = cache.findBySessionServerUrl(entry.getValue());
            if (provider != null) {
                cache.associatePlayer(entry.getKey(), provider);
                associated++;
            } else {
                WawelAuth.debug(
                    "[JoinSync] No matching provider for player " + entry.getKey()
                        + " with session URL "
                        + entry.getValue());
            }
        }

        WawelAuth.debug(
            "[JoinSync] Received " + PlayerProviderSyncPayload.CHANNEL
                + " with "
                + playerProviders.size()
                + " entries, associated "
                + associated);
        ci.cancel();
    }
}
