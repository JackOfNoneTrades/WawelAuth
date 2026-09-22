package org.fentanylsolutions.wawelauth.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> Server */
public class UpdateSkinLayersPacket implements IMessage {

    private short mask;

    public UpdateSkinLayersPacket() {}

    public UpdateSkinLayersPacket(short mask) {
        this.mask = mask;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mask = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(this.mask);
    }

    public static class Handler implements IMessageHandler<UpdateSkinLayersPacket, IMessage> {

        @Override
        public IMessage onMessage(UpdateSkinLayersPacket message, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;

            short mask = (short) (message.mask & 16383);
            player.getDataWatcher()
                .updateObject(16, mask);

            return null;
        }
    }
}
