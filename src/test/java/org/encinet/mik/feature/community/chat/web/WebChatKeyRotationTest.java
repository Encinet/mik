package org.encinet.mik.feature.community.chat.web;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WebChatKeyRotationTest {
    @Test void timeBasedRotationPreservesTheSocketAndOrdersQueuedApplicationMessages() throws Exception {
        try (var fixture = new WebChatCipherTest.Fixture()) {
            fixture.establish(); fixture.read(); fixture.rotate();
            var next = fixture.nextClient();
            var first = fixture.channel.writeOneOutbound(new TextWebSocketFrame("first"));
            var second = fixture.channel.writeOneOutbound(new TextWebSocketFrame("second"));
            assertFalse(first.isDone()); assertFalse(second.isDone());
            fixture.send("already in flight", 0);
            TextWebSocketFrame received = fixture.channel.readInbound();
            try { assertEquals("already in flight", received.text()); } finally { received.release(); }
            fixture.finishRotation(next);
            assertTrue(fixture.channel.isActive());
            assertTrue(first.isSuccess()); assertTrue(second.isSuccess());
            assertEquals("first", fixture.read()); assertEquals("second", fixture.read());
            assertNull(fixture.channel.readOutbound(), "Rotation never emits a second authorization event");
            fixture.send("after rotation", 0);
            received = fixture.channel.readInbound();
            try { assertEquals("after rotation", received.text()); } finally { received.release(); }
        }
    }

    @Test void recordBudgetsRotateBothDirectionsWithoutWaitingForTheTimer() throws Exception {
        for (boolean outbound : new boolean[]{true, false}) {
            try (var fixture = new WebChatCipherTest.Fixture()) {
                fixture.establish(); fixture.read();
                int count = WebChatCipher.MAX_RECORDS - 1;
                for (int i = 0; i < count; i++) {
                    if (outbound) {
                        fixture.channel.writeOutbound(new TextWebSocketFrame("data"));
                        assertEquals("data", fixture.read());
                    } else {
                        fixture.send("data", 0);
                        TextWebSocketFrame message = fixture.channel.readInbound();
                        assertNotNull(message); message.release();
                    }
                    if (i < count - 1) assertNull(fixture.channel.readOutbound());
                }
                fixture.finishRotation(fixture.nextClient());
                assertTrue(fixture.channel.isActive());
                fixture.channel.writeOutbound(new TextWebSocketFrame("new generation"));
                assertEquals("new generation", fixture.read());
            }
        }
    }

    @Test void aRotationTimeoutFailsPendingWritesAndClosesTheConnection() throws Exception {
        try (var fixture = new WebChatCipherTest.Fixture()) {
            fixture.establish(); fixture.read(); fixture.rotate(); fixture.nextClient();
            var pending = fixture.channel.writeOneOutbound(new TextWebSocketFrame("secret queued during rotation"));
            fixture.channel.advanceTimeBy(WebChatCipher.EXCHANGE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            fixture.channel.runScheduledPendingTasks();
            assertFalse(fixture.channel.isActive());
            assertTrue(pending.isDone()); assertFalse(pending.isSuccess());
            assertNull(fixture.channel.readOutbound());
        }
    }

    @Test void oldKeyTrafficAndUnsolicitedExchangeRecordsAreRejected() throws Exception {
        try (var fixture = new WebChatCipherTest.Fixture()) {
            fixture.establish(); fixture.read(); fixture.rotate();
            var next = fixture.nextClient();
            var old = fixture.client;
            fixture.finishRotation(next);
            byte[] oldPacket = old.sender.seal(WebChatEncryption.EMPTY, "\0old".getBytes(StandardCharsets.UTF_8));
            fixture.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(oldPacket)));
            assertFalse(fixture.channel.isActive()); assertNull(fixture.channel.readInbound());
        }
        try (var fixture = new WebChatCipherTest.Fixture()) {
            fixture.establish(); fixture.read(); fixture.send("{}", 1);
            assertFalse(fixture.channel.isActive()); assertNull(fixture.channel.readInbound());
        }
    }

    @Test void aPeerCannotKeepAnOldKeyAliveByIgnoringRotation() throws Exception {
        try (var fixture = new WebChatCipherTest.Fixture()) {
            fixture.establish(); fixture.read(); fixture.rotate(); fixture.nextClient();
            for (int i = 0; i < WebChatCipher.MAX_RECORDS + 257 && fixture.channel.isActive(); i++) {
                fixture.send("old key", 0);
                TextWebSocketFrame received = fixture.channel.readInbound();
                if (received != null) received.release();
            }
            assertFalse(fixture.channel.isActive());
        }
    }

    @Test void pendingWriteCallbacksCanCloseTheChannelDuringCleanup() throws Exception {
        try (var fixture = new WebChatCipherTest.Fixture()) {
            fixture.establish(); fixture.read(); fixture.rotate(); fixture.nextClient();
            var first = fixture.channel.newPromise();
            first.addListener(_ -> fixture.channel.close());
            fixture.channel.writeOneOutbound(new TextWebSocketFrame("first pending"), first);
            var second = fixture.channel.writeOneOutbound(new TextWebSocketFrame("second pending"));
            assertDoesNotThrow(() -> fixture.channel.close().syncUninterruptibly());
            assertTrue(first.isDone()); assertFalse(first.isSuccess());
            assertTrue(second.isDone()); assertFalse(second.isSuccess());
        }
    }
}
