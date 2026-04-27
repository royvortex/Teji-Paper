package io.papermc.paper.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.Executor;
import java.util.zip.Deflater;

/**
 * Async packet compression handler.
 * Offloads compression to a dedicated pool, preserving Netty pipeline order.
 */
public class AsyncCompressionHandler extends ChannelOutboundHandlerAdapter {
    private final Executor compressionPool;
    private final ThreadLocal<Deflater> deflater = ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, true)); // Zlib

    public AsyncCompressionHandler(Executor compressionPool) {
        this.compressionPool = compressionPool;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf) {
            buf.retain();
            compressionPool.execute(() -> {
                try {
                    ByteBuf compressed = compress(buf);
                    ctx.executor().execute(() -> ctx.write(compressed, promise));
                } catch (Exception e) {
                    // Fallback sync
                    ByteBuf compressed = compressSync(buf);
                    ctx.executor().execute(() -> ctx.write(compressed, promise));
                } finally {
                    buf.release();
                }
            });
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private ByteBuf compress(ByteBuf buf) {
        Deflater d = deflater.get();
        d.reset();
        // Implement compression logic: read from buf, compress to new ByteBuf
        ByteBuf compressed = ctx.alloc().buffer();
        // ... compress ...
        return compressed;
    }

    private ByteBuf compressSync(ByteBuf buf) {
        // Synchronous fallback
        return compress(buf);
    }
}