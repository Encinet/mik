package org.encinet.mik.feature.community.chat.web;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;

/** Bounded per-connection ordering around background compression; cipher state stays on the I/O loop. */
final class WebChatPacketEncoder extends ChannelDuplexHandler {
    private static final class Pending {
        final ChannelPromise promise;
        final int bytes;
        Object value;
        Future<?> task;
        Throwable failure;
        boolean ready;
        Pending(ChannelPromise promise, int bytes) { this.promise = promise; this.bytes = bytes; }
    }
    private final EventExecutorGroup workers;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private int pendingBytes;
    private boolean closed, flushPending;

    WebChatPacketEncoder(EventExecutorGroup workers) { this.workers = workers; }

    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        if (!(message instanceof TextWebSocketFrame) && !(message instanceof WebChatPacket)) {
            context.write(message, promise); return;
        }
        int bytes = message instanceof TextWebSocketFrame text ? text.content().readableBytes() : ((WebChatPacket) message).size();
        if (closed || pending.size() >= 16 || pendingBytes + bytes > 256 * 1024) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(new RejectedExecutionException("Chat encoding backpressure"));
            context.close(); return;
        }
        var next = new Pending(promise, bytes);
        pending.addLast(next); pendingBytes += bytes;
        if (message instanceof TextWebSocketFrame text && bytes >= 1024 && WebChatSocketServer.compression(context.channel())) {
            String json = text.text();
            text.release();
            try {
                next.task = workers.next().submit(() -> prepare(context, next, json));
            } catch (RejectedExecutionException busy) {
                next.failure = busy;
                next.ready = true;
            }
        } else {
            next.value = message;
            next.ready = true;
        }
        drain(context);
    }

    private void prepare(ChannelHandlerContext context, Pending next, String json) {
        if (next.promise.isDone()) return;
        WebChatPacket value = null;
        Throwable failure = null;
        try { value = WebChatPacket.application(json, true); }
        catch (Throwable invalid) { failure = invalid; }
        WebChatPacket prepared = value;
        Throwable error = failure;
        if (next.promise.isDone()) return;
        try {
            // Return directly to the I/O loop; completion must not need another slot in the worker queue.
            context.executor().execute(() -> {
                if (next.promise.isDone()) return;
                next.failure = error;
                next.value = prepared;
                next.ready = true;
                drain(context);
            });
        } catch (RejectedExecutionException stopped) {
            next.promise.tryFailure(stopped);
            context.channel().close();
        }
    }

    private void drain(ChannelHandlerContext context) {
        while (!closed && !pending.isEmpty()) {
            Pending next = pending.peekFirst();
            if (!next.ready && !next.promise.isDone()) break;
            pending.removeFirst(); pendingBytes -= next.bytes;
            if (next.promise.isDone()) {
                discard(next);
                continue;
            }
            if (next.failure != null) {
                next.promise.tryFailure(next.failure);
                destroy(); context.close(); return;
            }
            context.write(next.value, next.promise);
            next.value = null;
        }
        if (flushPending && !closed) {
            context.flush();
            if (pending.isEmpty()) flushPending = false;
        }
    }

    @Override public void flush(ChannelHandlerContext context) {
        flushPending = true;
        drain(context);
    }

    private static void discard(Pending next) {
        if (next.task != null) next.task.cancel(false);
        ReferenceCountUtil.release(next.value);
        next.value = null;
    }
    private void destroy() {
        if (closed) return;
        closed = true;
        while (!pending.isEmpty()) {
            Pending next = pending.removeFirst();
            next.promise.tryFailure(new ClosedChannelException());
            discard(next);
        }
        pendingBytes = 0;
    }
    @Override public void close(ChannelHandlerContext context, ChannelPromise promise) {
        destroy(); context.close(promise);
    }
    @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
        destroy(); super.channelInactive(context);
    }
    @Override public void handlerRemoved(ChannelHandlerContext context) { destroy(); }
}
