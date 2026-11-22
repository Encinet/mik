package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.Envelope;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/** Checks the unsigned length before allocating a frame buffer. */
final class MikdFrames {
    static final int VERSION = 1;
    static final int MAX_BYTES = 256 * 1024;

    private MikdFrames() { }

    static Envelope read(ReadableByteChannel channel) throws IOException {
        ByteBuffer prefix = ByteBuffer.allocate(Integer.BYTES);
        readFully(channel, prefix);
        int length = prefix.flip().getInt();
        if (length <= 0 || length > MAX_BYTES) throw new IOException("Invalid mikd frame length");
        ByteBuffer bytes = ByteBuffer.allocate(length);
        readFully(channel, bytes);
        return Envelope.parseFrom(bytes.array());
    }

    static void write(WritableByteChannel channel, Envelope envelope) throws IOException {
        int length = envelope.getSerializedSize();
        if (length <= 0 || length > MAX_BYTES) throw new IOException("Invalid mikd frame length");
        writeFully(channel, ByteBuffer.allocate(Integer.BYTES).putInt(length).flip());
        writeFully(channel, ByteBuffer.wrap(envelope.toByteArray()));
    }

    private static void readFully(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new EOFException("Truncated mikd frame");
    }

    private static void writeFully(WritableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
