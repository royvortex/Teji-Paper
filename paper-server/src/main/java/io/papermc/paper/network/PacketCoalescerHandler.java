package io.papermc.paper.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.ArrayList;
import java.util.List;

/**
 * Packet coalescing handler for batching outbound ByteBufs.
 */
public class PacketCoalescerHandler extends ChannelOutboundHandlerAdapter {
    private final List<ByteBuf> buffer = new ArrayList<>();
    private long lastFlush = System.nanoTime();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf && io.papermc.paper.configuration.GlobalConfiguration.get().tejiPaper.network.packetBatchingEnabled) {
            buffer.add(buf.retain());
            promise.setSuccess(); // Immediate success
            if (shouldFlush()) {
                flush(ctx);
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private boolean shouldFlush() {
        return buffer.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().tejiPaper.network.batchingMaxPackets
            || System.nanoTime() - lastFlush > io.papermc.paper.configuration.GlobalConfiguration.get().tejiPaper.network.batchingMaxDelayMs * 1_000_000L;
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (buffer.isEmpty()) return;
        ByteBuf batch = Unpooled.compositeBuffer();
        for (ByteBuf b : buffer) {
            ((io.netty.buffer.CompositeByteBuf) batch).addComponent(true, b);
        }
        ctx.writeAndFlush(batch);
        buffer.clear();
        lastFlush = System.nanoTime();
        super.flush(ctx);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        flush(ctx); // Flush on close
        super.close(ctx, promise);
    }
}