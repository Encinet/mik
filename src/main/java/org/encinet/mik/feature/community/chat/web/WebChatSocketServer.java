package org.encinet.mik.feature.community.chat.web;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.RejectedExecutionHandlers;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A bounded, chat-only loopback listener. No account/control HTTP routes are installed here. */
final class WebChatSocketServer implements AutoCloseable {
    interface Events {
        void opened(Channel channel, String address);
        void ready(Channel channel);
        void message(Channel channel, String text);
        void closed(Channel channel);
        void pong(Channel channel);
    }

    private static final AttributeKey<String> ADDRESS = AttributeKey.valueOf("mik.webchat.address");
    private static final AttributeKey<Boolean> UPGRADED = AttributeKey.valueOf("mik.webchat.upgraded");
    private final Events events;
    private final String origin;
    private final int maxConnections;
    private final WebChatEncryption encryption;
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
    private EventLoopGroup acceptors;
    private EventLoopGroup workers;
    private EventExecutorGroup codecs;
    private Channel listener;

    WebChatSocketServer(Events events, String origin, int maxConnections, WebChatEncryption encryption) {
        this.events = events;
        this.origin = origin;
        this.maxConnections = maxConnections;
        this.encryption = java.util.Objects.requireNonNull(encryption, "encryption");
    }

    void start(int port) throws InterruptedException {
        acceptors = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workers = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        codecs = new DefaultEventExecutorGroup(2, Thread.ofPlatform().daemon().name("mik-webchat-codec-", 0).factory(),
                256, RejectedExecutionHandlers.reject());
        listener = new ServerBootstrap().group(acceptors, workers).channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 32)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 256 * 1024))
                .childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, (MessageSizeEstimator) () -> {
                    var fallback = DefaultMessageSizeEstimator.DEFAULT.newHandle();
                    return message -> message instanceof WebChatPacket packet ? packet.size() + 17 : fallback.size(message);
                })
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) {
                        // Bound raw TCP connections as well as completed WebSocket upgrades.
                        if (channels.size() >= maxConnections + 16) { channel.close(); return; }
                        channels.add(channel);
                        channel.closeFuture().addListener(_ -> channels.remove(channel));
                        channel.pipeline().addLast(new HttpServerCodec(1024, 4096, 1024));
                        channel.pipeline().addLast(new HttpObjectAggregator(1024));
                        channel.pipeline().addLast(new HandshakeGate());
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            private long second;
                            private int frames;
                            @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                                if (message instanceof WebSocketFrame) {
                                    long now = System.currentTimeMillis() / 1000;
                                    if (second != now) { second = now; frames = 0; }
                                    if (++frames > 40) { io.netty.util.ReferenceCountUtil.release(message); context.close(); return; }
                                }
                                super.channelRead(context, message);
                            }
                        });
                        channel.pipeline().addLast(new WebSocketServerProtocolHandler(
                                WebSocketServerProtocolConfig.newBuilder().websocketPath("/ws")
                                        .handshakeTimeoutMillis(5000).forceCloseTimeoutMillis(1000)
                                        .maxFramePayloadLength(WebChatCipher.MAX_INBOUND_FRAME).allowExtensions(false)
                                        .expectMaskedFrames(true).allowMaskMismatch(false)
                                        .withUTF8Validator(true).dropPongFrames(false).build()));
                        channel.pipeline().addLast(new WebSocketFrameAggregator(WebChatCipher.MAX_INBOUND_FRAME));
                        channel.pipeline().addLast(new WebChatCipher(encryption));
                        channel.pipeline().addLast(new WebChatPacketEncoder(codecs));
                        channel.pipeline().addLast(new Frames());
                        channel.eventLoop().schedule(() -> {
                            if (!Boolean.TRUE.equals(channel.attr(UPGRADED).get())) channel.close();
                        }, 5, java.util.concurrent.TimeUnit.SECONDS);
                    }
                }).bind("127.0.0.1", port).sync().channel();
    }

    private final class HandshakeGate extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
            String address = request.headers().get("CF-Connecting-IP", "");
            if (address.isBlank() || address.length() > 64 || !address.matches("[a-fA-F0-9:.]+")) {
                address = "loopback";
            }
            // Cloudflare terminates TLS and overwrites this header; the origin listener is loopback-only.
            if (!request.decoderResult().isSuccess() || request.method() != HttpMethod.GET
                    || !request.uri().equals("/ws") || !origin.equals(request.headers().get(HttpHeaderNames.ORIGIN))
                    || !request.headers().getAll("X-Forwarded-Proto").equals(java.util.List.of("https"))
                    || !"websocket".equalsIgnoreCase(request.headers().get(HttpHeaderNames.UPGRADE))) {
                reject(context, HttpResponseStatus.FORBIDDEN); return;
            }
            if (context.channel().attr(ADDRESS).setIfAbsent(address) != null) {
                reject(context, HttpResponseStatus.BAD_REQUEST); return;
            }
            context.fireChannelRead(request.retain());
        }

        private void reject(ChannelHandlerContext context, HttpResponseStatus status) {
            var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable cause) { context.close(); }
    }

    private final class Frames extends SimpleChannelInboundHandler<WebSocketFrame> {
        private long second;
        private int frames;
        @Override public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                context.channel().attr(UPGRADED).set(true);
                events.opened(context.channel(), context.channel().attr(ADDRESS).get());
            }
            if (event == WebChatCipher.InitialKeyConfirmed.INSTANCE) {
                events.ready(context.channel()); return;
            }
            super.userEventTriggered(context, event);
        }
        @Override protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) {
            long now = System.currentTimeMillis() / 1000;
            if (second != now) { second = now; frames = 0; }
            if (++frames > 20) { context.close(); return; }
            if (frame instanceof TextWebSocketFrame text) events.message(context.channel(), text.text());
            else if (frame instanceof PongWebSocketFrame) events.pong(context.channel());
            else context.close();
        }
        @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
            events.closed(context.channel()); super.channelInactive(context);
        }
        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable cause) { context.close(); }
    }

    static void authorize(Channel channel, String clientPublicKey) {
        try {
            channel.eventLoop().execute(() -> {
                if (channel.isActive()) channel.pipeline().fireUserEventTriggered(new WebChatCipher.Authorization(clientPublicKey));
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) { channel.close(); }
    }

    static void send(Channel channel, String json) {
        if (!channel.isActive()) return;
        if (!channel.isWritable()) { channel.close(); return; }
        try { channel.writeAndFlush(new TextWebSocketFrame(json)).addListener(ChannelFutureListener.CLOSE_ON_FAILURE); }
        catch (RuntimeException closed) { channel.close(); }
    }

    static void send(Channel channel, WebChatPacket packet) {
        if (!channel.isActive()) return;
        if (!channel.isWritable()) { channel.close(); return; }
        channel.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    static boolean compression(Channel channel) { return Boolean.TRUE.equals(channel.attr(WebChatCipher.COMPRESSION).get()); }

    @Override public void close() {
        if (listener != null) listener.close();
        for (Channel channel : channels) channel.close();
        if (codecs != null) codecs.shutdownGracefully(0, 2, java.util.concurrent.TimeUnit.SECONDS);
        if (workers != null) workers.shutdownGracefully(0, 2, java.util.concurrent.TimeUnit.SECONDS);
        if (acceptors != null) acceptors.shutdownGracefully(0, 2, java.util.concurrent.TimeUnit.SECONDS);
        channels.clear();
    }
}
