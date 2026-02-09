package org.encinet.mik.util;

import java.nio.ByteBuffer;

/**
 * Protocol-related utility methods
 */
public final class ProtocolUtil {

    private ProtocolUtil() {
        // Utility class
    }

    /**
     * Encode an integer as a VarInt
     *
     * @param value the integer to encode
     * @return the VarInt encoded bytes
     */
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
