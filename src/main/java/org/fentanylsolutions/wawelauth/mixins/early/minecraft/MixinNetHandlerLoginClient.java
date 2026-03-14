package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.network.NetHandlerLoginClient;

import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;

@Mixin(NetHandlerLoginClient.class)
public class MixinNetHandlerLoginClient {

    /**
     * Redirect the joinServer() call in handleEncryptionRequest to always
     * route through SessionBridge.
     * <p>
     * All authentication is handled by WawelAuth: there is no vanilla
     * fallback. An active account MUST be set before the connection reaches
     * this point (enforced by MixinGuiConnecting + Step 6 account selection).
     * If no account is active, this throws rather than silently using
     * whatever stale session Minecraft happens to hold.
     */
    @Redirect(
        method = "handleEncryptionRequest",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Lcom/mojang/authlib/GameProfile;Ljava/lang/String;Ljava/lang/String;)V",
            remap = false))
    private void wawelauth$redirectJoinServer(MinecraftSessionService service, GameProfile profile, String token,
        String serverId) throws AuthenticationException {
        WawelClient client = WawelClient.instance();
        if (client == null || !client.getSessionBridge()
            .hasActiveAccount()) {
            String reason = client != null ? client.getSessionBridge()
                .getLastActivationError() : null;
            if (reason == null) {
                reason = "No WawelAuth account active";
            }
            throw new AuthenticationException(reason);
        }
        client.getSessionBridge()
            .joinServer(profile, token, serverId);
    }
}
