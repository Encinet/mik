package org.encinet.mik.feature.player.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientModSignalsTest {
    @Test
    void clientAnnouncementsDetectEachModWithoutDependingOnTheLoader() {
        byte[] registration = text("fabric:registry/sync\0axiom:enable\0plasmo:voice/v2/installed\0"
                + "xaerominimap:main\0xaeroworldmap:main\0");
        assertEquals(EnumSet.allOf(RecommendedClientMod.class),
                ClientModSignals.fromPayload("minecraft:register", registration));
    }

    @Test
    void commonNetworkingV1AcceptsBothPhasesAndMultiByteLengths() {
        List<String> channels = List.of("other:" + "a".repeat(128), "xaerominimap:main", "xaeroworldmap:main");
        for (String phase : List.of("play", "configuration")) {
            assertEquals(Set.of(RecommendedClientMod.XAERO_MINIMAP, RecommendedClientMod.XAERO_WORLD_MAP),
                    ClientModSignals.fromPayload("c:register", commonRegistration(1, phase, channels)));
        }
    }

    @Test
    void handshakesAndPlasmoInstalledFlagsArePositiveEvenWithoutUdpConnectivity() {
        assertEquals(Set.of(RecommendedClientMod.AXIOM),
                ClientModSignals.fromPayload("axiom:hello", new byte[0]));
        assertEquals(Set.of(RecommendedClientMod.PLASMO_VOICE),
                ClientModSignals.fromPayload("plasmo:voice/v2/installed", new byte[0]));
        Set<RecommendedClientMod> announced = Set.of(RecommendedClientMod.PLASMO_VOICE);
        assertFalse(ClientModSignals.missing(announced, false).contains(RecommendedClientMod.PLASMO_VOICE));
        assertFalse(ClientModSignals.missing(Set.of(), true).contains(RecommendedClientMod.PLASMO_VOICE));
    }

    @Test
    void minimapAndWorldMapAreIndependentAndTheirSharedLibraryProvesNeitherIsInstalled() {
        var installed = ClientModSignals.fromPayload("minecraft:register", text("xaerominimap:main\0xaerolib:main"));
        assertEquals(Set.of(RecommendedClientMod.XAERO_MINIMAP), installed);
        assertTrue(ClientModSignals.missing(installed, false).contains(RecommendedClientMod.XAERO_WORLD_MAP));
        assertEquals(Set.of(RecommendedClientMod.XAERO_WORLD_MAP),
                ClientModSignals.fromPayload("xaeroworldmap:main", new byte[0]));
        assertTrue(ClientModSignals.fromPayload("xaerolib:main", new byte[0]).isEmpty());
    }

    @Test
    void brandNamesLoaderChannelsUnregisterAndUnrelatedTextDoNotProveModPresence() {
        for (String channel : List.of("minecraft:brand", "minecraft:unregister", "fabric:mods", "neoforge:mods")) {
            assertTrue(ClientModSignals.fromPayload(channel, text("axiom:hello\0plasmo:voice/v2")).isEmpty());
        }
        assertTrue(ClientModSignals.fromPayload("minecraft:register",
                text("fabric:networking\0neoforge:network\0other:axiom\0plasmo:other\0xaerominimap_extra:main")).isEmpty());
    }

    @Test
    void malformedChannelsAreIgnored() {
        for (String channel : List.of("axiom:", "AXIOM:hello", "axiom:hello:extra", " axiom:hello",
                "axiom:hello\0plasmo:voice/v2", "axiom:" + "a".repeat(256))) {
            assertTrue(RecommendedClientMod.fromChannel(channel).isEmpty(), channel);
        }
    }

    @Test
    void malformedCommonRegistrationsCannotProducePartialDetectionsOrThrow() {
        byte[] valid = commonRegistration(1, "play", List.of("axiom:hello", "xaerominimap:main"));
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertTrue(assertDoesNotThrow(() -> ClientModSignals.fromPayload("c:register", truncated)).isEmpty());
        }
        assertTrue(ClientModSignals.fromPayload("c:register", Arrays.copyOf(valid, valid.length + 1)).isEmpty());
        assertTrue(ClientModSignals.fromPayload("c:register",
                new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f}).isEmpty());
        assertTrue(ClientModSignals.fromPayload("c:register",
                commonRegistration(2, "play", List.of("axiom:hello"))).isEmpty());
        assertTrue(ClientModSignals.fromPayload("c:register",
                commonRegistration(1, "login", List.of("axiom:hello"))).isEmpty());
    }

    @Test
    void oversizedRegistrationsAreIgnoredAndCompleteClientsNeedNoReminder() {
        byte[] oversized = new byte[32768];
        System.arraycopy(text("axiom:hello\0"), 0, oversized, 0, text("axiom:hello\0").length);
        assertTrue(ClientModSignals.fromPayload("minecraft:register", oversized).isEmpty());
        assertTrue(ClientModSignals.missing(EnumSet.allOf(RecommendedClientMod.class), false).isEmpty());
        assertEquals(EnumSet.allOf(RecommendedClientMod.class), ClientModSignals.missing(Set.of(), false));
    }

    private static byte[] commonRegistration(int version, String phase, List<String> channels) {
        var bytes = new ByteArrayOutputStream();
        writeVarInt(bytes, version);
        writeString(bytes, phase);
        writeVarInt(bytes, channels.size());
        channels.forEach(channel -> writeString(bytes, channel));
        return bytes.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream bytes, String value) {
        byte[] encoded = text(value);
        writeVarInt(bytes, encoded.length);
        bytes.writeBytes(encoded);
    }

    private static void writeVarInt(ByteArrayOutputStream bytes, int value) {
        do {
            int next = value & 0x7f;
            value >>>= 7;
            bytes.write(value == 0 ? next : next | 0x80);
        } while (value != 0);
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
