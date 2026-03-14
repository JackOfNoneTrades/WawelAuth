package org.fentanylsolutions.wawelauth.client.gui;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.fentanylsolutions.wawelauth.WawelAuth;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Schedules GUI transitions for the next client tick.
 * <p>
 * Minecraft.func_152344_a runs immediately when called on the client thread
 * in 1.7.10, so it cannot be used to "defer to next tick" from UI handlers.
 */
@SideOnly(Side.CLIENT)
public final class GuiTransitionScheduler {

    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<Runnable>();
    private static final GuiTransitionScheduler INSTANCE = new GuiTransitionScheduler();
    private static volatile boolean registered;

    private GuiTransitionScheduler() {}

    public static synchronized void register() {
        if (registered) return;
        // TickEvent.ClientTickEvent is fired on the FML bus, not MinecraftForge.EVENT_BUS.
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        registered = true;
    }

    public static void nextTick(Runnable action) {
        if (action == null) return;
        QUEUE.add(action);
    }

    /**
     * Close the current panel, then open a new screen on the next tick.
     * Two-tick delay ensures the old screen is fully torn down before the new one opens.
     */
    public static void transition(com.cleanroommc.modularui.screen.ModularPanel currentPanel, Runnable openAction) {
        nextTick(() -> {
            if (currentPanel != null) {
                currentPanel.closeIfOpen();
            }
            nextTick(openAction);
        });
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Runnable action;
        int processed = 0;
        final int maxPerTick = 64;
        while (processed < maxPerTick && (action = QUEUE.poll()) != null) {
            try {
                action.run();
            } catch (Throwable t) {
                WawelAuth.LOG.error("Deferred GUI transition failed", t);
            }
            processed++;
        }
    }
}
