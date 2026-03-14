package org.fentanylsolutions.wawelauth.wawelnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.network.NetworkManager;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * First handler in the Netty pipeline for each incoming connection.
 * Peeks at the first 2 bytes to determine if the client is speaking
 * HTTP or the Minecraft binary protocol.
 *
 * <ul>
 * <li>HTTP detected: tears down the MC pipeline, installs HTTP handlers</li>
 * <li>MC detected: removes itself, vanilla pipeline proceeds unchanged</li>
 * </ul>
 * <p>
 * Based on Netty's port unification example.
 */
public class ProtocolSwitchHandler extends ByteToMessageDecoder {

    private static final int PEEK_BYTES = 2;

    private final List<?> networkManagers;

    /**
     * @param networkManagers the live NetworkSystem.networkManagers list,
     *                        so we can remove the NetworkManager when switching to HTTP
     */
    public ProtocolSwitchHandler(List<?> networkManagers) {
        this.networkManagers = networkManagers;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < PEEK_BYTES) {
            return;
        }

        int b1 = in.getUnsignedByte(in.readerIndex());
        int b2 = in.getUnsignedByte(in.readerIndex() + 1);

        if (isHttp(b1, b2)) {
            switchToHttp(ctx);
        } else {
            ctx.pipeline()
                .remove(this);
        }
    }

    /**
     * Detects HTTP by checking if the first two bytes match the start
     * of an HTTP method keyword. MC handshake packets start with a
     * VarInt length (typically 0x00-0x0F), so there is zero ambiguity.
     */
    private static boolean isHttp(int b1, int b2) {
        return (b1 == 'G' && b2 == 'E') // GET
            || (b1 == 'P' && b2 == 'O') // POST
            || (b1 == 'P' && b2 == 'U') // PUT
            || (b1 == 'D' && b2 == 'E') // DELETE
            || (b1 == 'H' && b2 == 'E') // HEAD
            || (b1 == 'O' && b2 == 'P') // OPTIONS
            || (b1 == 'P' && b2 == 'A'); // PATCH
    }

    /**
     * Tears down the MC pipeline and installs HTTP handlers.
     * <p>
     * Instead of removing handlers by hardcoded name (brittle),
     * we iterate the pipeline and remove everything except ourselves.
     * We also remove the NetworkManager from the networkManagers list
     * to prevent zombie entries in NetworkSystem.networkTick().
     */
    private void switchToHttp(ChannelHandlerContext ctx) {
        WawelAuth.debug(
            "HTTP traffic detected from " + ctx.channel()
                .remoteAddress() + ", switching pipeline");

        ChannelPipeline pipeline = ctx.pipeline();

        // Find and remove NetworkManager from the networkManagers list
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            if (entry.getValue() instanceof NetworkManager) {
                // noinspection SynchronizeOnNonFinalField
                synchronized (networkManagers) {
                    networkManagers.remove(entry.getValue());
                }
                break;
            }
        }

        // Remove all handlers except this one
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            if (entry.getValue() != this) {
                toRemove.add(entry.getKey());
            }
        }
        for (String name : toRemove) {
            try {
                pipeline.remove(name);
            } catch (Exception e) {
                WawelAuth.LOG.warn("Failed to remove handler '{}' during pipeline switch: {}", name, e.getMessage());
            }
        }

        // Install HTTP handlers with config-driven limits
        ServerConfig.Http httpConfig = Config.server()
            .getHttp();
        pipeline.addLast("http_timeout", new ReadTimeoutHandler(httpConfig.getReadTimeoutSeconds()));
        pipeline.addLast("http_codec", new HttpServerCodec());
        pipeline.addLast("http_aggregator", new HttpObjectAggregator(httpConfig.getMaxContentLengthBytes()));
        pipeline.addLast("http_handler", new HttpRequestHandler());

        // Remove ourselves: ByteToMessageDecoder.handlerRemoved() fires
        // buffered bytes to the next handler (HttpServerCodec)
        pipeline.remove(this);
    }
}
