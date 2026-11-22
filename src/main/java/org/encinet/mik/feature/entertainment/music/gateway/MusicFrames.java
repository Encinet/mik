package org.encinet.mik.feature.entertainment.music.gateway;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class MusicFrames {
    public static final int MAX_REQUEST = 128 * 1024;
    public static final int MAX_REPLY = MAX_REQUEST;
    public static final int BLOCK_BYTES = 64 * 1024;

    static final byte[] PREFACE = {'M', 'I', 'K', 'M', 0, 2};
    private static final int ENCODED_AUDIO_V1 = 0b01;
    private static final int NOTE_EVENTS_V1 = 0b10;
    private static final int OUTPUT_PROFILE = ENCODED_AUDIO_V1 | NOTE_EVENTS_V1;

    private MusicFrames() { }

    static int negotiate(DataInputStream input, DataOutputStream output) throws IOException {
        output.write(PREFACE);
        output.writeByte(OUTPUT_PROFILE);
        output.flush();
        int accepted = input.readUnsignedByte();
        if (accepted == 0 || (accepted & ~OUTPUT_PROFILE) != 0) {
            throw new IOException("Incompatible music output profile");
        }
        return accepted;
    }

    static boolean supports(int profile, MusicMediaDescriptor descriptor) {
        if (descriptor.getRepresentationVersion() != 1) return false;
        return switch (descriptor.getRepresentation()) {
            case ENCODED_AUDIO -> (profile & ENCODED_AUDIO_V1) != 0;
            case NOTE_EVENTS -> (profile & NOTE_EVENTS_V1) != 0;
            default -> false;
        };
    }

    public static void write(DataOutputStream output, MessageLite message, int maximum) throws IOException {
        int size = message.getSerializedSize();
        if (size <= 0 || size > maximum) throw new IOException("Invalid music frame size");
        output.writeInt(size);
        message.writeTo(output);
        output.flush();
    }

    public static <T extends MessageLite> T read(DataInputStream input, Parser<T> parser, int maximum) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximum) throw new IOException("Invalid music frame size");
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return parser.parseFrom(bytes);
    }
}
