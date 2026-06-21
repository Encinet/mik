package org.encinet.mik.util;

import java.nio.ByteBuffer;

public final class ProtocolUtil {

    private ProtocolUtil() {
    }

    public static byte[] encodeVarInt(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        while ((value & ~0x7F) != 0) {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buffer.put((byte) value);
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }
}
