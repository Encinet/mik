package org.encinet.mik.feature.community.chat.web;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

class WebChatPacketTest {
    @Test void liveDataUsesTheSameAdaptiveCodecWhileAuthorizationAndControlsStayUncompressed() throws Exception {
        String body = "富文本消息 🌍".repeat(500);
        for (String type : java.util.List.of("message", "presence")) {
            String json = "{\"type\":\"" + type + "\",\"content\":\"" + body + "\"}";
            var packet = WebChatPacket.application(json, true);
            assertEquals(WebChatPacket.COMPRESSED_APPLICATION, packet.kind());
            assertEquals(json, expand(packet));
            assertEquals(WebChatPacket.APPLICATION, WebChatPacket.application(json, false).kind());
        }
        for (String type : java.util.List.of("authorized", "reauthorize", "accepted", "error", "key", "history"))
            assertEquals(WebChatPacket.APPLICATION, WebChatPacket.application("{\"type\":\"" + type + "\",\"data\":\"" + body + "\"}", true).kind());
        assertEquals(WebChatPacket.APPLICATION, WebChatPacket.application("{\"type\":\"message\",\"text\":\"hi\"}", true).kind());
    }

    @Test void historyCompressionIsOptionalAndOnlyUsedWhenItSavesSpace() throws Exception {
        String page = "{\"type\":\"history\",\"messages\":[\"" + "公共消息 private 管理 🌍".repeat(1000) + "\"]}";
        var plain = packet(page, false);
        assertEquals(WebChatPacket.APPLICATION, plain.kind());
        assertEquals(page, new String(plain.copyPayload(), StandardCharsets.UTF_8));
        var compressed = packet(page, true);
        assertEquals(WebChatPacket.COMPRESSED_APPLICATION, compressed.kind());
        assertTrue(compressed.copyPayload().length < plain.copyPayload().length / 4);
        assertEquals(page, expand(compressed));
        assertEquals(WebChatPacket.APPLICATION, packet("{\"type\":\"history\"}", true).kind());
        var random = new Random(42);
        char[] noise = new char[1024];
        for (int i = 0; i < noise.length; i++) noise[i] = (char) random.nextInt(128);
        assertEquals(WebChatPacket.APPLICATION, packet(new String(noise), true).kind());
    }

    @Test void framesAreIndependentBoundedAndImmutable() throws Exception {
        String first = "{\"type\":\"history\",\"text\":\"" + "private secret".repeat(500) + "\"}";
        String second = "{\"type\":\"history\",\"text\":\"" + "public text".repeat(500) + "\"}";
        var privatePage = packet(first, true);
        var publicPage = packet(second, true);
        assertEquals(second, expand(publicPage));
        assertEquals(first, expand(privatePage));
        byte[] modified = publicPage.copyPayload();
        Arrays.fill(modified, (byte) 0);
        assertEquals(second, expand(publicPage));
        String maximum = "x".repeat(WebChatPacket.MAX_BYTES);
        assertEquals(maximum, expand(packet(maximum, true)));
        assertThrows(IllegalArgumentException.class, () -> packet(maximum + "x", true));
        assertThrows(IllegalArgumentException.class, () -> packet("界".repeat(WebChatPacket.MAX_BYTES / 3 + 1), false));
    }

    @Test void aMessageCannotUseTheCompressionDictionaryOfAnotherMessage() throws Exception {
        String target = "target secret".repeat(500);
        var matching = WebChatPacket.history(java.util.List.of(target, target), true);
        var different = WebChatPacket.history(java.util.List.of("different text".repeat(500), target), true);
        assertEquals(target + target, expand(matching));
        assertEquals("different text".repeat(500) + target, expand(different));
        var left = blocks(matching);
        var right = blocks(different);
        assertEquals(3, left.size());
        assertEquals(3, right.size());
        assertArrayEquals(left.get(1), right.get(1), "The second message must encode independently of the first");
    }

    private static java.util.List<byte[]> blocks(WebChatPacket packet) {
        byte[] bytes = packet.copyPayload();
        var result = new java.util.ArrayList<byte[]>();
        int start = Integer.BYTES;
        for (int i = start; i <= bytes.length - 4; i++) {
            if (bytes[i] == 0 && bytes[i + 1] == 0 && bytes[i + 2] == -1 && bytes[i + 3] == -1) {
                result.add(Arrays.copyOfRange(bytes, start, i + 4));
                start = i + 4;
                i += 3;
            }
        }
        result.add(Arrays.copyOfRange(bytes, start, bytes.length));
        return result;
    }

    static WebChatPacket packet(String text, boolean compression) {
        return WebChatPacket.history(java.util.List.of(text), compression);
    }

    static String expand(WebChatPacket packet) throws Exception {
        return expand(packet.copyPayload());
    }

    static String expand(byte[] payload) throws Exception {
        int length = ByteBuffer.wrap(payload).getInt();
        byte[] result = new byte[length];
        try (var inflater = new Inflater()) {
            inflater.setInput(payload, Integer.BYTES, payload.length - Integer.BYTES);
            int size = inflater.inflate(result);
            assertTrue(inflater.finished());
            assertEquals(0, inflater.getRemaining());
            assertEquals(length, size);
        }
        return new String(result, StandardCharsets.UTF_8);
    }
}
