package org.fentanylsolutions.wawelauth.wawelclient;

import org.fentanylsolutions.fentlib.services.S00PacketServerInfoModifyService;
import org.fentanylsolutions.wawelauth.WawelAuth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Client-side ping deserialization for WawelAuth capabilities. Runtime-only, not persisted.
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
                ServerBindingPersistence.persistLocalAuthMetadata(serverData, ext.getWawelCapabilities());
                WawelAuth.debug("Ping capabilities updated for " + serverData.serverIP + " from WawelAuth payload");
            } else {
                // No payload, provider set unknown
                ext.setWawelCapabilities(ServerCapabilities.unadvertised(now));
                ServerBindingPersistence.persistLocalAuthMetadata(serverData, ext.getWawelCapabilities());
                WawelAuth.debug("Ping capabilities updated for " + serverData.serverIP + " as unadvertised/unknown");
            }

            // Provider resolution happens at call sites using the bound account's provider
        });

        registered = true;
        WawelAuth.debug("Registered WawelAuth client ping capability handler");
    }
}
