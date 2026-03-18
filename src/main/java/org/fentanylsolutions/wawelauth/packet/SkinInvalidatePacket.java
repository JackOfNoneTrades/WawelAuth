package org.fentanylsolutions.wawelauth.packet;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.fentanylsolutions.wawelauth.client.render.skinlayers.SkinLayers3DSetup;
import org.fentanylsolutions.wawelauth.wawelclient.WawelClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client packet instructing the client to evict cached
 * skin/cape data for a specific profile UUID so the new texture
 * is re-fetched on next access.
 */
public final class SkinInvalidatePacket implements IMessage {

    private long uuidMost;
    private long uuidLeast;

    public SkinInvalidatePacket() {}

    public SkinInvalidatePacket(UUID profileId) {
        this.uuidMost = profileId.getMostSignificantBits();
        this.uuidLeast = profileId.getLeastSignificantBits();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.uuidMost = buf.readLong();
        this.uuidLeast = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(uuidMost);
        buf.writeLong(uuidLeast);
    }

    public static final class Handler implements IMessageHandler<SkinInvalidatePacket, IMessage> {

        @Override
        public IMessage onMessage(SkinInvalidatePacket message, MessageContext ctx) {
            if (!ctx.side.isClient()) {
                return null;
            }

            final UUID profileId = new UUID(message.uuidMost, message.uuidLeast);
            Minecraft.getMinecraft()
                .func_152344_a(() -> { // addScheduledTask
                    WawelClient client = WawelClient.instance();
                    if (client != null) {
                        client.getTextureResolver()
                            .invalidate(profileId);
                    }
                    SkinLayers3DSetup.updateSkullCache(profileId, null);
                    SkinLayers3DSetup.updateState(profileId, null);
                });
            return null;
        }
    }
}
