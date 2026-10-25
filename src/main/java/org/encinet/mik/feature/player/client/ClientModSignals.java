package org.encinet.mik.feature.player.client;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

final class ClientModSignals {
    private ClientModSignals() {
    }

    static Set<RecommendedClientMod> fromPayload(String channel, byte[] data) {
        EnumSet<RecommendedClientMod> detected = EnumSet.noneOf(RecommendedClientMod.class);
        RecommendedClientMod.fromChannel(channel).ifPresent(detected::add);
        // REGISTER lists clientbound channels supported by the client, not server capabilities.
        // Do not interpret UNREGISTER, brand strings or a loader's namespace as a mod list.
        if (data.length > 32767) return detected;
        if (channel.equals("minecraft:register")) {
            for (String registered : new String(data, StandardCharsets.UTF_8).split("\u0000")) {
                RecommendedClientMod.fromChannel(registered).ifPresent(detected::add);
            }
        } else if (channel.equals("c:register")) {
            // Common networking v1 (Fabric/NeoForge): VarInt version, UTF-8 phase, channel list.
            try {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                if (readVarInt(buffer) != 1) return detected;
                String phase = readString(buffer);
                if (!phase.equals("play") && !phase.equals("configuration")) return detected;
                int count = readVarInt(buffer);
                if (count < 0 || count > buffer.remaining()) return detected;
                for (int index = 0; index < count; index++) {
                    RecommendedClientMod.fromChannel(readString(buffer)).ifPresent(detected::add);
                }
                if (buffer.hasRemaining()) return Set.of();
            } catch (BufferUnderflowException | IllegalArgumentException malformed) {
                return Set.of();
            }
        }
        return detected;
    }

    private static int readVarInt(ByteBuffer buffer) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = Byte.toUnsignedInt(buffer.get());
            if (shift == 28 && (next & 0xf0) != 0) throw new IllegalArgumentException("VarInt overflow");
            value |= (next & 0x7f) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("VarInt too long");
    }

    private static String readString(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        if (length < 0 || length > buffer.remaining()) throw new IllegalArgumentException("Invalid string length");
        byte[] text = new byte[length];
        buffer.get(text);
        return new String(text, StandardCharsets.UTF_8);
    }

    static Set<RecommendedClientMod> missing(Set<RecommendedClientMod> detected, boolean voiceClientPresent) {
        EnumSet<RecommendedClientMod> missing = EnumSet.allOf(RecommendedClientMod.class);
        missing.removeAll(detected);
        if (voiceClientPresent) missing.remove(RecommendedClientMod.PLASMO_VOICE);
        return missing;
    }
}
