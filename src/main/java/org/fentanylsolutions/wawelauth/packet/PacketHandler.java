package org.fentanylsolutions.wawelauth.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import org.fentanylsolutions.wawelauth.WawelAuth;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class PacketHandler {

    private static final String CHANNEL_NAME = WawelAuth.MODID.toUpperCase();
    private static SimpleNetworkWrapper network;
    private static int nextId = 0;
    private static boolean initialized = false;

    private PacketHandler() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        register(ClipboardCopyPacket.Handler.class, ClipboardCopyPacket.class, Side.CLIENT);
        register(SkinInvalidatePacket.Handler.class, SkinInvalidatePacket.class, Side.CLIENT);
        register(UpdateSkinLayersPacket.Handler.class, UpdateSkinLayersPacket.class, Side.SERVER);
        initialized = true;
    }

    private static <REQ extends IMessage, REPLY extends IMessage> void register(
        Class<? extends IMessageHandler<REQ, REPLY>> handler, Class<REQ> message, Side side) {
        network.registerMessage(handler, message, nextId++, side);
    }

    public static void sendToPlayer(IMessage message, EntityPlayerMP player) {
        if (!initialized || network == null || message == null || player == null) {
            return;
        }
        network.sendTo(message, player);
    }

    public static void sendToAll(IMessage message) {
        if (!initialized || network == null || message == null) {
            return;
        }
        network.sendToAll(message);
    }

    public static void sendToServer(IMessage message) {
        if (!initialized || network == null || message == null) {
            return;
        }
        network.sendToServer(message);
    }
}
