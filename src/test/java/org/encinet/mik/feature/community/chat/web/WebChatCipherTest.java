package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WebChatCipherTest {
    @Test void authorizationAndQueuedMessagesReachTheWireOnlyAfterEncryption() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.channel.writeOutbound(new TextWebSocketFrame("{\"type\":\"message\",\"text\":\"staff secret\"}"));
            assertNull(fixture.channel.readOutbound(), "Protected messages wait for key confirmation");
            fixture.establish();
            assertEquals("{\"type\":\"authorized\",\"name\":\"Alice\"}", fixture.read());
            assertEquals("{\"type\":\"message\",\"text\":\"staff secret\"}", fixture.read());
            assertNull(fixture.channel.readOutbound());
        }
    }

    @Test void authenticatedFramesAreOrderedAndReplaysCloseTheConnection() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.establish(); fixture.read();
            byte[] encrypted = fixture.client.sender.seal(WebChatEncryption.EMPTY, "\0private".getBytes(StandardCharsets.UTF_8));
            fixture.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(encrypted)));
            TextWebSocketFrame clear = fixture.channel.readInbound();
            try { assertEquals("private", clear.text()); } finally { clear.release(); }
            fixture.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(encrypted)));
            assertFalse(fixture.channel.isActive()); assertNull(fixture.channel.readInbound());
        }
    }

    @Test void plaintextAndTamperedCiphertextCannotFallBackToOrdinaryMessages() throws Exception {
        for (boolean plaintext : new boolean[]{true, false}) {
            try (var fixture = new Fixture()) {
                fixture.establish(); fixture.read();
                if (plaintext) fixture.channel.writeInbound(new TextWebSocketFrame("private secret"));
                else {
                    byte[] encrypted = fixture.client.sender.seal(WebChatEncryption.EMPTY, "\0private secret".getBytes(StandardCharsets.UTF_8));
                    encrypted[0] ^= 1;
                    fixture.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(encrypted)));
                }
                assertFalse(fixture.channel.isActive()); assertNull(fixture.channel.readInbound());
            }
        }
    }

    @Test void internalBinaryPayloadCannotBypassEncryption() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.establish(); fixture.read();
            assertThrows(IllegalArgumentException.class, () -> fixture.channel.writeOutbound(
                    new BinaryWebSocketFrame(Unpooled.copiedBuffer("private secret", StandardCharsets.UTF_8))));
            assertFalse(fixture.channel.isActive()); assertNull(fixture.channel.readOutbound());
        }
    }

    static final class Fixture implements AutoCloseable {
        final org.bouncycastle.crypto.AsymmetricCipherKeyPair initialKey = WebChatEncryption.suite(org.bouncycastle.crypto.hpke.HPKE.mode_base).generatePrivateKey();
        final String authorizationKey = WebChatEncryptionTest.publicKey(initialKey);
        final EmbeddedChannel channel = new EmbeddedChannel(new WebChatCipher(WebChatEncryption.generate()), new ChannelInboundHandlerAdapter() {
            @Override public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
                if (event == WebChatCipher.InitialKeyConfirmed.INSTANCE)
                    context.writeAndFlush(new TextWebSocketFrame("{\"type\":\"authorized\",\"name\":\"Alice\"}"));
                else super.userEventTriggered(context, event);
            }
        });
        WebChatEncryptionTest.Client client;

        Fixture() {
            channel.freezeTime();
            channel.writeOutbound(new TextWebSocketFrame("{\"type\":\"hello\",\"connectionId\":\"" + WebChatEncryptionTest.ID + "\"}"));
            TextWebSocketFrame hello = channel.readOutbound();
            try { assertEquals("hello", JsonParser.parseString(hello.text()).getAsJsonObject().get("type").getAsString()); }
            finally { hello.release(); }
            channel.pipeline().fireUserEventTriggered(new WebChatCipher.Authorization(authorizationKey));
            TextWebSocketFrame key = channel.readOutbound();
            try {
                assertFalse(key.text().contains("Alice"));
                client = new WebChatEncryptionTest.Client(JsonParser.parseString(key.text()).getAsJsonObject(), WebChatEncryptionTest.ID, initialKey, authorizationKey);
            } finally { key.release(); }
        }

        void establish() throws Exception {
            channel.writeInbound(new TextWebSocketFrame(client.initial().toString()));
            TextWebSocketFrame confirmation = channel.readOutbound();
            try { client.confirm(JsonParser.parseString(confirmation.text()).getAsJsonObject()); }
            finally { confirmation.release(); }
            send(client.finished().toString(), 1);
        }

        String read() throws Exception {
            BinaryWebSocketFrame encrypted = channel.readOutbound();
            try {
                byte[] bytes = new byte[encrypted.content().readableBytes()]; encrypted.content().readBytes(bytes);
                assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("Alice"));
                assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("staff secret"));
                byte[] clear = client.recipient.open(WebChatEncryption.EMPTY, bytes);
                assertEquals(0, clear[0]);
                return new String(clear, 1, clear.length - 1, StandardCharsets.UTF_8);
            } finally { encrypted.release(); }
        }

        void rotate() {
            channel.advanceTimeBy(WebChatCipher.KEY_LIFETIME.toMillis(), TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
        }
        WebChatEncryptionTest.Client nextClient() throws Exception {
            var offer = readControl();
            assertEquals(client.generation + 1, offer.get("generation").getAsLong());
            return new WebChatEncryptionTest.Client(offer, WebChatEncryptionTest.ID,
                    WebChatEncryption.suite(org.bouncycastle.crypto.hpke.HPKE.mode_base).generatePrivateKey(), authorizationKey);
        }
        com.google.gson.JsonObject readControl() throws Exception {
            BinaryWebSocketFrame frame = channel.readOutbound();
            assertNotNull(frame);
            try {
                byte[] cipher = new byte[frame.content().readableBytes()];
                frame.content().readBytes(cipher);
                byte[] record = client.recipient.open(WebChatEncryption.EMPTY, cipher);
                assertEquals(1, record[0]);
                return JsonParser.parseString(new String(record, 1, record.length - 1, StandardCharsets.UTF_8)).getAsJsonObject();
            } finally { frame.release(); }
        }
        void finishRotation(WebChatEncryptionTest.Client next) throws Exception {
            send(next.initial().toString(), 1);
            next.confirm(readControl());
            client = next;
            send(next.finished().toString(), 1);
        }
        void send(String data, int kind) throws Exception {
            byte[] body = data.getBytes(StandardCharsets.UTF_8), record = new byte[body.length + 1];
            record[0] = (byte) kind; System.arraycopy(body, 0, record, 1, body.length);
            byte[] cipher = client.sender.seal(WebChatEncryption.EMPTY, record);
            channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(cipher)));
        }

        @Override public void close() { channel.finishAndReleaseAll(); }
    }
}
