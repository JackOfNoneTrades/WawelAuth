package org.fentanylsolutions.wawelauth.wawelclient;

import org.fentanylsolutions.fentlib.services.S00PacketServerInfoModifyService;
import org.fentanylsolutions.wawelauth.WawelAuth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

/**
 * Registers client-side ping deserialization for WawelAuth capabilities.
 * Capabilities are runtime-only and never persisted to NBT.
 */
public final class WawelPingClientHooks {

    private static volatile boolean registered;

    private WawelPingClientHooks() {}

    public static synchronized void register() {
        if (registered) return;

        S00PacketServerInfoModifyService.registerDeserializeHandler((response, fentlibData, serverData) -> {
            if (!(serverData instanceof IServerDataExt)) return;
            IServerDataExt ext = (IServerDataExt) serverData;

            long now = System.currentTimeMillis();
            JsonElement wawelElement = fentlibData == null ? null : fentlibData.get(WawelAuth.MODID);

            if (wawelElement != null && wawelElement.isJsonObject()) {
                JsonObject payload = wawelElement.getAsJsonObject();
                ext.setWawelCapabilities(ServerCapabilities.fromPayload(payload, now));
                WawelAuth.debug("Ping capabilities updated for " + serverData.serverIP + " from WawelAuth payload");
            } else {
                // No payload from this server -> auth provider set is unknown.
                ext.setWawelCapabilities(ServerCapabilities.unadvertised(now));
                WawelAuth.debug("Ping capabilities updated for " + serverData.serverIP + " as unadvertised/unknown");
            }

            WawelClient client = WawelClient.instance();
            if (client != null && response != null && response.func_151318_b() != null) {
                GameProfile[] profiles = response.func_151318_b()
                    .func_151331_c();
                if (profiles != null && profiles.length > 0) {
                    client.getSessionBridge()
                        .rememberPingProfiles(ext.getWawelCapabilities(), profiles);
                }
            }
        });

        registered = true;
        WawelAuth.debug("Registered WawelAuth client ping capability handler");
    }
}
