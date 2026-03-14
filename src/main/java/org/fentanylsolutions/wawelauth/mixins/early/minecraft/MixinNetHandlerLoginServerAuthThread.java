package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.server.network.NetHandlerLoginServer;

import org.fentanylsolutions.wawelauth.wawelserver.LocalSessionVerifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;

/**
 * Redirect dedicated-server username verification in the login auth thread.
 * <p>
 * Targets NetHandlerLoginServer$1.run(), where vanilla calls:
 * MinecraftSessionService.hasJoinedServer(...)
 */
@Mixin(targets = "net.minecraft.server.network.NetHandlerLoginServer$1")
public class MixinNetHandlerLoginServerAuthThread {

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;hasJoinedServer(Lcom/mojang/authlib/GameProfile;Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;"),
        remap = false)
    private GameProfile wawelauth$redirectHasJoined(MinecraftSessionService service, GameProfile profile,
        String serverId) throws AuthenticationUnavailableException {
        return LocalSessionVerifier.hasJoinedServer(profile, serverId, service);
    }

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/NetHandlerLoginServer;func_147322_a(Ljava/lang/String;)V"))
    // NetHandlerLoginServer.disconnect
    private void wawelauth$customDisconnectReason(NetHandlerLoginServer handler, String reason) {
        handler.func_147322_a(LocalSessionVerifier.consumeDisconnectReason(reason)); // NetHandlerLoginServer.disconnect
    }

    @Redirect(
        method = "run",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"),
        remap = false)
    private String wawelauth$safeProfileName(GameProfile profile) {
        if (profile == null) {
            return "<unknown>";
        }
        String name = profile.getName();
        return name == null ? "<unknown>" : name;
    }
}
