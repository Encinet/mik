package org.encinet.mik.module.social.platform.qq.gateway;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Handles WebSocket framing and delegates complete Gateway events to the session state machine. */
final class QqGatewaySocketListener implements WebSocket.Listener {

    private final long generation;
    private final int maximumFrameBytes;
    private final Callbacks callbacks;
    private final StringBuilder text = new StringBuilder();
    private int textBytes;

    QqGatewaySocketListener(long generation, int maximumFrameBytes, Callbacks callbacks) {
        this.generation = generation;
        this.maximumFrameBytes = maximumFrameBytes;
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        if (!callbacks.opened(generation, webSocket)) {
            webSocket.abort();
            return;
        }
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBytes += StandardCharsets.UTF_8.encode(data.toString()).remaining();
        if (textBytes > maximumFrameBytes) {
            callbacks.failed(generation,
                    new IllegalStateException("QQ Gateway text frame exceeded the size limit"),
                    true);
            return CompletableFuture.completedFuture(null);
        }
        text.append(data);
        if (last) {
            String payload = text.toString();
            text.setLength(0);
            textBytes = 0;
            callbacks.text(generation, payload);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        callbacks.failed(generation,
                new IllegalStateException("QQ Gateway sent an unsupported binary frame"), true);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return webSocket.sendPong(message);
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        callbacks.closed(generation, statusCode, reason);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        callbacks.failed(generation, error, true);
    }

    interface Callbacks {
        boolean opened(long generation, WebSocket socket);

        void text(long generation, String payload);

        void closed(long generation, int statusCode, String reason);

        void failed(long generation, Throwable error, boolean preserveSession);
    }
}
