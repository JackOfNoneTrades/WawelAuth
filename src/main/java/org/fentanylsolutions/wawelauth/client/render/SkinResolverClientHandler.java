package org.fentanylsolutions.wawelauth.client.render;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.fentanylsolutions.wawelauth.client.render.skinlayers3d.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Drives WawelTextureResolver lifecycle: tick sweep, invalidate on join, clear on disconnect.
 * <p>
 * Disables skin overlay rendering when armor is equipped.
 */
@SideOnly(Side.CLIENT)
public final class SkinResolverClientHandler {

    private static final SkinResolverClientHandler INSTANCE = new SkinResolverClientHandler();
    private static volatile boolean registered;

    private final Set<UUID> invalidatedPlayers = ConcurrentHashMap.newKeySet();

    private SkinResolverClientHandler() {}

    public static synchronized void register() {
        if (registered) return;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WawelClient client = WawelClient.instance();
        if (client != null) {
            client.getTextureResolver()
                .tick();
        }
    }

    @SubscribeEvent
    public void onOtherPlayerJoin(EntityJoinWorldEvent event) {
        if (!(event.entity instanceof EntityOtherPlayerMP playerMP)) {
            return;
        }
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        UUID playerID = playerMP.getUniqueID();
        if (playerID == null) return;

        // EntityJoinWorldEvent fires on every tracking-range re-entry, not just
        // logins; only the first sighting per world should force a refetch.
        if (!invalidatedPlayers.add(playerID)) return;

        client.getTextureResolver()
            .invalidate(playerID);
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                SkinLayers3DSetup.updateSkullCache(playerID, null);
                SkinLayers3DSetup.updateState(playerID, null);
            });
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            invalidatedPlayers.clear();
        }
    }

    @SubscribeEvent
    public void onPlayerLeaveFMLEvent(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        WawelClient client = WawelClient.instance();
        if (client == null) return;

        invalidatedPlayers.clear();
        client.getTextureResolver()
            .invalidateAll();
        client.getConnectionProviderCache()
            .clear();
        LocalTextureLoader.clearImageCache();
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                SkinLayers3DSetup.clearSkullCache();
                SkinLayers3DSetup.clearState();
            });
    }

}
