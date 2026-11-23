package org.encinet.mik.feature.entertainment.music.gateway;

import org.encinet.mik.protocol.music.MusicMediaDescriptor;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MusicFrameHandshakeTest {
    @Test
    void aPeerCanNegotiateOneOutputVersionWithoutDisablingTheWholeChannel() throws IOException {
        var sent = new ByteArrayOutputStream();
        int profile = MusicFrames.negotiate(acknowledgment(0b10), new DataOutputStream(sent));

        assertArrayEquals(new byte[]{'M', 'I', 'K', 'M', 0, 2, 3}, sent.toByteArray());
        assertTrue(MusicFrames.supports(profile, descriptor(MusicRepresentation.NOTE_EVENTS, 1)));
        assertFalse(MusicFrames.supports(profile, descriptor(MusicRepresentation.NOTE_EVENTS, 2)));
        assertFalse(MusicFrames.supports(profile, descriptor(MusicRepresentation.ENCODED_AUDIO, 1)));
    }

    @Test
    void rejectsAnEmptyOrUnadvertisedAcknowledgment() {
        for (int profile : new int[]{0, 0b100, 0b110}) {
            assertThrows(IOException.class, () -> MusicFrames.negotiate(acknowledgment(profile),
                    new DataOutputStream(new ByteArrayOutputStream())));
        }
    }

    private static DataInputStream acknowledgment(int profile) {
        return new DataInputStream(new ByteArrayInputStream(new byte[]{(byte) profile}));
    }

    private static MusicMediaDescriptor descriptor(MusicRepresentation kind, int version) {
        return MusicMediaDescriptor.newBuilder().setRepresentation(kind).setRepresentationVersion(version).build();
    }
}
