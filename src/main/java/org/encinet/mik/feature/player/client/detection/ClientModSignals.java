package org.encinet.mik.feature.player.client.detection;

import org.encinet.mik.feature.player.client.ClientMod;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class ClientModSignals {
    private static final int MAX_REGISTRATION_BYTES = 32767;
    private static final Pattern CHANNEL_NAME = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private ClientModSignals() {
    }

    static Set<ClientMod> fromPayload(String channel, byte[] data) {
        Optional<ClientMod> direct = fromChannel(channel);
        if (direct.isPresent()) return Set.of(direct.get());
        if (!channel.equals("minecraft:register") && !channel.equals("c:register")) return Set.of();
        // REGISTER lists clientbound channels supported by the client, not server capabilities.
        // Do not interpret UNREGISTER, brand strings or a loader's namespace as a mod list.
        if (data.length > MAX_REGISTRATION_BYTES) return Set.of();
        EnumSet<ClientMod> detected = EnumSet.noneOf(ClientMod.class);
        try {
            if (channel.equals("minecraft:register")) {
                String channels = readUtf8(ByteBuffer.wrap(data));
                int start = 0;
                while (start < channels.length()) {
                    int end = channels.indexOf('\0', start);
                    if (end < 0) end = channels.length();
                    fromChannel(channels.substring(start, end)).ifPresent(detected::add);
                    start = end + 1;
                }
            } else {
                // Common networking v1 (Fabric/NeoForge): VarInt version, UTF-8 phase, channel list.
                ByteBuffer buffer = ByteBuffer.wrap(data);
                if (readVarInt(buffer) != 1) return Set.of();
                String phase = readString(buffer);
                if (!phase.equals("play") && !phase.equals("configuration")) return Set.of();
                int count = readVarInt(buffer);
                if (count < 0 || count > buffer.remaining()) return Set.of();
                for (int index = 0; index < count; index++) {
                    fromChannel(readString(buffer)).ifPresent(detected::add);
                }
                if (buffer.hasRemaining()) return Set.of();
            }
        } catch (BufferUnderflowException | IllegalArgumentException | CharacterCodingException malformed) {
            return Set.of();
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

    private static String readString(ByteBuffer buffer) throws CharacterCodingException {
        int length = readVarInt(buffer);
        if (length < 0 || length > buffer.remaining()) throw new IllegalArgumentException("Invalid string length");
        ByteBuffer text = buffer.slice(buffer.position(), length);
        buffer.position(buffer.position() + length);
        return readUtf8(text);
    }

    private static String readUtf8(ByteBuffer buffer) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().decode(buffer).toString();
    }

    static Optional<ClientMod> fromChannel(String channel) {
        if (channel.length() > 256) return Optional.empty();
        int colon = channel.indexOf(':');
        if (colon <= 0 || colon == channel.length() - 1) return Optional.empty();
        ClientMod candidate = switch (channel.substring(0, colon)) {
            case "axiom" -> ClientMod.AXIOM;
            // Voxy itself is silent; these VoxyServer channels advertise the compatible LOD client.
            case "voxyserver" -> switch (channel) {
                case "voxyserver:lod_ready", "voxyserver:lod_preferences", "voxyserver:lod_server_settings",
                        "voxyserver:lod_preserialized", "voxyserver:lod_clear" -> ClientMod.VOXY;
                default -> null;
            };
            case "xaerominimap" -> ClientMod.XAERO_MINIMAP;
            case "xaeroworldmap" -> ClientMod.XAERO_WORLD_MAP;
            case "plasmo" -> channel.equals("plasmo:voice") || channel.startsWith("plasmo:voice/")
                    ? ClientMod.PLASMO_VOICE : null;
            default -> null;
        };
        return candidate != null && CHANNEL_NAME.matcher(channel).matches()
                ? Optional.of(candidate) : Optional.empty();
    }
}
