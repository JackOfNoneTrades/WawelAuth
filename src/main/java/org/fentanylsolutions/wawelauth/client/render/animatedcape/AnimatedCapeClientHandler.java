package org.fentanylsolutions.wawelauth.client.render.animatedcape;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side event handler for animated cape textures.
 * <p>
 * Ticks all active animated capes each client tick, and clears
 * the tracker on world unload.
 */
@SideOnly(Side.CLIENT)
public final class AnimatedCapeClientHandler {

    private static final AnimatedCapeClientHandler INSTANCE = new AnimatedCapeClientHandler();
    private static volatile boolean registered;

    private AnimatedCapeClientHandler() {}

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
        AnimatedCapeTracker.tickAll();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            AnimatedCapeTracker.clearAll();
        }
    }
}
