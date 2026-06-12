package org.fentanylsolutions.wawelauth.common;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Runs tasks on the server main thread. SimpleImpl handlers run on Netty threads in 1.7.10. */
public final class ServerTaskScheduler {

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static boolean registered = false;

    private ServerTaskScheduler() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTaskScheduler());
        registered = true;
    }

    public static void schedule(Runnable task) {
        TASKS.add(task);
    }

    public static void clear() {
        TASKS.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            task.run();
        }
    }
}
