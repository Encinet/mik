package org.encinet.mik.feature.player.client.detection;

import org.encinet.mik.feature.player.client.ClientMod;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class ClientModSignals {
    private static final Pattern CHANNEL_NAME = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private ClientModSignals() {
    }

    static Set<ClientMod> fromPayload(String channel, byte[] data) {
        EnumSet<ClientMod> detected = EnumSet.noneOf(ClientMod.class);
        fromChannel(channel).ifPresent(detected::add);
        // REGISTER lists clientbound channels supported by the client, not server capabilities.
        // Do not interpret UNREGISTER, brand strings or a loader's namespace as a mod list.
        if (data.length > 32767) return detected;
        if (channel.equals("minecraft:register")) {
            for (String registered : new String(data, StandardCharsets.UTF_8).split("\u0000")) {
                fromChannel(registered).ifPresent(detected::add);
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
                    fromChannel(readString(buffer)).ifPresent(detected::add);
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

    static Optional<ClientMod> fromChannel(String channel) {
        if (channel.length() > 256 || !CHANNEL_NAME.matcher(channel).matches()) return Optional.empty();
        int colon = channel.indexOf(':');
        if (colon <= 0 || colon == channel.length() - 1) return Optional.empty();
        return switch (channel.substring(0, colon)) {
            case "axiom" -> Optional.of(ClientMod.AXIOM);
            case "xaerominimap" -> Optional.of(ClientMod.XAERO_MINIMAP);
            case "xaeroworldmap" -> Optional.of(ClientMod.XAERO_WORLD_MAP);
            case "plasmo" -> channel.equals("plasmo:voice") || channel.startsWith("plasmo:voice/")
                    ? Optional.of(ClientMod.PLASMO_VOICE) : Optional.empty();
            default -> Optional.empty();
        };
    }
}
