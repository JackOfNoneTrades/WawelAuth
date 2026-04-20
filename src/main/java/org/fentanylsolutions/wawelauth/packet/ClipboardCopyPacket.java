package org.fentanylsolutions.wawelauth.packet;

import org.fentanylsolutions.wawelauth.client.ClipboardHelper;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class ClipboardCopyPacket implements IMessage {

    private String text;
    private String description;

    public ClipboardCopyPacket() {}

    public ClipboardCopyPacket(String text, String description) {
        this.text = text == null ? "" : text;
        this.description = description == null ? "" : description;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.text = ByteBufUtils.readUTF8String(buf);
        this.description = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.text == null ? "" : this.text);
        ByteBufUtils.writeUTF8String(buf, this.description == null ? "" : this.description);
    }

    public static final class Handler implements IMessageHandler<ClipboardCopyPacket, IMessage> {

        @Override
        public IMessage onMessage(ClipboardCopyPacket message, MessageContext ctx) {
            if (!ctx.side.isClient()) {
                return null;
            }
            ClipboardHelper.copyToClipboard(message.text, message.description);
            return null;
        }
    }
}
