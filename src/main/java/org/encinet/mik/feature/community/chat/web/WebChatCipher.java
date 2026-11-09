package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Owns encryption, key generations and bounded exchange queues on the channel event loop. */
final class WebChatCipher extends ChannelDuplexHandler {
    static final int MAX_INBOUND_FRAME = 8192 + 1 + 16;
    private static final byte APPLICATION = 0, KEY_EXCHANGE = 1;
    static final int MAX_RECORDS = 16_384;
    static final Duration KEY_LIFETIME = Duration.ofMinutes(30);
    static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(5);
    private static final int HARD_RECORD_LIMIT = MAX_RECORDS + 256;
    private enum State { HELLO, AUTHORIZATION, KEY, FINISH, READY, ROTATING, CLOSED }
    record Authorization(String clientPublicKey) { }
    enum InitialKeyConfirmed { INSTANCE }
    private record Pending(byte[] clear, ChannelPromise promise) { }
    private final WebChatEncryption encryption;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private State state = State.HELLO;
    private String connectionId, clientPublicKey;
    private WebChatEncryption.Session session, exchange;
    private ScheduledFuture<?> deadline, rotation;
    private int pendingBytes, readRecords, writtenRecords;
    private long establishedAt;

    WebChatCipher(WebChatEncryption encryption) { this.encryption = encryption; }

    @Override public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof Authorization authorization) {
            try {
                if (state != State.AUTHORIZATION) throw new IllegalStateException("Unexpected chat authorization");
                clientPublicKey = authorization.clientPublicKey();
                beginExchange(context);
            } catch (Exception invalid) { terminate(context); }
        } else super.userEventTriggered(context, event);
    }

    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (state == State.CLOSED) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(new ClosedChannelException());
            terminate(context); return;
        }
        if (message instanceof BinaryWebSocketFrame || message instanceof ContinuationWebSocketFrame) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(new IllegalArgumentException("Plain application frames are required internally"));
            terminate(context); return;
        }
        if (!(message instanceof TextWebSocketFrame text)) { context.write(message, promise); return; }
        byte[] clear = null;
        try {
            clear = text.text().getBytes(StandardCharsets.UTF_8);
            if (clear.length > 128 * 1024) throw new IllegalArgumentException("Chat frame too large");
            if (state == State.READY) {
                maybeRotate(context);
                if (state == State.READY) {
                    writeProtected(context, clear, APPLICATION, promise);
                    maybeRotate(context);
                    return;
                }
            }
            if (state == State.HELLO) {
                JsonObject body = JsonParser.parseString(text.text()).getAsJsonObject();
                if (!body.keySet().equals(java.util.Set.of("type", "connectionId")) || !body.get("type").getAsString().equals("hello"))
                    throw new IllegalArgumentException("Expected connection hello");
                connectionId = body.get("connectionId").getAsString();
                if (!connectionId.matches("[A-Za-z0-9_-]{32}")) throw new IllegalArgumentException("Invalid connection");
                state = State.AUTHORIZATION;
                context.write(text.retain(), promise);
                return;
            }
            if (pending.size() >= 16 || pendingBytes + clear.length > 256 * 1024)
                throw new IllegalArgumentException("Chat key exchange backpressure");
            if (state != State.KEY && state != State.FINISH && state != State.ROTATING) throw new ClosedChannelException();
            pending.addLast(new Pending(clear, promise)); pendingBytes += clear.length;
            clear = null; // The exchange queue now owns and erases this buffer.
        } catch (Exception invalid) { promise.tryFailure(invalid); terminate(context); }
        finally {
            if (clear != null) Arrays.fill(clear, (byte) 0);
            ReferenceCountUtil.release(message);
        }
    }

    private void beginExchange(ChannelHandlerContext context) throws Exception {
        if (rotation != null) { rotation.cancel(false); rotation = null; }
        exchange = encryption.session(connectionId, session == null ? 0 : session.generation() + 1, clientPublicKey);
        state = session == null ? State.KEY : State.ROTATING;
        String announcement = exchange.announcement().toString();
        if (session == null) context.writeAndFlush(new TextWebSocketFrame(announcement));
        else {
            byte[] clear = announcement.getBytes(StandardCharsets.UTF_8);
            try { writeProtected(context, clear, KEY_EXCHANGE, context.newPromise()); }
            finally { Arrays.fill(clear, (byte) 0); }
            context.flush();
        }
        deadline = context.executor().schedule(() -> terminate(context), EXCHANGE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void maybeRotate(ChannelHandlerContext context) throws Exception {
        if (state == State.READY && context.channel().isActive()
                && (readRecords >= MAX_RECORDS || writtenRecords >= MAX_RECORDS
                    || System.nanoTime() - establishedAt >= KEY_LIFETIME.toNanos())) beginExchange(context);
    }

    private void checkBudget() {
        if (readRecords >= HARD_RECORD_LIMIT || writtenRecords >= HARD_RECORD_LIMIT
                || System.nanoTime() - establishedAt >= KEY_LIFETIME.plus(EXCHANGE_TIMEOUT).toNanos())
            throw new IllegalStateException("Chat key expired");
    }

    private void writeProtected(ChannelHandlerContext context, byte[] clear, byte kind, ChannelPromise promise) throws Exception {
        checkBudget();
        byte[] record = new byte[clear.length + 1];
        record[0] = kind;
        System.arraycopy(clear, 0, record, 1, clear.length);
        byte[] cipher;
        try { cipher = session.seal(record); }
        finally { Arrays.fill(record, (byte) 0); }
        writtenRecords++;
        context.write(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(cipher)), promise);
    }

    private void acceptReply(ChannelHandlerContext context, JsonObject reply) throws Exception {
        JsonObject confirmation = exchange.accept(reply);
        if (session == null) context.write(new TextWebSocketFrame(confirmation.toString()));
        else {
            byte[] clear = confirmation.toString().getBytes(StandardCharsets.UTF_8);
            try { writeProtected(context, clear, KEY_EXCHANGE, context.newPromise()); }
            finally { Arrays.fill(clear, (byte) 0); }
        }
        state = State.FINISH;
        context.flush();
    }

    private void activateExchange(ChannelHandlerContext context) throws Exception {
        boolean initial = session == null;
        if (session != null) session.close();
        session = exchange; exchange = null;
        state = State.READY;
        readRecords = 1; writtenRecords = 0; establishedAt = System.nanoTime();
        if (deadline != null) { deadline.cancel(false); deadline = null; }
        rotation = context.executor().schedule(() -> {
            try { if (state == State.READY) beginExchange(context); }
            catch (Exception invalid) { terminate(context); }
        }, KEY_LIFETIME.toMillis(), TimeUnit.MILLISECONDS);
        if (initial) context.fireUserEventTriggered(InitialKeyConfirmed.INSTANCE);
        while (!pending.isEmpty()) {
            Pending next = pending.removeFirst(); pendingBytes -= next.clear().length;
            try { writeProtected(context, next.clear(), APPLICATION, next.promise()); }
            catch (Exception invalid) { next.promise().tryFailure(invalid); throw invalid; }
            finally { Arrays.fill(next.clear(), (byte) 0); }
        }
        context.flush();
    }

    @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (!(message instanceof TextWebSocketFrame) && !(message instanceof BinaryWebSocketFrame)) {
            context.fireChannelRead(message); return;
        }
        try {
            if (state == State.KEY && message instanceof TextWebSocketFrame text && text.content().readableBytes() <= 1024) {
                acceptReply(context, JsonParser.parseString(text.text()).getAsJsonObject());
            } else if (state == State.FINISH && message instanceof BinaryWebSocketFrame binary) {
                if (binary.content().readableBytes() > 1025 + 16) throw new IllegalArgumentException("Invalid key confirmation");
                byte[] cipher = new byte[binary.content().readableBytes()];
                binary.content().getBytes(binary.content().readerIndex(), cipher);
                byte[] clear = exchange.open(cipher);
                try {
                    if (clear.length < 2 || clear[0] != KEY_EXCHANGE) throw new IllegalArgumentException("Expected key confirmation");
                    String json = decodeRecord(clear);
                    exchange.finish(JsonParser.parseString(json).getAsJsonObject());
                    activateExchange(context);
                } finally { Arrays.fill(clear, (byte) 0); }
            } else if ((state == State.READY || state == State.ROTATING) && message instanceof BinaryWebSocketFrame binary) {
                checkBudget();
                byte[] cipher = new byte[binary.content().readableBytes()];
                binary.content().getBytes(binary.content().readerIndex(), cipher);
                byte[] clear = session.open(cipher); readRecords++;
                try {
                    if (clear.length < 2 || clear.length > 8192 + 1) throw new IllegalArgumentException("Chat input too large");
                    String json = decodeRecord(clear);
                    if (clear[0] == KEY_EXCHANGE) {
                        if (state != State.ROTATING || clear.length > 1025) throw new IllegalArgumentException("Unexpected chat key");
                        acceptReply(context, JsonParser.parseString(json).getAsJsonObject());
                    } else if (clear[0] == APPLICATION) {
                        context.fireChannelRead(new TextWebSocketFrame(json));
                        maybeRotate(context);
                    } else throw new IllegalArgumentException("Unknown chat record");
                } finally { Arrays.fill(clear, (byte) 0); }
            } else throw new IllegalArgumentException("Encrypted chat required");
        } catch (Exception invalid) { terminate(context); }
        finally { ReferenceCountUtil.release(message); }
    }

    private static String decodeRecord(byte[] clear) throws java.nio.charset.CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(clear, 1, clear.length - 1)).toString();
    }

    private void destroy() {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        if (deadline != null) { deadline.cancel(false); deadline = null; }
        if (rotation != null) { rotation.cancel(false); rotation = null; }
        if (session != null) { session.close(); session = null; }
        if (exchange != null) { exchange.close(); exchange = null; }
        while (!pending.isEmpty()) {
            Pending item = pending.removeFirst();
            Arrays.fill(item.clear(), (byte) 0);
            item.promise().tryFailure(new ClosedChannelException());
        }
        pending.clear(); pendingBytes = 0;
        connectionId = null; clientPublicKey = null;
    }

    private void terminate(ChannelHandlerContext context) { destroy(); context.close(); }

    @Override public void close(ChannelHandlerContext context, ChannelPromise promise) throws Exception {
        destroy(); super.close(context, promise);
    }

    @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
        destroy(); super.channelInactive(context);
    }

    @Override public void handlerRemoved(ChannelHandlerContext context) throws Exception {
        destroy(); super.handlerRemoved(context);
    }

    @Override public void exceptionCaught(ChannelHandlerContext context, Throwable cause) { terminate(context); }
}
