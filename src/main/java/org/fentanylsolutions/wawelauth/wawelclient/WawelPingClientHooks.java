package org.fentanylsolutions.wawelauth.wawelclient;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.fentlib.services.S00PacketServerInfoModifyService;
import org.fentanylsolutions.wawelauth.WawelAuth;

import com.google.gson.JsonElement;

/**
 * Client-side ping deserialization. Capabilities are runtime-only; changes to
 * remembered local-auth identity are persisted on the client thread.
 */
public final class WawelPingClientHooks {

    private static volatile boolean registered;

    private WawelPingClientHooks() {}

    public static synchronized void register() {
        if (registered) return;

        S00PacketServerInfoModifyService.registerDeserializeHandler((response, fentlibData, serverData) -> {
            if (!(serverData instanceof IServerDataExt)) return;
            long now = System.currentTimeMillis();
            JsonElement wawelElement = fentlibData == null ? null : fentlibData.get(WawelAuth.MODID);
            ServerCapabilities capabilities = wawelElement != null && wawelElement.isJsonObject()
                ? ServerCapabilities.fromPayload(wawelElement.getAsJsonObject(), now)
                : ServerCapabilities.unadvertised(now);
            String pingAddress = serverData.serverIP;
            // Status packets have priority in 1.7.10: this callback runs on Netty, not the UI thread.
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (!java.util.Objects.equals(pingAddress, serverData.serverIP)) return;
                    ((IServerDataExt) serverData).setWawelCapabilities(capabilities);
                    ServerBindingPersistence.persistLocalAuthMetadata(serverData, capabilities);
                });

            // Provider resolution happens at call sites using the bound account's provider
        });

        registered = true;
        WawelAuth.debug("Registered WawelAuth client ping capability handler");
    }
}
