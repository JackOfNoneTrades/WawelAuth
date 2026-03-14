package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.IServerDataExt;
import org.fentanylsolutions.wawelauth.wawelclient.ServerBindingPersistence;
import org.fentanylsolutions.wawelauth.wawelclient.ServerCapabilities;
import org.fentanylsolutions.wawelauth.wawelclient.SessionBridge;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConnecting.class)
public class MixinGuiConnecting {

    /**
     * Swap the Minecraft session to the per-server selected account
     * before the connection thread starts.
     * <p>
     * At this point, Minecraft.func_147104_D() returns the current ServerData
     * (set by the constructor before calling func_146367_a).
     * The session swap is synchronous: Thread.start() at the end of
     * func_146367_a provides happens-before visibility to the new thread.
     */
    @Inject(method = "func_146367_a", at = @At("HEAD")) // GuiConnecting.connect
    private void wawelauth$onConnect(String ip, int port, CallbackInfo ci) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;
        SessionBridge bridge = client.getSessionBridge();

        ServerData serverData = Minecraft.getMinecraft()
            .func_147104_D(); // Minecraft.getCurrentServerData
        if (serverData == null) {
            bridge.clearActiveAccount();
            return;
        }

        IServerDataExt ext = (IServerDataExt) serverData;
        ServerCapabilities capabilities = ext.getWawelCapabilities();
        long accountId = ext.getWawelAccountId();
        if (accountId < 0) {
            // No persisted binding: attempt one-shot in-memory auto-pick
            // from live ping capabilities. Never writes to ServerData/NBT.
            long autoSelected = bridge.findSingleMatchingAccountId(capabilities);
            if (autoSelected >= 0) {
                bridge.activateAccount(autoSelected);
                bridge.applyServerCapabilities(capabilities);
                WawelAuth.debug(
                    "Auto-selected single matching account " + autoSelected
                        + " for server "
                        + serverData.serverIP
                        + " (non-persistent)");
                return;
            }

            // No unique auto-match: restore launcher session.
            bridge.clearActiveAccount();
            WawelAuth.debug("No bound/auto-selected WawelAuth account for server, restored launcher session");
            return;
        }

        // Auto-heal stale bindings (e.g. DB reset or account removed).
        if (ServerBindingPersistence.clearMissingBinding(serverData, client.getAccountManager())) {
            bridge.clearActiveAccount();
            WawelAuth.LOG.warn("Cleared stale account binding {} for server '{}'", accountId, serverData.serverIP);
            return;
        }

        bridge.activateAccount(accountId);
        bridge.applyServerCapabilities(capabilities);
    }
}
