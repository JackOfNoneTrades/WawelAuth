package org.fentanylsolutions.wawelauth.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> Server */
public class UpdateSkinLayersPacket implements IMessage {

    private byte mask;

    public UpdateSkinLayersPacket() {}

    public UpdateSkinLayersPacket(byte mask) {
        this.mask = mask;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mask = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.mask);
    }

    public static class Handler implements IMessageHandler<UpdateSkinLayersPacket, IMessage> {

        @Override
        public IMessage onMessage(UpdateSkinLayersPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player != null) {
                player.getDataWatcher()
                    .updateObject(16, message.mask);
            }
            return null;
        }
    }
}
