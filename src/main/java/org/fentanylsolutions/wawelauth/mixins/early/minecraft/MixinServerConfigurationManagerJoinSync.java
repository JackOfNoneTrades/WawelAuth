package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.management.ServerConfigurationManager;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.ping.PlayerProviderSyncPayload;
import org.fentanylsolutions.wawelauth.wawelcore.ping.WawelCapabilitySyncPayload;
import org.fentanylsolutions.wawelauth.wawelserver.LocalSessionVerifier;
import org.fentanylsolutions.wawelauth.wawelserver.WawelServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

@Mixin(ServerConfigurationManager.class)
public class MixinServerConfigurationManagerJoinSync {

    @Shadow
    public java.util.List<?> playerEntityList;

    @Inject(
        method = "initializeConnectionToPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;playerLoggedIn(Lnet/minecraft/entity/player/EntityPlayerMP;)V"))
    private void wawelauth$sendJoinCapabilities(NetworkManager netManager, EntityPlayerMP player,
        NetHandlerPlayServer netHandler, CallbackInfo ci) {
        String publicKeyBase64 = null;
        WawelServer server = WawelServer.instance();
        if (server != null && server.getKeyManager() != null) {
            publicKeyBase64 = server.getKeyManager()
                .getPublicKeyBase64();
        }

        // Send server capabilities
        byte[] payload = WawelCapabilitySyncPayload.encodeServerPayload(Config.server(), publicKeyBase64);
        netHandler.sendPacket(new S3FPacketCustomPayload(WawelCapabilitySyncPayload.CHANNEL, payload));

        WawelAuth.LOG.info(
            "[JoinSync] Sent {} to {} (bytes={}, localAuth={}, authUrls={})",
            WawelCapabilitySyncPayload.CHANNEL,
            player.getCommandSenderName(),
            payload.length,
            Config.server() != null && Config.server()
                .isLocalAuthEnabled()
                && Config.server()
                    .getApiRoot() != null
                && publicKeyBase64 != null,
            Config.server() != null ? Config.server()
                .getFallbackServers()
                .size()
                + (Config.server()
                    .isLocalAuthEnabled()
                    && Config.server()
                        .getApiRoot() != null ? 1 : 0)
                : 0);

        // Send all player-provider associations to the joining player
        Map<UUID, String> allProviders = LocalSessionVerifier.getAllPlayerProviderSessionUrls();
        if (!allProviders.isEmpty()) {
            byte[] ppPayload = PlayerProviderSyncPayload.encode(allProviders);
            netHandler.sendPacket(new S3FPacketCustomPayload(PlayerProviderSyncPayload.CHANNEL, ppPayload));
            WawelAuth.debug(
                "[JoinSync] Sent " + PlayerProviderSyncPayload.CHANNEL
                    + " to "
                    + player.getCommandSenderName()
                    + " with "
                    + allProviders.size()
                    + " player associations");
        }

        // Broadcast joining player's provider to everyone else
        UUID joiningUuid = player.getGameProfile() != null ? player.getGameProfile()
            .getId() : null;
        String joiningProvider = joiningUuid != null ? LocalSessionVerifier.getPlayerProviderSessionUrl(joiningUuid)
            : null;
        if (joiningUuid != null && joiningProvider != null) {
            byte[] singlePayload = PlayerProviderSyncPayload.encodeSingle(joiningUuid, joiningProvider);
            for (Object raw : playerEntityList) {
                if (!(raw instanceof EntityPlayerMP other)) continue;
                if (other == player) continue;
                try {
                    other.playerNetServerHandler
                        .sendPacket(new S3FPacketCustomPayload(PlayerProviderSyncPayload.CHANNEL, singlePayload));
                } catch (Exception e) {
                    WawelAuth.debug(
                        "[JoinSync] Failed to send PP to " + other.getCommandSenderName() + ": " + e.getMessage());
                }
            }
        }
    }

    @Inject(method = "playerLoggedOut", at = @At("HEAD"))
    private void wawelauth$cleanupPlayerProvider(EntityPlayerMP player, CallbackInfo ci) {
        GameProfile profile = player.getGameProfile();
        if (profile != null && profile.getId() != null) {
            LocalSessionVerifier.clearPlayer(profile.getId());
        }
    }

}
