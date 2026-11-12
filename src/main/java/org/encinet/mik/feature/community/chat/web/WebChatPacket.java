package org.encinet.mik.feature.community.chat.web;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

/** Immutable application bytes prepared on the history worker, before channel encryption. */
final class WebChatPacket {
    static final int MAX_BYTES = 128 * 1024;
    static final byte APPLICATION = 0, COMPRESSED_APPLICATION = 2;
    private final byte kind;
    private final byte[] payload;

    private WebChatPacket(byte kind, byte[] payload) {
        this.kind = kind;
        this.payload = payload;
    }

    static WebChatPacket application(String json, boolean compression) {
        byte[] clear = json.getBytes(StandardCharsets.UTF_8);
        if (clear.length > MAX_BYTES) throw new IllegalArgumentException("Chat frame too large");
        if (compression && clear.length >= 1024) {
            var body = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String type = body.has("type") ? body.get("type").getAsString() : "";
            // Authorization and control traffic never enter compression; history has its own scoped preparation.
            compression = type.equals("message") || type.equals("presence");
        } else compression = false;
        return prepare(clear, List.of(clear), compression);
    }

    static WebChatPacket history(List<String> parts, boolean compression) {
        var encoded = parts.stream().map(part -> part.getBytes(StandardCharsets.UTF_8)).toList();
        byte[] clear;
        try {
            long length = encoded.stream().mapToLong(part -> part.length).sum();
            if (length > MAX_BYTES) throw new IllegalArgumentException("Chat frame too large");
            clear = new byte[(int) length];
            int offset = 0;
            for (byte[] part : encoded) {
                System.arraycopy(part, 0, clear, offset, part.length);
                offset += part.length;
            }
            return prepare(clear, encoded, compression);
        } finally { encoded.forEach(part -> Arrays.fill(part, (byte) 0)); }
    }

    private static WebChatPacket prepare(byte[] clear, List<byte[]> parts, boolean compression) {
        if (compression && clear.length >= 1024) {
            // A fresh zlib stream per page, with at least 128 bytes saved including the length header.
            byte[] output = new byte[clear.length - Math.max(128, clear.length / 10)];
            try (var deflater = new Deflater(Deflater.BEST_SPEED)) {
                int size = Integer.BYTES;
                for (byte[] part : parts) {
                    deflater.setInput(part);
                    // Reset the dictionary between metadata and distinct access scopes or private threads.
                    for (;;) {
                        int available = output.length - size;
                        if (available == 0) return new WebChatPacket(APPLICATION, clear);
                        int count = deflater.deflate(output, size, available, Deflater.FULL_FLUSH);
                        size += count;
                        if (count < available) break;
                    }
                }
                deflater.finish();
                while (!deflater.finished() && size < output.length) {
                    int count = deflater.deflate(output, size, output.length - size);
                    if (count == 0) break;
                    size += count;
                }
                if (deflater.finished()) {
                    ByteBuffer.wrap(output).putInt(clear.length);
                    Arrays.fill(clear, (byte) 0);
                    return new WebChatPacket(COMPRESSED_APPLICATION, Arrays.copyOf(output, size));
                }
            } finally { Arrays.fill(output, (byte) 0); }
        }
        return new WebChatPacket(APPLICATION, clear);
    }

    byte kind() { return kind; }
    int size() { return payload.length; }
    byte[] copyPayload() { return payload.clone(); }
}
